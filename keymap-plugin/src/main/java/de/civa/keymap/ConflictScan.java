package de.civa.keymap;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Live conflict scan of a keymap.
 *
 * <p><b>External</b> conflicts are keystrokes the running macOS installation also claims. The
 * system side comes from the JBR {@code com.jetbrains.SystemShortcuts} API — the same source
 * IntelliJ's own "N shortcut conflicts with macOS" banner uses — accessed reflectively so the
 * plugin needs no compile-time JBR dependency and degrades gracefully on non-JetBrains runtimes.
 * The table is populated dynamically by macOS, so its contents (and therefore this scan) can vary
 * between queries; overlaps that never appear in it — key gestures, non-symbolic shortcuts — are
 * listed statically in {@link ConflictAdvice#SUPPLEMENT}. Shortcuts macOS exposes without a name
 * are kept, not skipped: an unnamed shortcut can still swallow the key (⌘Escape does), so hiding
 * it would hide a real breakage.</p>
 *
 * <p>Every overlap of the <em>selected</em> keymap is reported in {@link #keymapConflicts} — its
 * whole effective binding set (its own declarations plus everything inherited from parents), since
 * all of it is editable here (a read-only keymap is copied first). Each conflict keeps the source
 * of its actions ({@link ActionRef#source}) so bindings coming from another plugin or the IDE core
 * can be labelled. {@link #ownKeymap} is true only when the selected keymap <em>is</em> the keymap
 * this plugin bundles or a copy derived from it (matched along the parent chain); it gates the
 * curated, keymap-specific extras in {@link ConflictAdvice#SUPPLEMENT}.</p>
 *
 * <p><b>Internal</b> conflicts ({@link #internal}) are identical shortcuts carrying more than one
 * distinct action inside the keymap. They mirror IntelliJ's own {@link Keymap#getConflicts} check —
 * $Editor twins and use-shortcut-of aliases are filtered out — and are shown for reference only,
 * since whether two actions actually clash depends on runtime context that isn't statically knowable.</p>
 */
final class ConflictScan {
  private static final Logger LOG = Logger.getInstance(ConflictScan.class);
  /** The keymap this plugin bundles; the selected keymap counts as "ours" if it or a parent is it. */
  private static final String OWN_KEYMAP_NAME = "MacBook Pro DE";

  /** One macOS system shortcut as reported by the JetBrains Runtime. */
  record SystemShortcut(@Nullable String id, @Nullable String description) {
    String label() {
      if (description != null && !description.isEmpty()) return description;
      if (id != null && !id.isEmpty()) return id;
      return "an unnamed macOS shortcut";
    }
  }

  /** An IntelliJ action, with its name, providing plugin and description resolved where possible. */
  record ActionRef(String id, @Nullable String displayName, @Nullable String source,
                   @Nullable String description) {
    String label() {
      return displayName != null && !displayName.isEmpty() ? displayName : id;
    }
  }

  /** A keymap keystroke that macOS also claims. */
  record ExternalConflict(KeyStroke stroke, List<SystemShortcut> macOs, List<ActionRef> actions,
                          ConflictAdvice.Advice advice) {}

  /** One shortcut bound to more than one distinct action inside the keymap (aliases filtered out). */
  record InternalConflict(Shortcut shortcut, List<ActionRef> actions) {}

  /** A binding this keymap declares itself — its diff against the parent (via {@link KeymapImpl#writeScheme()}).
   *  An empty {@code shortcuts} list means the inherited binding is cleared here. */
  record ModifiedBinding(ActionRef action, List<Shortcut> shortcuts) {}

  final boolean jbrApiAvailable;
  /** True only for the keymap this plugin bundles or a copy of it — gates the curated extras. */
  final boolean ownKeymap;
  final List<ExternalConflict> keymapConflicts;
  final List<InternalConflict> internal;
  /** Bindings this keymap declares itself (its diff against the parent). */
  final List<ModifiedBinding> modified;

  private ConflictScan(boolean jbrApiAvailable, boolean ownKeymap, List<ExternalConflict> keymapConflicts,
                       List<InternalConflict> internal, List<ModifiedBinding> modified) {
    this.jbrApiAvailable = jbrApiAvailable;
    this.ownKeymap = ownKeymap;
    this.keymapConflicts = keymapConflicts;
    this.internal = internal;
    this.modified = modified;
  }

  static ConflictScan of(Keymap keymap) {
    Map<KeyStroke, List<SystemShortcut>> system = querySystemShortcuts();

    // macOS matches on the first keystroke (a chord loses its first key the same way); an internal
    // duplicate counts only when the whole shortcut (incl. second keystroke) is identical.
    Map<KeyStroke, List<String>> byFirstStroke = new HashMap<>();
    Map<Shortcut, List<String>> byShortcut = new HashMap<>();
    for (String actionId : keymap.getActionIdList()) {
      for (Shortcut shortcut : keymap.getShortcuts(actionId)) {
        if (!(shortcut instanceof KeyboardShortcut ks)) continue;
        byFirstStroke.computeIfAbsent(ks.getFirstKeyStroke(), k -> new ArrayList<>()).add(actionId);
        byShortcut.computeIfAbsent(ks, k -> new ArrayList<>()).add(actionId);
      }
    }

    List<ExternalConflict> keymapConflicts = new ArrayList<>();
    if (system != null) {
      for (Map.Entry<KeyStroke, List<String>> e : byFirstStroke.entrySet()) {
        List<SystemShortcut> macOs = system.get(e.getKey());
        if (macOs == null) continue;
        ConflictAdvice.Advice advice = ConflictAdvice.resolve(macOs, e.getKey());
        List<String> ids = e.getValue().stream().distinct().sorted().toList();
        keymapConflicts.add(new ExternalConflict(e.getKey(), macOs, refs(ids), advice));
      }
      keymapConflicts.sort(externalOrder());
    }

    List<InternalConflict> internal = new ArrayList<>();
    for (Map.Entry<Shortcut, List<String>> e : byShortcut.entrySet()) {
      List<String> ids = e.getValue().stream().distinct().sorted().toList();
      if (ids.size() < 2 || !(e.getKey() instanceof KeyboardShortcut ks)) continue;
      // Mirror IntelliJ's own conflict check: an action counts only if getConflicts reports a real
      // clash — that filters out $Editor twins and use-shortcut-of aliases (otherwise false positives).
      List<String> real = ids.stream().filter(id -> !keymap.getConflicts(id, ks).isEmpty()).toList();
      if (real.size() < 2) continue;
      internal.add(new InternalConflict(e.getKey(), refs(real)));
    }
    internal.sort(Comparator.comparingInt((InternalConflict c) -> -c.actions().size())
      .thenComparing(c -> KeymapUtil.getShortcutText(c.shortcut())));

    return new ConflictScan(system != null, isOurKeymap(keymap),
      List.copyOf(keymapConflicts), List.copyOf(internal), List.copyOf(modifiedBindings(keymap)));
  }

  private static Comparator<ExternalConflict> externalOrder() {
    return Comparator.comparingInt((ExternalConflict c) -> c.advice().category().ordinal())
      .thenComparing(c -> KeymapUtil.getKeystrokeText(c.stroke()));
  }

  /** True when the selected keymap, or any keymap up its parent chain, is the bundled one. */
  private static boolean isOurKeymap(Keymap keymap) {
    for (Keymap k = keymap; k != null; k = k.getParent()) {
      if (OWN_KEYMAP_NAME.equals(k.getName())) return true;
    }
    return false;
  }

  private static List<ActionRef> refs(List<String> ids) {
    ActionManager am = ActionManager.getInstance();
    List<ActionRef> result = new ArrayList<>(ids.size());
    for (String id : ids) {
      String name = null;
      String source = null;
      String description = null;
      AnAction action = am.getAction(id);
      if (action != null) {
        String text = action.getTemplateText();
        if (text != null && !text.isBlank()) name = text;
        source = sourceOf(action);
        description = action.getTemplatePresentation().getDescription();
      }
      result.add(new ActionRef(id, name, source, description));
    }
    return result;
  }

  /** Bindings this keymap declares itself — its diff against the parent, via {@link KeymapImpl#writeScheme()}.
   *  Cleared inherited bindings are included (an empty own-declaration yields an empty shortcut list). */
  private static List<ModifiedBinding> modifiedBindings(Keymap keymap) {
    if (!(keymap instanceof KeymapImpl impl)) return List.of();
    List<String> ids = new ArrayList<>();
    for (Object child : impl.writeScheme().getChildren("action")) {
      String id = ((Element) child).getAttributeValue("id");
      if (id != null && !id.isEmpty()) ids.add(id);
    }
    List<String> sorted = ids.stream().distinct().sorted().toList();
    List<ModifiedBinding> result = new ArrayList<>(sorted.size());
    for (ActionRef ref : refs(sorted)) {
      result.add(new ModifiedBinding(ref, List.of(keymap.getShortcuts(ref.id()))));
    }
    return result;
  }

  /** Which plugin provides an action — "IDE" for the platform core, else the plugin's name, or null. */
  private static @Nullable String sourceOf(AnAction action) {
    try {
      PluginDescriptor plugin = PluginManager.getPluginByClass(action.getClass());
      if (plugin == null || plugin.getPluginId() == null) return null;
      return "com.intellij".equals(plugin.getPluginId().getIdString()) ? "IDE" : plugin.getName();
    }
    catch (RuntimeException e) {
      return null;
    }
  }

  /** The live macOS shortcut table (keyed by keystroke), or {@code null} if the JBR API is unavailable. */
  static @Nullable Map<KeyStroke, List<SystemShortcut>> systemShortcuts() {
    return querySystemShortcuts();
  }

  /**
   * The macOS shortcut table keyed by keystroke, or {@code null} when the JBR API is unavailable.
   * Mirrors the platform's own mapping: prefer the extended key code derived from the reported
   * character (how JBR encodes character-addressed hotkeys), fall back to the raw key code.
   */
  private static @Nullable Map<KeyStroke, List<SystemShortcut>> querySystemShortcuts() {
    try {
      Object api = Class.forName("com.jetbrains.JBR").getMethod("getSystemShortcuts").invoke(null);
      if (api == null) return null;
      Object[] shortcuts = (Object[]) Class.forName("com.jetbrains.SystemShortcuts")
        .getMethod("querySystemShortcuts").invoke(api);
      if (shortcuts == null) return null;
      Class<?> shortcutClass = Class.forName("com.jetbrains.SystemShortcuts$Shortcut");
      Method getKeyCode = shortcutClass.getMethod("getKeyCode");
      Method getKeyChar = shortcutClass.getMethod("getKeyChar");
      Method getModifiers = shortcutClass.getMethod("getModifiers");
      Method getId = shortcutClass.getMethod("getId");
      Method getDescription = shortcutClass.getMethod("getDescription");

      Map<KeyStroke, List<SystemShortcut>> result = new HashMap<>();
      for (Object s : shortcuts) {
        char ch = (Character) getKeyChar.invoke(s);
        int code = ch != KeyEvent.CHAR_UNDEFINED
                   ? KeyEvent.getExtendedKeyCodeForChar(ch)
                   : (Integer) getKeyCode.invoke(s);
        if (code == KeyEvent.VK_UNDEFINED) continue;
        KeyStroke stroke = KeyStroke.getKeyStroke(code, (Integer) getModifiers.invoke(s));
        result.computeIfAbsent(stroke, k -> new ArrayList<>())
          .add(new SystemShortcut((String) getId.invoke(s), (String) getDescription.invoke(s)));
      }
      return result;
    }
    catch (ReflectiveOperationException | RuntimeException e) {
      LOG.info("JBR SystemShortcuts API unavailable, skipping macOS conflict scan: " + e);
      return null;
    }
  }
}

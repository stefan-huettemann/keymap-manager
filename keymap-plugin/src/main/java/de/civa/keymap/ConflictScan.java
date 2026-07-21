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
import org.jetbrains.annotations.Nullable;

import javax.swing.KeyStroke;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.event.KeyEvent;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

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
 * <p>For the keymap this plugin bundles, external conflicts are split by ownership: {@link
 * #keymapConflicts} are the bindings it curates, {@link #outsideConflicts} are bindings from other
 * plugins or the IDE that happen to sit on a contested key (we can only explain those). The split is
 * keyed to that keymap by name; for any other selected keymap it does not apply and every overlap is
 * reported as the keymap's own ({@link #outsideConflicts} stays empty).</p>
 *
 * <p><b>Internal</b> conflicts ({@link #internal}) are identical shortcuts carrying more than one
 * action inside the keymap. Almost always deliberate — the actions live in mutually exclusive UI
 * contexts — so they are shown for reference, not flagged.</p>
 */
final class ConflictScan {
  private static final Logger LOG = Logger.getInstance(ConflictScan.class);
  private static final String OWN_KEYMAP_RESOURCE = "/keymaps/MacBook Pro DE.xml";

  /** One macOS system shortcut as reported by the JetBrains Runtime. */
  record SystemShortcut(@Nullable String id, @Nullable String description) {
    String label() {
      if (description != null && !description.isEmpty()) return description;
      if (id != null && !id.isEmpty()) return id;
      return "an unnamed macOS shortcut";
    }
  }

  /** An IntelliJ action, with its human-readable name and providing plugin resolved where possible. */
  record ActionRef(String id, @Nullable String displayName, @Nullable String source) {
    String label() {
      return displayName != null && !displayName.isEmpty() ? displayName : id;
    }
  }

  /** A keymap keystroke that macOS also claims, narrowed to actions of one ownership. */
  record ExternalConflict(KeyStroke stroke, List<SystemShortcut> macOs, List<ActionRef> actions,
                          ConflictAdvice.Advice advice) {}

  /** One shortcut bound to more than one action inside the keymap. */
  record InternalConflict(Shortcut shortcut, List<ActionRef> actions) {}

  final boolean jbrApiAvailable;
  /** True only for the keymap this plugin bundles — gates the curated, keymap-specific extras. */
  final boolean ownKeymap;
  final List<ExternalConflict> keymapConflicts;
  final List<ExternalConflict> outsideConflicts;
  final List<InternalConflict> internal;

  private ConflictScan(boolean jbrApiAvailable, boolean ownKeymap, List<ExternalConflict> keymapConflicts,
                       List<ExternalConflict> outsideConflicts, List<InternalConflict> internal) {
    this.jbrApiAvailable = jbrApiAvailable;
    this.ownKeymap = ownKeymap;
    this.keymapConflicts = keymapConflicts;
    this.outsideConflicts = outsideConflicts;
    this.internal = internal;
  }

  static ConflictScan of(Keymap keymap) {
    Map<KeyStroke, List<SystemShortcut>> system = querySystemShortcuts();
    // Non-null only when this is the keymap the plugin bundles; then it splits our curated bindings
    // from platform/plugin ones. For any other keymap it is null: every overlap is that keymap's own.
    Set<String> ownActions = ownActionIdsFor(keymap);

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
    List<ExternalConflict> outsideConflicts = new ArrayList<>();
    if (system != null) {
      for (Map.Entry<KeyStroke, List<String>> e : byFirstStroke.entrySet()) {
        List<SystemShortcut> macOs = system.get(e.getKey());
        if (macOs == null) continue;
        ConflictAdvice.Advice advice = ConflictAdvice.resolve(macOs, e.getKey());
        List<String> ids = e.getValue().stream().distinct().sorted().toList();
        if (ownActions == null) {
          keymapConflicts.add(new ExternalConflict(e.getKey(), macOs, refs(ids), advice));
          continue;
        }
        List<ActionRef> ours = refs(ids.stream().filter(ownActions::contains).toList());
        List<ActionRef> theirs = refs(ids.stream().filter(id -> !ownActions.contains(id)).toList());
        if (!ours.isEmpty()) keymapConflicts.add(new ExternalConflict(e.getKey(), macOs, ours, advice));
        if (!theirs.isEmpty()) outsideConflicts.add(new ExternalConflict(e.getKey(), macOs, theirs, advice));
      }
      keymapConflicts.sort(externalOrder());
      outsideConflicts.sort(externalOrder());
    }

    List<InternalConflict> internal = new ArrayList<>();
    for (Map.Entry<Shortcut, List<String>> e : byShortcut.entrySet()) {
      List<String> ids = e.getValue().stream().distinct().sorted().toList();
      if (ids.size() < 2) continue;
      internal.add(new InternalConflict(e.getKey(), refs(ids)));
    }
    internal.sort(Comparator.comparingInt((InternalConflict c) -> -c.actions().size())
      .thenComparing(c -> KeymapUtil.getShortcutText(c.shortcut())));

    return new ConflictScan(system != null, ownActions != null, List.copyOf(keymapConflicts),
      List.copyOf(outsideConflicts), List.copyOf(internal));
  }

  private static Comparator<ExternalConflict> externalOrder() {
    return Comparator.comparingInt((ExternalConflict c) -> c.advice().category().ordinal())
      .thenComparing(c -> KeymapUtil.getKeystrokeText(c.stroke()));
  }

  private static List<ActionRef> refs(List<String> ids) {
    ActionManager am = ActionManager.getInstance();
    List<ActionRef> result = new ArrayList<>(ids.size());
    for (String id : ids) {
      String name = null;
      String source = null;
      AnAction action = am.getAction(id);
      if (action != null) {
        String text = action.getTemplateText();
        if (text != null && !text.isBlank()) name = text;
        source = sourceOf(action);
      }
      result.add(new ActionRef(id, name, source));
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

  /**
   * The action ids the plugin's bundled keymap binds directly — but only when {@code keymap} is that
   * keymap (matched by name against the bundled resource). Returns {@code null} for every other
   * keymap: the ownership split is a curated feature of our keymap, so applying our action set to a
   * different keymap would mislabel its bindings. A {@code null} result means "treat all as own".
   */
  private static @Nullable Set<String> ownActionIdsFor(Keymap keymap) {
    try (InputStream in = ConflictScan.class.getResourceAsStream(OWN_KEYMAP_RESOURCE)) {
      if (in == null) {
        LOG.warn("Bundled keymap resource not found: " + OWN_KEYMAP_RESOURCE);
        return null;
      }
      var factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(false);
      Element root = factory.newDocumentBuilder().parse(in).getDocumentElement();
      if (!keymap.getName().equals(root.getAttribute("name"))) {
        return null;  // a different keymap is selected — don't split against our action set
      }
      Set<String> ids = new HashSet<>();
      NodeList actions = root.getElementsByTagName("action");
      for (int i = 0; i < actions.getLength(); i++) {
        Element action = (Element) actions.item(i);
        // Only a binding counts as "ours"; an empty <action id=".."/> just clears the parent.
        if (action.getElementsByTagName("keyboard-shortcut").getLength() > 0) {
          ids.add(action.getAttribute("id"));
        }
      }
      return ids;
    }
    catch (Exception e) {
      LOG.warn("Could not read bundled keymap for ownership split: " + e);
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

package de.civa.keymap;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.keymap.Keymap;
import org.jetbrains.annotations.Nullable;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Suggests a practical, non-conflicting shortcut for one or more actions that are about to share a
 * new keystroke (the same "move several actions onto one key" scope the bulk rebind already
 * supports). Searches the keys a German-layout MacBook Pro can actually press ({@code keymaps/keys.md})
 * crossed with modifier combinations, cheapest first, and stops at the first candidate that has no
 * macOS system-shortcut overlap and is either genuinely unused in the keymap or — failing that —
 * shares the key only with actions {@link Keymap#getConflicts} itself does not consider a real clash
 * (alias/twin bindings, the same filter {@link ConflictScan} uses for "Double-bound keys"). When even
 * that runs out, falls back to the least-crowded macOS-clean candidate as an explicit best guess.
 */
final class ShortcutSuggester {
  /** {@code free} is true for a genuinely unused keystroke; false marks a best-guess double-bind —
   *  {@code sharedWith} then names the action(s) already on it, for the caller to disclose. */
  record Suggestion(KeyStroke stroke, boolean free, List<String> sharedWith) {}

  // Ascending modifier complexity. Command and Control lead alone since they are this keymap's two
  // primary modifiers; Option trails since it doubles as the German dead-key/special-character
  // modifier and is less conventional as a lone action modifier.
  private static final int[] MODIFIER_TIERS = {
    InputEvent.META_DOWN_MASK,
    InputEvent.CTRL_DOWN_MASK,
    InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
    InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
    InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK,
    InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK,
    InputEvent.META_DOWN_MASK | InputEvent.CTRL_DOWN_MASK,
    InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
    InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
    InputEvent.META_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
    InputEvent.META_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK,
    InputEvent.META_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
    InputEvent.SHIFT_DOWN_MASK,
    InputEvent.ALT_DOWN_MASK,
    InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
  };

  // Reachable German-MacBook keys (keymaps/keys.md "Bindable German keys" / "Keys" tables), most
  // mnemonic first: letters, then digits, then the seven German-layout extras, then function and
  // navigation keys. The "special-purpose" keys (Enter/Tab/Space/Escape/Backspace/Delete) are
  // deliberately last — technically reachable, but repurposing them for an arbitrary action is a bad
  // suggestion even when free.
  private static final int[] LETTERS = range(KeyEvent.VK_A, KeyEvent.VK_Z);
  private static final int[] DIGITS = range(KeyEvent.VK_0, KeyEvent.VK_9);
  // Ä Ö Ü ß, then + # < — extended codes copied verbatim from keys.md; never derive by hand (Ü breaks
  // the uppercase-codepoint pattern the other three follow).
  private static final int[] GERMAN_EXTRAS = {0x010000C4, 0x010000D6, 0x010000FC, 0x010000DF, 0x209, 0x208, 0x99};
  private static final int[] NAV_AND_FUNCTION = {
    KeyEvent.VK_F1, KeyEvent.VK_F2, KeyEvent.VK_F3, KeyEvent.VK_F4, KeyEvent.VK_F5, KeyEvent.VK_F6,
    KeyEvent.VK_F7, KeyEvent.VK_F8, KeyEvent.VK_F9, KeyEvent.VK_F10, KeyEvent.VK_F12,
    KeyEvent.VK_COMMA, KeyEvent.VK_PERIOD, KeyEvent.VK_MINUS,
    KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT,
    KeyEvent.VK_HOME, KeyEvent.VK_END, KeyEvent.VK_PAGE_UP, KeyEvent.VK_PAGE_DOWN,
  };
  private static final int[] LAST_RESORT = {
    KeyEvent.VK_ENTER, KeyEvent.VK_TAB, KeyEvent.VK_SPACE, KeyEvent.VK_ESCAPE,
    KeyEvent.VK_BACK_SPACE, KeyEvent.VK_DELETE,
  };

  private ShortcutSuggester() {}

  /** The best candidate for moving {@code movingIds} onto a new, shared keystroke — {@code null} only
   *  if every reachable key/modifier combination is somehow already a real macOS conflict. */
  static @Nullable Suggestion suggest(Keymap keymap, Set<String> movingIds) {
    List<Integer> keyOrder = keyOrder(movingIds);
    Map<KeyStroke, List<ConflictScan.SystemShortcut>> system = ConflictScan.systemShortcuts();

    Suggestion bestFallback = null;
    int bestFallbackCrowd = Integer.MAX_VALUE;
    for (int mods : MODIFIER_TIERS) {
      for (int code : keyOrder) {
        KeyStroke ks = KeyStroke.getKeyStroke(code, mods);
        if (system != null && system.containsKey(ks)) continue;  // macOS overlap: never suggest it
        List<String> holders = new ArrayList<>();
        for (String id : keymap.getActionIds(ks)) {
          if (!movingIds.contains(id)) holders.add(id);
        }
        if (holders.isEmpty()) return new Suggestion(ks, true, List.of());
        // Best guess: does the platform's own conflict check (alias/twin-aware) consider this real?
        KeyboardShortcut candidate = new KeyboardShortcut(ks, null);
        boolean realClash = holders.stream().anyMatch(id -> !keymap.getConflicts(id, candidate).isEmpty());
        if (!realClash && holders.size() < bestFallbackCrowd) {
          bestFallback = new Suggestion(ks, false, List.copyOf(holders));
          bestFallbackCrowd = holders.size();
        }
      }
    }
    return bestFallback;
  }

  private static List<Integer> keyOrder(Set<String> movingIds) {
    LinkedHashSet<Integer> order = new LinkedHashSet<>();
    Integer mnemonic = mnemonicLetter(movingIds);
    if (mnemonic != null) order.add(mnemonic);
    for (int c : LETTERS) order.add(c);
    for (int c : DIGITS) order.add(c);
    for (int c : GERMAN_EXTRAS) order.add(c);
    for (int c : NAV_AND_FUNCTION) order.add(c);
    for (int c : LAST_RESORT) order.add(c);
    return List.copyOf(order);
  }

  /** The first letter of the primary action's display name, if it starts with one — tried first since
   *  a mnemonic key (Command-R for "Run", say) is the most memorable pick when it happens to be free. */
  private static @Nullable Integer mnemonicLetter(Set<String> movingIds) {
    if (movingIds.isEmpty()) return null;
    String id = movingIds.iterator().next();
    AnAction action = ActionManager.getInstance().getAction(id);
    String text = action != null ? action.getTemplateText() : null;
    if (text == null || text.isEmpty()) return null;
    char c = Character.toUpperCase(text.charAt(0));
    return c >= 'A' && c <= 'Z' ? KeyEvent.getExtendedKeyCodeForChar(c) : null;
  }

  private static int[] range(int from, int to) {
    int[] r = new int[to - from + 1];
    for (int i = 0; i < r.length; i++) r[i] = from + i;
    return r;
  }
}

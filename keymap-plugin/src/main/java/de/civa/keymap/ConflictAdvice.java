package de.civa.keymap;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Curated, end-user-facing knowledge about macOS shortcut overlaps: what a detected conflict means
 * and what to do about it. Detection is live ({@link ConflictScan}); this class only annotates.
 * Entries are keyed by the macOS shortcut id reported by JBR, with a keystroke fallback for
 * machine-dependent hotkeys whose ids are unverified. Detected overlaps with no entry render as
 * {@link Category#UNCLASSIFIED}.
 *
 * <p>Every {@code note} here is shown verbatim to the user, so it stays plain-language and free of
 * internal references. (Dev cross-reference: keymaps/keys.md → Appendix C.)</p>
 */
final class ConflictAdvice {

  /** Declaration order is display order — the ones that actually break come first. */
  enum Category {
    RESOLVE("Needs attention — macOS takes this key first"),
    UNCLASSIFIED("Needs attention — macOS may take this key"),
    DELIBERATE("Works as-is — {ide} gets the key while focused");

    final String title;
    Category(String title) { this.title = title; }
  }

  record Advice(Category category, String note) {}

  /** A real overlap the live scan cannot see — shown statically in the report. {@code actionId} is the
   *  IDE action bound to this key (null if none), so the report can still offer Rebind/Remove on it. */
  record Supplement(String keys, String ideaSide, String macSide, String note, String actionId) {}

  private static final Map<String, Advice> BY_ID = new HashMap<>();
  private static final Map<KeyStroke, Advice> BY_STROKE = new HashMap<>();

  static {
    Advice windowManagement = new Advice(Category.DELIBERATE,
      "macOS uses this key to move or resize windows. {ide} receives it first while its window "
      + "is in front, so your {ide} shortcut keeps working. If you prefer the macOS window "
      + "action, change it under System Settings → Keyboard → Keyboard Shortcuts → Windows.");
    for (String id : List.of("FillWindow", "CenterWindow", "RestoreWindow",
                             "TileLeftHalf", "TileRightHalf", "TileTopHalf", "TileBottomHalf",
                             "ArrangeLeftRight", "ArrangeRightLeft", "ArrangeTopBottom", "ArrangeBottomTop")) {
      BY_ID.put(id, windowManagement);
    }
    BY_ID.put("MinimizeWindow", new Advice(Category.DELIBERATE,
      "Both macOS and {ide} minimize the window on ⌘M — the same thing on both sides, so there "
      + "is nothing to fix."));
    BY_ID.put("ShowContextualMenu", new Advice(Category.DELIBERATE,
      "macOS can open the context menu on this key, but {ide} receives it first while its "
      + "window is in front, so your {ide} shortcut keeps working. To free the key for macOS, "
      + "change it under System Settings → Keyboard → Keyboard Shortcuts → Keyboard."));

    Advice appWindowCycle = new Advice(Category.DELIBERATE,
      "{ide} switches between its project windows on this key, which is the same idea as the "
      + "macOS \"move focus between windows\" shortcut — and {ide} handles it first for its own "
      + "windows. Nothing to change.");
    BY_ID.put("FocusNextApplicationWindow", appWindowCycle);
    BY_ID.put("FocusPreviousApplicationWindow", appWindowCycle);

    Advice spaces = new Advice(Category.RESOLVE,
      "macOS switches Spaces on this key across the whole system, so the {ide} shortcut never "
      + "fires. Give the {ide} action a different shortcut, or change the macOS one under "
      + "System Settings → Keyboard → Keyboard Shortcuts → Mission Control.");
    BY_ID.put("SwitchToDesktopLeft", spaces);
    BY_ID.put("SwitchToDesktopRight", spaces);

    Advice missionControl = new Advice(Category.RESOLVE,
      "macOS uses this key for Mission Control across the whole system, so the {ide} shortcut "
      + "never fires. Give the {ide} action a different shortcut, or change the macOS one under "
      + "System Settings → Keyboard → Keyboard Shortcuts → Mission Control.");
    for (String id : List.of("ShowAllWindows", "ShowApplicationWindows", "ShowDesktop")) {
      BY_ID.put(id, missionControl);
    }

    Advice desktopSwitch = new Advice(Category.RESOLVE,
      "macOS switches desktops on this key (turned on automatically once you add a Space), so it "
      + "wins over {ide}. Give the {ide} action a different shortcut, change desktop "
      + "switching under System Settings → Keyboard → Keyboard Shortcuts → Mission Control, or "
      + "reach the {ide} action from the menu.");
    for (int i = 1; i <= 9; i++) {
      BY_ID.put("SwitchToDesktop" + i, desktopSwitch);
      BY_STROKE.put(KeyStroke.getKeyStroke(KeyEvent.VK_0 + i, InputEvent.CTRL_DOWN_MASK), desktopSwitch);
    }

    Advice keyboardAccess = new Advice(Category.RESOLVE,
      "macOS uses this key for keyboard navigation across the whole system, so the {ide} "
      + "shortcut never fires. Give the {ide} action a different shortcut, or turn the macOS "
      + "one off under System Settings → Keyboard → Keyboard Shortcuts → Keyboard.");
    for (String id : List.of("FocusMenuBar", "FocusDock", "FocusActiveWindow", "FocusToolbar",
                             "FocusFloatingWindow", "FocusStatusMenu", "ToggleKeyboardAccess", "ChangeTabMode")) {
      BY_ID.put(id, keyboardAccess);
    }

    Advice screenshots = new Advice(Category.RESOLVE,
      "macOS takes a screenshot on this key across the whole system, so the {ide} shortcut "
      + "never fires. Give the {ide} action a different shortcut, or change screenshots under "
      + "System Settings → Keyboard → Keyboard Shortcuts → Screenshots.");
    for (String id : List.of("ScreenshotToFile", "ScreenshotToClipboard", "ScreenshotAreaToFile",
                             "ScreenshotAreaToClipboard", "ScreenshotOptions")) {
      BY_ID.put(id, screenshots);
    }

    BY_ID.put("ShowSpotlight", new Advice(Category.RESOLVE,
      "macOS opens Spotlight on this key, so the {ide} shortcut never fires. Give the {ide} "
      + "action a different shortcut, or change Spotlight under System Settings → Keyboard → "
      + "Keyboard Shortcuts → Spotlight."));
    BY_ID.put("ShowFinderSearch", new Advice(Category.RESOLVE,
      "macOS opens a Finder search window on this key, so the {ide} shortcut never fires. Give "
      + "the {ide} action a different shortcut, or change it under System Settings → Keyboard → "
      + "Keyboard Shortcuts → Spotlight."));
    BY_ID.put("ShowNotificationCenter", new Advice(Category.RESOLVE,
      "macOS opens Notification Center on this key, so the {ide} shortcut never fires. Give the "
      + "{ide} action a different shortcut, or change it under System Settings → Keyboard → "
      + "Keyboard Shortcuts → Mission Control."));
    BY_ID.put("ToggleDockHiding", new Advice(Category.RESOLVE,
      "macOS shows or hides the Dock on this key, so the {ide} shortcut never fires. Give the "
      + "{ide} action a different shortcut, or change it under System Settings → Keyboard → "
      + "Keyboard Shortcuts → Launchpad & Dock."));
    BY_ID.put("ShowAccessibilityControls", new Advice(Category.RESOLVE,
      "macOS opens the Accessibility controls on this key, so the {ide} shortcut never fires. "
      + "Give the {ide} action a different shortcut, or change it under System Settings → "
      + "Keyboard → Keyboard Shortcuts → Accessibility."));

    Advice inputSources = new Advice(Category.RESOLVE,
      "macOS switches input sources on this key (turned on automatically once you add a second "
      + "input source) and wins over {ide}. Keep the {ide} shortcut and turn the macOS one "
      + "off under System Settings → Keyboard → Keyboard Shortcuts → Input Sources — this is "
      + "JetBrains' own recommendation.");
    BY_STROKE.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK), inputSources);
    BY_STROKE.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK), inputSources);

    BY_STROKE.put(KeyStroke.getKeyStroke(KeyEvent.VK_8, InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK),
      new Advice(Category.RESOLVE,
        "macOS zoom uses this key, but only while zoom shortcuts are turned on (off by default). "
        + "If it shows up here, macOS is taking the key — turn zoom off under System Settings → "
        + "Accessibility → Zoom, or give the {ide} action a different shortcut."));
  }

  /**
   * Best advice for a detected overlap: most severe match by macOS shortcut id, then by
   * keystroke, else {@link Category#UNCLASSIFIED}.
   */
  static Advice resolve(List<ConflictScan.SystemShortcut> macOs, KeyStroke stroke) {
    Advice best = null;
    for (ConflictScan.SystemShortcut s : macOs) {
      Advice a = s.id() == null ? null : BY_ID.get(s.id());
      if (a != null && (best == null || a.category().ordinal() < best.category().ordinal())) best = a;
    }
    Advice byStroke = BY_STROKE.get(stroke);
    if (byStroke != null && (best == null || byStroke.category().ordinal() < best.category().ordinal())) best = byStroke;
    return best != null ? best : new Advice(Category.UNCLASSIFIED,
      "macOS reserves this key under a shortcut with no public name, and it can take the key "
      + "before {ide} — so the bound action may quietly never fire (⌘Escape behaves this way). "
      + "Give the {ide} action a different shortcut, or find and turn off the macOS shortcut "
      + "using this key in System Settings → Keyboard.");
  }

  /** Real overlaps the JBR shortcut table cannot represent. */
  static final List<Supplement> SUPPLEMENT = List.of(
    new Supplement("⌃⌘Space", "Emoji & Symbols", "Emoji & Symbols viewer",
      "Both sides open the Emoji & Symbols viewer — the same thing, nothing to fix. macOS doesn't "
      + "list this one in the table the scan reads, so it's noted here instead of detected live.",
      "EmojiAndSymbols"),
    new Supplement("double-⌃", "Run Anything (double-tap ⌃)", "Dictation (optional)",
      "If you set dictation to a double-tap of Control, it takes over the Run Anything gesture. "
      + "Keep dictation on the 🌐 (fn) key or off. Tap-gestures can't be detected by the scan.",
      "RunAnything"));

  private ConflictAdvice() {}
}

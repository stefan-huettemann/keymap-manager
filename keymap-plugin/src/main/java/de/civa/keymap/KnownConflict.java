package de.civa.keymap;

import java.util.List;

/**
 * A shortcut of the MacBook Pro DE keymap that overlaps a macOS system shortcut.
 * These are known at build time (the keymap ships with the plugin), so instead of
 * IDEA's bare "N conflicts with macOS" count we can explain each one and say whether
 * it needs action.
 *
 * @see #ALL the curated list (see keymaps/keys.md → Appendix C)
 */
public record KnownConflict(String keys, String macOsFunction, String ideaAction,
                            Category category, String note) {

  public enum Category {
    /** Same concept on both sides — the overlap is intentional, no action needed. */
    ALIGNED("Aligned by design — no action needed"),
    /** Collides only because of a non-default macOS setting on this machine. */
    MACHINE_SETTING("Depends on your macOS settings — resolve if the shortcut misbehaves"),
    /** Stock-macOS default that bites other users even though it may be off here. */
    WATCHLIST("Stock-macOS defaults — may affect other machines");

    public final String title;
    Category(String title) { this.title = title; }
  }

  /** Curated conflict list. Keep in sync with keys.md → Appendix C. */
  public static final List<KnownConflict> ALL = List.of(
    new KnownConflict("⌘< / ⌘⇧<", "Cycle app windows forward/backward",
      "NextProjectWindow / PreviousProjectWindow", Category.ALIGNED,
      "IDEA cycles project windows — same concept; IDEA handles it before macOS."),
    new KnownConflict("⌃⌘Space", "Emoji & Symbols viewer",
      "EmojiAndSymbols", Category.ALIGNED,
      "Identical function on both sides."),
    new KnownConflict("⌘M / ⌘W / ⌘Q", "Minimize / Close / Quit window",
      "MinimizeCurrentWindow / CloseContent / Exit", Category.ALIGNED,
      "Standard macOS app conventions, deliberately kept."),
    new KnownConflict("⌘, / ⌃⌘F", "(App preferences) / Full screen",
      "ShowSettings / ToggleFullScreen", Category.ALIGNED,
      "Standard macOS app conventions, deliberately kept."),

    new KnownConflict("⌃Space / ⌃⌥Space", "Select previous / next input source",
      "CodeCompletion / ClassNameCompletion", Category.MACHINE_SETTING,
      "Auto-enabled by macOS when you have more than one input source. Keep the IDEA "
      + "binding and disable the macOS shortcut: System Settings → Keyboard → "
      + "Keyboard Shortcuts → Input Sources."),
    new KnownConflict("⌃1…⌃9", "Switch to Desktop 1…9",
      "GotoBookmark1…9", Category.MACHINE_SETTING,
      "Auto-enabled per additional Space. Remap desktop switching (System Settings → "
      + "Keyboard → Keyboard Shortcuts → Mission Control) or reach bookmarks via the menu."),
    new KnownConflict("⌥⌘8", "Accessibility zoom toggle",
      "ActivateUnitTestsToolWindow", Category.WATCHLIST,
      "Zoom is off by default (and off on this machine); only bites if you enable it."),
    new KnownConflict("double-⌃", "Dictation",
      "RunAnything (gesture)", Category.WATCHLIST,
      "If you set dictation to double-Control it shadows the RunAnything gesture; "
      + "keep dictation on the 🌐 (fn) key or off.")
  );
}

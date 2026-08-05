# Keys used in `MacBook Pro DE.xml`

Source: `keymaps/MacBook Pro DE.xml` — a **delta keymap**: 58 declared actions on top of
`parent="Mac OS X 10.5+"`, yielding **423 bound actions / 463 keystrokes** effectively. Target hardware: MacBook Pro
with **German (T1 / ISO) layout**, no numpad.

The tables below audit the **effective** keymap (own declarations plus everything inherited through the parent chain),
which is what the IDE actually dispatches — not the declared 58.

**Why keys can be "unreachable":** IntelliJ shortcuts address *physical keys* (AWT virtual key codes), not the
characters they produce. `meta SLASH` means the key `VK_SLASH` — which does not exist on a German keyboard, where `/` is
only typed as `⇧7`. Pressing `⌘⇧7` delivers `meta shift 7`, never `meta SLASH`, so the shortcut can never fire.

Legend:

| Mark      | Meaning                                                                                                                     |
|-----------|-----------------------------------------------------------------------------------------------------------------------------|
| ✅        | Reachable on a German MacBook Pro keyboard                                                                                  |
| ❌ DE     | **Unreachable on German layout** — the character exists only via `⇧`/`⌥` chord, there is no physical key with this key code |
| ❌ Mac    | **No such key on any Mac keyboard**                                                                                         |
| ⚠️ numpad | Numpad key — needs a full-size keyboard, **not present on a MacBook Pro**                                                   |

Mouse and Force-touch shortcuts are not keys and are omitted.

## The inheritance rule that governs this keymap

`Mac OS X 10.5+` is a **`MacOSDefaultKeymap`**. Every shortcut it inherits from `$default` has **Ctrl and Meta
swapped** — `MacOSDefaultKeymap.mapModifiers`, applied by `KeymapImpl.getParentShortcuts`
(`KeymapImpl.kt:245`, `.map { convertShortcut(it) }`). The conversion runs **only on shortcuts pulled from a parent**;
a keymap's own XML declarations are used verbatim.

That is why `Mac OS X 10.5+.xml` does not declare `$Paste` at all: it inherits `$default`'s `control V` and presents it
as **⌘V**. It *does* declare `$Undo` as `meta Z`, which is therefore taken as-is.

Two consequences that this keymap depends on:

- **Never copy a `$default` row into this file verbatim.** You get the Windows form. This keymap shipped `⌃V` for Paste
  from its creation until 2026-08-05 for exactly that reason — see "Regression history" below.
- **This keymap's name must not start with `Mac OS X`** (a separate hard invariant: that prefix selects
  `MacOSDefaultKeymap` and would convert its *own* declarations). So it performs no conversion itself, and
  **inheritance is the only correct route to a macOS modifier form**. Declare an action here only to deviate.

## Modifier keys

| Token in XML       | macOS key | Symbol | Uses | Status |
|--------------------|-----------|--------|------|--------|
| `meta`             | Command   | ⌘      | 207  | ✅     |
| `shift`            | Shift     | ⇧      | 150  | ✅     |
| `alt`              | Option    | ⌥      | 126  | ✅     |
| `control` / `ctrl` | Control   | ⌃      | 119  | ✅     |

Two gesture shortcuts use a modifier as the *key* itself: double-tap ⌃ (`RunAnything`) and double-tap ⇧
(`SearchEverywhere`). Both ✅.

## Keys

**No unreachable key code appears anywhere in the effective keymap** (machine-verified 2026-08-05). The formerly
offending codes are listed for reference with the substitute now carrying them.

| Token in XML                     | Key on a macOS keyboard              | Uses | Status | Note                                                                            |
|----------------------------------|--------------------------------------|------|--------|---------------------------------------------------------------------------------|
| `A` – `Z` (all 26 used)          | letter keys                          | 146  | ✅     | German QWERTZ swaps Y/Z positions, but both keys exist — bindings work          |
| `0` – `9` (all used)             | digit row                            | 51   | ✅     | the digits themselves are fine; only their *shifted* characters are unreachable |
| `F1`–`F10`, `F12` (`F11` unused) | function row                         | 65   | ✅     | may require `Fn` or the "Use F1, F2, etc. as standard function keys" setting    |
| `UP` / `DOWN` / `LEFT` / `RIGHT` | arrow keys ↑ ↓ ← →                   | 49   | ✅     |                                                                                 |
| `ENTER`                          | Return ↩                             | 23   | ✅     |                                                                                 |
| `TAB`                            | Tab ⇥                                | 22   | ✅     |                                                                                 |
| `PAGE_UP` / `PAGE_DOWN`          | ⇞ / ⇟ — on MacBook `Fn ↑` / `Fn ↓`   | 10   | ✅     |                                                                                 |
| `HOME` / `END`                   | ↖ / ↘ — on MacBook `Fn ←` / `Fn →`   | 9    | ✅     |                                                                                 |
| `BACK_SPACE`                     | Delete ⌫                             | 9    | ✅     |                                                                                 |
| `SPACE`                          | Space                                | 7    | ✅     |                                                                                 |
| `DELETE`                         | Forward Delete ⌦ — on MacBook `Fn ⌫` | 6    | ✅     |                                                                                 |
| `ESCAPE`                         | Esc ⎋                                | 4    | ✅     |                                                                                 |
| `PERIOD`                         | `.`                                  | 5    | ✅     | same physical key on German layout                                              |
| `COMMA`                          | `,`                                  | 3    | ✅     | same physical key on German layout                                              |
| `MINUS`                          | `-`                                  | 7    | ✅     | German `-` sits right of `.` (where US `/` is) — key code matches               |
| `PLUS`                           | `+`                                  | 9    | ✅     | dedicated German key right of `Ü`                                               |
| `NUMBER_SIGN`                    | `#`                                  | 6    | ✅     | dedicated German key right of `Ä`                                               |
| `#10000fc` / `#10000c4`          | `Ü` / `Ä`                            | 11/5 | ✅     |                                                                                 |
| `#10000d6` / `#10000df`          | `Ö` / `ß`                            | 5/3  | ✅     |                                                                                 |
| `LESS`                           | `<`                                  | 2    | ✅     | ISO key left of `Y`                                                             |
| ~~`SLASH`, `DIVIDE`~~            | `/`                                  | 0    | ❌ DE  | retired → `#` (`NUMBER_SIGN`)                                                   |
| ~~`EQUALS`, `ADD`~~              | `=`, numpad `+`                      | 0    | ❌ DE  | retired → `+` (`PLUS`)                                                          |
| ~~`SUBTRACT`~~                   | numpad `-`                           | 0    | ⚠️     | retired → `-` (`MINUS`)                                                         |
| ~~`MULTIPLY`~~                   | numpad `*`                           | 0    | ⚠️     | retired → `⇧+`, fold prefixes → `Ü`                                             |
| ~~`OPEN_/CLOSE_BRACKET`~~        | `[` / `]`                            | 0    | ❌ DE  | retired → `Ö` / `Ä`                                                             |
| ~~`BACK_QUOTE`~~                 | `` ` ``                              | 0    | ❌ DE  | retired → `ß` (editor windows) / `<` (project windows)                           |
| ~~`SEMICOLON`~~                  | `;`                                  | 0    | ❌ DE  | retired → `⇧,`                                                                  |
| ~~`QUOTE`~~                      | `'`                                  | 0    | ❌ DE  | retired → `⇧#`                                                                  |
| ~~`INSERT`~~                     | —                                    | 0    | ❌ Mac | retired per action (see Appendix A)                                             |
| ~~`NUMPAD1`–`NUMPAD5`~~          | numpad digits                        | 0    | ⚠️     | retired — digit row carries the second keystroke                                 |

`BACK_SLASH` was never used by any of the three keymaps.

## Bindable German keys — free shortcut real estate

The German T1 layout has physical keys that stock US-ANSI keymaps never bind — some don't exist on a US keyboard at all.
Stock `Mac OS X 10.5+` uses none of them; **all seven carry this keymap's rebinds.**

> **Runtime requirement:** the JetBrains Runtime only reports these keys while national keyboard layout support is
> active — JBR property `com.sun.awt.use.national.layouts`, **`true` by default on macOS**, surfaced as
> Settings → Keymap → "Use national keyboard layouts for shortcuts" (backed by the platform API
> `NationalKeyboardSupport`, persisted in `ui.lnf.xml`; changing it requires a restart). Plugin 1.1.0+ ships
> `NationalLayoutCheck`, which warns with a one-click fix when the keymap is selected while the support is off.

| Key       | Token in keymap XML                 | Key code                 | Status                                                                  |
|-----------|-------------------------------------|--------------------------|-------------------------------------------------------------------------|
| `Ä` / `ä` | `#10000c4`                          | `0x010000C4` (extended)  | ✅ verified: IDE export + JVM                                           |
| `Ö` / `ö` | `#10000d6`                          | `0x010000D6` (extended)  | ✅ verified: IDE export + JVM                                           |
| `Ü` / `ü` | `#10000fc`                          | `0x010000FC` (extended)  | ✅ verified: IDE export + JVM                                           |
| `ß`       | `#10000df`                          | `0x010000DF` (extended)  | ✅ verified: IDE export + JVM                                           |
| `+`       | `plus`                              | `0x209` `VK_PLUS`        | JVM-checked — dedicated key right of `Ü` (US ANSI: only `⇧=`)           |
| `#`       | `number_sign`                       | `0x208` `VK_NUMBER_SIGN` | JVM-checked — dedicated key right of `Ä` (US ANSI: only `⇧3`)           |
| `<`       | `less`                              | `0x99` `VK_LESS`         | JVM-checked — ISO key left of `Y`; **does not exist on US ANSI at all** |
| `´`       | `dead_acute` *or* `#10000b4`        | `0x81` / `0x010000B4`    | ⚠️ dead key, token unconfirmed — IDE-test before use                    |
| `^`       | `dead_circumflex` *or* `circumflex` | `0x82` / `0x202`         | ⚠️ dead key, token unconfirmed — IDE-test before use                    |

Usage — modifiers first; named tokens for keys with a `VK_` constant, lowercase `#hex` for the extended codes:

```xml

<keyboard-shortcut first-keystroke="meta #10000c4"/>        <!-- ⌘Ä -->
<keyboard-shortcut first-keystroke="meta shift #10000d6"/>  <!-- ⌘⇧Ö -->
<keyboard-shortcut first-keystroke="meta less"/>            <!-- ⌘< -->
<keyboard-shortcut first-keystroke="meta number_sign"/>     <!-- ⌘# -->
```

Notes:

- Codes address the *physical key* — upper/lower case yield the same token, and `⇧`-variants of these keys (`*` on `+`,
  `'` on `#`, `>` on `<`) come along for free as `shift <token>` bindings.
- **Verification levels.** Umlauts/ß were verified 2026-07-19 on this machine both by exporting a test binding from
  IntelliJ IDEA 2026.2 and via `KeyEvent.getExtendedKeyCodeForChar` (Corretto 17). `+` `#` `<` are confirmed by the JVM
  lookup, but no binding has been exported from the IDE yet — do the 30-second test (bind the key in Settings → Keymap,
  apply, grep the exported XML) before shipping bindings on them.
- ⚠️ `Ü` breaks the hex pattern: it uses the **lowercase** codepoint (`fc`, not `dc`), while `Ä`/`Ö` use the uppercase
  one. Never derive extended codes by hand; copy them from this table or re-export from the IDE.
- ⚠️ The dead keys `´` and `^` are risky on two counts: the palette names them `dead_acute`/`dead_circumflex`
  (`0x81`/`0x82`), but the JVM char-lookup on this machine returns different codes (`#10000b4` / `VK_CIRCUMFLEX
  0x202`) — whichever macOS actually delivers must be established by the IDE test. And macOS uses them to compose
  accents (`´` + `e` = `é`), so binding them may interfere with typing accented characters. Prefer the other seven keys.

## Appendix A — Actions with NO reachable binding (37)

Every macOS binding of these actions sits on a ❌/⚠️ key, so on a German MacBook Pro the action is only reachable via
menus or `Find Action`. Each gets exactly **one** replacement. **The macOS modifiers are preserved verbatim; only the
key is substituted.** No dead keys (`´` `^`) and no numpad keys are used.

| Dead key                           | Replacement                         | Rationale                                                                                                         |
|------------------------------------|-------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| `/` (`SLASH`, `DIVIDE`)            | `#` (`number_sign`)                 | `#` is *the* comment character; dedicated German key right of `Ä`                                                 |
| `=` + numpad `+` (`EQUALS`, `ADD`) | `+` (`plus`)                        | dedicated German key right of `Ü`; completes the pair with the inherited `⌘-` collapse bindings                    |
| numpad `-` (`SUBTRACT`)            | `-` (`minus`)                       | real key on German layout                                                                                         |
| numpad `*` (`MULTIPLY`)            | `⇧+` (`shift plus`)                 | `⇧+` types `*`                                                                                                    |
| `[` / `]` (`OPEN_/CLOSE_BRACKET`)  | `Ö` / `Ä` (`#10000d6` / `#10000c4`) | adjacent key pair: left key = start, right key = end                                                              |
| `` ` `` (`BACK_QUOTE`)             | `ß` (`#10000df`) / `<` (`less`)     | window cycling: `ß` = editor windows (+ scheme popup), `<` = project windows — same `⌘`/`⌘⇧` pattern on both keys |
| fold-to-level prefix strokes       | `Ü` (`#10000fc`)                    | `⌘Ü` = expand to level, `⌘⌥Ü` = expand all to level, mirroring macOS `⌘*` / `⌘⌥*`                                 |
| `'` (`QUOTE`)                      | `⇧#`                                | `⇧#` types `'`                                                                                                    |
| `;` (`SEMICOLON`)                  | `⇧,` (`shift comma`)                | `⇧,` types `;` — keeps the pairing with `ShowSettings` on `⌘,`                                                    |
| `INSERT`                           | per action                          | `⌃Ü` = toggle **Ü**berschreiben (overwrite); `⌥+` = add watch                                                     |

| Action                              | macOS binding(s)                             | Rebind (`first-keystroke`)      | Keys         |
|-------------------------------------|----------------------------------------------|---------------------------------|--------------|
| `CollapseTreeNode`                  | `SUBTRACT`                                   | `MINUS`                         | `-`          |
| `CommentByBlockComment`             | `meta alt SLASH` *(+ 6 variants)*            | `meta alt NUMBER_SIGN`          | ⌘⌥#          |
| `CommentByLineComment`              | `meta SLASH`; `meta DIVIDE`                  | `meta NUMBER_SIGN`              | ⌘#           |
| `EditorCodeBlockEnd`                | `meta alt CLOSE_BRACKET`                     | `meta alt #10000c4`             | ⌘⌥Ä          |
| `EditorCodeBlockEndWithSelection`   | `meta alt shift CLOSE_BRACKET`               | `meta alt shift #10000c4`       | ⌘⌥⇧Ä         |
| `EditorCodeBlockStart`              | `meta alt OPEN_BRACKET`                      | `meta alt #10000d6`             | ⌘⌥Ö          |
| `EditorCodeBlockStartWithSelection` | `meta alt shift OPEN_BRACKET`                | `meta alt shift #10000d6`       | ⌘⌥⇧Ö         |
| `EditorToggleInsertState`           | `INSERT`                                     | `control #10000fc`              | ⌃Ü ³         |
| `ExpandAll`                         | `meta ADD`; `meta EQUALS`                    | `meta PLUS`                     | ⌘+ ¹         |
| `ExpandAllRegions`                  | `meta shift ADD`; `meta shift EQUALS`        | `meta shift PLUS`               | ⌘⇧+          |
| `ExpandAllToLevel1`…`5`             | `meta alt MULTIPLY` + digit                  | `meta alt #10000fc` + `1`…`5`   | ⌘⌥Ü, digit   |
| `ExpandRegion`                      | `meta ADD`; `meta EQUALS`                    | `meta PLUS`                     | ⌘+ ¹         |
| `ExpandRegionRecursively`           | `meta alt ADD`; `meta alt EQUALS`            | `meta alt PLUS`                 | ⌘⌥+          |
| `ExpandToLevel1`…`5`                | `meta MULTIPLY` + digit                      | `meta #10000fc` + `1`…`5`       | ⌘Ü, digit    |
| `ExpandTreeNode`                    | `ADD`                                        | `PLUS`                          | `+`          |
| `FileChooser.GoToRoot`              | `meta SLASH`                                 | `meta NUMBER_SIGN`              | ⌘# ²         |
| `FullyExpandTreeNode`               | `MULTIPLY`                                   | `shift PLUS`                    | ⇧+ (= `*`)   |
| `HippieBackwardCompletion`          | `alt shift SLASH`                            | `alt shift NUMBER_SIGN`         | ⌥⇧#          |
| `HippieCompletion`                  | `alt SLASH`                                  | `alt NUMBER_SIGN`               | ⌥#           |
| `MaximizeToolWindow`                | `meta shift QUOTE`                           | `meta shift NUMBER_SIGN`        | ⌘⇧# (= ⌘`'`) |
| `NextProjectWindow`                 | `meta alt BACK_QUOTE`                        | `meta LESS`                     | ⌘<           |
| `NextWindow`                        | `meta BACK_QUOTE`                            | `meta #10000df`                 | ⌘ß           |
| `PreviousProjectWindow`             | `meta shift alt BACK_QUOTE`                  | `meta shift LESS`               | ⌘⇧<          |
| `PreviousWindow`                    | `meta shift BACK_QUOTE`                      | `meta shift #10000df`           | ⌘⇧ß ⁴        |
| `QuickChangeScheme`                 | `control BACK_QUOTE`                         | `control #10000df`              | ⌃ß           |
| `ShowProjectStructureSettings`      | `meta SEMICOLON`                             | `meta shift COMMA`              | ⌘⇧, (= ⌘`;`) |
| `XDebugger.NewWatch`                | `INSERT`                                     | `alt PLUS`                      | ⌥+ ³         |
| `ZoomCurrentWindow`                 | `meta control EQUALS`                        | `meta control PLUS`             | ⌘⌃+          |
| `ZoomInIdeAction`                   | `control alt EQUALS`                         | `control alt PLUS`              | ⌃⌥+          |

¹ `ExpandAll` and `ExpandRegion` share `⌘+` deliberately — macOS binds both to `⌘+`/`⌘=` too; the IDE separates them by
context (tree vs. editor).
² `CommentByLineComment` and `FileChooser.GoToRoot` share `⌘#` deliberately — macOS binds both to `⌘/`; contexts are
editor vs. file chooser.
³ The two `INSERT` actions must not collide with the `Ü` fold prefixes: `⌘Ü` and `⌘⌥Ü` are two-stroke prefixes, so a
single-stroke binding on either would make the IDE ambiguous. `EditorToggleInsertState` therefore takes `⌃Ü` (keeping
the *Ü*berschreiben mnemonic) and `XDebugger.NewWatch` takes `⌥+` (keeping the `+` = add mnemonic; `⌘+` is `ExpandAll`).
⁴ ⌘⇧ß types `⌘?`, which macOS maps to the **Help menu** search field. Verify with the plugin's live conflict report on
your machine; if it fires there, the ß tier can move to `⌃⇧ß`.

Collision audit (machine-verified 2026-08-05): counting co-bound keystrokes across the whole effective keymap, stock
`Mac OS X 10.5+` has **58** groups of actions sharing a first keystroke; this keymap has **55**. Every group here is a
subset of an upstream one — the rebinds introduce **no new** double-bound key.

## Appendix B — Actions that lose an alternative but stay reachable (14)

These keep a working macOS binding; only an unreachable *alternate* is dropped, so it can't linger as a ghost row in
Settings → Keymap. Each is declared with its surviving shortcut(s) only.

| Action                        | Removed dead binding        | Surviving binding                |
|-------------------------------|-----------------------------|----------------------------------|
| `$Copy`                       | `meta INSERT`               | `meta C`                         |
| `$Paste`                      | `shift INSERT`              | `meta V`                         |
| `PasteMultiple`               | `meta shift INSERT`         | `meta shift V`                   |
| `FileChooser.NewFolder`       | `alt INSERT`                | `meta N`                         |
| `Back`                        | `meta OPEN_BRACKET`         | `meta alt LEFT`; `button4`       |
| `Forward`                     | `meta CLOSE_BRACKET`        | `meta alt RIGHT`; `button5`      |
| `CollapseAll`                 | `meta SUBTRACT`             | `meta MINUS`                     |
| `CollapseAllRegions`          | `meta shift SUBTRACT`       | `meta shift MINUS`               |
| `CollapseRegion`              | `meta SUBTRACT`             | `meta MINUS`                     |
| `CollapseRegionRecursively`   | `meta alt SUBTRACT`         | `meta alt MINUS`                 |
| `CollapseExpandableComponent` | `meta SUBTRACT`             | `shift ENTER`; `meta MINUS`      |
| `ExpandExpandableComponent`   | `meta ADD`; `meta EQUALS`   | `shift ENTER`                    |
| `NextTab`                     | `meta shift CLOSE_BRACKET`  | rebound in Appendix C            |
| `PreviousTab`                 | `meta shift OPEN_BRACKET`   | rebound in Appendix C            |

`EditorToggleColumnMode` is **not** in this list: `Mac OS X 10.5+` declares `shift meta 8` in its own scheme, which
*replaces* `$default`'s `alt shift INSERT` outright rather than adding to it — there is no dead alternate to remove.

## Appendix C — Conflicts with macOS system shortcuts

macOS grabs some key combos before the IDE ever sees them, and some IDE bindings sit on combos macOS functions need.
All macOS bindings can be inspected three ways:

1. **System Settings → Keyboard → Keyboard Shortcuts** (interactive, per category, shows enabled state).
2. **Machine-readable ground truth**:
   `plutil -convert json -o - ~/Library/Preferences/com.apple.symbolichotkeys.plist` — every system shortcut with
   keycode, modifier mask, and enabled flag. (Note: window-management shortcuts are not all represented there.)
3. Apple's reference list: https://support.apple.com/HT201236

**Snapshot of this machine (2026-07-19), all 62 symbolic hotkeys decoded against the German layout:** F1–F12 act as
standard function keys (`fnState=1`); dictation is not on double-⌃; input-source switching (`⌃Space`/`⌃⌥Space`) is
**disabled**; Mission Control / App windows / Show Desktop are remapped to `⌃⌥⌘F9`–`F11` and desktop switching to
`⌃⌥⌘1`–`3` — none of which the keymap uses.

### Conflicts found → rebinds (6 actions)

The `Ö`/`Ä` rule from Appendix A extends here: **`Ö` = previous/left, `Ä` = next/right**, in three modifier tiers.
All six conflicts are Spaces navigation, and all six bindings are declared in `Mac OS X 10.5+`'s *own* scheme (so they
really are `⌃`-based on macOS, not an artifact of inheritance).

| Action              | macOS binding         | macOS function (enabled here)  | Rebind (`first-keystroke`) | Keys |
|---------------------|-----------------------|--------------------------------|----------------------------|------|
| `PreviousTab`       | `control LEFT`        | Spaces: move left a space      | `meta shift #10000d6`      | ⌘⇧Ö  |
| `NextTab`           | `control RIGHT`       | Spaces: move right a space     | `meta shift #10000c4`      | ⌘⇧Ä  |
| `PreviousEditorTab` | `control shift LEFT`  | Spaces: move left (⇧ variant)  | `control #10000d6`         | ⌃Ö   |
| `NextEditorTab`     | `control shift RIGHT` | Spaces: move right (⇧ variant) | `control #10000c4`         | ⌃Ä   |
| `Diff.PrevChange`   | `control shift LEFT`  | Spaces: move left (⇧ variant)  | `control shift #10000d6`   | ⌃⇧Ö  |
| `Diff.NextChange`   | `control shift RIGHT` | Spaces: move right (⇧ variant) | `control shift #10000c4`   | ⌃⇧Ä  |

`PreviousTab`/`NextTab` thereby land exactly where the Appendix A bracket rule would have put their dead
`meta shift OPEN_/CLOSE_BRACKET` bindings — the two rules agree.

**There is no `⌃Fn` → `⌘Fn` rule.** An earlier revision of this document rebound `ShowErrorDescription`, `Stop`,
`ChangeSignature`, `FindUsagesInFile` and `ToggleLineBreakpoint` off `⌃F1/F2/F6/F7/F8`, believing the macOS
accessibility hotkeys owned that row. Those actions are `control F1…F8` in **`$default`**, so on macOS they are
**already `⌘F1`…`⌘F8`** — the conflict never existed. They are inherited unchanged and are not declared here.

### Aligned — same function on both sides, deliberately kept

| Binding                 | IDE action                                                                              | macOS function                                                                                                            |
|-------------------------|-----------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| ⌃⌘Space                 | `EmojiAndSymbols`                                                                       | Emoji & Symbols viewer — identical by design                                                                              |
| ⌘< / ⌘⇧<                | `NextProjectWindow` / `PreviousProjectWindow`                                           | Cycle app windows forward/backward — the IDE implements the same concept for project windows                              |
| ⌘M / ⌘W / ⌘Q / ⌘, / ⌘⌃F | `MinimizeCurrentWindow` / `CloseContent` / `Exit` / `ShowSettings` / `ToggleFullScreen`  | Standard macOS app conventions                                                                                            |

### Watchlist for other machines (stock macOS defaults — relevant for plugin users)

These do **not** conflict on this machine because of local settings, but will on a stock macOS install:

| Stock macOS default                                                             | Affected IDE binding                                                                                         | Resolution                                                                                                                                                 |
|---------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `⌃Space` / `⌃⌥Space` input-source switching (auto-enabled with >1 input source) | `CodeCompletion` ⌃Space, `ChangesView.SetDefault` ⌃Space, `ClassNameCompletion` ⌃⌥Space                      | Keep the IDE bindings — disable/remap the macOS shortcut (System Settings → Keyboard → Keyboard Shortcuts → Input Sources); JetBrains' own recommendation  |
| `⌃1`…`⌃9` desktop switching (auto-enabled per additional Space)                 | `GotoBookmark1`–`9` ⌃n                                                                                       | Remap desktop switching (here: `⌃⌥⌘n`) or accept menu access to bookmarks                                                                                  |
| `⌘?` Help menu search (`⌘⇧ß` on German)                                         | `PreviousWindow` ⌘⇧ß                                                                                         | Verify with the live conflict report; move the ß tier to `⌃⇧ß` if it fires                                                                                 |
| `⌥⌘8` accessibility zoom toggle (zoom in/out land on `⌥⌘´`/`⌥⌘ß` on German)     | `ActivateUnitTestsToolWindow` ⌘⌥8                                                                            | Zoom is off by default and off here                                                                                                                        |
| Dictation on double-⌃ (optional setting)                                        | `RunAnything` double-⌃ gesture                                                                               | Keep dictation on 🌐 (fn) or off                                                                                                                           |
| `⌃↑` / `⌃↓` Mission Control / App windows (stock keys)                          | — nothing bound in this keymap                                                                               | No action needed                                                                                                                                           |

## Appendix D — Regression history

Recorded because the root cause is invisible in the XML and cost a full rebuild of the keymap.

**Symptom (shipped 2026-07-19 → 2026-08-05, plugin ≤ 1.6.1):** `⌃V` pasted instead of `⌘V`; `⌃X`, `⌃A`, `⌃S`,
`⌃⇧A`, `⌃⌥L`, `⌃D`, `⌃E` and 83 further actions carried Windows/Linux modifiers. On a Mac that made the keymap
effectively unusable.

**Cause.** The keymap was created (commit `ef87e8e`) as a *flattened* merge of `$default.xml` and `Mac OS X 10.5+.xml`
that copied `$default` rows **verbatim**, skipping the Ctrl↔Meta conversion the parent applies (see "The inheritance
rule" above). Because the keymap is correctly *not* named `Mac OS X …`, nothing converted them at runtime either. 90
actions were frozen in Windows form.

**Propagation.** This document was then generated *from that file*, so both audits reasoned about `⌃` shortcuts.
Appendix B once recorded `$Paste` → "surviving binding `control V`" as correct. Consequently ~8 Appendix A rebinds
inherited the wrong modifier (`⌃#` for commenting instead of `⌘#`, `⌃+`/`⌃⇧+` for folding instead of `⌘+`/`⌘⇧+`,
`⌃Ü`/`⌃⇧Ü` for fold-to-level instead of `⌘Ü`/`⌘⌥Ü`, `⌃⇧#` for `MaximizeToolWindow` instead of `⌘⇧#`), and the
`⌃Fn` → `⌘Fn` rule "fixed" five phantom conflicts.

**Unrelated drift.** Commit `42431b1` (a plugin *rebrand*, whose message did not mention the keymap) silently changed
22 further actions with no entry in this document: `⌃R` → `⌃⌘R` on `Run`, `Replace` and `UsageFiltering.ReadAccess`
(three actions on one key); `⌃⇧↑/↓` → `⌃⌘↑/↓` on `MoveStatementUp/Down`, `MethodUp/Down`, `ShowContent`; `⌃↩` → `⌘⌥↩`
on seven actions including `ViewSource`, `EditorSplitLine` and `Generate`; and `MinimizeCurrentWindow` (⌘M),
`TodoViewGroupByFlattenPackage` (⌃F) and `EditorRight`'s ⌃F alternate cleared outright. All are reverted — those
actions are simply inherited now.

**Fix (2026-08-05).** The keymap was rebuilt as a **delta** on `Mac OS X 10.5+`, declaring only the 58 actions of
Appendices A–C plus three empty plugin-default overrides. The structure makes the bug class impossible: any action not
declared is inherited *through* the parent, which converts it correctly. Verified — zero unreachable key codes in the
effective keymap, zero Spaces-grabbed combos, no new co-bound keystrokes, `⌘V`/`⌘C`/`⌘X`/`⌘A`/`⌘S` restored.

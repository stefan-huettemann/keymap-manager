# Keys used in `MacBook Pro DE.xml`

Source: `keymaps/MacBook Pro DE.xml` (447 actions). Target hardware: MacBook Pro with **German (T1 / ISO) layout**, no
numpad.

**Why keys can be "unreachable":** IntelliJ shortcuts address *physical keys* (AWT virtual key codes), not the
characters they produce. `meta SLASH` means the key
`VK_SLASH` — which does not exist on a German keyboard, where `/` is only typed as
`⇧7`. Pressing `⌘⇧7` delivers `meta shift 7`, never `meta SLASH`, so the shortcut can never fire. (Deeper background:
`handover-unreachable-keys.md`, a local research doc not tracked in this repo.)

Legend:

| Mark      | Meaning                                                                                                                     |
|-----------|-----------------------------------------------------------------------------------------------------------------------------|
| ✅        | Reachable on a German MacBook Pro keyboard                                                                                  |
| ❌ DE     | **Unreachable on German layout** — the character exists only via `⇧`/`⌥` chord, there is no physical key with this key code |
| ❌ Mac    | **No such key on any Mac keyboard**                                                                                         |
| ⚠️ numpad | Numpad key — needs a full-size keyboard, **not present on a MacBook Pro**                                                   |

Mouse and Force-touch shortcuts are not keys and are omitted.

## Modifier keys

| Token in XML       | macOS key | Symbol | Uses | Status |
|--------------------|-----------|--------|------|--------|
| `meta`             | Command   | ⌘      | 97   | ✅     |
| `alt`              | Option    | ⌥      | 136  | ✅     |
| `control` / `ctrl` | Control   | ⌃      | 264  | ✅     |
| `shift`            | Shift     | ⇧      | 160  | ✅     |

Two gesture shortcuts use a modifier as the *key* itself: double-tap ⌃ (`RunAnything`)
and double-tap ⇧ (`SearchEverywhere`). Both ✅.

## Keys

| Token in XML                     | Key on a macOS keyboard              | Uses | Status    | Note                                                                            |
|----------------------------------|--------------------------------------|------|-----------|---------------------------------------------------------------------------------|
| `A` – `Z` (all 26 used)          | letter keys                          | 146  | ✅        | German QWERTZ swaps Y/Z positions, but both keys exist — bindings work          |
| `0` – `9` (all used)             | digit row                            | 51   | ✅        | the digits themselves are fine; only their *shifted* characters are unreachable |
| `F1`–`F10`, `F12` (`F11` unused) | function row                         | 66   | ✅        | may require `Fn` or the "Use F1, F2, etc. as standard function keys" setting    |
| `UP` / `DOWN` / `LEFT` / `RIGHT` | arrow keys ↑ ↓ ← →                   | 60   | ✅        |                                                                                 |
| `HOME` / `END`                   | ↖ / ↘ — on MacBook `Fn ←` / `Fn →`   | 9    | ✅        |                                                                                 |
| `PAGE_UP` / `PAGE_DOWN`          | ⇞ / ⇟ — on MacBook `Fn ↑` / `Fn ↓`   | 10   | ✅        |                                                                                 |
| `ENTER`                          | Return ↩                             | 23   | ✅        |                                                                                 |
| `TAB`                            | Tab ⇥                                | 22   | ✅        |                                                                                 |
| `SPACE`                          | Space                                | 7    | ✅        |                                                                                 |
| `BACK_SPACE`                     | Delete ⌫                             | 9    | ✅        |                                                                                 |
| `DELETE`                         | Forward Delete ⌦ — on MacBook `Fn ⌫` | 6    | ✅        |                                                                                 |
| `ESCAPE`                         | Esc ⎋                                | 4    | ✅        |                                                                                 |
| `COMMA`                          | `,`                                  | 2    | ✅        | same physical key on German layout                                              |
| `PERIOD`                         | `.`                                  | 5    | ✅        | same physical key on German layout                                              |
| `MINUS`                          | `-`                                  | 6    | ✅        | German `-` sits right of `.` (where US `/` is) — key code matches               |
| `SLASH`                          | `/`                                  | 7    | ❌ DE     | German `/` is `⇧7` — no `VK_SLASH` key exists                                   |
| `EQUALS`                         | `=`                                  | 7    | ❌ DE     | German `=` is `⇧0`                                                              |
| `SEMICOLON`                      | `;`                                  | 1    | ❌ DE     | German `;` is `⇧,`                                                              |
| `QUOTE`                          | `'`                                  | 1    | ❌ DE     | German `'` is `⇧#`                                                              |
| `BACK_QUOTE`                     | `` ` ``                              | 5    | ❌ DE     | German `` ` `` is `⇧´`                                                          |
| `OPEN_BRACKET`                   | `[`                                  | 4    | ❌ DE     | German `[` only via `⌥5` — no dedicated key                                     |
| `CLOSE_BRACKET`                  | `]`                                  | 4    | ❌ DE     | German `]` only via `⌥6` — no dedicated key                                     |
| `INSERT`                         | —                                    | 6    | ❌ Mac    | Mac keyboards have no Insert key                                                |
| `ADD`                            | numpad `+`                           | 6    | ⚠️ numpad |                                                                                 |
| `SUBTRACT`                       | numpad `-`                           | 6    | ⚠️ numpad |                                                                                 |
| `MULTIPLY`                       | numpad `*`                           | 22   | ⚠️ numpad |                                                                                 |
| `DIVIDE`                         | numpad `/`                           | 4    | ⚠️ numpad |                                                                                 |
| `NUMPAD1`–`NUMPAD5`              | numpad digits                        | 10   | ⚠️ numpad | only as second keystroke of `Expand…ToLevel…`                                   |

Of the 8 key codes the handover forbids for German layouts, 7 appear in this keymap (`BACK_SLASH` is the only one
unused).

## Bindable German keys — free shortcut real estate

The German T1 layout has physical keys that stock US-ANSI keymaps never bind — some don't exist on a US keyboard at all.
**None of them are used anywhere in
`MacBook Pro DE.xml`**, making them the prime candidates for rebinding the Appendix A actions below.

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

Usage — modifiers first; named tokens for keys with a `VK_` constant, lowercase
`#hex` for the extended codes:

```xml

<keyboard-shortcut first-keystroke="meta #10000c4"/>        <!-- ⌘Ä -->
<keyboard-shortcut first-keystroke="meta shift #10000d6"/>  <!-- ⌘⇧Ö -->
<keyboard-shortcut first-keystroke="meta less"/>            <!-- ⌘< -->
<keyboard-shortcut first-keystroke="meta number_sign"/>     <!-- ⌘# -->
```

Notes:

- Codes address the *physical key* — upper/lower case yield the same token, and
  `⇧`-variants of these keys (`*` on `+`, `'` on `#`, `>` on `<`) come along for free as `shift <token>` bindings.
- **Verification levels.** Umlauts/ß were verified 2026-07-19 on this machine both by exporting a test binding from
  IntelliJ IDEA 2026.2 and via
  `KeyEvent.getExtendedKeyCodeForChar` (Corretto 17). `+` `#` `<` are confirmed by the JVM lookup and the handover's
  reflection palette, but no binding has been exported from the IDE yet — do the 30-second test (bind the key in
  Settings → Keymap, apply, grep the exported XML) before shipping bindings on them.
- ⚠️ `Ü` breaks the hex pattern: it uses the **lowercase** codepoint (`fc`, not
  `dc`), while `Ä`/`Ö` use the uppercase one. Never derive extended codes by hand; copy them from this table or
  re-export from the IDE.
- ⚠️ The dead keys `´` and `^` are risky on two counts: the handover's palette names them `dead_acute`/`dead_circumflex`
  (`0x81`/`0x82`), but the JVM char-lookup on this machine returns different codes (`#10000b4` / `VK_CIRCUMFLEX
  0x202`) — whichever macOS actually delivers must be established by the IDE test. And macOS uses them to compose
  accents (`´` + `e` = `é`), so binding them may interfere with typing accented characters. Prefer the other seven keys.

## Appendix A — Actions with NO reachable binding (37)

Every keyboard shortcut of these actions uses a ❌/⚠️ key, so on a German MacBook Pro the action is only reachable via
menus or `Find Action`.

**Rebind declaration** *(proposal — `MacBook Pro DE.xml` not yet changed)*. Each action gets exactly **one** new
binding, replacing all dead ones. No dead keys (`´` `^`) and no numpad keys are used. The mapping follows per-key
substitution rules, keeping the original modifiers wherever possible:

| Dead key                           | Replacement                         | Rationale                                                                                                         |
|------------------------------------|-------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| `/` (`SLASH`, `DIVIDE`)            | `#` (`number_sign`)                 | `#` is *the* comment character; dedicated German key right of `Ä`                                                 |
| `=` + numpad `+` (`EQUALS`, `ADD`) | `+` (`plus`)                        | dedicated German key right of `Ü`; completes the pair with the existing `⌃-` collapse bindings                    |
| numpad `-` (`SUBTRACT`)            | `-` (`minus`)                       | real key on German layout                                                                                         |
| numpad `*` (`MULTIPLY`)            | `⇧+` (`shift plus`)                 | `⇧+` types `*`                                                                                                    |
| `[` / `]` (`OPEN_/CLOSE_BRACKET`)  | `Ö` / `Ä` (`#10000d6` / `#10000c4`) | adjacent key pair: left key = start, right key = end                                                              |
| `` ` `` (`BACK_QUOTE`)             | `ß` (`#10000df`) / `<` (`less`)     | window cycling: `ß` = editor windows (+ scheme popup), `<` = project windows — same `⌘`/`⌘⇧` pattern on both keys |
| fold-to-level prefix strokes       | `Ü` (`#10000fc`)                    | `⌃Ü` = expand to level, `⌃⇧Ü` = expand all to level (`⇧` = "all", same rule as `⌃+`/`⌃⇧+`)                        |
| `'` (`QUOTE`)                      | `⇧#`                                | `⇧#` types `'`                                                                                                    |
| `;` (`SEMICOLON`)                  | `⇧,` (`shift comma`)                | `⇧,` types `;` — keeps the pairing with `ShowSettings` on `⌘,`                                                    |
| `INSERT`                           | per action                          | `⌘Ü` = toggle **Ü**berschreiben (overwrite); `⌘+` = add watch                                                     |

| Action                              | Dead binding(s)                             | Rebind (`first-keystroke`)         | Keys         |
|-------------------------------------|---------------------------------------------|------------------------------------|--------------|
| `CollapseTreeNode`                  | `SUBTRACT`                                  | `minus`                            | `-`          |
| `CommentByBlockComment`             | `meta alt SLASH` *(+ 5 variants)*           | `meta alt number_sign`             | ⌘⌥#          |
| `CommentByLineComment`              | `control SLASH`; `control DIVIDE`           | `control number_sign`              | ⌃#           |
| `EditorCodeBlockEnd`                | `meta alt CLOSE_BRACKET`                    | `meta alt #10000c4`                | ⌘⌥Ä          |
| `EditorCodeBlockEndWithSelection`   | `meta alt shift CLOSE_BRACKET`              | `meta alt shift #10000c4`          | ⌘⌥⇧Ä         |
| `EditorCodeBlockStart`              | `meta alt OPEN_BRACKET`                     | `meta alt #10000d6`                | ⌘⌥Ö          |
| `EditorCodeBlockStartWithSelection` | `meta alt shift OPEN_BRACKET`               | `meta alt shift #10000d6`          | ⌘⌥⇧Ö         |
| `EditorToggleInsertState`           | `INSERT`                                    | `meta #10000fc`                    | ⌘Ü           |
| `ExpandAll`                         | `control ADD`; `control EQUALS`             | `control plus`                     | ⌃+ ¹         |
| `ExpandAllRegions`                  | `control shift ADD`; `control shift EQUALS` | `control shift plus`               | ⌃⇧+          |
| `ExpandAllToLevel1`…`5`             | `meta alt MULTIPLY` + digit                 | `control shift #10000fc` + `1`…`5` | ⌃⇧Ü, digit   |
| `ExpandRegion`                      | `control ADD`; `control EQUALS`             | `control plus`                     | ⌃+ ¹         |
| `ExpandRegionRecursively`           | `control alt ADD`; `control alt EQUALS`     | `control alt plus`                 | ⌃⌥+ ²        |
| `ExpandToLevel1`…`5`                | `control MULTIPLY` + digit                  | `control #10000fc` + `1`…`5`       | ⌃Ü, digit    |
| `ExpandTreeNode`                    | `ADD`                                       | `plus`                             | `+`          |
| `FileChooser.GoToRoot`              | `meta SLASH`                                | `meta number_sign`                 | ⌘#           |
| `FullyExpandTreeNode`               | `MULTIPLY`                                  | `shift plus`                       | ⇧+ (= `*`)   |
| `HippieBackwardCompletion`          | `alt shift SLASH`                           | `alt shift number_sign`            | ⌥⇧#          |
| `HippieCompletion`                  | `alt SLASH`                                 | `alt number_sign`                  | ⌥#           |
| `MaximizeToolWindow`                | `control shift QUOTE`                       | `control shift number_sign`        | ⌃⇧# (= ⌃`'`) |
| `NextProjectWindow`                 | `meta alt BACK_QUOTE`                       | `meta less`                        | ⌘<           |
| `NextWindow`                        | `meta BACK_QUOTE`                           | `meta #10000df`                    | ⌘ß           |
| `PreviousProjectWindow`             | `meta shift alt BACK_QUOTE`                 | `meta shift less`                  | ⌘⇧<          |
| `PreviousWindow`                    | `meta shift BACK_QUOTE`                     | `meta shift #10000df`              | ⌘⇧ß          |
| `QuickChangeScheme`                 | `control BACK_QUOTE`                        | `control #10000df`                 | ⌃ß           |
| `ShowProjectStructureSettings`      | `meta SEMICOLON`                            | `meta shift comma`                 | ⌘⇧, (= ⌘`;`) |
| `XDebugger.NewWatch`                | `INSERT`                                    | `meta plus`                        | ⌘+           |
| `ZoomCurrentWindow`                 | `meta control EQUALS`                       | `meta control plus`                | ⌘⌃+          |
| `ZoomInIdeAction`                   | `control alt EQUALS`                        | `control alt plus`                 | ⌃⌥+ ²        |

¹ `ExpandAll` and `ExpandRegion` share `⌃+` deliberately — their original bindings were identical too; the IDE separates
them by context (tree vs. editor). ² `ExpandRegionRecursively` and `ZoomInIdeAction` shared `⌃⌥=` in the original
keymap; the shared rebind `⌃⌥+`
preserves that — and `ZoomInIdeAction` `⌃⌥+` now mirrors `ZoomOutIdeAction` `⌃⌥-`.

Collision audit: every rebind uses a key that was previously bound to nothing in the keymap (`plus`, `number_sign`,
`less`, the `#hex` keys, bare `minus`, `meta shift comma` — all verified unused), so no existing binding is shadowed.
The bare `+` / `-` / `⇧+` bindings live in tree contexts only, exactly like the bare numpad keys they replace — they do
not capture typing in the editor. With `<` carrying the project-window pair, all seven bindable German keys are in use.

## Appendix B — Actions that lose an alternative but stay reachable (15)

| Action                        | Dead binding                    | Surviving binding              |
|-------------------------------|---------------------------------|--------------------------------|
| `$Copy`                       | `control INSERT`                | `control C`                    |
| `$Paste`                      | `shift INSERT`                  | `control V`                    |
| `Back`                        | `meta OPEN_BRACKET`             | `meta alt LEFT`                |
| `CollapseAll`                 | `control SUBTRACT`              | `control MINUS`                |
| `CollapseAllRegions`          | `control shift SUBTRACT`        | `control shift MINUS`          |
| `CollapseExpandableComponent` | `control SUBTRACT`              | `shift ENTER`; `control MINUS` |
| `CollapseRegion`              | `control SUBTRACT`              | `control MINUS`                |
| `CollapseRegionRecursively`   | `control alt SUBTRACT`          | `control alt MINUS`            |
| `EditorToggleColumnMode`      | `shift meta MULTIPLY`           | `shift meta 8`                 |
| `ExpandExpandableComponent`   | `control ADD`; `control EQUALS` | `shift ENTER`                  |
| `FileChooser.NewFolder`       | `alt INSERT`                    | `control N`                    |
| `Forward`                     | `meta CLOSE_BRACKET`            | `meta alt RIGHT`               |
| `NextTab`                     | `meta shift CLOSE_BRACKET`      | `control RIGHT`                |
| `PasteMultiple`               | `control shift INSERT`          | `control shift V`              |
| `PreviousTab`                 | `meta shift OPEN_BRACKET`       | `control LEFT`                 |

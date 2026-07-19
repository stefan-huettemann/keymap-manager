# Keys used in `CIVA MacOS.xml`

Source: `keymaps/CIVA MacOS.xml` (447 actions). Target hardware: MacBook Pro with
**German (T1 / ISO) layout**, no numpad.

**Why keys can be "unreachable":** IntelliJ shortcuts address *physical keys* (AWT
virtual key codes), not the characters they produce. `meta SLASH` means the key
`VK_SLASH` — which does not exist on a German keyboard, where `/` is only typed as
`⇧7`. Pressing `⌘⇧7` delivers `meta shift 7`, never `meta SLASH`, so the shortcut
can never fire. See `handover-unreachable-keys.md` §4.

Legend:

| Mark | Meaning |
|---|---|
| ✅ | Reachable on a German MacBook Pro keyboard |
| ❌ DE | **Unreachable on German layout** — the character exists only via `⇧`/`⌥` chord, there is no physical key with this key code |
| ❌ Mac | **No such key on any Mac keyboard** |
| ⚠️ numpad | Numpad key — needs a full-size keyboard, **not present on a MacBook Pro** |

Mouse and Force-touch shortcuts are not keys and are omitted.

## Modifier keys

| Token in XML | macOS key | Symbol | Uses | Status |
|---|---|---|---|---|
| `meta` | Command | ⌘ | 97 | ✅ |
| `alt` | Option | ⌥ | 136 | ✅ |
| `control` / `ctrl` | Control | ⌃ | 264 | ✅ |
| `shift` | Shift | ⇧ | 160 | ✅ |

Two gesture shortcuts use a modifier as the *key* itself: double-tap ⌃ (`RunAnything`)
and double-tap ⇧ (`SearchEverywhere`). Both ✅.

## Keys

| Token in XML | Key on a macOS keyboard | Uses | Status | Note |
|---|---|---|---|---|
| `A` – `Z` (all 26 used) | letter keys | 146 | ✅ | German QWERTZ swaps Y/Z positions, but both keys exist — bindings work |
| `0` – `9` (all used) | digit row | 51 | ✅ | the digits themselves are fine; only their *shifted* characters are unreachable |
| `F1`–`F10`, `F12` (`F11` unused) | function row | 66 | ✅ | may require `Fn` or the "Use F1, F2, etc. as standard function keys" setting |
| `UP` / `DOWN` / `LEFT` / `RIGHT` | arrow keys ↑ ↓ ← → | 60 | ✅ | |
| `HOME` / `END` | ↖ / ↘ — on MacBook `Fn ←` / `Fn →` | 9 | ✅ | |
| `PAGE_UP` / `PAGE_DOWN` | ⇞ / ⇟ — on MacBook `Fn ↑` / `Fn ↓` | 10 | ✅ | |
| `ENTER` | Return ↩ | 23 | ✅ | |
| `TAB` | Tab ⇥ | 22 | ✅ | |
| `SPACE` | Space | 7 | ✅ | |
| `BACK_SPACE` | Delete ⌫ | 9 | ✅ | |
| `DELETE` | Forward Delete ⌦ — on MacBook `Fn ⌫` | 6 | ✅ | |
| `ESCAPE` | Esc ⎋ | 4 | ✅ | |
| `COMMA` | `,` | 2 | ✅ | same physical key on German layout |
| `PERIOD` | `.` | 5 | ✅ | same physical key on German layout |
| `MINUS` | `-` | 6 | ✅ | German `-` sits right of `.` (where US `/` is) — key code matches |
| `SLASH` | `/` | 7 | ❌ DE | German `/` is `⇧7` — no `VK_SLASH` key exists |
| `EQUALS` | `=` | 7 | ❌ DE | German `=` is `⇧0` |
| `SEMICOLON` | `;` | 1 | ❌ DE | German `;` is `⇧,` |
| `QUOTE` | `'` | 1 | ❌ DE | German `'` is `⇧#` |
| `BACK_QUOTE` | `` ` `` | 5 | ❌ DE | German `` ` `` is `⇧´` |
| `OPEN_BRACKET` | `[` | 4 | ❌ DE | German `[` only via `⌥5` — no dedicated key |
| `CLOSE_BRACKET` | `]` | 4 | ❌ DE | German `]` only via `⌥6` — no dedicated key |
| `INSERT` | — | 6 | ❌ Mac | Mac keyboards have no Insert key |
| `ADD` | numpad `+` | 6 | ⚠️ numpad | |
| `SUBTRACT` | numpad `-` | 6 | ⚠️ numpad | |
| `MULTIPLY` | numpad `*` | 22 | ⚠️ numpad | |
| `DIVIDE` | numpad `/` | 4 | ⚠️ numpad | |
| `NUMPAD1`–`NUMPAD5` | numpad digits | 10 | ⚠️ numpad | only as second keystroke of `Expand…ToLevel…` |

Of the 8 key codes the handover forbids for German layouts, 7 appear in this keymap
(`BACK_SLASH` is the only one unused).

## Appendix A — Actions with NO reachable binding (37)

Every keyboard shortcut of these actions uses a ❌/⚠️ key, so on a German MacBook Pro
the action is only reachable via menus or `Find Action`. These are the rebinding
work list.

| Action | Dead binding(s) |
|---|---|
| `CollapseTreeNode` | `SUBTRACT` |
| `CommentByBlockComment` | `meta alt SLASH`; `meta alt DIVIDE`; `control shift SLASH`; `control shift DIVIDE`; `meta shift SLASH`; `meta shift DIVIDE` |
| `CommentByLineComment` | `control SLASH`; `control DIVIDE` |
| `EditorCodeBlockEnd` | `meta alt CLOSE_BRACKET` |
| `EditorCodeBlockEndWithSelection` | `meta alt shift CLOSE_BRACKET` |
| `EditorCodeBlockStart` | `meta alt OPEN_BRACKET` |
| `EditorCodeBlockStartWithSelection` | `meta alt shift OPEN_BRACKET` |
| `EditorToggleInsertState` | `INSERT` |
| `ExpandAll` | `control ADD`; `control EQUALS` |
| `ExpandAllRegions` | `control shift ADD`; `control shift EQUALS` |
| `ExpandAllToLevel1`…`5` | `meta alt MULTIPLY` + digit/numpad digit |
| `ExpandRegion` | `control ADD`; `control EQUALS` |
| `ExpandRegionRecursively` | `control alt ADD`; `control alt EQUALS` |
| `ExpandToLevel1`…`5` | `control MULTIPLY` + digit/numpad digit |
| `ExpandTreeNode` | `ADD` |
| `FileChooser.GoToRoot` | `meta SLASH` |
| `FullyExpandTreeNode` | `MULTIPLY` |
| `HippieBackwardCompletion` | `alt shift SLASH` |
| `HippieCompletion` | `alt SLASH` |
| `MaximizeToolWindow` | `control shift QUOTE` |
| `NextProjectWindow` | `meta alt BACK_QUOTE` |
| `NextWindow` | `meta BACK_QUOTE` |
| `PreviousProjectWindow` | `meta shift alt BACK_QUOTE` |
| `PreviousWindow` | `meta shift BACK_QUOTE` |
| `QuickChangeScheme` | `control BACK_QUOTE` |
| `ShowProjectStructureSettings` | `meta SEMICOLON` |
| `XDebugger.NewWatch` | `INSERT` |
| `ZoomCurrentWindow` | `meta control EQUALS` |
| `ZoomInIdeAction` | `control alt EQUALS` |

## Appendix B — Actions that lose an alternative but stay reachable (15)

| Action | Dead binding | Surviving binding |
|---|---|---|
| `$Copy` | `control INSERT` | `control C` |
| `$Paste` | `shift INSERT` | `control V` |
| `Back` | `meta OPEN_BRACKET` | `meta alt LEFT` |
| `CollapseAll` | `control SUBTRACT` | `control MINUS` |
| `CollapseAllRegions` | `control shift SUBTRACT` | `control shift MINUS` |
| `CollapseExpandableComponent` | `control SUBTRACT` | `shift ENTER`; `control MINUS` |
| `CollapseRegion` | `control SUBTRACT` | `control MINUS` |
| `CollapseRegionRecursively` | `control alt SUBTRACT` | `control alt MINUS` |
| `EditorToggleColumnMode` | `shift meta MULTIPLY` | `shift meta 8` |
| `ExpandExpandableComponent` | `control ADD`; `control EQUALS` | `shift ENTER` |
| `FileChooser.NewFolder` | `alt INSERT` | `control N` |
| `Forward` | `meta CLOSE_BRACKET` | `meta alt RIGHT` |
| `NextTab` | `meta shift CLOSE_BRACKET` | `control RIGHT` |
| `PasteMultiple` | `control shift INSERT` | `control shift V` |
| `PreviousTab` | `meta shift OPEN_BRACKET` | `control LEFT` |

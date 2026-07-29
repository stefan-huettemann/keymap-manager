# 0003 — Full keymap editing, inherited shortcuts, keycap rendering

- **Status:** Accepted 2026-07-24 (supersedes the [0002](0002-plugin-scope-and-name.md) §2 "not a general tree editor" rudder)
- **Source:** internal (`../wip/TODO.md`, user-authored)
- **Date:** 2026-07-24

---

## 1. Context

[0002](0002-plugin-scope-and-name.md) relaxed the plugin from a pure conflict tool
to a "macOS-focused keymap manager", but kept a rudder: *"a purposeful manager …
**not** a general re-implementation of Settings → Keymap's tree editor."* The
report showed only **conflicts**, **double-bound keys** and the keymap's **own
declarations** (Modified). Actions bound purely through inheritance were visible
only where they happened to conflict.

`../wip/TODO.md` asks to go further: let the user **rebind / remove / revert any
shortcut** the keymap actually has — including inherited ones — plus a set of
UI-consistency changes (keycap-framed shortcuts, dialog layout, navigator
polish). The user's framing: *"we now allow changing every keybinding just like
the keymap settings."*

## 2. Decision — supersede the 0002 editing rudder

We **relax** the "not a general tree editor" rudder. The report becomes an
editable view of the keymap's **whole effective binding set** (own + inherited),
each shortcut rebindable / removable / revertable in place.

The **other** 0002 rudders survive and still bound the scope:

- **macOS focus** — the live `SystemShortcuts` scan remains the differentiator.
- **Purposeful, not exhaustive** — we still do **not** host the platform's full
  *action* tree. Concretely: we list **only actions that have a shortcut in this
  keymap** (own or inherited). Binding a shortcut onto an action that currently
  has **none** stays a job for Settings → Keymap; every category detail offers a
  **"Settings…"** link that opens it.

So the line moves from *"conflicts + own declarations"* to *"every **bound**
shortcut, fully editable"* — but not to *"every action, bound or not."*

## 3. Scope of changes (from `../wip/TODO.md`)

**Wording (docs only):** *navigator* = the left tree; *details* = the right pane.

**Cosmetics**
- Shortcuts render as **keycaps** — each modifier/key in its own rounded frame,
  matching Settings → Keymap (per user screenshot). Applied everywhere a shortcut
  shows: navigator tree cells, detail pane, dialogs. The frame always uses the
  "highlight" style (drawn in the glyph colour) — there is no dimmer variant.
- Gear toggles **Show Action IDs** and **Show Keymap** append, **dimmed**, next
  to the name in **every** surface — navigator (incl. conflict/double-bound/
  overlap rows), detail pane, conflict action list, section-contents listing,
  confirm-dialog lists. Format (revised 2026-07-26): the action id in parentheses,
  the defining keymap in brackets, joined by a dash — `(id) - [Keymap]` / `(id)` /
  `[Keymap]`.
- The meta is **right-aligned in every list**, immediately left of the keycaps
  (fixed 2026-07-26 — it was right-aligned in the navigator but trailed the action
  name on the left in the confirm dialogs and the conflict action list, so the same
  information sat on opposite sides of the window). The rule is one line of layout:
  the row's horizontal glue goes **before** the meta, not after. `ActionListView`'s
  rows changed from `FlowLayout` to `BoxLayout.X_AXIS` to allow it (gaps became
  struts, a 1px border replaces the old vgap). Its rows have no keycaps — every
  action there is on the one key named in the header — so the meta and the source
  tag end the row. The detail pane's **Action** fact row stays inline: it is a
  label:value line, not a list row, with no keycap column to align against.
- The scan-invisible **Supplement** entries show for **any** keymap that binds
  their action (not just the bundled one); the `ownKeymap` flag was removed.
- Shortcut keycaps are **right-aligned** in every list (tree, section listing,
  confirm dialogs) and every row is **vertically centred** (BoxLayout.X + glue).
  Right-aligned means at the **panel's right edge with a small margin** — the row
  is sized to exactly the viewport width, the name/meta ellipsize when they don't
  fit (full text as a tooltip), and the keycaps never move (revised 2026-07-26;
  before, an over-long row kept its natural width, so the tree scrolled sideways
  and every row's keycaps ended somewhere else).
- The **Remove** confirm dialog shows every shortcut assigned to each action (not
  just one) and, from a per-row/category link, clears them all.
- The scan-invisible **Supplement** entries (Emoji & Symbols, Dictation) are no
  longer awareness-only: each names its IDE action (`EmojiAndSymbols`,
  `RunAnything`) so its detail offers Rebind/Remove/Settings on it (Rebind only
  when the binding is a keystroke, not the double-⌃ gesture).
- Renames: **"Revert to default…" → "Revert…"**, **"Open Keymap settings" →
  "Settings…"**. Every **"Settings…"** carries a trailing *external-link* icon
  (`AllIcons.Ide.External_link_arrow`) signalling "leaves the plugin".

**Editing (the §2 decision)**
- Every bound shortcut can be rebound / removed / reverted, on any keymap
  (read-only keymaps derive an editable copy first, as today).

**New navigator category — "Inherited Shortcuts"**
- Title **"Inherited Shortcuts"**, subtitle **"Shortcuts inherited from parent
  keymaps"**. Lists the effective bindings this keymap does **not** declare itself
  (the complement of *Modified*), **excluding actions with no shortcut**.

**Navigator layout**
- **Title / subtitle** model: title shows collapsed; when expanded, a
  non-selectable **subtitle info-row** is the category's first child (the
  explanation above the action rows).
- Secondary info (counts, source tags, keycaps) is **right-aligned** with a right
  margin, via a custom full-width tree renderer.
- **All five categories are always listed** (reworked 2026-07-26): Keymap
  conflicts, Overlaps {ide} doesn't flag, Double-bound keys, Modified shortcuts,
  Inherited Shortcuts. Before, "Overlaps" was dropped entirely when it had nothing
  in it and "Double-bound keys" showed an empty section with no rows, so the
  navigator's shape changed per keymap. Now an empty category always shows one
  placeholder row, and selecting it explains itself in the details pane: heading,
  one-line status, what *would* be listed there and why nothing is
  (`emptyExplanation`), plus the category links (Settings… alone when empty). The
  scan-unavailable case is marked `ok=false` — ⚠ icon, amber status, its own text
  making clear the section is empty because no scan ran. "Show modified only"
  still narrows to the Modified category; that is what the toggle is for.
- Clicking a category Title shows its info and links in the details pane.
  (A "contents listed in the pane" comfort view was added 2026-07-24 then
  **reverted 2026-07-25** at the user's request — the links stayed.) Category
  links (revised 2026-07-24 after an initial "Settings-only" pass): **Inherited
  Shortcuts** gets only **Settings…**; every other category also gets bulk
  **Remove…** (clears all shortcuts of all its actions) and **Revert…**,
  deselectable in the confirm dialog. Inherited stays Settings-only because
  Revert-on-inherited is inert.
- **Revert reaches every list** (revised 2026-07-26). It was on the Modified rows
  only; it is now on **every** per-row detail — conflicts, overlaps, double-bound
  keys, supplements, Modified — gated on one predicate, `revertable(id)` =
  "the viewed keymap declares this action itself" (`ownIds`). That gate is what
  keeps it off Inherited rows (no own declaration → `clearOwnActionsId` would be a
  no-op), so the Inherited case needs no special-casing any more. On a conflict row
  the link follows the same rule as its Rebind/Remove neighbours — the checked
  actions, or all of the key's revertable ones when the checkboxes name none — and
  the category-level bulk Revert is filtered to the revertable ids too (before, it
  passed inherited ones that showed "current → identical" and did nothing).

**Dialogs (Rebind / Remove / Revert)**
- **Rebind** unchanged (already has a "Current:" row).
- **Remove** and **Revert** adopt Rebind's `FormBuilder` layout: the shortcut in a
  **"Current:"** header. Because a bulk Revert spans actions with *different*
  current bindings, Revert shows the single "Current:" header only for a
  single-action revert; a bulk revert shows a **per-row `current → reverts-to`**
  instead (the "reverts-to" = the parent keymap's binding).
- All dialogs open with the **action list expanded**; the button-row toggle
  inverts to **"Hide actions"**. (This overrides 0001's B5 nuance where a
  single-action revert stayed collapsed.)
- All dialog action lists are **scrollable**, capped at ~480px so the window
  can't grow unbounded.
- The list must sit in the centre panel's **`BorderLayout.CENTER`**, with only the
  fixed rows `NORTH` (shared `listLayout()`, fixed 2026-07-26). All three dialogs
  originally put the *whole* form in `NORTH`, which lays out at its preferred
  height and never shrinks — so on a window shorter than the form (screen-clamped
  on open, or dragged smaller) the list's bottom was cut off *outside* the scroll
  pane and those actions could not be reached at all. Rebind surfaced it because
  it stacks six more rows above the list than Remove/Revert. Verified: at 560px
  the old layout left the list hanging 150px below the window; the new one shrinks
  the viewport to 330px and scrolls.
- **All three** dialogs carry a **Select all / Deselect all** link *above* the
  list (added 2026-07-26 for Remove/Revert, extended to Rebind the same day), so
  it stays visible while the list scrolls. It is the shared
  `EditActionList.selectAllLink()` and behaves like the conflict detail's
  `ActionListView` toggle — "Deselect all" while anything is ticked (which is how
  these dialogs open), flipping to "Select all" once nothing is. It hides with the
  list under "Hide actions", and Rebind/Remove omit it when only one action is in
  play (Revert only builds a list for a bulk revert).

**Cogwheel menu**
- New trailing section with one entry, **"Settings…"**, opening Keymap settings.
- That entry carries the link arrow **after** its text (added 2026-07-26), so all
  three "leaves the plugin" surfaces look alike. Menu items paint their
  `Presentation` icon in the *left* gutter, so this uses the platform's
  `ActionUtil.SECONDARY_ICON` property instead — documented as "the icon that will
  be placed after the text", read by `ActionMenuItem.getSecondaryIcon()` and
  painted by `BegMenuItemUI` after the label; it is the same mechanism Git's
  working-trees "New" badge uses. Set on the *event's* presentation during
  `update()`, not on the template, so a clone can't drop it.

**Settings icon per navigator row** (added 2026-07-26)
- Every navigator row that names an action carries the **external-link arrow** as a
  **rightmost column**, after the keycaps, tinted to the theme's link colour
  (`JBUI.CurrentTheme.Link.Foreground.ENABLED` — the help "?" now uses the same
  constant instead of its own hardcoded blue). Clicking it does exactly what that
  row's `Settings…` link does: opens the platform Keymap page at the action.
- Rows that name no single action (section titles, subtitles, empty placeholders)
  reserve the column with a strut, so the keycap column stays aligned across rows.
- The **dialog lists** (`EditActionList`, i.e. Rebind / Remove / Revert) carry the
  same icon column, as a real `InplaceButton`. Leaving for the Keymap page there
  means abandoning the edit being confirmed, so it **cancels the dialog** first —
  stated in the tooltip — and defers the report close + settings open to a later
  event so the modal loop unwinds first. Same `ROW_RIGHT_INSET` as the navigator.
- A `TreeCellRenderer` only paints, so the click lives on the tree: `settingsIconHit`
  derives the icon's x-range from the row bounds rather than from the (per-paint,
  rebuilt) component tree — the cell always ends at the viewport edge and the icon
  is laid out last inside the shared `ROW_RIGHT_INSET`. Hovering it shows a hand
  cursor and a tooltip naming the action, since the icon has no label.

**Settings… lands on the action** (added 2026-07-26)
- Every `Settings…` link that has a single action in scope opens the platform
  Keymap page with that action **revealed and selected**, instead of dumping the
  user at the top of the tree:
  `showSettingsDialog(project, KeymapPanel.class, panel -> panel.selectAction(id))`.
  The `Consumer` overload runs before the dialog is shown, and `selectAction`
  explicitly defers until the panel has initialized — that is its documented
  purpose. The link carries a tooltip naming the action, since its text can't.
- Per-row details pass their own action; a conflict/double-bound row passes the
  key's **first** action (the one the row's label and meta already name, so the
  tooltip matches what was on screen); the unbound-supplement case passes its
  action so a shortcut can be assigned there; category and empty-placeholder rows
  keep the plain link.
- **Not possible:** presetting the page's *find-by-shortcut* filter —
  `KeymapPanel.filterTreeByShortcut` and its filtering panel are private. Nor can
  a keystroke be passed as the text filter: `ActionsTreeUtil.createActionFilter`
  matches template text, description, synonyms, action **id** and abbreviations,
  never shortcut text. The action id is the only way in.
- Verified against the 2026.1.4 platform sources; `verifyPlugin` verdict stays
  **Compatible** with no API warnings (`KeymapPanel` is public, un-annotated, and
  was already referenced by the plugin).

## 4. Consequences / follow-ups

- Update `CLAUDE.md`: scope paragraph (drop "not a general re-implementation of
  the tree editor"; state the new "every bound shortcut, not every action" line),
  and the `ConflictReportDialog` description (navigator categories, keycaps,
  dialog layout).
- `ConflictScan` gains an **inherited-bindings** list (effective bindings minus
  own declarations, shortcut-bearing only).
- Performance: a stock keymap has ~1–2k inherited bindings; build the
  `ActionRef`s and tree rows so this stays responsive (lazy / on-expand if
  needed).
- The **name/scope** identity from 0002 is unaffected; only the editing rudder
  moves. 0002 stays the record for name/vendor; this file owns the editing scope.

## 5. Decision

Accepted. Implemented in phases (mirroring 0001):

1. This spec + doc updates.
2. Keycap rendering (util + detail pane + dialogs + tree, right-aligned).
3. Navigator: title/subtitle info-rows, "Inherited Shortcuts" category.
4. Full editability on every bound row; renames + exit icon; category detail =
   info + "Settings…".
5. Dialog rework (Remove/Revert "Current:" layout, expand-by-default +
   "Hide actions", scroll cap, per-row revert target).
6. Cogwheel "Settings…" entry.

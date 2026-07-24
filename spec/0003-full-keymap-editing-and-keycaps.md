# 0003 — Full keymap editing, inherited shortcuts, keycap rendering

- **Status:** Accepted 2026-07-24 (supersedes the [0002](0002-plugin-scope-and-name.md) §2 "not a general tree editor" rudder)
- **Source:** internal (`wip/changes.md`, user-authored)
- **Date:** 2026-07-24

---

## 1. Context

[0002](0002-plugin-scope-and-name.md) relaxed the plugin from a pure conflict tool
to a "macOS-focused keymap manager", but kept a rudder: *"a purposeful manager …
**not** a general re-implementation of Settings → Keymap's tree editor."* The
report showed only **conflicts**, **double-bound keys** and the keymap's **own
declarations** (Modified). Actions bound purely through inheritance were visible
only where they happened to conflict.

`wip/changes.md` asks to go further: let the user **rebind / remove / revert any
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

## 3. Scope of changes (from `wip/changes.md`)

**Wording (docs only):** *navigator* = the left tree; *details* = the right pane.

**Cosmetics**
- Shortcuts render as **keycaps** — each modifier/key in its own rounded frame,
  matching Settings → Keymap (per user screenshot). Applied everywhere a shortcut
  shows: navigator tree cells, detail pane, dialogs. The frame always uses the
  "highlight" style (drawn in the glyph colour) — there is no dimmer variant.
- Gear toggles **Show Action IDs** and **Show Keymap** append the action id
  and/or the keymap that defines the binding (nearest declaring ancestor),
  **dimmed, comma-separated, no parentheses**, next to the name in **every**
  surface — navigator, detail pane, conflict action list, section-contents
  listing, confirm-dialog lists.
- Shortcut keycaps are **right-aligned** in every list (tree, section listing,
  confirm dialogs) and every row is **vertically centred** (BoxLayout.X + glue).
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
- Clicking a category Title shows its info in the details pane, its links, **and
  the category's contents listed in the pane** (each row selects that item — a
  comfort so the tree needn't be expanded). Category links (revised 2026-07-24
  after an initial "Settings-only" pass): **Inherited Shortcuts** gets only
  **Settings…**; every other category also gets bulk **Remove…** (clears all
  shortcuts of all its actions) and **Revert…**, deselectable in the confirm
  dialog. Inherited stays Settings-only because Revert-on-inherited is inert.

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

**Cogwheel menu**
- New trailing section with one entry, **"Settings…"**, opening Keymap settings.

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

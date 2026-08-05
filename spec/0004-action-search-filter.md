# 0004 — Action search / filter in the report

- **Status:** Implemented 2026-08-04 (graduates backlog item **B1**)
- **Source:** internal (user request: *"we need an action search feature"*)
- **Date:** 2026-08-04

---

## 1. Context

The report grew into an editable view of a keymap's **whole effective binding
set** ([0003](0003-full-keymap-editing-and-keycaps.md)). That is the point of the
plugin, and also its scale problem: "Inherited Shortcuts" alone runs to several
hundred rows on a stock keymap, and "Modified shortcuts" runs to ~450 on the
bundled `MacBook Pro DE` (which re-states every binding deliberately). Finding
*one* action meant scrolling.

[BACKLOG.md](BACKLOG.md) **B1** parked exactly this ("a filter box above the
report tree… narrows the rows live"). The user's request picks it up, with a
tighter scope than B1 sketched:

1. Move the keymap selector out of its own centred block.
2. Put a **search / filter field on the same row**.
3. Typing an action name **filters the navigator** to matching rows.
4. No match → **empty navigator plus a message explaining** that nothing matched.

## 2. Decision

A **view filter over the existing scan**, exactly as B1's rudder required — no
new data model, no re-scan, no change to `ConflictScan`.

**Matching.** Case-insensitive **substring of the action's displayed name** —
`ConflictScan.ActionRef.label()`, the same text every surface already shows (for
an action the platform names nothing, that *is* its id, so those stay findable).
Deliberately **not** matched:

- **Shortcut text** (B1 floated "or keystroke text"). Rejected for now: the
  keycap glyphs (⌘⌥⇧) are awkward to type, and a bare letter would match every
  action whose shortcut contains it — noise, not a filter. The Rebind dialog
  already answers "who owns this key?" live, and a conflict row is *keyed* by its
  keystroke, so the shortcut → action direction is covered.
- **Internal action ids**, unless the id *is* the label. Rationale: the user asked
  for names, and a row whose visible name doesn't contain the typed text reads as
  a bug when "Show Action IDs" is off. The platform's own Keymap page *does* match
  ids, so the no-match detail links there.

A row survives if **any** action on it matches, not just the first one the row
displays — a key bound to three actions is findable by any of them, and its
detail pane lists them all anyway.

**Shape of the filtered tree.**

- Sections keep only matching rows; a section that matched **nothing is omitted**
  entirely. Its "nothing to fix here ✔" placeholder would be a lie (the category
  may be full, just not of matches), and omitting it keeps the navigator to the
  sections that answer the query. This is a deliberate, filter-only exception to
  0003's *"all five categories are always listed"* rule, which stands unfiltered.
- Section counts become **"matched of total"** (`(3 of 41)`), so a filtered
  category cannot be misread as a short one.
- **Every** listed section is expanded while filtering — including the
  informational ones that are collapsed by default. A match hidden inside a
  collapsed section would defeat the filter.
- Category-level bulk **Remove… / Revert…** act on the rows the category
  currently **lists**: `categoryIds` filters too, so a bulk edit can never reach
  past what the navigator shows.

**No match at all** (every section dropped): the navigator is genuinely **empty**
and carries the platform's own empty-text status (`Tree.getEmptyText()`) — the
query, a one-line reminder that the filter matches names, and a clickable
*"Clear the filter"*. Beside it the detail pane shows a `NoMatch` view (not a tree
row): what was searched, what wasn't (shortcuts, ids, and — while "Show modified
only" is on — the four hidden categories), that only actions **with** a shortcut
are listed at all, then **Clear filter · Settings…**. It carries no amber ⚠:
nothing is wrong with the keymap, the filter simply hid everything, and the
summary line above still reports the keymap's real status.

**Layout.** The keymap row becomes `[Keymap: ⟨combo⟩ ⚙  ⟨filter⟩ … ?]`, the whole
selector + filter group **centred on the window** (the first cut put it flush
left; corrected 2026-08-04 after review), Activate/Reset centred under it, the
help "?" still pinned right. Two `BorderLayout` details had to be fixed for that
to look right:

- the help button lives in a **`GridBagLayout`** holder so it is **vertically
  centred** on the row — a `FlowLayout` lays its child out at the *top* of the
  combo-height EAST slot, which left the "?" visibly floating above the row;
- an empty **`mirrorSpacer(helpHolder)`** goes in **WEST**, because BorderLayout
  gives EAST its width first, so CENTER on its own centres the group on the
  *remaining* space — half the help button's width left of true centre, and off
  the Activate/Reset row's centre below it.

The field is a platform `SearchTextField(false)` (magnifier, ✕ clear button,
history popup **off** — this is a live filter, not a submitted search); **Esc** in
it clears the text instead of closing the dialog, via the platform's `ClearText`
action, which is enabled only while the field holds text — so an empty field still
lets Esc close.

## 3. Implementation

All in `ConflictReportDialog`; no other class changed.

- `filter` (trimmed + lowercased, `""` = off) and `filterText` (as typed, for the
  messages); `applyFilter` on every document change → `rebuildTree()`, the same
  "re-shape, don't re-scan" path the "Show modified only" toggle already used.
- `matchesFilter(payload)` per row type; `rows(entries, toPayload)` builds a
  section's payload list minus the drops; `addSection(...)` adds the title +
  subtitle + rows *or* the placeholder, and skips the section when filtering left
  it empty. `buildRoot` shrank to five `addSection` calls in the process.
- `Section` gained `total`; the renderer prints `(n)` or `(n of N)`.
- `shouldExpand` returns true for every section while filtering.
- `updateEmptyText()` + the `NoMatch` detail payload; `ellipsize` keeps the
  status line short (`StatusText` centres lines without wrapping).
- Docs: in-app help step, `plugin.xml` usage + change-notes, root `README.md`,
  `CLAUDE.md`.

Public API only: `SearchTextField`, `Tree.getEmptyText()` / `StatusText`,
`SimpleTextAttributes`, `JBTextField.getEmptyText()`. `verifyPlugin` verdict after
the change: **Compatible**, no API warnings.

## 4. Consequences / follow-ups

- Every keystroke rebuilds the tree model (~500 nodes worst case) and re-renders
  the detail pane for the newly selected first match. Cheap in practice; if a
  pathological keymap ever makes it feel slow, debounce `applyFilter` rather than
  making the filter lazier.
- The filter is a **view** filter only: **Export…** still writes the keymap's real
  categories, unfiltered. That is intentional — export is about the keymap, not
  about what is on screen.
- Shortcut-text matching stays available as a later increment (§2), as does
  matching ids behind the "Show Action IDs" toggle. Neither is scheduled.
- 0003's "all five categories are always listed" rule now reads "…unless the
  action filter is active"; nothing else in 0002/0003 moves.

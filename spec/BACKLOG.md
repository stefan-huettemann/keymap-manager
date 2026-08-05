# Feature backlog

Candidate features that fit the plugin's identity (a **macOS-focused keymap
manager**, per [0002](0002-plugin-scope-and-name.md)) but aren't scheduled yet.

This is a holding area, distinct from the numbered decision records: entries here
are **ideas**, not decisions. When one is picked up, **graduate it to a numbered
spec** (`0003-…`, etc.) with the full requirements + plan, and remove it here.

Each notes the value, the scope/rudder to respect, and a rough size.

Ids are historical and never reused, so a gap just means that entry graduated:
**B1** (filter / search in the report) became
[0004](0004-action-search-filter.md), implemented 2026-08-04 — action **names**
only; keystroke-text matching stayed unscheduled (see 0004 §2).

---

## B2 — Compare two keymaps

- **What:** gear → **Compare with…** → pick keymap B; the detail shows where A and
  B differ (added / changed / removed bindings), side by side.
- **Why:** generalizes the "modified vs parent" idea to **any pair** — "what did I
  change vs stock", or diff two keymaps before switching between them.
- **Scope / rudder:** watch the "not a general keymap editor" line — keep it a
  **read-only comparison** (a view), not simultaneous editing of both. macOS-
  agnostic, so justify it as an audit tool.
- **Size:** medium. A compare mode/dialog diffing two keymaps' effective bindings
  (`getActionIdList` + `getShortcuts`); reuses the `ActionRef` resolution.
- **Status:** Idea.

## B3 — Guided conflict resolution (German-layout aware)

- **What:** **"Resolve all conflicts…"** steps through each real
  (`RESOLVE` / `UNCLASSIFIED`) conflict and proposes a **reachable replacement
  key** drawn from `keys.md`'s bindable-German-keys table. Apply / Skip / Pick
  another.
- **Why:** the most **on-brand** of the three — macOS + German T1 layout +
  conflicts is the plugin's raison d'être. Turns the report from "here's what's
  broken" into "here's the fix, one click."
- **Scope / rudder:** strongly macOS + T1-layout specific (the differentiator, so
  a clean fit). The suggestion source is `keys.md`'s bindable keys, and it **must
  respect** the German-layout rule in `CLAUDE.md` (never propose the forbidden
  keys). Needs a suggestion engine (pick an unused, reachable key).
- **Size:** large. Suggestion engine + step-through UI + reuse of the rebind path.
  Highest value, highest effort.
- **Status:** Idea.

---

*Also parked, separately:* the plugin is still **pre-publish** — first release
needs a version bump + refreshed `plugin.xml` change-notes, and the vendor choice
(CIVA vs PulmoVention, [0002](0002-plugin-scope-and-name.md) Decision C) before
the manual Marketplace upload. See `PUBLISHING.md`.

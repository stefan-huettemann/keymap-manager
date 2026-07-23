# 0001 — Filter / view for modified shortcuts

- **Status:** In progress — **Phases 1–2 implemented 2026-07-23** (extend the plugin; scope/name framing in [0002](0002-plugin-scope-and-name.md))
- **Source:** [IJPL-228176 — "Add filter for modified shortcuts in Keymap settings"](https://youtrack.jetbrains.com/issue/IJPL-228176)
- **Date:** 2026-07-23

---

## 1. The source issue (pinned facts)

Read from the YouTrack REST API on 2026-07-23; reproduced here so we don't
depend on the issue staying reachable or unchanged.

| Field | Value |
|-------|-------|
| ID | IJPL-228176 |
| Summary | Add filter for modified shortcuts in Keymap settings |
| Type | Feature |
| Priority | Normal |
| State | Open |
| Subsystem | UI. Keymap, Shortcuts |

**Description (paraphrased):** Managing customized keyboard shortcuts is hard.
Modified shortcuts already render in a **different color** inside *Settings →
Keymap*, but there is **no filtered view** to see only the shortcuts a user has
changed. The request is a **filter toggle (e.g. "Show modified")** that lists
only user-modified shortcuts, so a user can quickly **audit** their
customizations and **revert them to default** without scrolling the whole tree.
Key motivation: as customization accumulates over time, finding a specific
modified shortcut becomes impractical.

**Sole comment (Nadia Tarashkevich, JetBrains):** a workaround — open the keymap
file in the IDE config folder's `keymaps/` subfolder; it is a plain list of the
shortcuts modified relative to the defaults.

That comment is the tell: "modified shortcuts" == **the keymap's own `<action>`
declarations** (its diff against its parent). The saved keymap XML *is* exactly
that set. This is the same thing our plugin already serializes.

---

## 2. Requirements analysis

What the issue actually asks for, decomposed:

- **R1 — Identify.** Show only the shortcuts a keymap declares itself (its diff
  against the inherited defaults), not the full binding set.
- **R2 — Audit.** Make that set browsable/countable so a user can review "what
  have I changed?" at a glance, for arbitrarily large customizations.
- **R3 — Revert.** From that view, restore an entry to its default (drop the
  override so the inherited binding re-applies), one at a time and ideally in
  bulk.
- **R4 — Location.** The issue targets the **platform's own** *Settings → Keymap*
  panel. See §5 for why a plugin cannot honor that literally, and what we do
  instead.

Non-goals implied by the issue: it is not asking for conflict detection, not a
new editor, not import/export. It is a **read + revert audit view**.

---

## 3. What we already have (feasibility)

The plugin already computes the exact data R1 needs. It is used today only for
the **Export → "Changes vs the parent keymap"** category:

- `ConflictReportDialog.java:825` — the `CHANGES` export category is literally
  *"everything this keymap declares itself"*.
- `ConflictReportDialog.java:880` — produced by `KeymapImpl.writeScheme()`, whose
  root `<keymap>` carries exactly the own `<action>` declarations.
- `ConflictReportDialog.java:967` — the count is
  `impl.writeScheme().getChildren("action").size()` — i.e. **the number of
  modified shortcuts** is already something we compute.

Reusable UI/infrastructure that a "modified" view would sit on top of:

- **Keymap selector** — dropdown of every installed keymap (active / ours / rest),
  Activate / Reset, gear menu. R1/R2 are per-keymap; the selector already scopes
  "which keymap".
- **Tree + section model** (`buildRoot()`, `Section`) — sections like
  "Double-bound keys" are already informational, collapsed-by-default groups. A
  "Modified shortcuts" section is the same shape.
- **`ActionListView` / rebind / remove** — per-action links, checkboxes,
  Select-all, and the `ShortcutInputDialog`. Revert (R3) reuses this surface.
- **Read-only → editable derivation** — any edit already auto-derives, registers
  and activates an editable copy; revert inherits that for free.

**Conclusion:** ~80% of this feature already exists as plumbing. The genuinely
new work is (a) a per-action "is this modified?" predicate wired to the UI, and
(b) a real *revert* operation (§4).

---

## 4. Integration plan

Delivered as a **new view/section inside the existing report dialog**, not a new
window. Phased so each step is shippable on its own.

### Phase 1 — surface the count (smallest useful step) — ✅ done 2026-07-23
- The summary line already knows the modified count (§3, line 967). Show it:
  *"N shortcuts modified vs the default keymap."*
- Zero new data model; pure display. Validates the framing with users cheaply.

### Phase 2 — a "Modified shortcuts" section/view — ✅ done 2026-07-23
- Add a `modified` list to `ConflictScan` (or a sibling scan): for the selected
  keymap, the action ids in `writeScheme().getChildren("action")`, each resolved
  to name/source/current-binding via the existing `refs(...)` path.
- Render it as a new tree `Section` (like `SEC_DOUBLE`), each row = action + its
  current shortcut + (optionally) the default it overrides.
- Add a **view toggle** ("Show modified only") that hides the conflict sections
  and shows just this one — the issue's "Show modified" filter, in our dialog.
- Empty-state text when the keymap declares nothing of its own (e.g. a fresh
  copy or `$default`).

### Phase 3 — revert to default (R3)
- New operation distinct from the existing **Remove** (which clears a binding to
  *empty*). Revert must **drop the own-declaration** so the parent binding
  re-inherits — for a `KeymapImpl`, clearing the action's own shortcuts and
  *not* re-adding, then letting `getParent()` supply the binding.
- Wire it as a links row in the detail pane, mirroring **Rebind… · Remove…**:
  add **Revert to default…** operating on the checked actions (or all).
- Confirmation dialog with the same **Show actions** checkbox-list pattern used
  by Rebind/Remove, so the affected set is reviewable before applying.
- Edge case: an action *added* in this keymap that has **no** parent binding —
  revert then means "remove entirely"; label/handle that honestly.

### Phase 4 — polish
- "Revert all…" as a bulk action from the section header.
- Respect the existing **Show Action IDs** toggle in the new rows.
- `{ide}` placeholder discipline for all new strings (never hardcode "IntelliJ").
- Update `CLAUDE.md`'s dialog paragraph and, if exported, extend the reference
  to the CHANGES category wording.

### Testing
- The project's XML invariants + ad-hoc `xml.etree` check still apply to any
  keymap we write back.
- Manual `runIde`: derive a copy, change a few shortcuts, confirm the section
  lists exactly those, revert one, confirm it re-inherits the parent binding.

**Rough size:** Phase 1 trivial; Phase 2 ~half a day (mostly wiring a section we
already have the data for); Phase 3 is the real work (the revert semantics +
confirmation UX); Phase 4 small.

---

## 5. Discussion — meaningful extension, or a sibling plugin?

### The literal ask is not plugin-reachable
IJPL-228176 wants the filter **inside the platform's `Settings → Keymap` panel**.
That panel (`KeymapPanel`) is **not an extension point** — a plugin cannot inject
a "Show modified" toggle into JetBrains' own keymap UI. So *neither* an extension
of our plugin *nor* a brand-new sibling plugin can satisfy the issue literally.
Both can only offer the **equivalent capability in their own surface**. This
neutralizes the strongest argument for a sibling ("do it where users expect it")
— nobody can do it there.

### Why an extension of *this* plugin fits
- **The data and ~80% of the UI already exist here** (§3). A sibling would have
  to re-implement the keymap selector, the tree/section model, the action-list
  view, the read-only→editable derivation, and the `writeScheme` diff — i.e.
  rebuild this plugin to add one view.
- **Thematic coherence.** The plugin's job is *"understand and control what your
  keymap actually does."* Today that's conflicts + rebind/remove/export/import.
  "Which shortcuts have I changed, and revert them" is the same job from the
  audit angle. Revert-to-default is a near-twin of the existing Remove.
- **One place to manage a keymap** beats two plugins a user must install and
  reconcile.

### Scope: this is already a keymap manager
The old guard in `CLAUDE.md` — *"a shortcut-conflict manager … not a general
keymap editor"* — predates what the plugin has become. It already does
selector · activate · duplicate · rename · delete · export · import · rebind ·
remove. That is keymap **management**, not just conflict resolution. So a
*Modified shortcuts* audit + revert view is **not scope creep** — it is
consistent with what the plugin already is. We stop treating growth as a risk to
mitigate and instead name the identity honestly. The only rudder we keep: stay
**macOS-focused** (the live system-shortcut scan is the differentiator) and
remain a *purposeful manager*, not a wholesale reimplementation of the platform's
Keymap-settings tree editor. The broadened scope statement (and a possible
rename) are decided in [0002](0002-plugin-scope-and-name.md).

### When a sibling *would* be right (rejected here)
- If we wanted separate branding/distribution decoupled from the bundled
  MacBook-Pro-DE keymap. Not a goal now.
- If the feature demanded infrastructure ours can't host. It doesn't — it reuses
  ours almost entirely.

### Recommendation
**Extend this plugin.** Add a *Modified shortcuts* audit-and-revert view to the
existing report dialog (Phases 1–4). Do **not** spin up a sibling plugin, and do
**not** attempt to hook the platform Keymap settings.

This gives the plugin a clean value story: *it provides today, in its own dialog,
the "show modified / revert" auditing that IJPL-228176 is still asking JetBrains
to build into the IDE.*

---

## 6. Decision

**Extend the plugin — accepted in principle** (user, 2026-07-23): the feature
fits, and the plugin may grow beyond pure conflict resolution. The residual
questions are not *whether* but *how we frame it* — the broadened scope statement
and a possible plugin rename — tracked in
[0002](0002-plugin-scope-and-name.md). Implementation is not blocked on the name;
Phases 1–2 can start once 0002's scope statement is agreed.

## 7. Implementation notes (Phases 1–2, 2026-07-23)

Landed as a read-only audit view; no revert yet (that is Phase 3).

- **`ConflictScan`** — new `record ModifiedBinding(ActionRef action, List<Shortcut> shortcuts)`
  and a `modified` list on the scan, computed in `modifiedBindings(keymap)` from
  `KeymapImpl.writeScheme().getChildren("action")` (the same own-declarations set the
  export's CHANGES category uses), resolved through the existing `refs(...)`. An empty
  shortcut list = a cleared inherited binding.
- **`ConflictReportDialog`**
  - *Phase 1:* `updateSummary()` appends "N shortcut(s) modified in this keymap."
  - *Phase 2:* `SEC_MODIFIED` section + `ModifiedItem` payload; `buildRoot()` gates the
    conflict sections behind `showModifiedOnly` and always appends the Modified section;
    `Renderer.renderModified` and `buildModifiedDetail` render the rows/detail; a
    **"Show modified only"** gear toggle (`showModifiedOnly` + `rebuildTree()`) filters to
    just this section. Expansion/selection generalized via `shouldExpand` / `selectFirstRow`.
- The Modified section is informational (collapsed by default) except in "Show modified only"
  mode. For the bundled *MacBook Pro DE* the count is large (it re-states its bindings); for a
  derived/user keymap it shows just the handful of changes — the intended audit case.
- Verified: IDE rebuild + `./gradlew buildPlugin` both clean. Not yet exercised in `runIde`.

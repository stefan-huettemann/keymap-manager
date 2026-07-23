# 0002 — Plugin scope and name

- **Status:** Accepted & applied 2026-07-23 (name: **Keymap Manager**)
- **Related:** [0001](0001-modified-shortcuts-filter.md) triggered this; see also
  the pre-publish window noted in `PUBLISHING.md`.
- **Date:** 2026-07-23

---

## 1. Context

Two things forced this decision now:

1. **The plugin outgrew its name.** It launched as *Manage Keymap Conflicts*, but
   already does far more than conflicts: keymap selector, activate, duplicate,
   rename, delete, export (multiple categories), import from XML, in-place
   rebind/remove, plus a bundled starter keymap. Adding a *Modified shortcuts*
   audit + revert view (0001) broadens it further.
2. **We are still pre-publish.** `PUBLISHING.md` describes the first upload as
   ahead of us. Name and vendor are cheapest to set **before** the first
   Marketplace release (a later rename triggers re-moderation and churns the
   listing). The plugin **id** was already set to `de.civa.plugins.keymapmanager`
   — which itself signals the "keymap manager" identity, ahead of the display
   name.

The old `CLAUDE.md` guard — *"a shortcut-conflict manager … not a general keymap
editor"* — is now more restrictive than reality and is holding back a natural,
low-cost feature. Decision: relax it.

## 2. Decision A — scope (agreed: relax)

The plugin is a **macOS-focused keymap manager for JetBrains IDEs**, not merely a
conflict tool. Proposed replacement for the `CLAUDE.md` scope statement:

> A **macOS-focused keymap manager** for JetBrains IDEs: find and fix shortcut
> conflicts, audit and revert your own customizations, rebind/remove shortcuts in
> place, import/export keymaps as XML, and start from a bundled MacBook-Pro-DE
> keymap. It **complements** the platform's Keymap settings rather than replacing
> them; its differentiator is the **live macOS system-shortcut scan** (JBR
> `SystemShortcuts`). It stays a *focused manager* — not a general tree editor for
> every action, and not cross-platform (the scan is macOS-only).

Two rudders survive the relaxation, so "manager" doesn't drift into "clone of
Settings → Keymap":

- **macOS focus** — the live system-shortcut scan is the reason to use it over
  the built-in keymap UI. Features that don't need macOS awareness should still
  earn their place.
- **Purposeful, not exhaustive** — we add targeted tools (find conflicts, audit
  changes, rebind a key), not a full re-hosting of the platform action tree.

**Action when accepted:** update the scope paragraph in `CLAUDE.md` and the
closing "Keep the surface at this set…" sentence to match this wording.

## 3. Decision B — name (decided: **Keymap Manager**)

Renaming the **display name** is safe pre-publish and consistent with the already
committed id (`de.civa.plugins.keymapmanager`). The **id never changes** — only
the shown `<name>` (and optionally the `<vendor>`).

Candidates:

| Option | Pros | Cons |
|--------|------|------|
| **Keymap Manager for macOS** *(rec.)* | Natural English; signals the macOS differentiator; matches the id; strong Marketplace search hit; honest about macOS-only | Slightly generic |
| **macOS Keymap Manager** | Same virtues, terser | "macOS…" leading names sort oddly in some lists |
| **PulmoVention Keymap Manager** | Brand-forward; pairs with a CIVA→PulmoVention vendor rebrand | Brand-led names discover worse in search; couples name to a branding decision not yet finalized |
| **Manage Keymap Conflicts** *(keep)* | No change; already chosen | Undersells and mis-describes a plugin that does much more than conflicts |

Guidance applied: Marketplace discovery favors **function-led** names over
brand-led ones; the differentiator (**macOS**) belongs in the title for honesty
and search. Brand belongs on the **vendor**, which is a separate axis (see §4).

**Recommendation was** *Keymap Manager for macOS*. **Decided (user, 2026-07-23):
plain "Keymap Manager"** — a neutral name, no platform qualifier and no brand in
the title. Rationale: keep the title clean and let the **description** carry the
specifics (it was rewritten to spell out exactly what the plugin does today,
macOS focus included). Brand stays on the vendor field (Decision C).

Caveat noted for publish time: "Keymap Manager" is **generic** — do a quick
Marketplace search before first upload to confirm it's distinct enough and not
confusingly close to an existing plugin.

## 4. Decision C — vendor (separate, not blocking)

Vendor is independent of the display name and changeable later (see the earlier
Marketplace-rename analysis). Current `<vendor>` is **CIVA**. A move to
**PulmoVention** is a branding call that can be made at or after first publish;
it does not gate 0001 or the rename. Note: a vendor literally named "JetBrains"
would be rejected by moderation — not in play here.

## 5. Consequences / follow-ups once names are chosen

- Update `plugin.xml` `<name>` (and `<vendor>` if rebranding).
- Update `CLAUDE.md` scope paragraph + closing sentence (Decision A).
- Update `README.md`, `PUBLISHING.md`, and the Marketplace listing copy to the
  new name; keep the `<description>` accurate to the broadened scope.
- The `<id>` and the Java package `de.civa.keymap` stay as-is (unaffected).

## 6. Decision

- **A (scope):** accepted — relaxed to the "macOS-focused keymap manager"
  statement above; the `CLAUDE.md` scope paragraph + closing sentence were
  updated to match (2026-07-23).
- **B (name):** decided — **"Keymap Manager"**. Applied across `plugin.xml`
  (`<name>`, Tools action, description), the dialog/About titles, the startup
  notification, `settings.gradle.kts` (ZIP → `Keymap-Manager-<version>.zip`),
  and the docs (`README`, `INSTALL`, `PUBLISHING`, `CLAUDE`).
- **C (vendor):** deferred; non-blocking. Still `CIVA`.

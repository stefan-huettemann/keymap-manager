# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository is

A **JetBrains plugins project** (see `README.md`). The active plugin is a keymap plugin: `CIVA Keymap` (ID `de.civa.keymap`) bundles the **MacBook Pro DE** keymap for all JetBrains IDEs on macOS. The driving constraint is a **German (T1/ISO) keyboard on a MacBook Pro** — many stock IntelliJ bindings are physically unpressable there and are being replaced.

`keymaps/keys.md` is the canonical reference: the German-layout constraint, the audit of every key the keymap uses, and the machine-verified table of bindable German keys (umlaut hex encoding). Read it before touching keymap XML. (A more detailed research doc, `handover-unreachable-keys.md`, exists locally but is gitignored — do not rely on it being present.)

## Commands

```bash
# Build the plugin ZIP  →  keymap-plugin/build/distributions/CIVA-Keymap-<version>.zip
# (<version> comes from build.gradle.kts; currently 1.4.0)
cd keymap-plugin && ./gradlew buildPlugin

# Launch a sandbox IDE with the plugin loaded (for manual verification)
cd keymap-plugin && ./gradlew runIde
```

There are no tests or linters. After editing keymap XML, verify with an ad-hoc Python script (xml.etree): well-formed, action ids unique and alphabetically sorted, every id present in `keymaps/$default.xml` or `keymaps/Mac OS X 10.5+.xml` (unknown ids fail **silently** at runtime), and only `keyboard-shortcut` / `mouse-shortcut` / `keyboard-gesture-shortcut` child elements (anything else throws `InvalidDataException` at load).

Install/activation steps for the built ZIP: `keymap-plugin/INSTALL.md`.

## Structure and data flow

- `keymaps/MacBook Pro DE.xml` — **working copy / source of truth** for the keymap. It is a *flattened* merge of `$default.xml` and `Mac OS X 10.5+.xml` (Mac entries win; stale conflicting shortcuts pruned): all ~447 bindings are re-stated explicitly even though `parent="Mac OS X 10.5+"` would inherit most, deliberately pinning them against upstream changes.
- `keymap-plugin/src/main/resources/keymaps/MacBook Pro DE.xml` — the plugin resource, a **manual copy** of the file above. After editing the working copy, `cp` it here and rebuild; nothing syncs them automatically.
- `keymaps/$default.xml`, `keymaps/Mac OS X 10.5+.xml` — reference copies of the platform keymaps. All three keymap files are sorted alphabetically by action id for side-by-side diffing; keep them sorted.
- `keymaps/orig/` — pristine platform exports; do not modify.
- `keymaps/keys.md` — generated audit of every modifier/key used, with German-layout reachability. Appendix A (37 actions with no reachable binding) is the rebinding work list.
- `keymap-plugin/` — Gradle project: `plugin.xml` + the keymap XML, wired via the `bundledKeymap` extension point, plus five Java classes: `NationalLayoutCheck` fires a one-shot startup/keymap-change notification for **whatever keymap is active** — a warning with a one-click fix when *our* keymap is active while JBR national keyboard layout support (`com.sun.awt.use.national.layouts`, default on for macOS) is off (without which the German-key bindings never fire), otherwise a plain nudge to review conflicts; both carry the "Review keymap conflicts" action. `ConflictScan` + `ConflictAdvice` + `ConflictReportDialog` implement the live report, also opened by `ShowConflictReportAction` (Tools → Review Keymap Conflicts). The dialog has three parts — a keymap **selector** (dropdown of all installed keymaps: active first, then this plugin's, then the rest, each tagged with its source; Activate/Reset for a non-active pick), a prominent **summary**, and a **details** tree + explanation pane. From any conflict — the keymap's own **or** an outside one (bindings from another plugin/IDE, shown with their providing plugin as a source tag) — it can **remove** the shortcut or **bulk-rebind** every mapping on one keystroke at once (the change IDE Settings can't do in bulk). Rebinding uses `ShortcutInputDialog`: the key field grabs a full keypress — even IDE-bound combos like ⌃X, because it intercepts via an `IdeEventQueue` dispatcher ahead of the keymap dispatcher — or a typed single key, alongside modifier checkboxes and a live green/red status that flags a macOS overlap **and** an existing in-keymap binding of the new shortcut; keys map through `KeyEvent.getExtendedKeyCodeForChar`, so German keys Ä Ö Ü ß work, with manual correction when capture can't read a key. Any edit on a read-only keymap first derives, registers and activates an editable copy. **Export…** writes the selected keymap to XML (`KeymapImpl.writeScheme`) in three scopes: full, only conflicting mappings, or conflicting + overlapping. Conflict detection is runtime (JBR `com.jetbrains.SystemShortcuts` via reflection, same source as IDEA's own conflict banner; in-keymap duplicates via public `Keymap` API). The ownership split (our curated bindings vs. plugin/IDE ones) and the static `SUPPLEMENT` extras apply **only to our bundled keymap**, matched by name against the bundled resource; for any other selected keymap every overlap is reported as that keymap's own. Curated per-conflict advice lives in `ConflictAdvice` (keep in sync with `keys.md` Appendix C). Keep the surface at this set — the plugin is the keymap plus its conflict report and the rebind/export tools that serve it, not a general keymap editor.

## Keymap XML invariants

- Filename and the `name=` attribute must match exactly (`MacBook Pro DE` / `MacBook Pro DE.xml`). **Never rename the keymap** — users who selected it would be silently reverted to the default.
- `parent="Mac OS X 10.5+"`. The name must not start with `Mac OS X` (triggers `MacOSDefaultKeymap` modifier conversion) and must not reuse an official keymap name (`Eclipse`, `Emacs`, `NetBeans 6.5`, `QtCreator`, `ReSharper`, `Sublime Text`, `Visual Studio`, `Visual Assist`, `Xcode`, `Rider`, `VSCode`, `macOS System Shortcuts`, or their `… OSX`/`… (Mac OS X)` variants — canonical list in `notifyAboutMissingKeymap`, `KeymapImpl.kt`).
- Empty `<action id="X"/>` elements are **load-bearing** — they clear bindings inherited from the parent. Do not remove them as cleanup.
- German-layout rule: bindings address *physical keys* (VK codes), not characters. Never bind `SLASH`, `BACK_SLASH`, `OPEN_BRACKET`, `CLOSE_BRACKET`, `SEMICOLON`, `QUOTE`, `BACK_QUOTE`, `EQUALS` (no physical key on German T1), `INSERT` (no Mac key), or numpad keys `ADD`/`SUBTRACT`/`MULTIPLY`/`DIVIDE`/`NUMPADn` (MacBook Pro has no numpad).
- `Ä Ö Ü ß` are bindable via extended hex codes (`meta #10000d6` = ⌘Ö). Do not compute the codes by hand — `Ü` breaks the codepoint pattern. The machine-verified values are tabulated in `keymaps/keys.md` ("Bindable German keys").

## Gradle gotchas (already hit once)

- The platform dependency must use the Maven repository, not installers: `intellijIdeaCommunity("2026.1.4") { useInstaller = false }` — installer URL resolution fails for this version. Note `useInstaller` is set inside the configuration lambda; the named-parameter form no longer exists in IntelliJ Platform Gradle Plugin 2.18+.
- `buildSearchableOptions = false` stays off — the task boots a headless IDE for nothing in a resource-only plugin.
- `sinceBuild = "261"` with **no** `untilBuild` is deliberate: the plugin has no API surface, so capping compatibility only forces pointless re-releases.
- The build prints "IntelliJ IDEA Community (IC) is no longer published since 2025.3, use: intellijIdea(...)". The pinned `intellijIdeaCommunity("2026.1.4")` still resolves; switch to `intellijIdea("...")` when next bumping the platform version.

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository is

A **JetBrains plugins project** (see `README.md`). The active plugin is a keymap plugin: `CIVA Keymap` (ID `de.civa.keymap`) bundles the **MacBook Pro DE** keymap for all JetBrains IDEs on macOS. The driving constraint is a **German (T1/ISO) keyboard on a MacBook Pro** — many stock IntelliJ bindings are physically unpressable there and are being replaced.

`keymaps/keys.md` is the canonical reference: the German-layout constraint, the audit of every key the keymap uses, and the machine-verified table of bindable German keys (umlaut hex encoding). Read it before touching keymap XML. (A more detailed research doc, `handover-unreachable-keys.md`, exists locally but is gitignored — do not rely on it being present.)

## Commands

```bash
# Build the plugin ZIP  →  keymap-plugin/build/distributions/CIVA-Keymap-1.0.0.zip
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
- `keymap-plugin/` — Gradle project: `plugin.xml` + the keymap XML, wired via the `bundledKeymap` extension point, plus exactly one Java class, `NationalLayoutCheck` — it warns (with a one-click fix) when the keymap is selected while JBR national keyboard layout support (`com.sun.awt.use.national.layouts`, default on for macOS) is disabled, without which the German-key bindings never fire. Keep the code surface at this minimum; the plugin's job is the keymap, not features.

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

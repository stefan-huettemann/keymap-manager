# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository is

A **JetBrains plugins project** (see `README.md`). The active plugin is a keymap plugin: `CIVA Keymap` (ID `de.civa.keymap`) bundles the **CIVA MacOS** keymap for all JetBrains IDEs on macOS. The driving constraint is a **German (T1/ISO) keyboard on a MacBook Pro** — many stock IntelliJ bindings are physically unpressable there and are being replaced.

`handover-unreachable-keys.md` is the canonical background document: platform internals (verified against intellij-community source), the German-layout constraint, and the umlaut hex-encoding trick. Read it before touching keymap XML.

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

- `keymaps/CIVA MacOS.xml` — **working copy / source of truth** for the keymap. It is a *flattened* merge of `$default.xml` and `Mac OS X 10.5+.xml` (Mac entries win; stale conflicting shortcuts pruned): all ~447 bindings are re-stated explicitly even though `parent="Mac OS X 10.5+"` would inherit most, deliberately pinning them against upstream changes.
- `keymap-plugin/src/main/resources/keymaps/CIVA MacOS.xml` — the plugin resource, a **manual copy** of the file above. After editing the working copy, `cp` it here and rebuild; nothing syncs them automatically.
- `keymaps/$default.xml`, `keymaps/Mac OS X 10.5+.xml` — reference copies of the platform keymaps. All three keymap files are sorted alphabetically by action id for side-by-side diffing; keep them sorted.
- `keymaps/orig/` — pristine platform exports; do not modify.
- `keymaps/keys.md` — generated audit of every modifier/key used, with German-layout reachability. Appendix A (37 actions with no reachable binding) is the rebinding work list.
- `keymap-plugin/` — resource-only Gradle project: `plugin.xml` + the keymap XML, wired via the `bundledKeymap` extension point. **No Java/Kotlin code, ever** — if a `.kt`/`.java` file appears, something went wrong.

## Keymap XML invariants

- Filename and the `name=` attribute must match exactly (`CIVA MacOS` / `CIVA MacOS.xml`). **Never rename the keymap** — users who selected it would be silently reverted to the default.
- `parent="Mac OS X 10.5+"`. The name must not start with `Mac OS X` and must not reuse a reserved keymap name (list in `handover-unreachable-keys.md` §3.6).
- Empty `<action id="X"/>` elements are **load-bearing** — they clear bindings inherited from the parent. Do not remove them as cleanup.
- German-layout rule: bindings address *physical keys* (VK codes), not characters. Never bind `SLASH`, `BACK_SLASH`, `OPEN_BRACKET`, `CLOSE_BRACKET`, `SEMICOLON`, `QUOTE`, `BACK_QUOTE`, `EQUALS` (no physical key on German T1), `INSERT` (no Mac key), or numpad keys `ADD`/`SUBTRACT`/`MULTIPLY`/`DIVIDE`/`NUMPADn` (MacBook Pro has no numpad).
- `Ä Ö Ü ß` are bindable via extended hex codes (`meta #10000d6` = ⌘Ö). Do not compute the codes by hand — `Ü` breaks the codepoint pattern. The machine-verified values are tabulated in `keymaps/keys.md` ("Bindable German keys").

## Gradle gotchas (already hit once)

- The platform dependency must use the Maven repository, not installers: `intellijIdeaCommunity("2026.1.4") { useInstaller = false }` — installer URL resolution fails for this version. Note `useInstaller` is set inside the configuration lambda; the named-parameter form no longer exists in IntelliJ Platform Gradle Plugin 2.18+.
- `buildSearchableOptions = false` stays off — the task boots a headless IDE for nothing in a resource-only plugin.
- `sinceBuild = "261"` with **no** `untilBuild` is deliberate: the plugin has no API surface, so capping compatibility only forces pointless re-releases.

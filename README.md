# JetBrains Plugins

Development workspace for JetBrains IDE plugins.

## Plugins

### CIVA Keymap (`keymap-plugin/`)

Resource-only plugin that bundles the **MacBook Pro DE** keymap for all JetBrains
IDEs on macOS. Motivation: the stock keymaps are designed for US ANSI keyboards,
and a significant number of their shortcuts are physically unpressable on a
German (T1) layout — this keymap replaces them with reachable bindings.

```bash
cd keymap-plugin
./gradlew buildPlugin
# → build/distributions/CIVA-Keymap-1.0.0.zip
```

Installation and settings-sync setup: [`keymap-plugin/INSTALL.md`](keymap-plugin/INSTALL.md)

## Repository layout

| Path | Content |
|---|---|
| `keymap-plugin/` | Gradle plugin project (plugin.xml + bundled keymap, no code) |
| `keymaps/MacBook Pro DE.xml` | Keymap source of truth; copy into the plugin resources before building |
| `keymaps/$default.xml`, `keymaps/Mac OS X 10.5+.xml` | Platform reference keymaps, sorted for diffing |
| `keymaps/keys.md` | Audit of all keys used, with German-layout reachability |
| `keymaps/orig/` | Pristine platform keymap exports |
| `handover-unreachable-keys.md` | Background: platform internals and the German keyboard constraint |
| `CLAUDE.md` | Guidance for Claude Code sessions in this repo |

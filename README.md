# JetBrains Plugins

Development workspace for JetBrains IDE plugins.

**Repository:** https://github.com/stefan-huettemann/keymap-manager

## Plugins

### Keymap Manager (`keymap-plugin/`)

Plugin for **managing keymap shortcut conflicts** on macOS — a live, explained report of
overlaps with macOS system shortcuts and duplicate in-keymap bindings, with in-place
rebind/remove and XML export/import for sharing keymaps. It also bundles the **MacBook Pro DE** keymap as a ready-made
starting point: the stock keymaps target US ANSI keyboards, and many of their shortcuts are
physically unpressable on a German (T1) layout — this keymap replaces them with reachable bindings.

```bash
cd keymap-plugin
./gradlew buildPlugin
# → build/distributions/Keymap-Manager-1.6.0.zip
```

Installation and settings-sync setup: [`keymap-plugin/INSTALL.md`](keymap-plugin/INSTALL.md)

Releasing to the JetBrains Marketplace: [`PUBLISHING.md`](PUBLISHING.md)

## Repository layout

| Path | Content |
|---|---|
| `keymap-plugin/` | Gradle plugin project (plugin.xml + bundled keymap + conflict-report code) |
| `keymaps/MacBook Pro DE.xml` | Keymap source of truth; copy into the plugin resources before building |
| `keymaps/$default.xml`, `keymaps/Mac OS X 10.5+.xml` | Platform reference keymaps, sorted for diffing |
| `keymaps/keys.md` | Audit of all keys used, with German-layout reachability |
| `keymaps/orig/` | Pristine platform keymap exports |
| `CLAUDE.md` | Guidance for Claude Code sessions in this repo |

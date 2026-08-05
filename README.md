# JetBrains Plugins

Development workspace for JetBrains IDE plugins.

**Repository:** https://github.com/stefan-huettemann/keymap-manager

## Plugins

### Keymap Manager (`keymap-plugin/`)

#### Stop losing shortcuts to macOS

Every JetBrains IDE ships shortcuts that macOS quietly claims for itself — Spotlight, Mission
Control, window snapping, screenshots, and more. Some still reach the IDE; some never fire at
all, and you won't know which is which until it bites you mid-flow. **Keymap Manager** finds
every one of them on **any** keymap you have installed, explains exactly what's happening, and
lets you fix it without leaving the IDE.

Open it from **Tools → Keymap Manager…** or Find Action.

#### What it does

- **Find conflicts** — a live, grouped report of every shortcut that clashes with a **macOS
  system shortcut** (read from the JetBrains Runtime's live system-shortcut table, the same
  source as the IDE's own conflict banner), plus **double-bound keys** (one keystroke on
  several actions) and a separate section for macOS overlaps the IDE doesn't flag. Each
  conflict is explained — whether your IDE still wins the key or macOS takes it first — with
  tailored advice.
- **Fix it in place** — **rebind**, **remove**, or **revert** to the parent keymap's binding,
  for one action, several at once, or a whole category. Works on the keymap's own bindings
  *and* on the ones it inherits; a read-only keymap gets an editable copy automatically. The
  rebind field captures a real keypress — even IDE-bound combos like ⌃X — offers the keys you
  can't press into a field (↩ ⎋ ⌫ ⇥ Space), supports German keys (Ä Ö Ü ß), flags macOS
  overlaps and existing bindings as you type, and can **suggest** a free (or least-conflicting)
  shortcut for you.
- **Review what changed** — **Modified shortcuts** shows what a keymap declares itself (its
  diff against the parent); **Inherited shortcuts** shows what it takes from the parent
  unchanged. Narrow the report to just the modified ones, or show each action's **id** and the
  **keymap that defines** its binding.
- **Find one action fast** — the **filter field** beside the keymap selector narrows every
  section live to the actions whose name you type; each section's count then reads
  "matched of total", and a filter that matches nothing says so.
- **Manage keymaps** — pick any installed keymap, then **activate**, **duplicate**, **rename**,
  or **delete** it.
- **Import / export** keymaps as XML — the whole inheritance chain, or just the categories you
  want (conflicts, overlaps, double-bound keys, or your own changes versus the parent).

Shortcuts render as **keycaps** everywhere, matching Settings → Keymap. (Giving a shortcut to
an action that has **none** is still a Settings → Keymap job — every category links straight
there, landing on the action you were looking at.)

#### Bonus: a keymap for German MacBook Pro keyboards

On a German (T1) layout, dozens of stock IntelliJ shortcuts are physically unpressable — keys
like /, =, ;, ', `, [, ] are only reachable via a Shift/Option chord, and there's no numpad or
Insert key. The bundled **MacBook Pro DE** keymap is rebuilt around all of that, ready to select
under **Settings → Keymap** — no rebinding required to get started.

*macOS-focused. Report wording adapts to the host IDE (IntelliJ IDEA, PyCharm, GoLand, Android
Studio, …).*

```bash
cd keymap-plugin
./gradlew buildPlugin
# → build/distributions/Keymap-Manager-1.7.0.zip
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
| `keymaps/validate_keymap.py` | Keymap invariant checker; must print `PASS` after any keymap edit |
| `docs/` | Marketplace listing copy (not the in-IDE `plugin.xml` description) |
| `CLAUDE.md` | Guidance for Claude Code sessions in this repo |

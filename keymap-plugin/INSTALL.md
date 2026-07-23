# Installing the Manage Keymap Conflicts Plugin

The plugin ships the **MacBook Pro DE** keymap for all JetBrains IDEs (IntelliJ IDEA,
PyCharm, CLion, …) on macOS.

---

## Part 1 — Install the plugin

Install it in **each IDE** where you want the keymap (each JetBrains IDE manages
its own plugins). Two ways:

### Option A — From the JetBrains Marketplace (recommended)

1. Open **Settings** (`⌘ ,`) → **Plugins**.
2. Select the **Marketplace** tab and search for **Manage Keymap Conflicts**.
3. Click **Install**, then **Restart IDE** when prompted.

No ZIP or build needed, and updates arrive automatically. Because it comes from
the Marketplace, **Backup and Sync can install it on your other IDEs for you** —
see Part 3.

### Option B — From disk (a local build, or before it is on the Marketplace)

You need the plugin ZIP:

```
keymap-plugin/build/distributions/Manage-Keymap-Conflicts-1.5.0.zip
```

If it is missing, rebuild it:

```bash
cd keymap-plugin
./gradlew buildPlugin
```

Then, in each IDE:

1. Open **Settings** (`⌘ ,`) → **Plugins**.
2. Click the **⚙ gear icon** (top right of the plugin list) → **Install Plugin from Disk…**
3. Select `Manage-Keymap-Conflicts-1.5.0.zip` → **OK**.
4. Click **Restart IDE** when prompted.

> Installing the plugin does **not** switch your active keymap — that is Part 2.

## Part 2 — Activate the keymap

1. Open **Settings** (`⌘ ,`) → **Keymap**.
2. In the dropdown at the top, select **MacBook Pro DE**.
3. Click **Apply** / **OK**.

Verify: the dropdown shows *MacBook Pro DE*, and e.g. `⌘⇧G` triggers *Find Previous*.

> **Requirement: national keyboard layout support.** The keymap binds keys of the
> German layout (`Ä Ö Ü ß + # <`), which the JetBrains Runtime only reports while
> **Settings → Keymap → "Use national keyboard layouts for shortcuts"** is enabled.
> It is enabled by default on macOS — leave it on. If it is off when you select the
> keymap, the plugin (1.1.0+) shows a warning with an **Enable and restart** button.

> **macOS shortcut conflicts.** IDEA may report that a few shortcuts also belong to
> macOS. Most are intentional (window cycling, emoji picker, standard app shortcuts).
> When the keymap is activated, the plugin (1.2.0+) offers a **Manage keymap conflicts**
> action (also under **Tools → Manage Keymap Conflicts**) that opens a grouped, explained
> report of which overlaps are by design and which depend on your macOS settings, where you
> can rebind or remove them in place.

---

## Part 3 — Sync the keymap to your other installations

JetBrains **Backup and Sync** distributes your settings — including which keymap
is active and any personal shortcut overrides — to every IDE logged into your
JetBrains account.

> **Plugin distribution depends on how you installed it.** Backup and Sync only
> auto-installs plugins that come from the JetBrains Marketplace:
> - **Marketplace install (Option A):** sync installs the plugin on your other
>   IDEs automatically — nothing else to do.
> - **From-disk install (Option B):** sync transfers only the keymap *selection*,
>   not the plugin, so do **Part 1 on every installation first** — otherwise those
>   IDEs won't know the *MacBook Pro DE* keymap and will silently fall back to the
>   default (macOS) keymap.

Step by step:

1. On the machine where the keymap is installed and active (Parts 1–2 done):
   1. Open **Settings** (`⌘ ,`) → **Backup and Sync**.
   2. Click **Enable backup and sync** and log in with your **JetBrains Account**
      if prompted.
   3. In the category list, make sure **Keymap** is checked (leave the others as
      you prefer).
   4. Choose the sync scope: select **across all JetBrains IDE products** so
      IntelliJ IDEA, PyCharm and CLion all share the setting (instead of
      per-product sync).
   5. If the IDE asks how to resolve existing cloud settings, choose to **push
      the local settings** (keep this machine's configuration) — pulling would
      overwrite your fresh keymap selection.
2. On **every other installation** (other machine, or another IDE on the same
   machine):
   1. Make sure the plugin is installed — Part 1 above. (A Marketplace install may
      arrive automatically via sync; a from-disk install must be done here manually.)
   2. Enable **Backup and Sync** the same way, logging into the **same JetBrains
      Account**, and choose to **get settings from the account** when asked.
   3. Restart or wait a moment: **Settings → Keymap** should now show
      **MacBook Pro DE** as the active keymap.

---

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Keymap dropdown has no *MacBook Pro DE* entry | Plugin not installed or disabled in this IDE. Check **Settings → Plugins → Installed** for *Manage Keymap Conflicts*. |
| Another installation reverted to the *macOS* keymap | The plugin isn't installed there. Sync auto-installs Marketplace plugins but not from-disk ones — install it there (Part 1). |
| Shortcuts behave oddly after an IDE update | Check `Help → Show Log in Finder` (`idea.log`) for `Cannot find parent scheme` or `Cannot find keymaps/...` warnings and reinstall the plugin ZIP. |
| Want to tweak a shortcut | Just change it in **Settings → Keymap** while *MacBook Pro DE* is active — the IDE stores your change as a personal override on top of the plugin keymap, and Backup and Sync distributes it. |

**Do not rename** the keymap (`name="MacBook Pro DE"`) in future plugin versions —
every installation that has it selected would silently revert to the default
keymap.

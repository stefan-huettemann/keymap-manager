# Installing the CIVA Keymap Plugin

The plugin ships the **MacBook Pro DE** keymap for all JetBrains IDEs (IntelliJ IDEA,
PyCharm, CLion, …) on macOS.

## What you need

The plugin ZIP:

```
keymap-plugin/build/distributions/CIVA-Keymap-1.0.0.zip
```

If it is missing, rebuild it:

```bash
cd keymap-plugin
./gradlew buildPlugin
```

---

## Part 1 — Install the plugin from disk

Repeat in **each IDE** where you want the keymap (each JetBrains IDE manages its
own plugins):

1. Open **Settings** (`⌘ ,`).
2. Go to **Plugins**.
3. Click the **⚙ gear icon** (top right of the plugin list) → **Install Plugin from Disk…**
4. Select `CIVA-Keymap-1.0.0.zip` → **OK**.
5. Click **Restart IDE** when prompted.

> Installing the plugin does **not** switch your active keymap — that is Part 2.

## Part 2 — Activate the keymap

1. Open **Settings** (`⌘ ,`) → **Keymap**.
2. In the dropdown at the top, select **MacBook Pro DE**.
3. Click **Apply** / **OK**.

Verify: the dropdown shows *MacBook Pro DE*, and e.g. `⌘⇧G` triggers *Find Previous*.

---

## Part 3 — Sync the keymap to your other installations

JetBrains **Backup and Sync** distributes your settings — including which keymap
is active and any personal shortcut overrides — to every IDE logged into your
JetBrains account.

> **Important limitation:** Backup and Sync only auto-installs plugins that come
> from the JetBrains Marketplace. This plugin is installed **from disk**, so sync
> cannot install it on your other machines/IDEs. Do **Part 1 on every
> installation first** — otherwise those IDEs won't know the *MacBook Pro DE* keymap
> and will silently fall back to the default (macOS) keymap.

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
   1. Install the plugin from disk — Part 1 above.
   2. Enable **Backup and Sync** the same way, logging into the **same JetBrains
      Account**, and choose to **get settings from the account** when asked.
   3. Restart or wait a moment: **Settings → Keymap** should now show
      **MacBook Pro DE** as the active keymap.

---

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Keymap dropdown has no *MacBook Pro DE* entry | Plugin not installed or disabled in this IDE. Check **Settings → Plugins → Installed** for *CIVA Keymap*. |
| Another installation reverted to the *macOS* keymap | The plugin isn't installed there — sync only transfers the keymap *selection*, not the from-disk plugin. Do Part 1 on that installation. |
| Shortcuts behave oddly after an IDE update | Check `Help → Show Log in Finder` (`idea.log`) for `Cannot find parent scheme` or `Cannot find keymaps/...` warnings and reinstall the plugin ZIP. |
| Want to tweak a shortcut | Just change it in **Settings → Keymap** while *MacBook Pro DE* is active — the IDE stores your change as a personal override on top of the plugin keymap, and Backup and Sync distributes it. |

**Do not rename** the keymap (`name="MacBook Pro DE"`) in future plugin versions —
every installation that has it selected would silently revert to the default
keymap.

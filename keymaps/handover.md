# Handover: JetBrains Keymap Plugin

**For:** Claude Code CLI
**From:** prior research session, 2026-07-19
**Environment:** macOS 26.3.1, MacBook Pro. JetBrains IDE installed locally.

---

## 1. Goal

Build a JetBrains plugin that adds a **custom keymap** to the user's IDEs — the same mechanism the official "Eclipse Keymap" / "VSCode Keymap" plugins use.

Three deliverables, in order:

1. **Define the keymap** — a custom keymap derived from the platform defaults, sourced from the user's existing exported keymap.
2. **Create the plugin project** — in a **sub-directory of the current project**, not at the repo root.
3. **Build and install it** into the user's local IDE.

---

## 2. Decisions already made

Do not re-litigate these; they were chosen deliberately.

| Decision | Choice | Consequence |
|---|---|---|
| Platform scope | **macOS only** | One keymap file, `parent="Mac OS X 10.5+"`. No `$OS$` macro needed. |
| Target IDEs | **All JetBrains IDEs** | `<depends>com.intellij.modules.platform</depends>` and nothing else. |
| Deploy | **Build ZIP + manual install** | `./gradlew buildPlugin`, then Settings → Plugins → ⚙ → Install Plugin from Disk. No custom deploy task. |
| Binding source | **User's exported keymap** | Ask Stefan for his exported keymap XML first; derive the plugin resource from it. |

Open items you should confirm with the user early:

- **Plugin name / ID.** Suggested default: name `CIVA Keymap`, ID `de.civa.keymap`, group `de.civa`. Confirm before scaffolding.
- **Keymap display name.** Suggested `CIVA`. See §4.3 for naming rules — some names are reserved and trigger special behaviour.

---

## 3. Critical background (verified against `intellij-community` @ master)

This was checked against source, not recalled. Trust it over general knowledge.

### 3.1 A keymap plugin contains no code

It is a resource-only plugin: one `plugin.xml` plus keymap XML files. No Java, no Kotlin, no actions, no services. If you find yourself writing a `.kt` file, you have gone off track.

### 3.2 The extension point

```xml
<extensions defaultExtensionNs="com.intellij">
  <bundledKeymap file="CIVA.xml"/>
</extensions>
```

`file` resolves relative to `keymaps/` on the classpath — i.e. `src/main/resources/keymaps/CIVA.xml`.

For multi-OS plugins there is an `$OS$` macro (`file="$OS$/CIVA.xml"` → `keymaps/macos/CIVA.xml`). **Not needed here** — macOS-only was chosen.

Source: `DefaultKeymap.getEffectiveFile`:

```kotlin
internal fun getEffectiveFile(bean: BundledKeymapBean): String =
  "keymaps/${bean.file.replace("\$OS\$", osName())}"
```

### 3.3 Keymap XML format

```xml
<keymap version="1" name="CIVA" parent="Mac OS X 10.5+" disable-mnemonics="true">
  <action id="GotoDeclaration">
    <keyboard-shortcut first-keystroke="F3"/>
    <mouse-shortcut keystroke="control button1"/>
  </action>

  <!-- Two-stroke binding -->
  <action id="ExpandAllToLevel1">
    <keyboard-shortcut first-keystroke="meta alt MULTIPLY" second-keystroke="1"/>
  </action>

  <!-- Empty element = explicitly REMOVE the shortcuts inherited from parent -->
  <action id="EditorScrollUp"/>
</keymap>
```

Element names accepted by `KeymapImpl.readExternal` — anything else throws `InvalidDataException`:

- `keyboard-shortcut` — attrs `first-keystroke`, `second-keystroke`
- `mouse-shortcut` — attr `keystroke`
- `keyboard-gesture-shortcut` — attrs `keystroke`, `modifier` (`dblClick` | `hold`)

Modifier tokens are AWT `KeyStroke` syntax: `meta` = ⌘, `alt` = ⌥, `control`/`ctrl` = ⌃, `shift` = ⇧.

An **empty `<action>` element is meaningful** — it clears the parent's bindings rather than being a no-op. The macOS default keymap uses this to free up `⌘↑`/`⌘↓`.

### 3.4 Which parent to use

Two Mac keymaps exist in `platform/platform-resources/src/keymaps/`, and they are **siblings**, both with `parent="$default"`:

| File | Internal name | UI label | What it is |
|---|---|---|---|
| `Mac OS X 10.5+.xml` | `Mac OS X 10.5+` | **macOS** | The modern default on macOS. Full Cocoa conventions: `⌘F`, `⌘O`, `⌘N`, `⌘W`, `⌘[`/`⌘]`, plus the system Emacs text bindings `⌃A ⌃E ⌃K ⌃P ⌃N ⌃B ⌃F`. ~27 KB. |
| `Mac OS X.xml` | `Mac OS X` | **IntelliJ IDEA Classic** | Legacy, pre-Leopard. Keeps Windows-style Ctrl bindings inherited from `$default` (`⌃N` new, `⌃⇧F` find in path, `⌃F5` rerun). ~18 KB. |

**Use `parent="Mac OS X 10.5+"`.** It is what `DefaultKeymap.defaultKeymapName` returns when `OS.CURRENT == OS.macOS`, so it matches what the user sees today.

### 3.5 How the name becomes the UI label

Two-step chain. Get this wrong and the keymap shows up under an unexpected label.

**Step 1 — filename → internal name** (`DefaultKeymap.getKeymapName`):

```kotlin
FileUtilRt.getNameWithoutExtension(bean.file).removePrefix("\$OS\$/")
```

**⚠️ But** `KeymapImpl.readExternal` then does `name = keymapElement.getAttributeValue("name")!!` — the XML `name=` attribute **overwrites** the filename-derived name once the keymap is read. **Keep the filename and the `name=` attribute identical.** This is the single easiest thing to get wrong.

**Step 2 — internal name → UI label** (`DefaultKeymap.getKeymapPresentableName`, reached via `DefaultKeymapImpl.getPresentableName()`):

Hardcoded special cases:

| Internal name | UI label |
|---|---|
| `$default` | Windows |
| `Mac OS X 10.5+` | macOS |
| `Mac OS X` | IntelliJ IDEA Classic |
| `Default for GNOME` / `KDE` / `XWin` | GNOME / KDE / XWin |
| `NetBeans 6.5` | NetBeans |

Everything else falls through to:

```kotlin
val newName = name.removeSuffix(" (Mac OS X)").removeSuffix(" OSX")
(if (newName === name) name else "$newName (macOS)")
  .removePrefix("${osName()}/")
```

So ` (Mac OS X)` and ` OSX` are the two recognised "Mac variant" suffixes, both rendered as ` (macOS)`. Any other name is displayed **verbatim**.

Since this plugin is macOS-only and ships exactly one keymap, a plain name like `CIVA` displays as `CIVA`. Clean.

### 3.6 Reserved names — avoid these

- **Do not** start the name with `Mac OS X`. `DefaultKeymap.loadKeymap` branches on `keymapName.startsWith(KeymapManager.MAC_OS_X_KEYMAP)` and instantiates `MacOSDefaultKeymap` instead of `DefaultKeymapImpl`, applying extra modifier conversion you do not want.
- **Do not** reuse official keymap names. `notifyAboutMissingKeymap` in `KeymapImpl.kt` holds the canonical list: `Eclipse`, `Eclipse (Mac OS X)`, `Emacs`, `NetBeans 6.5`, `QtCreator`, `QtCreator OSX`, `ReSharper`, `ReSharper OSX`, `Sublime Text`, `Sublime Text (Mac OS X)`, `Visual Studio`, `Visual Studio OSX`, `Visual Studio 2022`, `Visual Studio for Mac`, `Visual Assist`, `Visual Assist OSX`, `Xcode`, `Rider`, `Rider OSX`, `VSCode`, `VSCode OSX`, `macOS System Shortcuts`.
- Avoid a trailing ` OSX` or ` (Mac OS X)` unless you actually want the automatic ` (macOS)` rewrite.

---

## 4. Work plan

### Phase 1 — Define the keymap

1. **Ask Stefan for his current keymap.** Custom keymaps live at:

   ```
   ~/Library/Application Support/JetBrains/<IDE><version>/keymaps/<Name>.xml
   ```

   If he has not made one yet: in the IDE, Settings → Keymap → ⚙ → Duplicate, adjust bindings, apply. The file appears at that path. That file is **already in exactly the right format** and contains only the deltas from its parent — which is what you want.

2. **Inspect its `parent` attribute.** If it is anything other than `Mac OS X 10.5+`, discuss with Stefan before rewriting — the deltas are only meaningful relative to their parent.

3. **Normalise it** into the plugin resource:
   - Set `name` to the agreed keymap name (must match the filename).
   - Set `parent="Mac OS X 10.5+"`.
   - Add `disable-mnemonics="true"` (both Mac keymaps in the platform set this).
   - Strip anything IDE-specific that will not resolve in other JetBrains IDEs — see the verification step in §5.

4. **Diff against the parent.** For every action you keep, confirm it is actually a *change* from `Mac OS X 10.5+`. Redundant entries are harmless but make the file misleading. Reference copy:
   `https://raw.githubusercontent.com/JetBrains/intellij-community/master/platform/platform-resources/src/keymaps/Mac%20OS%20X%2010.5%2B.xml`

5. **Check for conflicts.** Any binding you introduce that collides with an existing `Mac OS X 10.5+` shortcut will silently shadow it. Call these out to Stefan rather than deciding unilaterally.

### Phase 2 — Create the plugin project

Scaffold into a sub-directory, e.g. `./keymap-plugin/`.

Base it on the **IntelliJ Platform Plugin Template**: `https://github.com/JetBrains/intellij-platform-plugin-template`. Take the Gradle setup and delete every sample source file — there is no code in this plugin.

Target layout:

```
keymap-plugin/
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
├── gradle/wrapper/
└── src/main/resources/
    ├── META-INF/plugin.xml
    └── keymaps/CIVA.xml
```

`plugin.xml`:

```xml
<idea-plugin>
  <id>de.civa.keymap</id>
  <name>CIVA Keymap</name>
  <vendor email="stefan.huettemann@civa.de">CIVA</vendor>

  <description><![CDATA[
    Adds the CIVA keymap. After installing, select <b>CIVA</b> under
    Settings → Keymap.
  ]]></description>

  <depends>com.intellij.modules.platform</depends>

  <extensions defaultExtensionNs="com.intellij">
    <bundledKeymap file="CIVA.xml"/>
  </extensions>
</idea-plugin>
```

`build.gradle.kts` — sketch only. **Check the current IntelliJ Platform Gradle Plugin version before pinning it**; do not copy a version number from this document. Likewise confirm which IDE build Stefan actually runs before setting `sinceBuild`.

```kotlin
plugins {
  id("java")
  id("org.jetbrains.intellij.platform") version "<CHECK CURRENT>"
}

group = "de.civa"
version = "1.0.0"

repositories {
  mavenCentral()
  intellijPlatform { defaultRepositories() }
}

dependencies {
  intellijPlatform {
    intellijIdeaCommunity("<CHECK STEFAN'S IDE BUILD>")
  }
}

intellijPlatform {
  pluginConfiguration {
    ideaVersion {
      sinceBuild = "<CHECK>"
      untilBuild = provider { null }   // keymap-only: no API surface, don't cap it
    }
  }
}
```

Notes:

- `untilBuild` open-ended is deliberate. The plugin touches no platform API, so there is nothing to break across releases. Capping it just forces needless re-releases.
- Signing (`signPlugin`) is a **Marketplace requirement only**. Not needed for install-from-disk. Skip it unless Stefan asks about publishing.

### Phase 3 — Build and install

```bash
cd keymap-plugin
./gradlew buildPlugin
# → build/distributions/CIVA-Keymap-1.0.0.zip
```

Then, in the IDE: **Settings → Plugins → ⚙ → Install Plugin from Disk…** → select the ZIP → restart.

**Then tell Stefan to activate it:** Settings → Keymap → select **CIVA** from the dropdown. Installing does not switch the active keymap. This trips people up constantly.

For your own iteration while developing, `./gradlew runIde` launches a sandbox IDE with the plugin loaded — much faster than build-install-restart. Use it to check the keymap appears and the bindings resolve, then produce the ZIP once for Stefan.

---

## 5. Verification checklist

Before handing back:

- [ ] Filename and `name=` attribute match exactly (§3.5 — the top failure mode).
- [ ] `parent="Mac OS X 10.5+"` and the plugin does not itself define a keymap whose name starts with `Mac OS X`.
- [ ] Name is not on the reserved list in §3.6.
- [ ] XML is well-formed and uses only `keyboard-shortcut` / `mouse-shortcut` / `keyboard-gesture-shortcut` children. Anything else throws `InvalidDataException` at load.
- [ ] **Every `action id` resolves.** Unknown IDs are silently ignored — the binding just does not work, with no error. Verify against `Mac OS X 10.5+.xml`, `$default.xml`, and `Help → Find Action` in the IDE. This is worth a scripted check rather than eyeballing.
- [ ] Since the plugin targets all JetBrains IDEs, confirm no action ID is Java/IDEA-specific if Stefan will use it in e.g. PyCharm or WebStorm. IDs from `JavaPlugin.xml` will not resolve in non-Java IDEs.
- [ ] `runIde` sandbox: keymap appears in Settings → Keymap under the expected label, and a spot-check of 3–5 bindings actually fires the right action.
- [ ] Check the IDE log for `Cannot find parent scheme` or `Cannot find keymaps/...` warnings — both are logged rather than thrown.

---

## 6. Gotchas

- **Renaming the keymap breaks user selection.** If the `name` attribute changes between plugin versions, anyone who had it selected is silently reverted to the default. Pick the name once.
- **Empty `<action id="X"/>` removes inherited bindings.** Do not "tidy up" empty elements — they are load-bearing.
- **`isBundledKeymapHidden`** hides macOS keymaps on non-Mac systems for *bundled* plugins. Our plugin is not bundled, so this does not bite — but it is why the platform's own Mac keymaps vanish from the dropdown on Linux.
- **No signing needed** for local install. Only for Marketplace.
- **Do not hardcode versions from this document.** The Gradle plugin version and IDE build numbers were not verified for 2026-07; look them up.

---

## 7. Reference sources

All verified against `master` on 2026-07-19:

- Keymap resources: `https://github.com/JetBrains/intellij-community/tree/master/platform/platform-resources/src/keymaps`
- `DefaultKeymap.kt` (name resolution, `$OS$` macro, default selection): `https://github.com/JetBrains/intellij-community/blob/master/platform/platform-impl/src/com/intellij/openapi/keymap/impl/DefaultKeymap.kt`
- `DefaultKeymapImpl.kt` (`getPresentableName` delegation): `https://github.com/JetBrains/intellij-community/blob/master/platform/platform-impl/src/com/intellij/openapi/keymap/impl/DefaultKeymapImpl.kt`
- `KeymapImpl.kt` (XML parsing, reserved names table): `https://github.com/JetBrains/intellij-community/blob/master/platform/platform-impl/src/com/intellij/openapi/keymap/impl/KeymapImpl.kt`
- `KeymapManager.java` (name constants): `https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/openapi/keymap/KeymapManager.java`
- Plugin template: `https://github.com/JetBrains/intellij-platform-plugin-template`
- Extension point docs: `https://plugins.jetbrains.com/intellij-platform-explorer?extensions=com.intellij.bundledKeymap`

---

## 8. First message to send Stefan

> Before I scaffold anything — two things:
>
> 1. Do you already have a custom keymap in the IDE? If so it'll be at
>    `~/Library/Application Support/JetBrains/<IDE><version>/keymaps/<Name>.xml`.
>    Point me at it and I'll build the plugin around it. If not, duplicate the
>    macOS keymap in Settings → Keymap, adjust what you want, and apply.
> 2. Confirm the naming: plugin `CIVA Keymap` / ID `de.civa.keymap`, keymap
>    displayed as `CIVA`. Happy to use something else.
>
> Also — which JetBrains IDE and version are you running? I need the build
> number to pin the Gradle config.

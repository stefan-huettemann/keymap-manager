# Handover: JetBrains Keymap Plugin (German Keyboard)

**For:** Claude Code CLI
**From:** prior research session, 2026-07-19
**User:** Stefan (stefan.huettemann@civa.de)
**Environment:** MacBook Pro, macOS 26.3.1, **German keyboard layout (T1 / ISO)**

---

## 1. Goal

Build a JetBrains plugin that adds a **custom keymap** to the user's IDEs — the same mechanism the official "Eclipse Keymap" / "VSCode Keymap" plugins use.

The driving motivation is **not** cosmetic. The stock IntelliJ keymaps are designed for US ANSI, and a significant fraction of their shortcuts are **physically unpressable on a German keyboard**. §5 explains exactly why and which ones. The plugin exists to replace those bindings with reachable ones.

Three deliverables, in order:

1. **Define the keymap** — a custom keymap derived from `Mac OS X 10.5+`, sourced from Stefan's existing exported keymap, using only keys that are reachable on a German layout.
2. **Create the plugin project** — in a **sub-directory of the current project**, not at the repo root.
3. **Build and install it** into the local IDE.

---

## 2. Decisions already made

Do not re-litigate these.

| Decision | Choice | Consequence |
|---|---|---|
| Platform scope | **macOS only** | One keymap file, `parent="Mac OS X 10.5+"`. No `$OS$` macro. |
| Target IDEs | **All JetBrains IDEs** | `<depends>com.intellij.modules.platform</depends>` and nothing else. |
| Deploy | **Build ZIP + manual install** | `./gradlew buildPlugin`, then Settings → Plugins → ⚙ → Install Plugin from Disk. No custom deploy task. |
| Binding source | **Stefan's exported keymap** | Ask for his exported keymap XML first; derive the plugin resource from it. |

Confirm early with Stefan:

- **Plugin name / ID.** Suggested: name `CIVA Keymap`, ID `de.civa.keymap`, group `de.civa`.
- **Keymap display name.** Suggested `CIVA`. See §4.3 — some names are reserved.
- **His IDE and build number** — needed to pin the Gradle config.

---

## 3. Platform background (verified against `intellij-community` @ master)

Checked against source, not recalled. Trust this over general knowledge.

### 3.1 A keymap plugin contains no code

Resource-only: one `plugin.xml` plus keymap XML. No Java, no Kotlin, no actions. If you're writing a `.kt` file, you've gone off track.

### 3.2 The extension point

```xml
<extensions defaultExtensionNs="com.intellij">
  <bundledKeymap file="CIVA.xml"/>
</extensions>
```

`file` resolves relative to `keymaps/` on the classpath → `src/main/resources/keymaps/CIVA.xml`.

There is an `$OS$` macro for multi-OS plugins (`file="$OS$/CIVA.xml"` → `keymaps/macos/CIVA.xml`). **Not needed here.** Source — `DefaultKeymap.getEffectiveFile`:

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

  <!-- Two-stroke -->
  <action id="ExpandAllToLevel1">
    <keyboard-shortcut first-keystroke="meta alt MULTIPLY" second-keystroke="1"/>
  </action>

  <!-- Empty element = explicitly REMOVE the parent's shortcuts -->
  <action id="EditorScrollUp"/>
</keymap>
```

Accepted child elements (`KeymapImpl.readExternal` throws `InvalidDataException` on anything else):

- `keyboard-shortcut` — attrs `first-keystroke`, `second-keystroke`
- `mouse-shortcut` — attr `keystroke`
- `keyboard-gesture-shortcut` — attrs `keystroke`, `modifier` (`dblClick` | `hold`)

An **empty `<action id="X"/>` is load-bearing** — it clears inherited bindings. Do not "tidy these up."

### 3.4 Parent keymap

Two Mac keymaps exist, and they are **siblings**, both `parent="$default"`:

| File | Internal name | UI label | What it is |
|---|---|---|---|
| `Mac OS X 10.5+.xml` | `Mac OS X 10.5+` | **macOS** | Modern default on macOS. Full Cocoa conventions. ~27 KB. |
| `Mac OS X.xml` | `Mac OS X` | **IntelliJ IDEA Classic** | Legacy pre-Leopard. Keeps Windows-style Ctrl bindings. ~18 KB. |

**Use `parent="Mac OS X 10.5+"`.** It's what `DefaultKeymap.defaultKeymapName` returns on macOS.

### 3.5 Name → UI label

Two steps. Get it wrong and the keymap shows under an unexpected label.

**Step 1 — filename → internal name** (`DefaultKeymap.getKeymapName`):

```kotlin
FileUtilRt.getNameWithoutExtension(bean.file).removePrefix("\$OS\$/")
```

**⚠️ But** `KeymapImpl.readExternal` then does `name = keymapElement.getAttributeValue("name")!!` — the XML `name=` attribute **overwrites** the filename-derived name. **Keep filename and `name=` identical.** Easiest thing to get wrong.

**Step 2 — internal name → UI label** (`DefaultKeymap.getKeymapPresentableName`):

| Internal name | UI label |
|---|---|
| `$default` | Windows |
| `Mac OS X 10.5+` | macOS |
| `Mac OS X` | IntelliJ IDEA Classic |
| `Default for GNOME` / `KDE` / `XWin` | GNOME / KDE / XWin |
| `NetBeans 6.5` | NetBeans |

Everything else:

```kotlin
val newName = name.removeSuffix(" (Mac OS X)").removeSuffix(" OSX")
(if (newName === name) name else "$newName (macOS)").removePrefix("${osName()}/")
```

A plain name like `CIVA` displays verbatim as `CIVA`.

### 3.6 Reserved names — avoid

- **Never** start the name with `Mac OS X`. `DefaultKeymap.loadKeymap` branches on `keymapName.startsWith(KeymapManager.MAC_OS_X_KEYMAP)` and instantiates `MacOSDefaultKeymap`, applying modifier conversion you don't want.
- **Never** reuse an official name. Canonical list is in `notifyAboutMissingKeymap` (`KeymapImpl.kt`): `Eclipse`, `Eclipse (Mac OS X)`, `Emacs`, `NetBeans 6.5`, `QtCreator`, `QtCreator OSX`, `ReSharper`, `ReSharper OSX`, `Sublime Text`, `Sublime Text (Mac OS X)`, `Visual Studio`, `Visual Studio OSX`, `Visual Studio 2022`, `Visual Studio for Mac`, `Visual Assist`, `Visual Assist OSX`, `Xcode`, `Rider`, `Rider OSX`, `VSCode`, `VSCode OSX`, `macOS System Shortcuts`.
- Avoid trailing ` OSX` / ` (Mac OS X)` unless you want the automatic ` (macOS)` rewrite.

---

## 4. ⚠️ THE CORE CONSTRAINT — German keyboard reachability

**Read this before generating a single binding.**

### 4.1 Why shortcuts fail

AWT `KeyStroke` — what `first-keystroke` parses into — is based on **virtual key codes**, i.e. *physical keys*, not resulting characters. `meta SLASH` means `VK_SLASH`.

A German keyboard has **no key that emits `VK_SLASH`**. Pressing ⌘⇧7 (which types `/`) delivers `meta shift 7` to the JVM. The keymap looks for `meta SLASH`, sees `meta shift 7`, no match, **nothing fires**. The character produced is irrelevant — the lookup never gets that far.

So the rule is not "avoid shifted characters." It is: **only bind physical keys that exist on the German layout.**

JetBrains acknowledges this directly in their docs: *"All keymaps in IntelliJ IDEA are designed for the QWERTY US English keyboard layout… there is no dedicated keyboard key for the forward slash / in the German keyboard layout, and therefore it is impossible to use the Ctrl+/ shortcut."*

### 4.2 UNREACHABLE — characters requiring Shift on German T1

These 19 characters have **no dedicated key**. Any binding naming them by their US `VK_` code is dead on this hardware.

| Physical key | Unshifted | **Shift produces** | US `VK_` that is therefore unreachable |
|---|---|---|---|
| left of `1` | `^` | **`°`** | — |
| `1` | 1 | **`!`** | — |
| `2` | 2 | **`"`** | — |
| `3` | 3 | **`§`** | — |
| `4` | 4 | **`$`** | — |
| `5` | 5 | **`%`** | — |
| `6` | 6 | **`&`** | — |
| `7` | 7 | **`/`** | **`VK_SLASH`** |
| `8` | 8 | **`(`** | — |
| `9` | 9 | **`)`** | — |
| `0` | 0 | **`=`** | **`VK_EQUALS`** |
| right of `0` | `ß` | **`?`** | — |
| right of `ß` | `´` | **`` ` ``** | **`VK_BACK_QUOTE`** |
| right of `Ü` | `+` | **`*`** | — |
| right of `Ä` | `#` | **`'`** | **`VK_QUOTE`** |
| left of `Y` | `<` | **`>`** | — |
| `,` | `,` | **`;`** | **`VK_SEMICOLON`** |
| `.` | `.` | **`:`** | — |
| `-` | `-` | **`_`** | — |

Full set: `° ! " § $ % & / ( ) = ? ` * ' > ; : _`

**US `VK_` codes with no German physical key at all — never emit these:**

```
VK_SLASH          VK_BACK_SLASH     VK_OPEN_BRACKET   VK_CLOSE_BRACKET
VK_SEMICOLON      VK_QUOTE          VK_BACK_QUOTE     VK_EQUALS
```

Position mapping, for reference — the German key sitting where the US key would be:

| US ANSI | German key in that position | German's actual `VK_` |
|---|---|---|
| `` ` `` `VK_BACK_QUOTE` | `^` | `VK_DEAD_CIRCUMFLEX` |
| `-` `VK_MINUS` | `ß` | *(extended — see §4.4)* |
| `=` `VK_EQUALS` | `´` | `VK_DEAD_ACUTE` |
| `[` `VK_OPEN_BRACKET` | `Ü` | *(extended)* |
| `]` `VK_CLOSE_BRACKET` | `+` | `VK_PLUS` |
| `\` `VK_BACK_SLASH` | `#` | `VK_NUMBER_SIGN` |
| `;` `VK_SEMICOLON` | `Ö` | *(extended)* |
| `'` `VK_QUOTE` | `Ä` | *(extended)* |
| `,` `VK_COMMA` | `,` | `VK_COMMA` ✓ |
| `.` `VK_PERIOD` | `.` | `VK_PERIOD` ✓ |
| `/` `VK_SLASH` | `-` | `VK_MINUS` |
| *(absent on ANSI)* | `<` | `VK_LESS` |

### 4.3 REACHABLE — the complete usable palette

Every name below was enumerated by reflecting over `java.awt.event.KeyEvent` on the JVM. These are the exact lowercase tokens `KeyStrokeAdapter` writes and parses.

`KeyStrokeAdapter.LazyVirtualKeys` builds its name↔code map by stripping the `VK_` prefix and lowercasing, so the token is always `VK_FOO` → `foo`.

**Letters** — `VK_A`…`VK_Z`

```
a b c d e f g h i j k l m n o p q r s t u v w x y z
```

**Digits** — `VK_0`…`VK_9`

```
0 1 2 3 4 5 6 7 8 9
```

**Punctuation present on German T1**

| Key | `VK_` constant | Code | Token in XML |
|---|---|---|---|
| `-` | `VK_MINUS` | `0x2d` | `minus` |
| `,` | `VK_COMMA` | `0x2c` | `comma` |
| `.` | `VK_PERIOD` | `0x2e` | `period` |
| `+` | `VK_PLUS` | `0x209` | `plus` |
| `#` | `VK_NUMBER_SIGN` | `0x208` | `number_sign` |
| `<` | `VK_LESS` | `0x99` | `less` |
| `´` | `VK_DEAD_ACUTE` | `0x81` | `dead_acute` |
| `^` | `VK_DEAD_CIRCUMFLEX` | `0x82` | `dead_circumflex` |

**Whitespace / editing**

```
enter tab space back_space delete escape
```

**Navigation**

```
up down left right home end page_up page_down
```

**Function keys**

```
f1 f2 f3 f4 f5 f6 f7 f8 f9 f10 f11 f12
```

**Numpad** — *full-size keyboards only; a MacBook Pro has none. Do not rely on these.*

```
numpad0 … numpad9 add subtract multiply divide decimal separator
```

**Modifier tokens** (`KeyStrokeAdapter.LazyModifiers`) — parser accepts all of these; `toString()` emits the **bold** ones:

| Meaning | Accepted on parse | Emitted on write |
|---|---|---|
| ⇧ | `shift` | **`shift`** |
| ⌃ | `ctrl`, `control` | **`ctrl`** |
| ⌘ | `meta` | **`meta`** |
| ⌥ | `alt` | **`alt`** |
| AltGr | `altgr`, `altgraph` | **`altGraph`** |
| Mouse | `button1`, `button2`, `button3` | same |

**Not present on a MacBook Pro German keyboard** — avoid: `insert`, `printscreen`, `scroll_lock`, `num_lock`, `pause`, `context_menu`, `f13`+.

### 4.4 ✅ Ä Ö Ü ß — how to name them in the XML

**These ARE bindable.** They have no `VK_` constant, but `KeyStrokeAdapter` has a hex escape hatch. This is the single most valuable finding for a German keymap: four prominent, unshifted, home-row-adjacent keys that no US keymap competes for.

**How it works.** IntelliJ does *not* use `KeyStroke.getKeyStroke(String)`. `KeymapImpl` routes both directions through `KeyStrokeAdapter`:

```kotlin
// reading  (KeymapImpl.readExternal)
val firstKeyStroke = KeyStrokeAdapter.getKeyStroke(firstKeyStrokeStr) ?: continue
// writing  (KeymapImpl.writeOwnActionIds)
element.setAttribute(FIRST_KEYSTROKE_ATTRIBUTE, KeyStrokeAdapter.toString(shortcut.firstKeyStroke))
```

`toString()` falls back to hex when no `VK_` name exists:

```java
String name = LazyVirtualKeys.myCodeToName.get(code);
if (name == null) {
  sb.append('#');
  name = Integer.toHexString(code);
}
return sb.append(name).toString();
```

and the parser closes the loop via `Integer.decode`, which natively reads a `#` prefix as hex:

```java
Integer code = LazyVirtualKeys.myNameToCode.get(tokenLowerCase);
if (code == null) {
  code = Integer.decode(token);   // "#10000d6" -> 16777430
}
```

The code itself comes from `getDefaultKeyStroke`: `if (code == VK_UNDEFINED) code = event.getExtendedKeyCode()`. Java 7+ extended key codes are `0x01000000 | char`.

**The values** (computed via `KeyEvent.getExtendedKeyCodeForChar` on the JVM):

| Key | Extended key code | **Token in keymap XML** |
|---|---|---|
| `Ä` / `ä` | `0x010000C4` | **`#10000c4`** |
| `Ö` / `ö` | `0x010000D6` | **`#10000d6`** |
| `Ü` / `ü` | `0x010000FC` | **`#10000fc`** |
| `ß` | `0x010000DF` | **`#10000df`** |

Usage — lowercase hex, `#` prefix, modifiers first:

```xml
<action id="CommentByLineComment">
  <keyboard-shortcut first-keystroke="meta #10000d6"/>   <!-- ⌘Ö -->
</action>
<action id="CommentByBlockComment">
  <keyboard-shortcut first-keystroke="meta shift #10000d6"/>   <!-- ⌘⇧Ö -->
</action>
```

> ### 🚨 DO NOT COMPUTE THESE BY HAND
>
> `Ü` breaks the pattern. `Ä` and `Ö` use their **uppercase** codepoint (`C4`, `D6`), but `Ü` uses the **lowercase** one (`FC`, **not** `DC`) — even though `Character.toUpperCase('ü')` correctly returns `U+00DC`.
>
> This is an asymmetry in the JDK's hardcoded `sun.awt.ExtendedKeyCodes` table, reproducible for both `ü` and `Ü`. Always derive from the JVM, never from the codepoint. §4.5 has a script.

### 4.5 Verify the hex values on Stefan's machine

The table above was computed on Linux OpenJDK 11. `sun.awt.ExtendedKeyCodes` is shared Java code so values *should* be identical under the JetBrains Runtime on macOS — but whether macOS AWT reports `VK_UNDEFINED` + extended code for those physical keys is platform-specific and was **not** verified on the target machine.

**Authoritative check (30 seconds).** Have Stefan bind ⌘Ö to anything in Settings → Keymap, apply, then:

```bash
grep -rn 'first-keystroke="[^"]*#' ~/Library/Application\ Support/JetBrains/*/keymaps/*.xml
```

Whatever is in that file is ground truth. Use it.

**Recompute from the JVM if needed** (any JDK, `jshell` or `jjs`):

```javascript
// jjs recompute.js
var KeyEvent = Java.type("java.awt.event.KeyEvent");
var Integer  = Java.type("java.lang.Integer");
["ä","ö","ü","ß","Ä","Ö","Ü"].forEach(function(s){
  var code = KeyEvent.getExtendedKeyCodeForChar(s.charCodeAt(0));
  print(s + "  ->  #" + Integer.toHexString(code));
});
```

---

## 5. Work plan

### Phase 1 — Define the keymap

1. **Get Stefan's current keymap.** Custom keymaps live at:

   ```
   ~/Library/Application Support/JetBrains/<IDE><version>/keymaps/<Name>.xml
   ```

   If he has none: Settings → Keymap → ⚙ → Duplicate, adjust, apply. The file appears at that path, already in the right format, containing only deltas from its parent.

2. **Check its `parent` attribute.** If it isn't `Mac OS X 10.5+`, discuss before rewriting — deltas are only meaningful relative to their parent.

3. **Audit `Mac OS X 10.5+` for unreachable bindings.** Fetch the parent (URL in §7), scan every `first-keystroke` / `second-keystroke`, and flag any using a token from the forbidden list in §4.2. These are the actions that need new bindings — this list *is* the core work item. Present it to Stefan before choosing replacements.

4. **Normalise into the plugin resource:**
   - `name` = agreed keymap name, matching the filename exactly.
   - `parent="Mac OS X 10.5+"`.
   - `disable-mnemonics="true"` (both platform Mac keymaps set this).
   - Every binding uses only tokens from §4.3 or the hex forms from §4.4.

5. **Check for conflicts.** Any new binding colliding with an existing `Mac OS X 10.5+` shortcut silently shadows it. Report collisions to Stefan; don't resolve them unilaterally.

### Phase 2 — Create the plugin project

Scaffold into a sub-directory, e.g. `./keymap-plugin/`. Base it on the [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template) — take the Gradle setup, delete every sample source file.

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
    Keymap optimised for the German (T1) keyboard layout.
    After installing, select <b>CIVA</b> under Settings → Keymap.
  ]]></description>

  <depends>com.intellij.modules.platform</depends>

  <extensions defaultExtensionNs="com.intellij">
    <bundledKeymap file="CIVA.xml"/>
  </extensions>
</idea-plugin>
```

`build.gradle.kts` — **sketch only. Look up the current IntelliJ Platform Gradle Plugin version and Stefan's actual IDE build; do not copy version numbers from this document.**

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
  intellijPlatform { intellijIdeaCommunity("<CHECK STEFAN'S BUILD>") }
}

intellijPlatform {
  pluginConfiguration {
    ideaVersion {
      sinceBuild = "<CHECK>"
      untilBuild = provider { null }   // no API surface — don't cap it
    }
  }
}
```

Signing (`signPlugin`) is a **Marketplace requirement only**. Skip it for install-from-disk.

### Phase 3 — Build and install

```bash
cd keymap-plugin
./gradlew buildPlugin
# → build/distributions/CIVA-Keymap-1.0.0.zip
```

IDE: **Settings → Plugins → ⚙ → Install Plugin from Disk…** → select ZIP → restart.

**Then tell Stefan to activate it:** Settings → Keymap → select **CIVA**. Installing does not switch the active keymap.

For your own iteration, `./gradlew runIde` launches a sandbox IDE with the plugin loaded — far faster than build/install/restart.

---

## 6. Verification checklist

- [ ] Filename and `name=` attribute match exactly (§3.5 — top failure mode).
- [ ] `parent="Mac OS X 10.5+"`; plugin defines no keymap whose name starts with `Mac OS X`.
- [ ] Name not on the reserved list (§3.6).
- [ ] **No binding uses `VK_SLASH`, `VK_BACK_SLASH`, `VK_OPEN_BRACKET`, `VK_CLOSE_BRACKET`, `VK_SEMICOLON`, `VK_QUOTE`, `VK_BACK_QUOTE`, or `VK_EQUALS`.** Script this — grep the final XML for `slash|back_slash|open_bracket|close_bracket|semicolon|quote|back_quote|equals`.
- [ ] Every other token appears in the §4.3 palette or is a valid `#hex` form.
- [ ] Hex values for Ä Ö Ü ß confirmed against Stefan's machine (§4.5), not copied from this doc.
- [ ] XML well-formed; only `keyboard-shortcut` / `mouse-shortcut` / `keyboard-gesture-shortcut` children.
- [ ] **Every `action id` resolves.** Unknown IDs are silently ignored — the binding just doesn't work, no error. Verify against `Mac OS X 10.5+.xml`, `$default.xml`, and `Help → Find Action`. Script this too.
- [ ] Targets all JetBrains IDEs → no Java/IDEA-specific action IDs (those from `JavaPlugin.xml` won't resolve in PyCharm/WebStorm).
- [ ] `runIde` sandbox: keymap appears under the expected label; **physically press** 3–5 of the rebound shortcuts, including at least one umlaut binding, and confirm the right action fires.
- [ ] IDE log clean of `Cannot find parent scheme` / `Cannot find keymaps/...` — both are logged, not thrown.

---

## 7. Sources

All verified against `master` on 2026-07-19. Raw URLs given so they can be fetched directly.

### Primary — IntelliJ platform source

| What | Path / URL |
|---|---|
| **`KeyStrokeAdapter.java`** — the `#hex` encoding, modifier tokens, `LazyVirtualKeys` name map. **The key file for §4.4.** | `platform/platform-impl/src/com/intellij/ui/KeyStrokeAdapter.java`<br>https://raw.githubusercontent.com/JetBrains/intellij-community/master/platform/platform-impl/src/com/intellij/ui/KeyStrokeAdapter.java |
| `KeymapImpl.kt` — XML parsing/writing, reserved-name table in `notifyAboutMissingKeymap` | `platform/platform-impl/src/com/intellij/openapi/keymap/impl/KeymapImpl.kt`<br>https://raw.githubusercontent.com/JetBrains/intellij-community/master/platform/platform-impl/src/com/intellij/openapi/keymap/impl/KeymapImpl.kt |
| `DefaultKeymap.kt` — name resolution, `$OS$` macro, `defaultKeymapName`, `getKeymapPresentableName` | `platform/platform-impl/src/com/intellij/openapi/keymap/impl/DefaultKeymap.kt`<br>https://raw.githubusercontent.com/JetBrains/intellij-community/master/platform/platform-impl/src/com/intellij/openapi/keymap/impl/DefaultKeymap.kt |
| `DefaultKeymapImpl.kt` — `getPresentableName()` delegation | `platform/platform-impl/src/com/intellij/openapi/keymap/impl/DefaultKeymapImpl.kt`<br>https://raw.githubusercontent.com/JetBrains/intellij-community/master/platform/platform-impl/src/com/intellij/openapi/keymap/impl/DefaultKeymapImpl.kt |
| `KeymapManager.java` — the `MAC_OS_X_*` name constants | `platform/platform-api/src/com/intellij/openapi/keymap/KeymapManager.java`<br>https://raw.githubusercontent.com/JetBrains/intellij-community/master/platform/platform-api/src/com/intellij/openapi/keymap/KeymapManager.java |

### Keymap resources (reference bindings)

- Directory: https://github.com/JetBrains/intellij-community/tree/master/platform/platform-resources/src/keymaps
- **Parent keymap** `Mac OS X 10.5+.xml`:
  `https://raw.githubusercontent.com/JetBrains/intellij-community/master/platform/platform-resources/src/keymaps/Mac OS X 10.5+.xml`
- Root `$default.xml`:
  `https://raw.githubusercontent.com/JetBrains/intellij-community/master/platform/platform-resources/src/keymaps/%24default.xml`

### JDK

- `java.awt.event.KeyEvent` — the `VK_` constants (189 total). Enumerate by reflection; §4.5 has the script.
- `sun.awt.ExtendedKeyCodes` — source of the `Ü` anomaly. Internal, not directly readable; observe behaviour via `KeyEvent.getExtendedKeyCodeForChar`.

### JetBrains documentation

- Shortcut troubleshooting (confirms the German `/` problem explicitly): https://www.jetbrains.com/help/idea/keyboard-shortcuts-troubleshooting.html
- Keymap settings: https://www.jetbrains.com/help/idea/settings-keymap.html
- `bundledKeymap` EP usages: https://plugins.jetbrains.com/intellij-platform-explorer?extensions=com.intellij.bundledKeymap
- Plugin template: https://github.com/JetBrains/intellij-platform-plugin-template

### Prior art worth checking first

**Keymap Nationalizer** — a Marketplace plugin that generates a conflict-free keymap for a given keyboard layout. Surfaced in JetBrains' own troubleshooting doc as the recommended workaround. **Look at this before building from scratch** — it may solve the problem outright, or at minimum serve as a reference for which bindings need rework. Search the Marketplace and report back to Stefan.

---

## 8. First message to send Stefan

> Before I scaffold anything — three things:
>
> 1. **Your current keymap.** If you've already customised one it'll be at
>    `~/Library/Application Support/JetBrains/<IDE><version>/keymaps/<Name>.xml`.
>    Point me at it. If not, duplicate the macOS keymap in Settings → Keymap,
>    adjust what you want, apply, and it'll show up there.
> 2. **Which IDE and version** are you running? I need the build number for the
>    Gradle config.
> 3. **A 30-second check** so I can confirm the umlaut encoding on your machine:
>    bind ⌘Ö to anything in Settings → Keymap, apply, then run
>    `grep -rn 'first-keystroke="[^"]*#' ~/Library/Application\ Support/JetBrains/*/keymaps/*.xml`
>    and paste the output.
>
> Also — naming. I'm assuming plugin `CIVA Keymap` / ID `de.civa.keymap`, keymap
> shown as `CIVA` in the dropdown. Say the word if you'd rather something else.
>
> One thing I want to flag before we build: there's an existing **Keymap
> Nationalizer** plugin that generates layout-appropriate keymaps automatically.
> Worth 10 minutes to check whether it already does what you need — I'll look
> into it unless you'd rather go straight to the custom build.

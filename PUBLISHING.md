# Publishing to the JetBrains Marketplace

How to register and release **Keymap Manager** (`de.civa.plugins.keymapmanager`) on the
[JetBrains Marketplace](https://plugins.jetbrains.com). All Gradle commands run from `keymap-plugin/`.

## What's already set up

- **Metadata** in `src/main/resources/META-INF/plugin.xml`: unique `<id>`, `<name>`, `<vendor>` (email + url),
  a meaningful English `<description>`, `<change-notes>`, and `<idea-version since-build="261">` (no
  `until-build`, so it stays compatible with future IDEs).
- **Icon**: `pluginIcon.svg` + `pluginIcon_dark.svg` in `META-INF/`.
- **Gradle publishing**: `publishPlugin` reads a Marketplace token from `PUBLISH_TOKEN`; optional author
  signing (`signPlugin`) activates only when the signing env vars are present.
- **Compatibility**: `verifyPlugin` is pinned to a concrete IDE and last reported *Compatible* with zero
  API warnings.

## Prerequisites (one-time)

1. **JetBrains Account** — sign in at <https://account.jetbrains.com> (the same account used for the IDE).
2. **Marketplace sign-in** — sign in at <https://plugins.jetbrains.com>; on the first upload you must accept
   the **Marketplace Developer Agreement**.
3. **(Optional) Vendor profile** — to list the plugin under an organization ("CIVA") rather than your
   personal name, create a Vendor on Marketplace and publish under it. This should match the `<vendor>` in
   `plugin.xml`.

## Build and check before every release

```bash
cd keymap-plugin
./gradlew buildPlugin      # → build/distributions/Keymap-Manager-<version>.zip
./gradlew verifyPlugin     # verdict under build/reports/pluginVerifier/<IDE>/…/verification-verdict.txt
```

Confirm the verdict is **Compatible** before uploading.

## First release — manual web upload (required)

The **first version of a plugin always goes through manual moderation** by JetBrains; this cannot be done
with `publishPlugin`.

1. Go to <https://plugins.jetbrains.com> → your profile → **Upload plugin**.
2. Upload `keymap-plugin/build/distributions/Keymap-Manager-<version>.zip`.
3. Set the **License** (e.g. free / your chosen license) and pick a **Category** (closest fit:
   *User Interface* or *Tools Integration* — editable later).
4. Submit. Marketplace runs its automated Plugin Verifier and a reviewer checks the submission
   (typically ~2 business days). Once approved, the plugin page goes public.

## Subsequent releases — automated

After the first release is approved, ship updates from the command line:

1. Bump `version` in `keymap-plugin/build.gradle.kts`.
2. Update `<change-notes>` in `plugin.xml`.
3. `./gradlew buildPlugin verifyPlugin` and confirm *Compatible*.
4. Generate a **permanent token**: <https://plugins.jetbrains.com/author/me/tokens>.
5. Publish:

   ```bash
   cd keymap-plugin
   PUBLISH_TOKEN=<your-token> ./gradlew publishPlugin
   ```

   This uploads to the **stable** channel. Post-first-release updates publish after automated checks
   (no manual moderation).

### Optional: sign the artifact yourself

Marketplace signs plugins on its side, so author signing is optional. To sign locally, set all three env
vars before `publishPlugin` (or `signPlugin`):

```bash
export CERTIFICATE_CHAIN="$(cat chain.crt)"
export PRIVATE_KEY="$(cat private.pem)"
export PRIVATE_KEY_PASSWORD="…"
```

See the [plugin signing guide](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html) for
generating the key/chain.

## Release checklist

- [ ] `version` bumped in `build.gradle.kts`
- [ ] `<change-notes>` updated in `plugin.xml`
- [ ] `./gradlew buildPlugin verifyPlugin` → **Compatible**
- [ ] First release: uploaded on the web and approved · Updates: `publishPlugin` with `PUBLISH_TOKEN`
- [ ] Git tag / commit for the released version

## References

- [Publishing a plugin](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html)
- [Marketplace publishing (Gradle)](https://plugins.jetbrains.com/docs/intellij/deployment.html)
- [Plugin verifier](https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html)

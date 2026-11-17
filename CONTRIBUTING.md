# Contributing

Run `nix develop -c ./check` before sending a change. For a focused test run:

```sh
nix develop -c ./check testDebugUnitTest --tests '*RouteControllerTest'
```

The Android app uses Java 17, native Views and a Shizuku UserService. Keep routing
changes in `RouteTrial` and `RouteService`; tile, Activity and notification code
must use the application-owned controller. Do not introduce a second journal
or worker. Test safety changes before changing the implementation.

Transaction tests use an in-memory backend. Controller and tile tests simulate
Binder replies. UI tests render the layouts with Robolectric.
The test-only tile shadow fixes a missing `ShadowService` inheritance in
Robolectric 4.14.1 so service destruction can be exercised.

Preserve these constraints:

- Save the recovery record before sending any apply request.
- Verify the exact device pair, not just preference counts.
- Refuse pre-existing preferences. Require Voice stopped before restore.
- Keep recovery data through failure, process death and device loss.
- Never claim service destruction restores preferences.
- Do not add root, arbitrary shell commands, policy edits or automatic retries
  of route writes.

For hardware tests, check microphone input and headphone playback separately,
including after opening Quick Settings. Restore and verify empty routing
preferences before ending a session. Use paired wireless debugging for diagnostics.

## Bug reports

The issue templates ask for the output of **Copy diagnostics**. It covers the app
version, phone model, Android/One UI version, Shizuku status and exact app message.
Also include the receiver and headphone models, whether Voice was active, and
separate microphone and playback observations. Say whether Restore succeeded.

Redact device identifiers and personal content from logs and screenshots.

## Development environments

The committed Nix lock pins the Linux toolchain. Android Studio can also open the
Gradle project with JDK 17 and SDK/build tools 35. For Nix builds, `check` selects
the Nix-provided aapt2 binary. No build or test command contacts a phone.

Development APKs use local signing keys. Build on the same machine to install
updates over an existing build; CI generates a new key for each run.

## Releases

Release APKs use a permanent signing key. Obtainium follows the GitHub releases
at `https://github.com/meatcar/split-voice` without additional filters.

Configure these GitHub Actions settings once:

- Secret `RELEASE_KEYSTORE_BASE64`: base64-encoded PKCS12 keystore, alias `split-voice`.
- Secret `RELEASE_KEYSTORE_PASSWORD`: keystore and key password.
- Variable `RELEASE_CERT_SHA256`: signing certificate's 64-character SHA-256 digest, without colons.

Keep an encrypted backup of the keystore and password outside GitHub. Future
updates need the same key. Git commit signing and APK signing use separate keys.

Every push to `main` that changes more than docs publishes a release. The
workflow derives the version from the commit count: `versionCode` is the count and
`versionName` is `VERSION_BASE.<count>`, with the tag `v<versionName>`. Edit
`VERSION_BASE` in `.github/workflows/release.yml` to start a new minor or major
line. The workflow runs tests and lint, builds the release APK, checks its
signature against the configured certificate, and publishes it with `SHA256SUMS`.
Run it manually from the Actions tab to retry; it skips versions already released.
Published APKs are not replaced.

Release notes come from conventional commit messages (`feat`, `fix`, `perf`,
`refactor`, `chore(deps)`) through `cliff.toml`. Other types stay out of the notes,
so write those subjects for users. A release with none says so. The GitHub
releases page is the changelog.

`fastlane/metadata/android/` holds the store listing in the layout F-Droid and
the Play Store read. Submitting the app to either store is a manual step.

Development builds and release APKs have different signing keys. Restore any
active routing, then uninstall the development build before installing a release.

## Agent environments

`.agents/setup` is for disposable Amp orbs only. Do not run it on an existing
workstation. Use `nix develop` locally. Keep setup, resume and check executable. The lifecycle in
`.agents/sandbox/` is vendored from `meatcar/nix-templates#sandbox`; put project behavior in
`.agents/Taskfile.yml`.

Contributions are under the project's Apache-2.0 license. Credit upstream work
and retain any license notices when bringing in code.

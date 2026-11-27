# Contributing

Run `nix develop -c ./check` before sending a change. For a focused test run:

```sh
nix develop -c ./check testDebugUnitTest --tests '*RouteControllerTest'
```

The Android app uses Java 17, native Views and a Shizuku UserService. Keep routing
rules in `RouteTrial`, hidden API calls in `AudioServiceBackend`, and the Binder
surface in `RouteService`. Tile, Activity and notification code must use the
application-owned controller. Do not introduce a second journal or worker. Test
safety changes before changing the implementation.

The UI uses framework Views with no AndroidX or Material dependency. Colors come
from the system dynamic palette in `values/colors.xml` and `values-night/colors.xml`.
User docs follow [Diátaxis](https://diataxis.fr/) in `docs/`. When you change a
message or state, update `docs/reference.md`.

## Testing

| Layer | Runs | Covers |
| --- | --- | --- |
| Unit tests (`./check`) | Every push and PR | Routing rules with an in-memory backend, controller and tile with simulated Binder replies, every screen state with Robolectric on Android 12 and 15 |
| Contract test (`Instrumented` workflow) | Weekly, on demand, and on PRs that touch routing | `AudioServiceContractTest` runs `AudioServiceProbe` as the shell user on emulators for API 31 to 36. It checks that the hidden calls exist, that a product strategy serves voice chat, and that a split of fake devices applies and restores. It also runs a smoke test of the screen. |
| Hardware checklist | Before a release that touches routing | Below |

`./check` enforces a line-coverage floor on everything except `RouteService` and
`AudioServiceBackend`, which the contract test covers. `MainActivityTest` writes a
screenshot of each screen state to `app/build/reports/ui/`, and CI uploads them with
the APK. Review them for any UI change. The test-only tile shadow fixes a missing
`ShadowService` inheritance in Robolectric 4.14.1 so service destruction can be
exercised.

When a scheduled contract test fails, a new Android version or emulator image
changed the hidden API. Reproduce it with the same API level before changing
`AudioServiceBackend`.

### Hardware checklist

Use a phone with Shizuku, a USB mic receiver and LE Audio headphones. Connect over
paired wireless debugging so USB stays free for the receiver.

1. Run the [contract test on the phone](docs/how-to.md#check-a-phone-with-adb).
2. Install the build. Start a voice chat. Tap **Split** in the app.
3. Check mic input and headphone playback separately, including after opening
   Quick Settings.
4. End the voice chat, tap **Restore**, and confirm `dumpsys audio` lists no
   preferred devices for capture preset 7 or the phone strategy.
5. Repeat with the tile, and with **Split automatically**.
6. Add the result to `docs/compatibility.md`.

User-facing text lives in `strings.xml`, so translations can be added. Messages built
inside `RouteService` and `RouteTrial` run in Shizuku's shell process, which has no
app resources. They stay English until they carry message codes.

Preserve these constraints:

- Save the recovery record before sending any apply request.
- Verify the exact device pair, not just preference counts.
- Refuse pre-existing preferences. Require Voice stopped before restore.
- Keep recovery data through failure, process death and device loss.
- Never claim service destruction restores preferences.
- Do not add root, arbitrary shell commands, policy edits or automatic retries
  of route writes.

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

The release fails when any commit since the previous release is not verified by
GitHub. Sign commits and merge pull requests with a merge commit, or push the
signed branch to `main` as a fast-forward. "Rebase and merge" rewrites commits
and drops their signatures.

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

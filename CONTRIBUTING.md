# Contributing

Run `nix develop -c ./check` before sending a change. For a focused test run:

```sh
nix develop -c ./check testDebugUnitTest --tests '*RouteControllerTest'
```

The Android app uses Java 17, native Views and a Shizuku UserService. Keep device
choice in `Routing`, saved choices in `Settings`, the shell-side compare-and-set in
`RouteTrial`, hidden API calls in `AudioServiceBackend`, and the Binder surface in
`RouteService`. `RouteController` reconciles: it reads audio state, plans with
`Routing`, journals, then routes. Tile, Activity and notification code must use the
application-owned controller. Do not introduce a second journal or worker. Test
safety changes before changing the implementation.

The UI uses framework Views with no AndroidX or Material dependency. Colors come
from the system dynamic palette in `values/colors.xml` and `values-night/colors.xml`.
User docs follow [Diátaxis](https://diataxis.fr/) in `docs/`. When you change a
message or state, update `docs/reference.md`.

## Testing

| Layer | Runs | Covers |
| --- | --- | --- |
| Unit tests (`./check`) | Every push and PR | Device choice and settings as plain JUnit; the real `RouteTrial` over an in-memory audio server behind a fake Binder for the controller, tile and notification; every screen state with Robolectric on API 36 and 37 |
| Contract test (`Instrumented` workflow) | Weekly, on demand, and on PRs that touch routing | `AudioServiceContractTest` runs `AudioServiceProbe` as the shell user on emulators for API 36 and 37, the two supported Android versions. It checks that the hidden calls exist, that a product strategy serves voice chat, that the emulator's built-in mic and speaker route and clear, and that the shell sees which app records for voice chat. It also runs a smoke test of the screen. |
| Hardware checklist | Before a release that touches routing | Below |

`./check` enforces a line-coverage floor on everything except `RouteService` and
`AudioServiceBackend`, which the contract test covers. `MainActivityTest` and
`AdvancedActivityTest` write a screenshot of each screen state to `app/build/reports/ui/`, and CI uploads them with
the APK. Review them for any UI change. The test-only tile shadow fixes a missing
`ShadowService` inheritance in Robolectric 4.14.1 so service destruction can be
exercised.

When a scheduled contract test fails, a new Android version or emulator image
changed the hidden API. Reproduce it with the same API level before changing
`AudioServiceBackend`.

### Hardware checklist

Use a phone with Shizuku, a USB mic and LE Audio headphones. Connect over paired
wireless debugging so USB stays free for the mic.

1. Run the [contract test on the phone](docs/how-to.md#check-a-phone-with-adb).
2. Install the build and start a voice chat. The card says **Split** and names both
   devices. Check mic input and headphone playback separately.
3. Unplug the mic during the call. The mic row says **Android's choice** and audio
   keeps working. Plug it back in; Split Voice uses it again.
4. Pick **Off**, then **Automatic**, from the notification and from the tile.
5. Make a profile with the headphones' mic first, and pick it. The other side hears
   the headphones.
6. Set an override for the voice app. Start a new call; the card names the app and
   the override.
7. Hang up. The card says **Ready**, diagnostics show `Recovery pending: false`, and
   `dumpsys audio` lists no preferred devices for capture preset 7 or the voice
   strategy.
8. Stop Shizuku during a call, hang up, start Shizuku and open the app. The card goes
   from **Restore needed** to **Ready**.
9. Add the result to `docs/compatibility.md`.

User-facing text lives in `strings.xml`, so translations can be added. Messages built
inside `RouteService` and `RouteTrial` run in Shizuku's shell process, which has no
app resources. They stay English until they carry message codes.

Preserve these constraints:

- Save the recovery record before sending any route request.
- Set at most one preferred device per side, and only in communication mode.
- Change a preference only when it is empty or one Split Voice set. Leave others'.
- Read back every write. Treat a device Android did not keep as refused.
- Keep recovery data through failure, process death and device loss.
- Every missing input, such as a device, profile or app name, falls back to the
  next choice and finally to Android's own. No state may need the user to fix it.
- Do not add root, arbitrary shell commands or policy edits.

## Bug reports

The issue templates ask for the output of **Copy diagnostics**. It covers the app
version, phone model, Android/One UI version, Shizuku status and exact app message.
Also include the mic and headphone models, the voice app, and separate microphone
and playback observations.

Redact device identifiers and personal content from logs and screenshots.

## Development environments

The committed Nix lock pins the Linux toolchain. Android Studio can also open the
Gradle project with JDK 21 and SDK/build tools 36. For Nix builds, `check` selects
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

Development builds and release APKs have different signing keys. End any voice
chat, then uninstall the development build before installing a release.

## Agent environments

`.agents/setup` is for disposable Amp orbs only. Do not run it on an existing
workstation. Use `nix develop` locally. Keep setup, resume and check executable. The lifecycle in
`.agents/sandbox/` is vendored from `meatcar/nix-templates#sandbox`; put project behavior in
`.agents/Taskfile.yml`.

Contributions are under the project's Apache-2.0 license. Credit upstream work
and retain any license notices when bringing in code.

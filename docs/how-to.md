# How-to guides

Each guide assumes you finished [your first split](tutorial.md).

## Split from Quick Settings

1. In Split Voice, tap **Add** next to **Quick Settings tile**, then confirm. On Android 12, open Quick Settings, tap the edit button and drag **Split Voice** in.
2. Start a voice chat.
3. Swipe down twice and tap **Split Voice**. The tile turns on.
4. After the voice chat ends, tap the tile again to restore.

The tile's subtitle tells you its state. When it says **Set up**, **Not ready** or **Restore needed**, a tap opens the app so you can see why. See [tile states](reference.md#tile).

## Split automatically

1. Turn on **Split automatically**.
2. Android shows a notification while it watches for voice chats. Leave it on.

Split Voice now splits when a voice chat starts and restores when it ends. It tries each once per chat and never retries. If an attempt fails, the screen shows why and you finish by hand.

It pauses while Split Voice is not connected to Shizuku, such as after a restart.

## Start again after a restart

Shizuku stops when the phone restarts.

1. Open Shizuku and start it with wireless debugging.
2. Open Split Voice and tap **Connect**.

If a split was on when the phone restarted, the card says **Restore needed**. End any voice chat, then tap **Restore**.

## Restore when something went wrong

The card says **Restore needed** when Split Voice changed audio routing and has not undone it yet.

1. End the voice chat. Restore is refused during a voice chat.
2. If the setup steps are visible, start Shizuku and tap **Connect**.
3. Tap **Restore**.

To get a reminder until you restore, turn on **Restore reminder**.

## Uninstall safely

1. Make sure the card does not say **Split is on** or **Restore needed**. If it does, restore first.
2. Uninstall Split Voice.

Uninstalling or clearing app data deletes the record Split Voice uses to restore.

## Report a problem

1. Reproduce the problem, so the screen shows the message.
2. Tap **Copy diagnostics**. It leaves out device names and addresses.
3. Tap **Report a problem**, choose a template and paste the diagnostics.

Use the compatibility template to report a phone, mic or headphones that work or do not work.

## Check a phone with adb

This checks that the phone's hidden audio calls still work the way Split Voice expects. It is the same check CI runs on emulators. It sets and clears routing for made-up devices, so your real devices are not affected.

1. In Split Voice, restore if a split is on, then [uninstall it](#uninstall-safely). The test installs a development build, which has a different signing key.
2. End any voice chat.
3. Connect the phone with `adb`, over USB or wireless debugging.
4. Run:

   ```sh
   nix develop -c ./check connectedDebugAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.class=com.github.meatcar.splitvoice.AudioServiceContractTest
   ```

5. Read the result in `app/build/reports/androidTests/connected/`. Add what you found to a compatibility report.

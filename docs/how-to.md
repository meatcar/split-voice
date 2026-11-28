# How-to guides

Each guide assumes you finished [the tutorial](tutorial.md).

## Change devices for one call

During a voice chat, pick another choice. It lasts until the call ends.

- In the app, tap a chip under the devices: **Automatic**, a profile or **Off**.
- In the notification, tap one of the choices.
- On the Quick Settings tile, tap to step through the choices. It skips profiles with nothing connected.

**Off** gives this call back to Android.

## Make a profile

Use a profile when Automatic picks the wrong devices, such as when you want your headphones' own mic while walking.

1. Connect the devices you want to add.
2. Tap **Advanced › New profile** and name it.
3. Tap **Add mic** and **Add headphones**. Add as many as you like.
4. Use the arrows to put them in order. Split Voice uses the first one that is connected, separately for mic and headphones.

Devices you add stay in the profile when they are disconnected. When none on a side is connected, Android chooses for that side.

To use a profile, pick it for one call, set it as **All apps**, or give it to one app.

## Set a choice for one app

1. Use the app for a voice chat once. Split Voice notes which app it was.
2. Tap **Advanced**. The app is listed under **App overrides**.
3. Tap it and pick **Automatic**, a profile or **Off**.

To add an app you have not used yet, tap **Add app**. To undo, pick **Uses All apps setting**.

## Turn Split Voice off

Tap **Advanced › All apps** and pick **Off**. Apps with their own override keep it. With everything off, the notification goes away.

## Start again after a restart

Shizuku stops when the phone restarts.

1. Open Shizuku and start it with wireless debugging.
2. Open Split Voice. It connects by itself.

If the phone restarted during a call, the card says **Restore needed** until Shizuku runs. Split Voice then gives audio back to Android by itself.

## Uninstall safely

1. End any voice chat, so the card does not say **Restore needed**.
2. Uninstall Split Voice.

Uninstalling during a call can leave that call's devices set, with nothing left to clear them.

## Report a problem

1. Reproduce the problem, so the screen shows it.
2. Tap **Copy diagnostics**. It leaves out device names, addresses and app names.
3. Tap **Report a problem**, choose a template and paste the diagnostics.

Use the compatibility template to report a phone, mic or headphones that work or do not.

## Check a phone with adb

This checks that the phone's hidden audio calls still work the way Split Voice expects. CI runs the same check on emulators. It routes the phone's own mic and speaker during a pretend voice chat, clears them again, and records a second of audio to check that Split Voice can tell which app is in a voice chat.

1. [Uninstall Split Voice](#uninstall-safely). The test installs a development build with a different signing key.
2. End any voice chat.
3. Connect the phone with `adb`, over USB or wireless debugging.
4. Run:

   ```sh
   nix develop -c ./check connectedDebugAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.class=com.github.meatcar.splitvoice.AudioServiceContractTest
   ```

5. Read the result in `app/build/reports/androidTests/connected/`. Add what you found to a compatibility report.

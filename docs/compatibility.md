# Compatibility

Split Voice changes system-wide routing through hidden Android audio APIs, so results depend on the phone, Android version, receiver and headphones.

## What the app expects

- Android 12 or later.
- One receiver input, seen by Android as a USB device.
- One headphone output, seen by Android as an LE Audio (BLE) headset.
- Shizuku started over wireless debugging, not root.

The app refuses to apply when it does not see exactly that pair.

## Verified

The **Instrumented** workflow runs every week on emulators for Android 12 through 16 (API 31 to 36). On each, it runs the routing service as the shell user, as Shizuku does, and checks that the hidden audio calls still exist, accept a split and restore cleanly. Unit tests cover the screen, tile, notifications and recovery logic on Android 12 and 15.

Emulators have no real receivers or headphones, so the table below only lists reports from real hardware. To run the same check on your phone, see [Check a phone with adb](how-to.md#check-a-phone-with-adb).

## Reports

| Phone | Android | Receiver | Headphones | Result |
| --- | --- | --- | --- | --- |
| _No reports yet_ | | | | |

## Add your device

Open a [compatibility report](https://github.com/meatcar/split-voice/issues/new?template=compatibility.yml). Tap **Copy diagnostics** in the app first and paste the result. Diagnostics exclude device addresses and names.

If your hardware is not detected, include that in the report. Other device types are only added once someone can test them on real hardware.

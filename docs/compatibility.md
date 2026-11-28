# Compatibility

Split Voice changes system-wide routing through hidden Android audio APIs, so results depend on the phone, Android version, receiver and headphones.

## What the app expects

- Android 16 or 17.
- Shizuku started over wireless debugging, not root.
- For **Automatic**: a mic Android sees as a USB device, USB headset or wired headset. LE Audio headphones are the tested pairing.
- For profiles: any mic and output Android lists. Android may refuse some; Split Voice then moves to the next one.

## Verified

Split Voice supports the two newest Android versions, Android 16 and 17. Each release can change the hidden audio APIs, so support for the oldest version ends when a new one ships.

The **Instrumented** workflow runs every week on emulators for both (API 36 and 37). On each, it runs the routing code as the shell user, as Shizuku does. It checks that the hidden audio calls still exist, set and clear the phone's own mic and speaker, and report which app records for voice chat. Unit tests cover the choices, profiles, screens, tile, notification and recovery logic on both.

Emulators have no real receivers or headphones, so the table below only lists reports from real hardware. To run the same check on your phone, see [Check a phone with adb](how-to.md#check-a-phone-with-adb).

## Reports

| Phone | Android | Receiver | Headphones | Result |
| --- | --- | --- | --- | --- |
| _No reports yet_ | | | | |

## Add your device

Open a [compatibility report](https://github.com/meatcar/split-voice/issues/new?template=compatibility.yml). Tap **Copy diagnostics** in the app first and paste the result. Diagnostics exclude device addresses and names.

If **Automatic** does not pick your mic, include that in the report. Other device types are only added once someone can test them on real hardware. A profile works in the meantime.

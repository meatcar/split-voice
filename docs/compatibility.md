# Compatibility

Split Voice changes system-wide routing through hidden Android audio APIs, so results depend on the phone, Android version, receiver and headphones.

## What the app expects

- Android 12 or later.
- One receiver input, seen by Android as a USB device.
- One headphone output, seen by Android as an LE Audio (BLE) headset.
- Shizuku started over wireless debugging, not root.

The app refuses to apply when it does not see exactly that pair.

## Verified

Automated tests run on Android 12 (API 31) and Android 15 (API 35) emulation, and a smoke test runs on an API 34 emulator. These cover the UI, tile, notifications and recovery logic. They cannot cover real receivers or headphones, so the table below only lists reports from real hardware.

## Reports

| Phone | Android | Receiver | Headphones | Result |
| --- | --- | --- | --- | --- |
| _No reports yet_ | | | | |

## Add your device

Open a [compatibility report](https://github.com/meatcar/split-voice/issues/new?template=compatibility.yml). Tap **Copy diagnostics** in the app first and paste the result. Diagnostics exclude device addresses and names.

If your hardware is not detected, include that in the report. Other device types are only added once someone can test them on real hardware.

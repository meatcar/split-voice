# Reference

## Requirements

| Need | Detail |
| --- | --- |
| Android | 16 or 17 (API 36 or 37) |
| Shizuku | Started with wireless debugging or adb (shell user, uid 2000). Root is refused. |
| Voice chat | Split Voice changes routing only while Android is in communication mode (`MODE_IN_COMMUNICATION`) |

## Choices

Each voice chat uses one choice. The first that applies wins:

1. The choice you picked for this call.
2. The app's override, when Split Voice knows the app.
3. **All apps**. The default is **Automatic**.

| Choice | Mic | Headphones |
| --- | --- | --- |
| Automatic | The most recently connected USB or wired mic | The most recently connected headphones, only when it found a mic. Otherwise Android chooses. |
| A profile | The first connected mic in the profile's list | The first connected headphones in the list. Each side is independent. |
| Off | Android chooses | Android chooses |

When no listed device is connected, Android chooses for that side. A deleted profile counts as **Automatic** for **All apps** and as no override for apps.

| Kind | Device types |
| --- | --- |
| USB or wired mic | USB device, USB headset, wired headset |
| Headphones | LE Audio headset, hearing aid, wired headset or headphones, USB headset, Bluetooth SCO |
| Mics in a profile | Phone mic, USB device, headset or accessory, wired headset, LE Audio headset, Bluetooth SCO, line in |
| Outputs in a profile | Phone speaker or earpiece, LE Audio headset or speaker, hearing aid, wired headset or headphones, USB device or headset, Bluetooth SCO |

Profiles find a saved device again by Bluetooth address, by product name for USB and wired devices, and by type for the phone's own devices.

## Rules

- Split Voice sets one preferred mic and one preferred output for voice chat, never more.
- It changes routing only during a voice chat. When the call ends, it clears what it set.
- It never replaces a preference another app or tool set. The device row then says *Chosen by another app*.
- If Android refuses a device, Split Voice tries the next one in the list and skips that device until the call ends.
- Before every change, it saves a recovery record of what it will set.

## Screen

| Title | Meaning |
| --- | --- |
| Finish setup | Not connected to Shizuku. The card lists the next step. |
| Working… | Connecting or reading audio state |
| Restore needed | A recovery record exists and Split Voice is not connected to Shizuku. Start Shizuku. |
| Can't check audio | Connected, but the last check failed. The message says why. |
| Ready | No voice chat. Split Voice steps in when one starts. |
| Off | Everything is off, or this call uses **Off** |
| Split | A voice chat uses at least one device Split Voice chose |
| Not split | A voice chat, but no chosen device is connected or allowed |

During a voice chat the card also shows the app and choice, one row each for mic and headphones, and chips to [change the choice for this call](how-to.md#change-devices-for-one-call). "· this call" follows the choice you picked.

| Row note | Meaning |
| --- | --- |
| No USB or wired mic connected | Automatic found no mic, so it left both sides to Android |
| No headphones connected | Automatic found a mic but no headphones |
| None of this profile's mics are connected / headphones are connected | The profile's list for that side has nothing connected |
| Android refused … | Android rejected these devices for this call |
| Chosen by another app. Split Voice leaves it alone. | Another app or tool set a preference first |

## Tile

| Subtitle | Tile | Tap |
| --- | --- | --- |
| The choice in use | On | Next choice for this call |
| Not split, or Off during a call | Off | Next choice for this call |
| Ready, Off, Restore needed, Set up, Not ready | Off | Opens Split Voice |
| Working… | Unavailable | Nothing |

## Notification

Split Voice keeps a notification while any choice is on or a restore is pending. It shows the screen's title and message. During a voice chat it offers up to three other choices, with **Off** last.

## Messages

| Message | What to do |
| --- | --- |
| Start Shizuku to continue. | Start Shizuku with wireless debugging. It stops after every restart. |
| Start Shizuku. Split Voice then restores normal audio by itself. | A call was routed when Shizuku stopped. Start Shizuku. |
| Tap Connect, then allow Split Voice in Shizuku. | Tap **Connect** and allow access in the Shizuku prompt. |
| Shizuku access denied. Tap Connect to ask again. | Tap **Connect** and allow. You can also allow it in the Shizuku app. |
| Shizuku is running as root. Restart it with wireless debugging. | Split Voice only runs as the shell user. |
| Connecting to Shizuku… | Wait. |
| Shizuku did not answer. Restart Shizuku, then tap Connect. | Stop and start Shizuku. |
| Cannot connect to Shizuku (…). | Restart Shizuku. If it repeats, report it with diagnostics. |
| Lost the connection to Shizuku. Start it again. | Start Shizuku. |
| Cannot save the recovery record, so nothing changed. | Free some storage. |
| Shizuku gave no answer. Audio state unknown. / Cannot read audio routing. | Restart Shizuku. |
| This Android version rejected the routing call (…). Send diagnostics. | The hidden API changed on this phone. Report it with diagnostics. |

## Settings

| Setting | Default | Stored in `settings` as |
| --- | --- | --- |
| All apps | Automatic | `all` |
| Profiles | None | `profiles` |
| App overrides | None | `overrides` |
| Apps seen in a voice chat | None | `seen` |
| Quick Settings tile added | No | `tile` |

The recovery record is `routing-recovery`: `input` and `output`, each a list of devices. App data is excluded from backups.

## Permissions

| Permission | Used for |
| --- | --- |
| Shizuku | Reading and changing audio routing |
| `POST_NOTIFICATIONS` | The status notification and its call controls. Optional. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Watching for voice chats while any choice is on |

Split Voice has no internet or microphone permission.

## Diagnostics

**Copy diagnostics** copies these lines. It never includes device names, addresses or app names.

| Line | Content |
| --- | --- |
| App, Android, Phone, Build | Versions and model |
| Shizuku | Running, version, uid and permission |
| State | Title and message |
| Recovery pending | Whether a recovery record exists |
| Voice chat | Whether Android is in communication mode |
| Voice app | Whether Split Voice could tell which app is in the call |
| Using | The choice in use |
| Mic, Headphones | What Split Voice set, or who chose instead, by device type |
| Refused | Device types Android refused this call |
| Inputs, Outputs | Connected device types |

## What Split Voice calls

All calls go to `IAudioService` from the shell user.

| Call | Purpose |
| --- | --- |
| `setPreferredDevicesForCapturePreset`, `clearPreferredDevicesForCapturePreset` | Voice chat mic, capture preset 7 (`VOICE_COMMUNICATION`) |
| `setPreferredDevicesForStrategy`, `removePreferredDevicesForStrategy` | Voice chat output, the product strategy that serves `USAGE_VOICE_COMMUNICATION` |
| `getPreferredDevicesForCapturePreset`, `getPreferredDevicesForStrategy` | Reading back what is set |
| `getMode`, `isBluetoothScoOn`, `getAudioProductStrategies` | Voice chat state |
| `getActiveRecordingConfigurations` | Which app records for voice chat, for app overrides |

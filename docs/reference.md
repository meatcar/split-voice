# Reference

## Requirements

| Need | Detail |
| --- | --- |
| Android | 16 (API 36) or later |
| Mic receiver | Exactly one input that Android reports as a USB device (`TYPE_USB_DEVICE`) |
| Headphones | Exactly one output that Android reports as an LE Audio headset (`TYPE_BLE_HEADSET`) |
| Shizuku | Started with wireless debugging or adb (shell user, uid 2000). Root is refused. |
| Voice chat | Android must be in communication mode (`MODE_IN_COMMUNICATION`) to split, and in normal mode to restore |

## Screen

The status card shows one of these titles.

| Title | Meaning | Action |
| --- | --- | --- |
| Finish setup | Split Voice is not connected to Shizuku. The card lists the next setup step. | Follow the steps |
| Ready to split | Devices are ready and no app has chosen audio devices | **Split** |
| Split is on | The mic and headphone choices are set and match the recovery record | **Restore** |
| Restore needed | A recovery record exists, but the split is not confirmed or Split Voice is not connected | **Restore** once connected |
| Not ready | Connected, but something below the title is missing | Fix what the checks show |
| Working… | Connecting, splitting or restoring | Wait |

When connected, the card shows three checks:

| Check | Connected / In progress | Not connected / Not started | Count, in red |
| --- | --- | --- | --- |
| Mic receiver | One USB input | None | More than one |
| LE Audio headphones | One LE Audio output | None | More than one |
| Voice chat | Communication mode | Any other mode | — |

## Tile

| Subtitle | Tile | Tap |
| --- | --- | --- |
| Set up | Off | Opens Split Voice |
| Off | Off | Splits |
| On | On | Restores |
| Restore needed | Off | Restores when connected, otherwise opens Split Voice |
| Not ready | Off | Opens Split Voice |
| Working… | Unavailable | Nothing |

## Messages

The message under the title, with what to do.

| Message | What to do |
| --- | --- |
| Start Shizuku to continue. | Start Shizuku with wireless debugging. It stops after every restart. |
| Start Shizuku, then tap Restore. | A split was on when Shizuku stopped. Start Shizuku, connect, then restore. |
| Tap Connect, then allow Split Voice in Shizuku. | Tap **Connect** and allow access in the Shizuku prompt. |
| Shizuku access denied. Tap Connect to ask again. | Tap **Connect** and allow. You can also allow it in the Shizuku app. |
| Shizuku is running as root. Restart it with wireless debugging. | Split Voice only runs as the shell user. Stop Shizuku and start it with wireless debugging. |
| Shizuku did not answer. Restart Shizuku, then tap Connect. | Stop and start Shizuku. |
| Cannot connect to Shizuku (…). | Restart Shizuku. If it repeats, report it with diagnostics. |
| Lost the connection to Shizuku. Start it again. | Shizuku stopped while Split Voice used it. Start it and connect. |
| Reconnect Shizuku. / Reconnect Shizuku, then tap Restore. | The connection changed during an action. Tap **Connect**. |
| Start a voice chat, then tap Split. | Everything is ready. |
| Start a voice chat first. Nothing changed. | Splitting only works during a voice chat. |
| Connect one mic receiver and one pair of LE Audio headphones. | Check the device rows. Unplug extra USB audio devices and disconnect other LE Audio headphones. |
| Your headphones' own mic is in use. … | Android picked the headphones' mic. Plug in the receiver, then restart the voice chat. |
| Another app already chose audio devices. Nothing changed. | Another app or tool set routing first. Split Voice will not override it. Close that app or restart the phone. |
| Another app chose audio devices. Split Voice will not override it. | Same as above. |
| A device connected or disconnected. Nothing changed. Try again. | Tap **Split** again once your devices are stable. |
| Cannot identify your devices. Nothing changed. | Android did not report device addresses. Report it with diagnostics. |
| Cannot save the recovery record. Nothing changed. | Free some storage and try again. |
| Split is on. End the voice chat, then tap Restore. | The split worked. |
| End the voice chat, then tap Restore. | Restore is refused during a voice chat. |
| End the voice chat first. Nothing changed. | Same as above. |
| Normal audio restored. | Done. |
| Normal audio restored, but the recovery record was not cleared. | Tap **Restore** again. It is safe to repeat. |
| Android did not keep the mic choice. … / Android did not keep the headphone choice. … | Android rejected part of the split. End the voice chat and tap **Restore**. Report it with diagnostics. |
| Android did not confirm the restore. Tap Restore again. | Tap **Restore** again. |
| Another app changed audio devices after the split. Split Voice will not override it. | Something else changed routing. Restart the phone to clear it, and report it with diagnostics. |
| The recovery record is incomplete. Keep app data and send diagnostics. | Do not clear app data. Report it. |
| This Android version rejected the routing call (…). Send diagnostics. | The hidden API changed on this phone. Report it with diagnostics. |
| Shizuku gave no answer. Audio state unknown. / Cannot read audio routing. | Restart Shizuku and connect. |

## Options

| Option | Default | Stored as |
| --- | --- | --- |
| Split automatically | Off | `settings` › `auto` |
| Restore reminder | Off | `settings` › `notifications` |
| Quick Settings tile | Not added | `settings` › `tile` |

The recovery record is `routing-recovery`: `pending`, `receiver` and `earbuds`. It is excluded from backups.

## Permissions

| Permission | Used for |
| --- | --- |
| Shizuku | Changing audio routing |
| `POST_NOTIFICATIONS` | Restore reminder and the automatic split notification |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Watching for voice chats while **Split automatically** is on |

Split Voice has no internet or microphone permission.

## Diagnostics

**Copy diagnostics** copies these lines. It never includes device names or addresses.

| Line | Content |
| --- | --- |
| App | Version name and code |
| Android | Release and SDK level |
| Phone, Build | Manufacturer, model and build |
| Shizuku | Running, version, uid and permission |
| State | Title and message |
| Recovery pending | Whether a recovery record exists |
| Audio mode | 0 normal, 2 call, 3 communication |
| Bluetooth SCO | Whether the headphones' own mic is in use |
| No preferences set | Whether no app has chosen devices |
| One receiver and one LE headset | Whether exactly one of each is connected |
| Split matches | Whether the current choices match the recovery record |
| Input types, Output types | Connected device types |

## What Split Voice changes

| Call | Target | Device |
| --- | --- | --- |
| `IAudioService.setPreferredDevicesForCapturePreset` | Capture preset 7 (`VOICE_COMMUNICATION`) | The receiver |
| `IAudioService.setPreferredDevicesForStrategy` | The product strategy that serves `USAGE_VOICE_COMMUNICATION` | The headphones |

Restore calls `clearPreferredDevicesForCapturePreset` and `removePreferredDevicesForStrategy` for the same targets. Nothing else on the phone changes.

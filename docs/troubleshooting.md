# Troubleshooting

Open Split Voice and read the title and message at the top. Tap **Copy diagnostics**
before you report a problem.

## Setup

| Message | What to do |
| --- | --- |
| Start Shizuku, then connect. | Start Shizuku, then tap **Connect**. Shizuku stops after every reboot. |
| Tap Connect to allow Shizuku access. | Tap **Connect** and allow Split Voice in the Shizuku prompt. |
| Shizuku permission denied. | Tap **Connect** again and allow access. You can also grant it in the Shizuku app. |
| Start Shizuku using wireless debugging, not root. | Restart Shizuku with wireless debugging. Split Voice refuses to run as root. |
| Connection timed out. Restart Shizuku. | Stop and start Shizuku, then tap **Connect**. |
| Connection failed. Reconnect Shizuku. | Shizuku stopped while Split Voice worked. Start it and tap **Connect**. |

## Splitting

| Message | What to do |
| --- | --- |
| Connect one receiver and one LE headset. | Connect exactly one USB receiver and one LE Audio headset. Disconnect other USB audio devices. |
| Start Voice before applying. | Start your voice app first, so Android is in a call or voice mode. Then tap the tile. |
| Bluetooth mic active. Connect the physical receiver. | Android uses the headset's own microphone. Connect the receiver and start your voice app again. |
| Another app has routing preferences set. | Another app or tool already chose audio devices. Split Voice will not overwrite them. Stop that app. |
| Devices changed. Check again. | A device connected or disconnected. Tap **Check routes**. |
| Input readback failed, or Combined readback failed. | Android did not accept the change. Stop Voice, tap **Restore**, then try again. If it repeats, send diagnostics. |
| Unsupported or unavailable API | This Android or One UI version may not support the routing calls. Send diagnostics. |

## Automatic split

Turn on **Split automatically** to apply the split when a voice session starts and
restore it when the session ends. It makes one attempt per session. It never
retries a failed apply or restore. If one fails, the app shows **Recovery required**
and you finish with the tile or **Restore**.

- Open Split Voice after you reboot and restart Shizuku. Automatic split cannot
  connect before then.
- It shows a notification while it runs. Turning the setting off removes it.
- It reacts to any app that starts a voice session. Turn it off if you want to
  choose each time.

## Recovery

If the title says **Recovery required**, Split Voice changed routing and has not
restored it yet.

1. Stop the voice session. Restore is refused while Voice is active.
2. Start and connect Shizuku.
3. Tap **Restore**.

| Message | What to do |
| --- | --- |
| Stop Voice to restore. | End the call or voice session first. |
| Start Shizuku to restore. | Shizuku was stopped. Start it and tap **Connect**. |
| Preferences changed outside Split Voice. Refusing to overwrite them. | Something else changed routing after Split Voice. Do not force it. Send diagnostics. |
| Cannot save recovery state. Nothing applied. | Android could not save the record, so Split Voice changed nothing. Free storage space and try again. |

Restore before you uninstall Split Voice or clear its data. Both delete the recovery
record.

## FAQ

**Why does it need Shizuku?** Changing the preferred audio devices is a privileged
call. Shizuku lets Split Voice make it without root. Split Voice only uses it for
routing.

**Does it record my voice?** No. It requests no microphone permission and no
internet permission. See [PRIVACY.md](../PRIVACY.md).

**Can I use Bluetooth headphones that are not LE Audio?** No. Split Voice only
looks for an LE headset output.

**Does it work with any wireless microphone?** It supports a receiver that shows up
as a USB audio input. Other receivers are not tested. Open a compatibility report if
you try one.

**What happens if I reboot while a split is active?** The recovery record stays on
the phone. Shizuku does not restart by itself, so start it, connect, and tap
**Restore**.

# How Split Voice works

## The problem

Android treats Bluetooth headphones as a pair: during a call or voice chat, it takes the microphone from the same headphones it plays through. Headphone mics are often poor, and a wireless clip-on mic sounds much better. Plug the mic's receiver in, and Android still decides on its own which mic and which speaker a voice chat uses.

Android can route input and output to different devices. Apps that manage their own audio do this. There is no setting that lets you choose it for another app, such as ChatGPT Voice or a calling app.

## The workaround

Android's audio service keeps a list of preferred devices for each kind of audio. Split Voice sets two of them:

- For voice chat input (capture preset `VOICE_COMMUNICATION`), it prefers the mic receiver.
- For voice chat output (the product strategy that serves `USAGE_VOICE_COMMUNICATION`), it prefers the LE Audio headphones. Its ID differs between phones, so Split Voice looks it up each time.

Every app's voice chat then uses the receiver to listen and the headphones to play. Restore clears both preferences, so Android goes back to its own choices.

These calls are part of Android, but they are hidden from apps and need a system permission (`MODIFY_AUDIO_ROUTING`). The shell user that `adb` uses holds that permission.

## Why Shizuku

[Shizuku](https://shizuku.rikka.app/) starts a process as the shell user, using wireless debugging on the phone itself. Split Voice runs a small service in that process and sends it three requests: inspect, apply and restore. That service only calls the audio methods above. It runs no shell commands.

Split Voice refuses to run when Shizuku runs as root. Shell access is enough, and the smaller permission is safer.

Wireless debugging stops when the phone restarts, so Shizuku stops too. That is why you start Shizuku again after each restart.

## Why only LE Audio

Classic Bluetooth headphones use one link (SCO) for both mic and playback during calls. When Android opens that link, it uses the headphones' mic. LE Audio headphones can play voice audio without that link, so the input can come from somewhere else. Split Voice refuses to split while the SCO link is in use.

The receiver must appear as a USB audio input, which covers most plug-in receivers for wireless mics. Other device types are only added after someone tests them on real hardware.

## Keeping your audio safe

Changing system-wide routing from outside Android's settings is risky: a mistake would leave every call on the wrong device, with no menu to undo it. Split Voice is built so that you can always undo the split.

- **Save first.** Before it changes anything, it saves a recovery record with the two devices it will use. If the app crashes, the phone restarts or Shizuku stops, the record stays, and the app shows **Restore needed**.
- **Check before changing.** It splits only when exactly one receiver and one LE Audio headphone are connected, a voice chat is running, the headphone mic is off, and no other app has set preferences.
- **Read back.** After each change, it reads the preferences back and fails if Android did not keep them.
- **Do not override.** If another app has set preferences, before or after the split, Split Voice stops and tells you. It does not overwrite them.
- **Restore when idle.** Restore runs only when no voice chat is active, so it does not move a live call.
- **No retries.** Failed changes are reported, not retried. Automatic split also tries once per voice chat.

## Limits

- The calls are hidden, so a phone maker or a new Android version can change them. A weekly test checks them on an emulator for every supported Android version. Real phones can still differ; see [Compatibility](compatibility.md).
- Preferences are system-wide. While a split is on, other apps' voice chats use the same devices.
- Split Voice cannot start Shizuku for you. Android does not let an app turn on wireless debugging.
- Uninstalling Split Voice or clearing its data deletes the recovery record. Restore first.

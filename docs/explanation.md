# How Split Voice works

## The problem

During a call or voice chat, Android treats your headphones as a headset: it takes the mic from the same headphones it plays through. Headphone mics are often poor, and a clip-on wireless mic sounds much better. Plug the mic's receiver in, and Android still decides by itself which mic and which speaker a voice chat uses. There is no setting to change that for another app.

## The workaround

Android's audio service keeps a list of preferred devices for each kind of audio. Split Voice sets two of them:

- For voice chat input (capture preset `VOICE_COMMUNICATION`), the mic you want.
- For voice chat output (the product strategy that serves `USAGE_VOICE_COMMUNICATION`), the headphones you want. The strategy's ID differs between phones, so Split Voice looks it up.

Every voice chat then uses those two devices. Clearing the preferences gives the choice back to Android.

These calls are part of Android, but they are hidden from apps and need a system permission (`MODIFY_AUDIO_ROUTING`). The shell user that `adb` uses holds it.

## Why Shizuku

[Shizuku](https://shizuku.rikka.app/) starts a process as the shell user, using wireless debugging on the phone itself. Split Voice runs a small service in that process with two requests: inspect and route. It only calls the audio methods in the [reference](reference.md#what-split-voice-calls) and runs no shell commands.

Split Voice refuses to run when Shizuku runs as root. Shell access is enough, and the smaller permission is safer.

Wireless debugging stops when the phone restarts, so Shizuku stops too. Android does not let an app turn it back on.

## Why one device per side

Android does not treat a preferred-device list as a ranking. It uses the whole list when every device on it is connected, and otherwise none of it. So a profile's order is Split Voice's: it picks the first connected device and sets only that one. When the device disconnects, Split Voice notices and picks the next.

Split Voice cannot mix or fan out audio like a PC patch bay. Android only lets it choose the one way in and the one way out for voice chat.

## Why Automatic needs a plug-in mic

Automatic is for the common case: you plugged in a mic because you want to use it. Without one, there is nothing to split, and Android's own choice is the right one. Split Voice stays out of the way rather than guess.

Classic Bluetooth headphones use one link (SCO) for both mic and playback during calls. LE Audio headphones can play voice audio without taking the mic, so they pair best with a plug-in mic.

## Knowing which app is in a call

Android reports which apps are recording. During a voice chat, Split Voice asks for the one recording as voice communication and uses that app's override. It sees app names only because the shell user may, and it never sends them anywhere. When it cannot tell, it uses **All apps**.

## Keeping your audio safe

Changing system-wide routing from outside Android's settings is risky: a mistake would leave every call on the wrong device, with no menu to undo it. Split Voice is built so that every state can recover by itself.

- **Calls only.** It changes routing only during a voice chat, and clears what it set when the call ends. Between calls, Android is in charge.
- **Save first.** Before each change, it records the devices it will set. If the app crashes, the phone restarts or Shizuku stops, the record stays. The next time it connects outside a call, it clears those preferences.
- **Compare and set.** It changes a preference only when it is empty or one Split Voice set. A preference set by another app or tool is left alone and shown on screen.
- **Read back.** After each change, it reads the preference back. A device Android did not keep counts as refused, and Split Voice moves to the next one.
- **Degrade, never break.** A missing device, a refused device, a deleted profile or an unknown app each fall back to the next choice, and in the end to Android's own.

## Limits

- The calls are hidden, so a phone maker or a new Android version can change them. A weekly test checks them on emulators for both supported Android versions. Real phones can differ; see [Compatibility](compatibility.md).
- Preferences are system-wide. Two apps in voice chats at once share one choice.
- Split Voice routes voice chat only. Music and video follow Android's usual rules.
- Split Voice starts working after you open it once following a restart.

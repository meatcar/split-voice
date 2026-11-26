# Your first split

This tutorial takes you from a fresh install to a voice chat that uses your wireless mic and your LE Audio headphones, then back to normal audio. It takes about ten minutes.

You need:

- An Android 12 or later phone with Wi-Fi.
- A wireless mic whose receiver plugs into the phone's USB-C port.
- Headphones with LE Audio turned on. On Samsung phones, turn it on in the headphones' Bluetooth settings.
- An app with voice chat, such as ChatGPT or a calling app.

## 1. Install the apps

1. Install Split Voice from [Releases](https://github.com/meatcar/split-voice/releases/latest) or through [Obtainium](https://obtainium.imranr.dev/).
2. Open Split Voice. The card says **Finish setup**.
3. Tap **Get Shizuku** and install Shizuku.

Back in Split Voice, step 1 now shows a check mark.

## 2. Start Shizuku

Shizuku runs a small helper with the same access as `adb`. Split Voice asks it to change audio routing.

1. Tap **Open Shizuku**.
2. Follow Shizuku's steps under **Start via Wireless debugging**. The first time, you pair Shizuku with your phone using a code from **Developer options › Wireless debugging**.
3. Wait until Shizuku says it is running, then go back to Split Voice.

Step 2 now shows a check mark.

## 3. Connect

1. Tap **Connect**.
2. Shizuku asks whether to allow Split Voice. Tap **Allow**.

The setup steps go away. The card now lists three checks: **Mic receiver**, **LE Audio headphones** and **Voice chat**.

## 4. Get ready

1. Plug the mic receiver into the phone and turn on the mic.
2. Connect your headphones.

Both device checks now say **Connected**. Voice chat says **Not started**.

## 5. Split

1. Start a voice chat in your voice app.
2. Open Split Voice. The title says **Ready to split**.
3. Tap **Split**.

The card turns blue and says **Split is on**. Talk into the wireless mic. The other side hears the mic, and you hear them in your headphones.

## 6. Restore

1. End the voice chat.
2. Open Split Voice and tap **Restore**.

The title says **Ready to split** again, and Android uses its normal audio choices. Always restore before you uninstall Split Voice.

## Next steps

- [Split from Quick Settings](how-to.md#split-from-quick-settings) so you do not have to leave the voice chat.
- [Split automatically](how-to.md#split-automatically) every time a voice chat starts.
- Read [how Split Voice works](explanation.md) and why it is safe to undo.

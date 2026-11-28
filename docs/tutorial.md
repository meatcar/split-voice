# Your first voice chat with Split Voice

This tutorial takes you from a fresh install to a voice chat that uses your plug-in mic and your headphones. It takes about ten minutes.

You need:

- An Android 16 or 17 phone with Wi-Fi.
- A mic that plugs into the phone: a wireless mic receiver on USB-C, a USB mic or a wired headset.
- Headphones. LE Audio headphones work best; on Samsung phones, turn LE Audio on in the headphones' Bluetooth settings.
- An app with voice chat, such as Discord, WhatsApp or ChatGPT.

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

## 3. Connect

1. Tap **Connect**.
2. Shizuku asks whether to allow Split Voice. Tap **Allow**.

The card now says **Ready**. Split Voice runs in the background from now on. Tap **Allow** next to **Notifications** so you can see and change what it does during a call.

## 4. Talk

1. Plug in the mic and turn it on. Connect your headphones.
2. Start a voice chat.
3. Open Split Voice, or pull down the notification.

The card says **Split** and lists the mic and headphones in use. Talk into your mic. The other side hears it, and you hear them in your headphones.

## 5. Hang up

End the voice chat. Split Voice gives audio back to Android, and the card says **Ready** again. You do not need to do anything.

## Next steps

- [Change devices for one call](how-to.md#change-devices-for-one-call).
- [Make a profile](how-to.md#make-a-profile) when Automatic picks the wrong devices.
- Read [how Split Voice works](explanation.md) and why it is safe.

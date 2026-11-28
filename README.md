# Split Voice

Talk through a plug-in mic and listen through your headphones in any Android voice chat. Android does not let you choose this. Split Voice does it with [Shizuku](https://shizuku.rikka.app/), without root.

Plug in a mic and it just works. Profiles and per-app choices are there when you need them.

<img src="docs/images/screen.png" width="330" alt="Split Voice during a Discord call, using the DJI mic and Jabra headphones">

## Get started

You need Android 16 or 17, a mic that plugs into the phone, such as a wireless mic receiver on USB-C, and headphones. LE Audio headphones work best.

Install the APK from [Releases](https://github.com/meatcar/split-voice/releases/latest), or add `https://github.com/meatcar/split-voice` to [Obtainium](https://obtainium.imranr.dev/) for updates. Then follow [the tutorial](docs/tutorial.md).

## Documentation

- [Tutorial](docs/tutorial.md): set up and make your first voice chat.
- [How-to guides](docs/how-to.md): change devices for one call, profiles, per-app choices, after a restart, report a problem.
- [Reference](docs/reference.md): choices and rules, screen and tile states, messages, settings, permissions, diagnostics.
- [How it works](docs/explanation.md): why this needs Shizuku and how your audio stays safe.
- [Compatibility](docs/compatibility.md): tested phones and devices.

Split Voice does not record audio and has no internet or microphone permission. See [PRIVACY.md](PRIVACY.md).

## Build

On Linux with [Nix](https://nixos.org/download/):

```sh
git clone https://github.com/meatcar/split-voice.git
cd split-voice
nix develop -c ./check
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`. See [CONTRIBUTING.md](CONTRIBUTING.md) for development instructions.

## Disclaimer

Split Voice uses hidden Android audio APIs and changes system-wide routing during voice chats. Results vary by phone and Android version. End any voice chat before you uninstall it. Report what works for you in a [compatibility report](https://github.com/meatcar/split-voice/issues/new?template=compatibility.yml).

## Credits and license

Inspired by [BTMicFix](https://github.com/Endda/BTMicFix) by Endda. Built with [Shizuku](https://shizuku.rikka.app/) and [meatcar/nix-templates](https://github.com/meatcar/nix-templates).

By [Denys](https://denys.me). Licensed under [Apache-2.0](LICENSE). Dependency notices are in [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES).

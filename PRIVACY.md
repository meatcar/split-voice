# Privacy

Split Voice collects, stores and sends no personal data. It has no analytics, no
accounts, no ads and no crash reporting.

- **Network:** the app does not request the internet permission. It cannot send
  anything off the phone. Links to Shizuku, the documentation and GitHub issues open in
  your browser.
- **Audio:** it does not record audio and does not request microphone permission.
  It only asks Android which audio devices are connected and sets which input and
  output Android prefers.
- **Local data:** it keeps on the phone your profiles, with the name and
  Bluetooth address of each device you add; the apps you set a choice for or used
  for voice chat; and a recovery record of the devices it set during a call, deleted
  when the call ends. Split Voice learns which app is in a voice chat from Android's
  list of apps that are recording. Backups and device transfers include none of it.
- **Permissions:** Shizuku access, which you grant, to change audio routing.
  Notification permission, which is optional, for the status notification and
  call controls. Foreground service permission, to watch for voice chats while any
  choice is on.
- **Diagnostics:** **Copy diagnostics** puts a text summary on your clipboard
  only when you tap it. It lists the app and Android versions, phone model, Shizuku
  status, route state and audio device types. It never includes device addresses,
  device names or app names. Review it before you paste it into a bug report.

This document describes the app in this repository. Changes to it are visible in the
commit history.

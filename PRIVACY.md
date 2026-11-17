# Privacy

Split Voice collects, stores and sends no personal data. It has no analytics, no
accounts, no ads and no crash reporting.

- **Network:** the app does not request the internet permission. It cannot send
  anything off the phone. The links it opens (Shizuku's guide) open in your browser.
- **Audio:** it does not record audio and does not request microphone permission.
  It only asks Android which audio devices are connected and sets which input and
  output Android prefers.
- **Local data:** it keeps a recovery record on the phone: whether a split is
  active and the two device identifiers it changed, so it can restore them. The
  record is deleted when routing is restored. Backups and device transfers do not
  include it.
- **Permissions:** Shizuku access, which you grant, to change audio routing.
  Notification permission, which is optional, for the recovery notification.
  Foreground service permission, used only while you have **Split automatically**
  turned on, to watch for voice sessions. It stays off by default.
- **Diagnostics:** **Copy diagnostics** puts a text summary on your clipboard
  only when you tap it. It lists the app and Android versions, phone model, Shizuku
  status, route state and audio device types. It never includes device addresses or
  names. Review it before you paste it into a bug report.

This document describes the app in this repository. Changes to it are visible in the
commit history.

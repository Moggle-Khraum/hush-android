# Hush

An Android app that quiets the apps you choose. It has two independent modes:

- **NetBlock**: cuts selected apps off from the internet. It uses a local VPN
  (`VpnService`) that reads and discards their packets. No root needed. Optional
  options include blocking related apps (same family or shared UID) and ending
  their background processes every 15 seconds.
- **RedirectBlock**: drops web links opened by selected apps (ads, casino pages,
  surprise browser tabs). Hush registers as the handler for http/https links.
  Links from apps that set no referrer, and links opened inside an app's own
  browser, are passed through or can't be caught.

Each mode keeps its own app list and on/off state, and both can run at the same time.

## Limits

- Android only lets a normal app end background processes. Foreground apps need
  you to tap "Force stop" on their settings page.
- Blocking does not survive a reboot.
- Only one VPN can run at a time, so NetBlock conflicts with other VPN apps.
- `QUERY_ALL_PACKAGES` is restricted on Google Play. This build is for sideloading.

## Build

The GitHub Actions workflow `.github/workflows/build-apk.yml` builds a debug APK
on every push to `main`, on pull requests, and on manual runs. Tags starting with
`v` also publish a GitHub Release.

Local build: `./gradlew assembleDebug` (JDK 17, Android SDK 35).

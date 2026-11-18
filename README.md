# NetBlocker

Per-app internet kill switch for Android (no root).

## How it works
Selected apps are routed into a local VPN (`VpnService`) that reads and discards
every packet, so those apps have no internet. Unselected apps are untouched.
Nothing leaves your phone; the app has no INTERNET permission.

On top of that:
- **Include related apps**: apps from the same family (same shared user ID, or
  the same first two package segments, e.g. `com.facebook.orca` and
  `com.facebook.katana`) are blocked too. Platform/vendor prefixes such as
  `com.google` or `com.samsung` are never grouped, and Play Services and the
  Play Store are never blocked. The extra packages are listed in the app.
- **Kill background processes**: while blocking, the blocked apps' background
  processes are ended every 15 seconds so old connections die and restarted
  processes (push, sync) don't linger.
- **Force stop** button next to each ticked app opens its system settings page.

## Limits (Android, without root)
- Only background processes can be killed. A foreground app, or a full
  force-stop, needs you to tap "Force stop" on the settings page.
- Other apps' files and data can't be deleted. Cached content still shows
  offline; it just can't load anything new.
- Push notifications still arrive via Google Play Services (not blocked).
- Only one VPN can be active at a time, and blocking doesn't survive a reboot.
- `QUERY_ALL_PACKAGES` is used to find related helper packages. That is fine for
  sideloading but restricted on Google Play.

## Use
1. Tick the apps to cut off.
2. Press "Block selected apps" and accept the VPN prompt.
3. Press "Stop blocking" to restore internet.

## Build
Locally: open the folder in Android Studio (Ladybug or newer), or run
`./gradlew assembleDebug`. The APK is at `app/build/outputs/apk/debug/`.

CI: the **Build APK** workflow (`.github/workflows/build-apk.yml`) runs on every
push to `main`, on pull requests, and manually from the Actions tab. Download the
APK from the run's **Artifacts**. Pushing a tag like `v1.0` also publishes the
APK as a GitHub Release.

The APK is signed with the debug key, so it installs directly on a phone
(enable "Install unknown apps" for your browser or file manager).

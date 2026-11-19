# Twelve Fifths Game Booster

An Android game booster with a live overlay HUD, a game library, real (unprivileged) system metrics, and a session-based optimizer.

This is a **new app** (`com.twelvefifths.booster`), not a reskin of Lounge.

Version `0.1.0` (versionCode `1`) is a compiling scaffold: interfaces, stub implementations that already talk to real Android APIs where those APIs exist, and placeholder screens. Teammates fill in the rest — see [COLLAB.md](COLLAB.md).

## What works in 0.1.0

- Dark charcoal UI with glass-like panels (gradients and veils, not blur).
- Dashboard, Library, Optimize, HUD settings, and a first-run permissions screen.
- RAM from `ActivityManager.MemoryInfo`.
- Optional CPU / GPU temperatures when `/sys/class/thermal` is readable.
- Optional CPU usage when `/proc/stat` is readable (usually it is not).
- Ping as a timed TCP connect.
- Game detection (`CATEGORY_GAME`) and manual add, launch, last played.
- Foreground-app detection via usage stats (after the user grants access).
- One-click optimize: close user-selected background apps, optional Do Not Disturb, open battery-saver settings, restore on end.
- Always-on-top HUD (`SYSTEM_ALERT_WINDOW`) in a foreground service, draggable, start/stop from the app or the notification.

Unsupported metrics (other games' FPS, GPU usage, GPU VRAM on an unprivileged APK) are labeled **Unsupported**, never faked. Details are in [COLLAB.md](COLLAB.md).

## Build

Requirements: JDK 17, Android SDK with `compileSdk` 34.

```bash
# local.properties is gitignored. Copy the example and set sdk.dir.
cp local.properties.example local.properties

export JAVA_HOME=/path/to/jdk-17
./gradlew :app:assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

Toolchain used on the original build box:

- `JAVA_HOME=/home/box/sdk/jdk-17.0.21+9`
- Android SDK `/home/box/sdk/android`
- AGP 8.5.2, Kotlin 2.0.20, Compose BOM 2024.09.00, Gradle 8.9
- `minSdk` 26, `compileSdk` / `targetSdk` 34

## Package layout

```
com.twelvefifths.booster
  MainActivity, BoosterApplication, BoosterGraph, Permissions
  ui/         theme, screens, in-app HUD preview
  metrics/    MetricsSource, MetricsSnapshot, MetricSupport
  optimize/   GameLibrary, ForegroundAppDetector, Optimizer
  hud/        HudService, HudWindowController
  data/       BoosterStore (DataStore), HudPreferences, SessionRecord
```

## Permissions

The app declares (and requests only when needed):

- `SYSTEM_ALERT_WINDOW` — HUD overlay
- `PACKAGE_USAGE_STATS` — which app is in the foreground
- `POST_NOTIFICATIONS` — HUD foreground-service notification (Android 13+)
- `ACCESS_NOTIFICATION_POLICY` — optional Do Not Disturb during a session
- `KILL_BACKGROUND_PROCESSES` — close user-selected background apps
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` — the HUD service
- `INTERNET` / `ACCESS_NETWORK_STATE` — TCP latency check
- `QUERY_ALL_PACKAGES` (plus a `<queries>` launcher intent) — list / detect games

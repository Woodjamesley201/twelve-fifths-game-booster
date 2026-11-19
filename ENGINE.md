# Engine notes (metrics / optimize / data / HUD plumbing)

Owner: Script Nathan. This is the contract `ui/` (Ui isaac) and integration (Goji) can rely on.
Everything is additive: existing constructor calls and signatures still compile.

## Metrics: `metrics/`

- `MetricsSource.snapshots()` is **one shared sampler** (~1 Hz). It starts with the first collector and stops 5 s after the last one leaves. Collect it from as many places as you like.
- `MetricsSnapshot` adds these fields (all with defaults):
  - `cpuTempSource` / `gpuTempSource`: the zone that produced the value, e.g. `thermal_zone3 (cpu-big-core0)`.
  - `batteryTempC` + `batteryTempSupport`: the battery temperature, **never** copied into the CPU or GPU fields.
  - `thermalStatus: ThermalStatusLevel?` + support: the PowerManager level, not degrees.
  - `thermalHeadroom` + support: a ratio, where 1.0 means throttling now.
  - `ramAvailableBytes`, `lowMemory`.
  - `pingTarget` (e.g. `8.8.8.8:443`) and `pingStatus: PingStatus` (OK / TIMEOUT / UNREACHABLE / UNKNOWN_HOST / NO_NETWORK / INVALID_TARGET / NOT_MEASURED). `pingMs` is set only when the status is OK. Label it `MetricsSnapshot.PING_METHOD` ("TCP connect").
- `snapshot.temperatureSummary()` returns the honest fallback chain **CPU zone → battery → thermal status**, with `source`, `label` (`CPU °C` / `BATT °C` / `THERMAL`), `celsius` (null for status), and `detail`. Use it for one "temperature" card.
- The ping target is configurable: `store.setPingTarget("host:port")` returns the parsed `PingTarget`, or null if the input is invalid. Also available: `store.resetPingTarget()` and the `store.pingTarget` flow.

## HUD: `hud/`

- `HudService.start(context): HudStartResult`: STARTED / ALREADY_RUNNING / NEEDS_OVERLAY_PERMISSION / NOT_ALLOWED_FROM_BACKGROUND / FAILED.
- Other controls: `HudService.stop(context)` and `HudService.toggle(context)`.
- `HudService.running: StateFlow<Boolean>` and `HudService.overlayVisible: StateFlow<Boolean>`. Use these instead of local `hudRunning` state, because the HUD can also be stopped from the notification or the tile.
- Notification actions: Hide/Show HUD and Stop HUD. The quick-settings tile is `HudTileService` ("Game HUD"). `HudToggleActivity` is an invisible fallback for the tile.
- Overlay window:
  - Drag anywhere to move it.
  - Drag the ◢ corner to resize the width (140–520 dp); the text scales with it.
  - Tap to toggle compact/expanded.
  - Position, width, and compact state are saved to `HudPreferences` through `store.updateHudPreferences { }`, which is atomic.
  - Setting `posX`/`posY`/`widthDp` from the settings screen moves the live window, for example to reset its position.
- `HudRows.build(snapshot, prefs)` is the pure row model the overlay renders. Compact mode shows only rows that have a real value, up to 4. Use it for the in-app preview so both views match.
- POST_NOTIFICATIONS is optional. Without it the HUD still runs, but Android hides the notification, so only the tile and the in-app toggle work.

## Optimize: `optimize/`

- `PermissionChecker(context)`:
  - `all()`, `missing()`, `isGranted(BoosterPermission)`, `state(p)`, `openSettings(ctx, p)`.
  - Each `PermissionState` carries `granted`, `title`, `reason`, and `settingsIntent`. NOTIFICATIONS also has `runtimePermission`, to request first with a RequestPermission launcher.
  - Permissions covered: OVERLAY, USAGE_ACCESS, DND_POLICY, NOTIFICATIONS.
- `ClosableApps.list(context)` is now `suspend` and runs on IO. The existing `LaunchedEffect` call still compiles.
  - It excludes system, updated-system, and persistent apps, plus our own app, launchers, IMEs, accessibility services, device admins, the live wallpaper, the default dialer/SMS app, Play services/Store, System UI, and Samsung game services.
- `OptimizerCapabilities.canKillOtherApps` is **false on Android 14+**, which includes the Tab A9 on Android 16. The platform ignores `killBackgroundProcesses` for other apps there. Show this, and offer `SettingsShortcuts.appInfo(pkg)` so the user can Force stop an app themselves.
- `SettingsShortcuts`:
  - `batterySaver()`.
  - `gameMode(ctx, gamePkg)`: opens Samsung Gaming Hub/Game Booster if one is installed, otherwise the game's App info.
  - `appInfo(pkg)` and `open(ctx, shortcut)`.
- `OptimizationProfile` adds:
  - `autoEndWhenGameLeaves` (default true). This needs usage access; the grace period is 8 s, and this app, System UI, and permission dialogs are ignored.
  - `launchGameOnStart` (default false). It is skipped when a settings screen was opened.
- `OptimizationSession` / `SessionRecord` add:
  - `afterOptimize`: the sample about 1.5 s after the start actions. `before` is the sample at start and `after` the sample at end.
  - `requestedClosePackages`, `closeSupported`, `dndRestored`, `endReason`.
  - `notes`: a plain-language log of what was and wasn't done. Show it.
- `Optimizer.endSession(reason)` is new; `endSession()` still means USER.
- If the process died mid-session, a session it left behind is restored the first time `activeSession` is collected (DND is put back and the session is recorded as RECOVERED_AFTER_RESTART).
- `GameEntry.isInstalled` is false when a manually added package was uninstalled. The library scans lazily on first observe and rescans whenever a package is added or removed.
- `store.profiles` is a `Flow<Map<String, OptimizationProfile>>`.

## Unsupported on an unrooted Android APK (shown as UNSUPPORTED, never faked)

- Other apps' FPS: only readable through SurfaceFlinger/`dumpsys gfxinfo` via root or ADB. The overlay's own frame rate is never reported.
- GPU usage, VRAM: there is no public API; it needs root or vendor sysfs, which SELinux blocks.
- GPU temperature: shown only if a GPU thermal zone is readable. Usually SELinux blocks it.
- CPU temperature: shown only if a CPU thermal zone is readable. Otherwise the battery temperature and thermal status are shown, labelled as such.
- CPU usage: `/proc/stat` is denied to apps on Android 8+. It is probed once and then reported UNSUPPORTED.
- Closing other apps: a no-op on Android 14+.
- Toggling battery saver or game mode: needs WRITE_SECURE_SETTINGS / privileged access, so the app only opens the relevant screens.

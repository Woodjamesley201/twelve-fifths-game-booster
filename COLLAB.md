# Twelve Fifths Game Booster — collaboration

This is a **new** Android app, independent of Lounge, OneOS, and Aqualux. Do not edit those trees while working here.

## Ownership

| Area | Owner |
| --- | --- |
| Project scaffold, Gradle, manifest, `MainActivity`, `BoosterApplication`, `BoosterGraph`, `Permissions`, integration in `ui/navigation/BoosterApp.kt`, APK, git/repo | **Goji** |
| `ui/` (theme, glass, screens, in-app HUD visuals, visual resources) | **Ui isaac** |
| `metrics/`, `optimize/`, `HudService` + `HudWindowController` (overlay window plumbing), `data/` | **Script Nathan** |

The live HUD is shared: Nathan owns the foreground service and `WindowManager` overlay; isaac owns the look (`ui/hud/HudOverlay.kt` for the in-app preview, and any later visual upgrade of the overlay). Keep those seams so either side can land changes without rewriting the other.

Interfaces and stub implementations already compile. Fill them in; do not invent a second, incompatible snapshot or session type.

## Architecture

Single `ComponentActivity` + `HudService` (foreground, `specialUse`). Kotlin, Jetpack Compose, minSdk 26, compile/target 34.

- `metrics.MetricsSource` emits `Flow<MetricsSnapshot>`.
- `optimize.GameLibrary`, `ForegroundAppDetector`, and `Optimizer` cover detection, session start, and restore.
- `data.BoosterStore` is a DataStore-backed store for the library, profiles, HUD prefs, and session history (before/after samples). Nathan may later split structured records into Room.

## Android limits (honest — do not paper over these)

Unprivileged Android apps cannot do everything a Windows booster can. The UI must show `MetricSupport` and must **never fabricate a number**.

| Metric / feature | What Android allows |
| --- | --- |
| Overlay HUD | Allowed after the user grants **Display over other apps** (`SYSTEM_ALERT_WINDOW`, `TYPE_APPLICATION_OVERLAY`). Draggable. Foreground service required. |
| Other apps' FPS | **Not readable** without root or an ADB/debug capture (e.g. `dumpsys gfxinfo` / SurfaceFlinger, not available to a Play-style APK). Mark `UNSUPPORTED`. The overlay's own frame pacing is **not** game FPS — never write it into `fps` or label it as FPS. |
| GPU usage / GPU VRAM | **Not readable** without root or a vendor hook. Mark `UNSUPPORTED`. |
| Temperatures | Try readable nodes under `/sys/class/thermal` (millidegrees, only when the zone type looks like CPU/GPU and the value is plausible). `PowerManager` thermal **status** and **headroom** are not degrees Celsius — do not map them onto `cpuTempC` / `gpuTempC`. Battery temperature is not CPU or GPU temperature. If nothing numeric is readable, `UNSUPPORTED`. |
| CPU usage | Reading `/proc/stat` is blocked for apps on Android 8+. Try it; if it fails or is zeros, `UNSUPPORTED`. |
| RAM | `ActivityManager.MemoryInfo` — this is real and should be `SUPPORTED`. |
| Ping | Apps cannot send ICMP. Closest legitimate alternative: a **timed TCP connect** (implemented against `8.8.8.8:443`). Label it as TCP, not ICMP. |
| Foreground game | `UsageStatsManager`, after the user grants usage access. |
| Installed games | `ApplicationInfo.CATEGORY_GAME` plus manual add. `QUERY_ALL_PACKAGES` is declared; `<queries>` for `MAIN`/`LAUNCHER` is also declared so launchable apps can be listed without it. |
| Optimization | `ActivityManager.killBackgroundProcesses` for **user-selected, non-system** packages only; Do Not Disturb when notification-policy access is granted (restored at session end); **open** battery-saver / generic settings screens (no cross-OEM “game mode” intent — do not pretend we flipped a hidden switch); launch the game via its launch intent. |
| Global HUD shortcut | Android does not give third-party apps a reliable system-wide keybind. Use the in-app toggle and the HUD notification action. |

**Never** terminate essential system processes or change critical OS settings. `PackageSafety` must keep system packages out of the close list.

## Device notes (Galaxy Tab A9 / SM-X110, Android 16, Mali)

- Do not use `RenderEffect` blur or a heavy shader stack on first draw. Glass is a gradient + translucent veil + rim (`ui/theme/Glass.kt`). The live overlay is a lightweight platform view, not a Compose blur layer.
- Declare every field before any initializer or `init` that reads it. Assign the dependency graph in declaration order (`BoosterGraph`).
- `MainActivity` paints a flat charcoal frame first; metric collection and the real UI start only after that frame.

## Filling in work

Isaac: flesh out Dashboard, Library, Optimize, HUD settings, and onboarding. Keep support labels for unsupported metrics. Stay off blur.

Nathan: tighten `AndroidMetricsSource` (thermal zoning, `/proc/stat` fallbacks), game detection edge cases, session restore, and overlay window behavior (save position, compact/expanded). Keep values honest.

Goji: wiring, Gradle, permissions, releases, repository.

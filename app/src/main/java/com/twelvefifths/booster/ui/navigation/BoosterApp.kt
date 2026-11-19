package com.twelvefifths.booster.ui.navigation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twelvefifths.booster.Permissions
import com.twelvefifths.booster.data.BoosterStore
import com.twelvefifths.booster.hud.HudService
import com.twelvefifths.booster.ui.format.HudStartText
import com.twelvefifths.booster.optimize.SettingsShortcuts
import com.twelvefifths.booster.optimize.PermissionChecker
import com.twelvefifths.booster.optimize.OptimizerCapabilities
import com.twelvefifths.booster.optimize.BoosterPermission
import com.twelvefifths.booster.metrics.PingTarget
import com.twelvefifths.booster.hud.HudStartResult
import com.twelvefifths.booster.metrics.MetricsSnapshot
import com.twelvefifths.booster.metrics.MetricsSource
import com.twelvefifths.booster.optimize.ClosableApps
import com.twelvefifths.booster.optimize.ForegroundAppDetector
import com.twelvefifths.booster.optimize.GameEntry
import com.twelvefifths.booster.optimize.GameLibrary
import com.twelvefifths.booster.optimize.OptimizationProfile
import com.twelvefifths.booster.optimize.OptimizationSession
import com.twelvefifths.booster.optimize.Optimizer
import com.twelvefifths.booster.ui.components.BoosterChrome
import com.twelvefifths.booster.ui.components.rememberMetricHistory
import com.twelvefifths.booster.ui.format.PermissionKind
import com.twelvefifths.booster.ui.screens.DashboardScreen
import com.twelvefifths.booster.ui.screens.HudSettingsScreen
import com.twelvefifths.booster.ui.screens.LibraryScreen
import com.twelvefifths.booster.ui.screens.OptimizeScreen
import com.twelvefifths.booster.ui.screens.PermissionsOnboardingScreen
import com.twelvefifths.booster.ui.theme.Charcoal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Root navigation and wiring between the ui/ screens and the metrics /
 * optimize / data interfaces.
 *
 * Owner: Goji (integration); screens themselves are Ui isaac.
 */
@Composable
fun BoosterApp(
    metricsSource: MetricsSource,
    gameLibrary: GameLibrary,
    foregroundAppDetector: ForegroundAppDetector,
    optimizer: Optimizer,
    store: BoosterStore,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Remember the cold flows so recomposition does not restart collection
    // (each snapshot would otherwise re-subscribe and resample immediately).
    val snapshotFlow = remember(metricsSource) { metricsSource.snapshots() }
    val gamesFlow = remember(gameLibrary) { gameLibrary.observeGames() }
    val foregroundFlow = remember(foregroundAppDetector) { foregroundAppDetector.observeForegroundApp() }

    val snapshot by snapshotFlow.collectAsStateWithLifecycle(MetricsSnapshot.empty())
    val games by gamesFlow.collectAsStateWithLifecycle(emptyList())
    val prefs by store.hudPreferences.collectAsStateWithLifecycle(null)
    val history by store.sessionHistory.collectAsStateWithLifecycle(emptyList())
    val activeSession by optimizer.activeSession.collectAsStateWithLifecycle(null)
    val foregroundPackage by foregroundFlow.collectAsStateWithLifecycle(null)
    val metricHistory = rememberMetricHistory(snapshot)
    val hudRunning by HudService.running.collectAsStateWithLifecycle()
    val overlayVisible by HudService.overlayVisible.collectAsStateWithLifecycle()
    val pingTarget by store.pingTarget.collectAsStateWithLifecycle(PingTarget.DEFAULT)
    val permissionChecker = remember(context) { PermissionChecker(context) }

    var destination by remember { mutableStateOf(AppDestination.Dashboard) }
    var permissionStates by remember { mutableStateOf(permissionChecker.all()) }
    var notificationAsked by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<String?>(null) }
    fun granted(p: BoosterPermission) = permissionStates.firstOrNull { it.permission == p }?.granted == true
    val overlayGranted = granted(BoosterPermission.OVERLAY)
    val usageGranted = granted(BoosterPermission.USAGE_ACCESS)
    val notificationsGranted = granted(BoosterPermission.NOTIFICATIONS)
    val dndGranted = granted(BoosterPermission.DND_POLICY)
    var selectedGame by remember { mutableStateOf<GameEntry?>(null) }
    var profile by remember { mutableStateOf(OptimizationProfile(gamePackage = "")) }
    var closable by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> permissionStates = permissionChecker.all() }

    fun refreshPermissions() {
        permissionStates = permissionChecker.all()
    }

    fun request(p: BoosterPermission) {
        val state = permissionChecker.state(p)
        val runtime = state.runtimePermission
        if (runtime != null && !state.granted && !notificationAsked) {
            // First ask uses the runtime dialog; later taps open Settings
            // (Android stops showing the dialog after repeated denials).
            notificationAsked = true
            notificationLauncher.launch(runtime)
        } else {
            permissionChecker.openSettings(context, p)
        }
    }

    fun requestPermission(kind: PermissionKind) = request(
        when (kind) {
            PermissionKind.OVERLAY -> BoosterPermission.OVERLAY
            PermissionKind.USAGE_ACCESS -> BoosterPermission.USAGE_ACCESS
            PermissionKind.DND -> BoosterPermission.DND_POLICY
            PermissionKind.NOTIFICATIONS -> BoosterPermission.NOTIFICATIONS
        },
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        gameLibrary.refreshInstalledGames()
        closable = withContext(Dispatchers.IO) { ClosableApps.list(context) }
    }

    LaunchedEffect(selectedGame, games) {
        val current = selectedGame ?: games.firstOrNull()
        if (selectedGame == null && current != null) {
            selectedGame = current
        }
        val pkg = current?.packageName ?: return@LaunchedEffect
        if (profile.gamePackage != pkg) profile = store.loadProfile(pkg)
    }

    val loadedPrefs = prefs
    if (loadedPrefs == null) {
        // DataStore not read yet: keep the flat charcoal frame instead of
        // flashing onboarding for returning users.
        Box(Modifier.fillMaxSize().background(Charcoal.Night))
        return
    }

    if (!loadedPrefs.onboardingDone) {
        PermissionsOnboardingScreen(
            permissions = permissionStates,
            onRequest = { request(it) },
            onContinue = { scope.launch { store.setOnboardingDone(true) } },
        )
        return
    }

    fun startHud() {
        val result = HudService.start(context)
        banner = HudStartText.message(result)
        if (result == HudStartResult.NEEDS_OVERLAY_PERMISSION) request(BoosterPermission.OVERLAY)
    }

    fun toggleHud() {
        if (hudRunning) HudService.stop(context) else startHud()
    }

    suspend fun startOptimize(game: GameEntry): Result<OptimizationSession> {
        val ready = if (profile.gamePackage == game.packageName) profile
        else store.loadProfile(game.packageName)
        val result = optimizer.startSession(game, ready.copy(gamePackage = game.packageName))
        if (result.isSuccess) gameLibrary.markPlayed(game.packageName)
        return result
    }

    val canKillOtherApps = OptimizerCapabilities.canKillOtherApps
    val openAppInfo: (String) -> Unit = { pkg -> SettingsShortcuts.open(context, SettingsShortcuts.appInfo(pkg)) }
    val shortcuts = remember(selectedGame?.packageName) {
        val pkg = selectedGame?.packageName
        listOfNotNull(
            SettingsShortcuts.batterySaver(),
            SettingsShortcuts.gameMode(context, pkg),
            pkg?.let { SettingsShortcuts.appInfo(it, title = "Game app info") },
        ).distinctBy { it.id }
    }

    BoosterChrome(
        destination = destination,
        onNavigate = { destination = it },
        message = banner,
        onDismissMessage = { banner = null },
    ) { dest ->
        val body = Modifier.fillMaxSize()
        when (dest) {
            AppDestination.Onboarding, AppDestination.Permissions -> PermissionsOnboardingScreen(
                permissions = permissionStates,
                onRequest = { request(it) },
                onContinue = null,
                modifier = body,
            )
            AppDestination.Dashboard -> DashboardScreen(
                snapshot = snapshot,
                metricHistory = metricHistory,
                games = games,
                sessions = history,
                activeSession = activeSession,
                hudRunning = hudRunning,
                onToggleHud = { toggleHud() },
                onOpenHudSettings = { destination = AppDestination.HudSettings },
                onOptimizeLast = {
                    val last = games.maxByOrNull { it.lastPlayedMs ?: 0L } ?: games.firstOrNull()
                    if (last != null) {
                        selectedGame = last
                        destination = AppDestination.Optimize
                    }
                },
                onGrantPermission = { requestPermission(it) },
                modifier = body,
            )
            AppDestination.Library -> LibraryScreen(
                games = games,
                launchableApps = closable,
                closableApps = closable,
                dndGranted = dndGranted,
                activeSession = activeSession,
                onRefresh = {
                    gameLibrary.refreshInstalledGames()
                    closable = withContext(Dispatchers.IO) { ClosableApps.list(context) }
                },
                onAddManual = { pkg, name -> scope.launch { gameLibrary.addGameManually(pkg, name) } },
                onRemoveManual = { pkg -> scope.launch { gameLibrary.removeManualGame(pkg) } },
                onLaunch = { game ->
                    val ok = gameLibrary.launchGame(game.packageName)
                    if (ok) scope.launch { gameLibrary.markPlayed(game.packageName) }
                    ok
                },
                onBoost = { game ->
                    selectedGame = game
                    destination = AppDestination.Optimize
                },
                loadProfile = { pkg -> store.loadProfile(pkg) },
                onSaveProfile = { next ->
                    if (profile.gamePackage == next.gamePackage) profile = next
                    scope.launch { store.saveProfile(next) }
                },
                onGrantPermission = { requestPermission(it) },
                modifier = body,
                usageGranted = usageGranted,
                canKillOtherApps = canKillOtherApps,
                onOpenAppInfo = openAppInfo,
            )
            AppDestination.Optimize -> OptimizeScreen(
                games = games,
                closableApps = closable,
                selectedGame = selectedGame,
                onSelectGame = { game -> selectedGame = game },
                profile = profile,
                onProfileChange = { next ->
                    profile = next
                    scope.launch { store.saveProfile(next) }
                },
                activeSession = activeSession,
                dndGranted = dndGranted,
                usageGranted = usageGranted,
                foregroundPackage = foregroundPackage,
                onOptimize = { game -> startOptimize(game) },
                onRestore = { optimizer.endSession() },
                canKillOtherApps = canKillOtherApps,
                shortcuts = shortcuts,
                onOpenShortcut = { sc ->
                    if (!SettingsShortcuts.open(context, sc)) banner = "Couldn't open ${sc.title}."
                },
                onOpenAppInfo = openAppInfo,
                onLaunch = { game ->
                    val ok = gameLibrary.launchGame(game.packageName)
                    if (ok) scope.launch { gameLibrary.markPlayed(game.packageName) }
                    ok
                },
                onGrantPermission = { requestPermission(it) },
                modifier = body,
            )
            AppDestination.HudSettings -> HudSettingsScreen(
                prefs = loadedPrefs,
                snapshot = snapshot,
                metricHistory = metricHistory,
                overlayGranted = overlayGranted,
                notificationsGranted = notificationsGranted,
                hudRunning = hudRunning,
                overlayVisible = overlayVisible,
                pingTarget = pingTarget.label,
                onPrefsChange = { next ->
                    val base = loadedPrefs
                    scope.launch {
                        store.updateHudPreferences { cur ->
                            // Keep a position/width the user dragged on the live overlay
                            // unless this edit changed it.
                            val moved = next.posX != base.posX || next.posY != base.posY
                            val resized = next.widthDp != base.widthDp
                            val compactChanged = next.compact != base.compact
                            next.copy(
                                posX = if (moved) next.posX else cur.posX,
                                posY = if (moved) next.posY else cur.posY,
                                widthDp = if (resized) next.widthDp else cur.widthDp,
                                compact = if (compactChanged) next.compact else cur.compact,
                            )
                        }
                    }
                },
                onToggleHud = { toggleHud() },
                onShowOverlay = { startHud() },
                onSetPingTarget = { input -> store.setPingTarget(input) != null },
                onResetPingTarget = { scope.launch { store.resetPingTarget() } },
                onGrantPermission = { requestPermission(it) },
                modifier = body,
            )
        }
    }
}

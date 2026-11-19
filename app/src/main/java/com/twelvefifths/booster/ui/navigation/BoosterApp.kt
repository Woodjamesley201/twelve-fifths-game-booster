package com.twelvefifths.booster.ui.navigation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twelvefifths.booster.Permissions
import com.twelvefifths.booster.data.BoosterStore
import com.twelvefifths.booster.data.HudPreferences
import com.twelvefifths.booster.hud.HudService
import com.twelvefifths.booster.metrics.MetricsSnapshot
import com.twelvefifths.booster.metrics.MetricsSource
import com.twelvefifths.booster.optimize.ClosableApps
import com.twelvefifths.booster.optimize.ForegroundAppDetector
import com.twelvefifths.booster.optimize.GameEntry
import com.twelvefifths.booster.optimize.GameLibrary
import com.twelvefifths.booster.optimize.OptimizationProfile
import com.twelvefifths.booster.optimize.Optimizer
import com.twelvefifths.booster.ui.screens.DashboardScreen
import com.twelvefifths.booster.ui.screens.HudSettingsScreen
import com.twelvefifths.booster.ui.screens.LibraryScreen
import com.twelvefifths.booster.ui.screens.OptimizeScreen
import com.twelvefifths.booster.ui.screens.PermissionsOnboardingScreen
import com.twelvefifths.booster.ui.theme.Charcoal
import kotlinx.coroutines.launch

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

    val snapshot by metricsSource.snapshots().collectAsStateWithLifecycle(MetricsSnapshot.empty())
    val games by gameLibrary.observeGames().collectAsStateWithLifecycle(emptyList())
    val prefs by store.hudPreferences.collectAsStateWithLifecycle(HudPreferences())
    val history by store.sessionHistory.collectAsStateWithLifecycle(emptyList())
    val activeSession by optimizer.activeSession.collectAsStateWithLifecycle(null)
    val foregroundPackage by foregroundAppDetector.observeForegroundApp()
        .collectAsStateWithLifecycle(null)

    var destination by remember { mutableStateOf(AppDestination.Dashboard) }
    var overlayGranted by remember { mutableStateOf(Permissions.canDrawOverlays(context)) }
    var usageGranted by remember { mutableStateOf(foregroundAppDetector.hasUsageAccess()) }
    var notificationsGranted by remember { mutableStateOf(Permissions.hasNotificationPermission(context)) }
    var dndGranted by remember { mutableStateOf(Permissions.hasDndAccess(context)) }
    var hudRunning by remember { mutableStateOf(false) }
    var selectedGame by remember { mutableStateOf<GameEntry?>(null) }
    var profile by remember { mutableStateOf(OptimizationProfile(gamePackage = "")) }
    var closable by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsGranted = granted }

    fun refreshPermissions() {
        overlayGranted = Permissions.canDrawOverlays(context)
        usageGranted = foregroundAppDetector.hasUsageAccess()
        notificationsGranted = Permissions.hasNotificationPermission(context)
        dndGranted = Permissions.hasDndAccess(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        gameLibrary.refreshInstalledGames()
        closable = ClosableApps.list(context)
    }

    LaunchedEffect(selectedGame, games) {
        val current = selectedGame ?: games.firstOrNull()
        if (selectedGame == null && current != null) {
            selectedGame = current
        }
        val pkg = current?.packageName ?: return@LaunchedEffect
        profile = store.loadProfile(pkg)
    }

    val background = Brush.verticalGradient(
        colors = listOf(Charcoal.Night, Color(0xFF10131C), Charcoal.Night),
    )

    if (!prefs.onboardingDone) {
        Box(Modifier.fillMaxSize().background(background)) {
            PermissionsOnboardingScreen(
                overlayGranted = overlayGranted,
                usageGranted = usageGranted,
                notificationsGranted = notificationsGranted,
                dndGranted = dndGranted,
                onRequestOverlay = { Permissions.openOverlaySettings(context) },
                onRequestUsage = { Permissions.openUsageAccessSettings(context) },
                onRequestNotifications = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onRequestDnd = { Permissions.openDndAccessSettings(context) },
                onContinue = { scope.launch { store.setOnboardingDone(true) } },
            )
        }
        return
    }

    fun toggleHud() {
        if (hudRunning) {
            HudService.stop(context)
            hudRunning = false
        } else {
            if (!overlayGranted) {
                Permissions.openOverlaySettings(context)
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
            HudService.start(context)
            hudRunning = true
        }
    }

    fun startOptimize(game: GameEntry) {
        scope.launch {
            val ready = if (profile.gamePackage == game.packageName) profile
            else store.loadProfile(game.packageName)
            optimizer.startSession(game, ready.copy(gamePackage = game.packageName))
            gameLibrary.markPlayed(game.packageName)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(background),
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = Charcoal.NightRaised.copy(alpha = 0.96f)) {
                barItem(AppDestination.Dashboard, "Dashboard", Icons.Outlined.Home, destination) {
                    destination = it
                }
                barItem(AppDestination.Library, "Library", Icons.Outlined.SportsEsports, destination) {
                    destination = it
                }
                barItem(AppDestination.Optimize, "Optimize", Icons.Outlined.Speed, destination) {
                    destination = it
                }
                barItem(AppDestination.HudSettings, "HUD", Icons.Outlined.Tune, destination) {
                    destination = it
                }
            }
        },
    ) { padding ->
        val body = Modifier
            .fillMaxSize()
            .background(background)
            .padding(padding)
        when (destination) {
            AppDestination.Onboarding -> Unit
            AppDestination.Dashboard -> DashboardScreen(
                snapshot = snapshot,
                games = games,
                history = history,
                activeSession = activeSession,
                onOpenHudSettings = { destination = AppDestination.HudSettings },
                onToggleHud = { toggleHud() },
                hudRunning = hudRunning,
                onOptimizeLast = {
                    val last = games.maxByOrNull { it.lastPlayedMs ?: 0L } ?: games.firstOrNull()
                    if (last != null) startOptimize(last)
                },
                modifier = body,
            )
            AppDestination.Library -> LibraryScreen(
                games = games,
                onRefresh = { scope.launch { gameLibrary.refreshInstalledGames() } },
                onAddManual = { pkg, name -> scope.launch { gameLibrary.addGameManually(pkg, name) } },
                onRemoveManual = { pkg -> scope.launch { gameLibrary.removeManualGame(pkg) } },
                onLaunch = { game ->
                    if (gameLibrary.launchGame(game.packageName)) {
                        scope.launch { gameLibrary.markPlayed(game.packageName) }
                    }
                },
                modifier = body,
            )
            AppDestination.Optimize -> OptimizeScreen(
                games = games,
                closablePackages = closable,
                selectedGame = selectedGame,
                onSelectGame = { game ->
                    selectedGame = game
                    scope.launch { profile = store.loadProfile(game.packageName) }
                },
                profile = profile,
                onProfileChange = { next ->
                    profile = next
                    scope.launch { store.saveProfile(next) }
                },
                activeSession = activeSession,
                usageAccessGranted = usageGranted,
                foregroundPackage = foregroundPackage,
                dndAccessGranted = dndGranted,
                onStart = { selectedGame?.let { startOptimize(it) } },
                onEnd = { scope.launch { optimizer.endSession() } },
                onOpenUsageAccess = { Permissions.openUsageAccessSettings(context) },
                onOpenDndAccess = { Permissions.openDndAccessSettings(context) },
                modifier = body,
            )
            AppDestination.HudSettings -> HudSettingsScreen(
                prefs = prefs,
                snapshot = snapshot,
                overlayGranted = overlayGranted,
                hudRunning = hudRunning,
                onPrefsChange = { next -> scope.launch { store.setHudPreferences(next) } },
                onRequestOverlay = { Permissions.openOverlaySettings(context) },
                onToggleHud = { toggleHud() },
                modifier = body,
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.barItem(
    dest: AppDestination,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    current: AppDestination,
    onClick: (AppDestination) -> Unit,
) {
    NavigationBarItem(
        selected = current == dest,
        onClick = { onClick(dest) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Charcoal.NeonBlue,
            selectedTextColor = Charcoal.NeonBlue,
            indicatorColor = Charcoal.NeonBlueSoft,
            unselectedIconColor = Charcoal.TextMuted,
            unselectedTextColor = Charcoal.TextMuted,
        ),
    )
}

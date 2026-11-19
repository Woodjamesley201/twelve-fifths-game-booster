package com.twelvefifths.booster.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twelvefifths.booster.optimize.GameEntry
import com.twelvefifths.booster.optimize.LaunchableApp
import com.twelvefifths.booster.ui.format.AddGamePicker
import com.twelvefifths.booster.optimize.OptimizationProfile
import com.twelvefifths.booster.optimize.OptimizationSession
import com.twelvefifths.booster.ui.components.AppIcon
import com.twelvefifths.booster.ui.components.GlassDialog
import com.twelvefifths.booster.ui.components.ProfileEditor
import com.twelvefifths.booster.ui.components.ScreenTitle
import com.twelvefifths.booster.ui.components.StatusPill
import com.twelvefifths.booster.ui.format.MetricFormat
import com.twelvefifths.booster.optimize.BoosterPermission
import com.twelvefifths.booster.ui.theme.BoosterTypography
import com.twelvefifths.booster.ui.theme.Charcoal
import com.twelvefifths.booster.ui.theme.GlassPanel
import com.twelvefifths.booster.ui.theme.PrimaryButton
import com.twelvefifths.booster.ui.theme.SecondaryButton
import kotlinx.coroutines.launch

/**
 * Game library: detected (CATEGORY_GAME) + manually added games with icons,
 * last played, per-game profile editing, Boost and Launch.
 *
 * Owner: Ui isaac.
 */
@Composable
fun LibraryScreen(
    games: List<GameEntry>,
    loadLaunchableApps: suspend () -> List<LaunchableApp>,
    closableApps: List<Pair<String, String>>,
    dndGranted: Boolean,
    activeSession: OptimizationSession?,
    onRefresh: suspend () -> Unit,
    onAddManual: (packageName: String, displayName: String) -> Unit,
    onRemoveManual: (packageName: String) -> Unit,
    onLaunch: (GameEntry) -> Boolean,
    onBoost: (GameEntry) -> Unit,
    loadProfile: suspend (String) -> OptimizationProfile,
    onSaveProfile: (OptimizationProfile) -> Unit,
    onGrantPermission: (BoosterPermission?) -> Unit,
    modifier: Modifier = Modifier,
    usageGranted: Boolean = true,
    canKillOtherApps: Boolean = true,
    onOpenAppInfo: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<GameEntry?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val now = remember(games) { System.currentTimeMillis() }

    fun refresh() {
        if (refreshing) return
        scope.launch {
            refreshing = true
            try {
                onRefresh()
            } finally {
                refreshing = false
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ScreenTitle(
                    title = "Library",
                    subtitle = "${games.size} game(s) · detected by Android's game category, plus ones you add",
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (refreshing) {
                                CircularProgressIndicator(Modifier.size(20.dp), color = Charcoal.NeonBlue, strokeWidth = 2.dp)
                            } else {
                                IconButton(onClick = { refresh() }) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = "Rescan installed games", tint = Charcoal.TextMuted)
                                }
                            }
                            PrimaryButton("Add game", { showAdd = true }, icon = Icons.Outlined.Add)
                        }
                    },
                )
                AnimatedVisibility(message != null) {
                    GlassPanel(accent = Charcoal.Warn) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(message.orEmpty(), style = BoosterTypography.bodyLarge, modifier = Modifier.weight(1f))
                            Text("Dismiss", style = BoosterTypography.labelLarge, color = Charcoal.NeonBlue, modifier = Modifier.clickable { message = null })
                        }
                    }
                }
            }
        }
        if (games.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                GlassPanel {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No games yet", style = BoosterTypography.titleLarge)
                        Text(
                            "Only apps that declare Android's game category are detected automatically. Tap Add game to pick any installed app.",
                            style = BoosterTypography.bodyMedium,
                        )
                    }
                }
            }
        }
        items(games, key = { it.packageName }) { game ->
            GameCard(
                game = game,
                nowMs = now,
                isBoosted = activeSession?.gamePackage == game.packageName,
                boostEnabled = activeSession == null,
                onLaunch = { if (!onLaunch(game)) message = "Couldn't launch ${game.displayName}. It may have been uninstalled." },
                onBoost = { onBoost(game) },
                onEdit = { editing = game },
                onRemove = if (game.isManual) ({ onRemoveManual(game.packageName) }) else null,
                modifier = Modifier.animateItem(),
            )
        }
    }

    if (showAdd) {
        AddGameDialog(
            load = loadLaunchableApps,
            excluded = games.map { it.packageName }.toSet(),
            onAdd = { pkg, label ->
                onAddManual(pkg, label)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
    editing?.let { game ->
        ProfileDialog(
            game = game,
            closableApps = closableApps,
            dndGranted = dndGranted,
            loadProfile = loadProfile,
            onSave = {
                onSaveProfile(it)
                editing = null
            },
            onGrantPermission = onGrantPermission,
            onDismiss = { editing = null },
            usageGranted = usageGranted,
            canKillOtherApps = canKillOtherApps,
            onOpenAppInfo = onOpenAppInfo,
        )
    }
}

@Composable
private fun GameCard(
    game: GameEntry,
    nowMs: Long,
    isBoosted: Boolean,
    boostEnabled: Boolean,
    onLaunch: () -> Unit,
    onBoost: () -> Unit,
    onEdit: () -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier.animateContentSize(), accent = if (isBoosted) Charcoal.NeonPurple else null) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AppIcon(game.packageName, game.displayName, size = 56.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(game.displayName, style = BoosterTypography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(MetricFormat.lastPlayed(game.lastPlayedMs, nowMs), style = BoosterTypography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(if (game.isManual) "Added by you" else "Detected", if (game.isManual) Charcoal.NeonPurple else Charcoal.NeonBlue)
                        if (!game.isInstalled) StatusPill("Not installed", Charcoal.Warn)
                        if (isBoosted) StatusPill("Boosted", Charcoal.Ok)
                    }
                }
                if (onRemove != null) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove ${game.displayName}", tint = Charcoal.TextDim)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Launch", onLaunch, Modifier.weight(1f), enabled = game.isInstalled, icon = Icons.Outlined.PlayArrow)
                SecondaryButton("Boost", onBoost, Modifier.weight(1f), enabled = boostEnabled && game.isInstalled, icon = Icons.Outlined.Bolt)
                SecondaryButton("Profile", onEdit, Modifier.weight(1f), icon = Icons.Outlined.Tune)
            }
        }
    }
}

@Composable
private fun AddGameDialog(
    load: suspend () -> List<LaunchableApp>,
    excluded: Set<String>,
    onAdd: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    // listLaunchableApps runs on Dispatchers.IO inside the engine.
    var apps by remember { mutableStateOf<List<LaunchableApp>?>(null) }
    LaunchedEffect(Unit) { apps = load() }
    val filtered = remember(apps, query, showSystem, excluded) {
        AddGamePicker.filter(apps.orEmpty(), query, showSystem, excluded)
    }
    GlassDialog(
        title = "Add a game",
        onDismiss = onDismiss,
        actions = { SecondaryButton("Close", onDismiss) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Pick any installed app you can launch. Games are listed first.", style = BoosterTypography.bodyMedium)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Charcoal.TextMuted) },
                placeholder = { Text("Search apps") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Charcoal.NeonBlue,
                    unfocusedBorderColor = Charcoal.Line,
                    cursorColor = Charcoal.NeonBlue,
                ),
            )
            com.twelvefifths.booster.ui.components.ToggleRow(
                title = "Show system apps",
                checked = showSystem,
                onCheckedChange = { showSystem = it },
            )
            if (apps == null) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = Charcoal.NeonBlue)
                }
            } else if (filtered.isEmpty()) {
                Text("No matching apps.", style = BoosterTypography.bodyMedium, modifier = Modifier.padding(8.dp))
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    val pkg = app.packageName
                    val label = app.label
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onAdd(pkg, label) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppIcon(pkg, label, size = 36.dp)
                        Column(Modifier.weight(1f)) {
                            Text(label, style = BoosterTypography.bodyLarge, maxLines = 1)
                            Text(pkg, style = BoosterTypography.bodyMedium, color = Charcoal.TextDim, maxLines = 1)
                        }
                        if (app.isGame) StatusPill("Game", Charcoal.NeonBlue)
                        if (app.isSystem) StatusPill("System", Charcoal.TextDim)
                        Icon(Icons.Outlined.Add, contentDescription = "Add $label", tint = Charcoal.NeonBlue)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileDialog(
    game: GameEntry,
    closableApps: List<Pair<String, String>>,
    dndGranted: Boolean,
    loadProfile: suspend (String) -> OptimizationProfile,
    onSave: (OptimizationProfile) -> Unit,
    onGrantPermission: (BoosterPermission?) -> Unit,
    onDismiss: () -> Unit,
    usageGranted: Boolean,
    canKillOtherApps: Boolean,
    onOpenAppInfo: (String) -> Unit,
) {
    var profile by remember(game.packageName) { mutableStateOf<OptimizationProfile?>(null) }
    LaunchedEffect(game.packageName) {
        profile = loadProfile(game.packageName).copy(gamePackage = game.packageName)
    }
    GlassDialog(
        title = "${game.displayName} profile",
        onDismiss = onDismiss,
        actions = {
            SecondaryButton("Cancel", onDismiss)
            PrimaryButton("Save", { profile?.let(onSave) }, enabled = profile != null)
        },
    ) {
        val p = profile
        if (p == null) {
            Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = Charcoal.NeonBlue)
            }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ProfileEditor(
                    profile = p,
                    onChange = { profile = it },
                    closableApps = closableApps,
                    dndGranted = dndGranted,
                    onGrantPermission = onGrantPermission,
                    usageGranted = usageGranted,
                    canKillOtherApps = canKillOtherApps,
                    onOpenAppInfo = onOpenAppInfo,
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

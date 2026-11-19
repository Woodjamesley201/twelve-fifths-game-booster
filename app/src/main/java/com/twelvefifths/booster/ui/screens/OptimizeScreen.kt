package com.twelvefifths.booster.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twelvefifths.booster.optimize.GameEntry
import com.twelvefifths.booster.optimize.OptimizationProfile
import com.twelvefifths.booster.optimize.OptimizationSession
import com.twelvefifths.booster.optimize.SessionEndReason
import com.twelvefifths.booster.optimize.SettingsShortcut
import com.twelvefifths.booster.ui.components.AppIcon
import com.twelvefifths.booster.ui.components.ProfileEditor
import com.twelvefifths.booster.ui.components.ScreenTitle
import com.twelvefifths.booster.ui.components.SectionHeader
import com.twelvefifths.booster.ui.components.StatusPill
import com.twelvefifths.booster.ui.format.Metric
import com.twelvefifths.booster.ui.format.MetricFormat
import com.twelvefifths.booster.ui.format.OptimizePlan
import com.twelvefifths.booster.optimize.BoosterPermission
import com.twelvefifths.booster.ui.format.SessionCompare
import com.twelvefifths.booster.ui.theme.BoosterTypography
import com.twelvefifths.booster.ui.theme.Charcoal
import com.twelvefifths.booster.ui.theme.ChoiceChip
import com.twelvefifths.booster.ui.theme.GlassPanel
import com.twelvefifths.booster.ui.theme.PillButton
import com.twelvefifths.booster.ui.theme.PrimaryButton
import com.twelvefifths.booster.ui.theme.SecondaryButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One-click Optimize. Shows exactly what will happen before it happens, the
 * progress, the real result (what was actually closed / toggled), and Restore.
 *
 * Owner: Ui isaac.
 */
private sealed interface OptimizeUiState {
    data object Idle : OptimizeUiState
    data object Working : OptimizeUiState
    data object Restoring : OptimizeUiState
    data class Failed(val message: String) : OptimizeUiState
    data class Restored(val session: OptimizationSession) : OptimizeUiState
}

@Composable
fun OptimizeScreen(
    games: List<GameEntry>,
    closableApps: List<Pair<String, String>>,
    selectedGame: GameEntry?,
    onSelectGame: (GameEntry) -> Unit,
    profile: OptimizationProfile,
    onProfileChange: (OptimizationProfile) -> Unit,
    activeSession: OptimizationSession?,
    dndGranted: Boolean,
    usageGranted: Boolean,
    foregroundPackage: String?,
    onOptimize: suspend (GameEntry) -> Result<OptimizationSession>,
    onRestore: suspend () -> Result<OptimizationSession>,
    onLaunch: (GameEntry) -> Boolean,
    onGrantPermission: (BoosterPermission?) -> Unit,
    modifier: Modifier = Modifier,
    canKillOtherApps: Boolean = true,
    shortcuts: List<SettingsShortcut> = emptyList(),
    onOpenShortcut: (SettingsShortcut) -> Unit = {},
    onOpenAppInfo: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf<OptimizeUiState>(OptimizeUiState.Idle) }
    val labelOf: (String) -> String = { pkg ->
        closableApps.firstOrNull { it.first == pkg }?.second
            ?: games.firstOrNull { it.packageName == pkg }?.displayName
            ?: pkg
    }
    val editingProfile = selectedGame != null && profile.gamePackage == selectedGame.packageName
    val plan = OptimizePlan.build(
        profile = if (editingProfile) profile else OptimizationProfile(gamePackage = selectedGame?.packageName.orEmpty()),
        closablePackages = closableApps.map { it.first }.toSet(),
        dndGranted = dndGranted,
        labelOf = labelOf,
        canKillOtherApps = canKillOtherApps,
        usageGranted = usageGranted,
    )

    fun optimize() {
        val game = selectedGame ?: return
        if (ui == OptimizeUiState.Working) return
        scope.launch {
            ui = OptimizeUiState.Working
            val result = onOptimize(game)
            ui = result.fold(
                onSuccess = { OptimizeUiState.Idle },
                onFailure = { OptimizeUiState.Failed(it.message ?: "Optimize failed") },
            )
        }
    }

    fun restore() {
        scope.launch {
            ui = OptimizeUiState.Restoring
            val result = onRestore()
            ui = result.fold(
                onSuccess = { OptimizeUiState.Restored(it) },
                onFailure = { OptimizeUiState.Failed(it.message ?: "Restore failed") },
            )
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth.value >= 760f
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ScreenTitle(
                title = "Optimize",
                subtitle = "Honest, reversible session tweaks · nothing hidden, nothing system-level",
                trailing = {
                    if (usageGranted) {
                        val fg = foregroundPackage?.let(labelOf)
                        StatusPill(fg?.let { "Foreground: $it" } ?: "Foreground: unknown", Charcoal.NeonCyan)
                    } else {
                        PillButton("Grant usage access", { onGrantPermission(BoosterPermission.USAGE_ACCESS) })
                    }
                },
            )

            GamePicker(games, selectedGame, onSelectGame, locked = activeSession != null)

            val left: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AnimatedContent(
                        targetState = when {
                            activeSession != null && ui != OptimizeUiState.Restoring -> "active"
                            else -> "launch"
                        },
                        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() },
                        label = "optimizeState",
                    ) { key ->
                        if (key == "active" && activeSession != null) {
                            ActiveSessionCard(
                                session = activeSession,
                                gameName = labelOf(activeSession.gamePackage),
                                labelOf = labelOf,
                                restoring = ui == OptimizeUiState.Restoring,
                                onRestore = { restore() },
                                onLaunch = {
                                    val g = games.firstOrNull { it.packageName == activeSession.gamePackage }
                                    if (g == null || !onLaunch(g)) ui = OptimizeUiState.Failed("Couldn't launch the game.")
                                },
                            )
                        } else {
                            BoostButtonCard(
                                game = selectedGame,
                                working = ui == OptimizeUiState.Working || ui == OptimizeUiState.Restoring,
                                restoring = ui == OptimizeUiState.Restoring,
                                onOptimize = { optimize() },
                            )
                        }
                    }
                    when (val s = ui) {
                        is OptimizeUiState.Failed -> GlassPanel(accent = Charcoal.Danger) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(s.message, style = BoosterTypography.bodyLarge, color = Charcoal.Danger, modifier = Modifier.weight(1f))
                                Text("Dismiss", style = BoosterTypography.labelLarge, color = Charcoal.NeonBlue, modifier = Modifier.clickable { ui = OptimizeUiState.Idle })
                            }
                        }
                        is OptimizeUiState.Restored -> RestoredCard(s.session, labelOf(s.session.gamePackage)) { ui = OptimizeUiState.Idle }
                        else -> Unit
                    }
                }
            }
            val right: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PlanCard(plan)
                    if (shortcuts.isNotEmpty()) ShortcutsCard(shortcuts, onOpenShortcut)
                    if (selectedGame != null && editingProfile) {
                        GlassPanel {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                SectionHeader("Profile for ${selectedGame.displayName}", subtitle = "Saved per game")
                                Spacer(Modifier.height(8.dp))
                                ProfileEditor(
                                    profile = profile,
                                    onChange = onProfileChange,
                                    closableApps = closableApps,
                                    dndGranted = dndGranted,
                                    onGrantPermission = onGrantPermission,
                                    usageGranted = usageGranted,
                                    canKillOtherApps = canKillOtherApps,
                                    onOpenAppInfo = onOpenAppInfo,
                                )
                            }
                        }
                    }
                }
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    left(Modifier.weight(0.9f))
                    right(Modifier.weight(1.1f))
                }
            } else {
                left(Modifier.fillMaxWidth())
                right(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun GamePicker(
    games: List<GameEntry>,
    selected: GameEntry?,
    onSelect: (GameEntry) -> Unit,
    locked: Boolean,
) {
    if (games.isEmpty()) {
        GlassPanel {
            Text(
                "Add a game in the Library first, then come back to boost it.",
                style = BoosterTypography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        games.forEach { g ->
            ChoiceChip(
                text = g.displayName,
                selected = selected?.packageName == g.packageName,
                onClick = { if (!locked) onSelect(g) },
            )
        }
    }
}

@Composable
private fun BoostButtonCard(
    game: GameEntry?,
    working: Boolean,
    restoring: Boolean,
    onOptimize: () -> Unit,
) {
    GlassPanel(accent = Charcoal.NeonBlue) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BoostOrb(enabled = game != null && !working, working = working, onClick = onOptimize)
            Text(
                when {
                    restoring -> "Restoring…"
                    working -> "Optimizing…"
                    game == null -> "Pick a game"
                    else -> "Optimize for ${game.displayName}"
                },
                style = BoosterTypography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                "Takes a before snapshot, applies only the steps listed in the plan, and keeps a record for comparison.",
                style = BoosterTypography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Large gradient orb. The sweeping ring only animates while [working]. */
@Composable
private fun BoostOrb(enabled: Boolean, working: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(168.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Charcoal.NeonBlue.copy(alpha = 0.22f), Charcoal.NeonPurple.copy(alpha = 0.22f))), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (working) {
            val t = rememberInfiniteTransition(label = "orb")
            val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
            Canvas(Modifier.size(168.dp)) {
                drawArc(
                    brush = Brush.linearGradient(listOf(Charcoal.NeonBlue, Charcoal.NeonPurple)),
                    startAngle = angle,
                    sweepAngle = 110f,
                    useCenter = false,
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        } else {
            Canvas(Modifier.size(168.dp)) {
                drawCircle(
                    brush = Brush.linearGradient(listOf(Charcoal.NeonBlue, Charcoal.NeonPurple)),
                    style = Stroke(width = 2.dp.toPx()),
                    alpha = if (enabled) 1f else 0.35f,
                )
            }
        }
        Box(
            Modifier
                .size(118.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(Charcoal.NeonBlue, Charcoal.NeonPurple)),
                    CircleShape,
                    alpha = if (enabled || working) 1f else 0.4f,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (working) "…" else "BOOST", style = BoosterTypography.titleLarge, color = Color.White)
        }
    }
}

@Composable
private fun PlanCard(plan: List<OptimizePlan.Step>) {
    GlassPanel {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader("What Optimize will do", subtitle = "Exactly these steps, nothing else")
            plan.forEach { step ->
                val (icon, tint) = when (step.kind) {
                    OptimizePlan.Kind.DO -> Icons.Outlined.CheckCircle to Charcoal.Ok
                    OptimizePlan.Kind.SKIP -> Icons.Outlined.RemoveCircleOutline to Charcoal.TextDim
                    OptimizePlan.Kind.NEVER -> Icons.Outlined.Block to Charcoal.TextMuted
                }
                PlanLine(icon, tint, step.title, step.detail)
            }
        }
    }
}

@Composable
private fun ShortcutsCard(shortcuts: List<SettingsShortcut>, onOpen: (SettingsShortcut) -> Unit) {
    GlassPanel {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader("Shortcuts", subtitle = "Open the system screens yourself; nothing is switched for you")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                shortcuts.forEach { sc -> SecondaryButton(sc.title, { onOpen(sc) }, icon = Icons.AutoMirrored.Outlined.OpenInNew) }
            }
            shortcuts.forEach { sc ->
                Text("${sc.title}: ${sc.description}", style = BoosterTypography.bodyMedium, color = Charcoal.TextDim)
            }
        }
    }
}

@Composable
private fun PlanLine(icon: ImageVector, tint: Color, title: String, detail: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = BoosterTypography.bodyLarge)
            if (detail != null) Text(detail, style = BoosterTypography.bodyMedium, color = Charcoal.TextDim)
        }
    }
}

@Composable
private fun ActiveSessionCard(
    session: OptimizationSession,
    gameName: String,
    labelOf: (String) -> String,
    restoring: Boolean,
    onRestore: () -> Unit,
    onLaunch: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    GlassPanel(accent = Charcoal.NeonPurple) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AppIcon(session.gamePackage, gameName, size = 52.dp)
                Column(Modifier.weight(1f)) {
                    Text(gameName, style = BoosterTypography.titleLarge)
                    Text("Boosted for ${MetricFormat.duration(now - session.startedAtMs)}", style = BoosterTypography.bodyMedium)
                }
                StatusPill("Active", Charcoal.Ok)
            }
            Text("WHAT ACTUALLY HAPPENED", style = BoosterTypography.labelSmall)
            if (session.notes.isNotEmpty()) {
                session.notes.forEach { note -> PlanLine(Icons.Outlined.CheckCircle, Charcoal.NeonBlue, note, null) }
            } else {
                PlanLine(
                    Icons.Outlined.CheckCircle,
                    if (session.closedPackages.isEmpty()) Charcoal.TextDim else Charcoal.Ok,
                    when {
                        !session.closeSupported && session.requestedClosePackages.isNotEmpty() ->
                            "Closing apps isn't available on this Android version"
                        session.closedPackages.isEmpty() -> "No apps were closed"
                        else -> "Asked Android to close ${session.closedPackages.size} background app(s)"
                    },
                    session.closedPackages.joinToString { labelOf(it) }.ifEmpty { null },
                )
                PlanLine(
                    Icons.Outlined.CheckCircle,
                    if (session.dndWasEnabledByUs) Charcoal.Ok else Charcoal.TextDim,
                    if (session.dndWasEnabledByUs) "Do Not Disturb turned on" else "Do Not Disturb not changed",
                    if (session.dndWasEnabledByUs) "Restored when you tap Restore." else null,
                )
            }
            val before = session.before
            val ram = before?.let { MetricFormat.text(MetricFormat.display(Metric.RAM, it)) } ?: "Not captured"
            PlanLine(Icons.Outlined.CheckCircle, Charcoal.NeonBlue, "Before snapshot", "RAM $ram")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Launch game", onLaunch, Modifier.weight(1f), icon = Icons.Outlined.PlayArrow)
                SecondaryButton(if (restoring) "Restoring…" else "Restore", onRestore, Modifier.weight(1f), enabled = !restoring, icon = Icons.Outlined.Restore)
            }
        }
    }
}

@Composable
private fun RestoredCard(session: OptimizationSession, gameName: String, onDismiss: () -> Unit) {
    val rows = Metric.entries.mapNotNull { SessionCompare.row(it, session.before, session.after) }
    GlassPanel(accent = Charcoal.Ok) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Restored · $gameName", style = BoosterTypography.titleMedium, modifier = Modifier.weight(1f))
                Text("Done", style = BoosterTypography.labelLarge, color = Charcoal.NeonBlue, modifier = Modifier.clickable(onClick = onDismiss))
            }
            Text(
                "Session lasted ${MetricFormat.duration((session.endedAtMs ?: session.startedAtMs) - session.startedAtMs)}" +
                    when (session.endReason) {
                        SessionEndReason.GAME_LEFT_FOREGROUND -> " · ended when the game left the screen."
                        SessionEndReason.RECOVERED_AFTER_RESTART -> " · restored after the app restarted."
                        else -> "."
                    },
                style = BoosterTypography.bodyMedium,
            )
            session.notes.forEach { note ->
                Text("• $note", style = BoosterTypography.bodyMedium, color = Charcoal.TextMuted)
            }
            Text("START → AT RESTORE", style = BoosterTypography.labelSmall)
            rows.forEach { r ->
                Row {
                    Text(r.metric.shortLabel, style = BoosterTypography.labelLarge, color = Charcoal.TextMuted, modifier = Modifier.weight(1f))
                    Text("${r.before} → ${r.after}", style = BoosterTypography.labelLarge, modifier = Modifier.weight(2f))
                    Text(r.delta ?: "—", style = BoosterTypography.labelLarge, color = when (r.improved) {
                        true -> Charcoal.Ok
                        false -> Charcoal.Warn
                        null -> Charcoal.TextDim
                    }, modifier = Modifier.weight(0.8f))
                }
            }
        }
    }
}

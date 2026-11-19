package com.twelvefifths.booster.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.twelvefifths.booster.optimize.GameEntry
import com.twelvefifths.booster.optimize.OptimizationProfile
import com.twelvefifths.booster.optimize.OptimizationSession
import com.twelvefifths.booster.ui.theme.BoosterTypography
import com.twelvefifths.booster.ui.theme.Charcoal
import com.twelvefifths.booster.ui.theme.GlassPanel
import com.twelvefifths.booster.ui.theme.PrimaryButton
import com.twelvefifths.booster.ui.theme.SecondaryButton

/**
 * One-click optimize plus per-game profile options.
 * Closable packages are supplied by the caller; system apps are never listed.
 *
 * Owner: Ui isaac.
 */
@Composable
fun OptimizeScreen(
    games: List<GameEntry>,
    closablePackages: List<Pair<String, String>>,
    selectedGame: GameEntry?,
    onSelectGame: (GameEntry) -> Unit,
    profile: OptimizationProfile,
    onProfileChange: (OptimizationProfile) -> Unit,
    activeSession: OptimizationSession?,
    usageAccessGranted: Boolean,
    foregroundPackage: String?,
    dndAccessGranted: Boolean,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenDndAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Optimize", style = BoosterTypography.displaySmall)
                Text(
                    "Closes only the background apps you tick, can turn on Do Not Disturb if you grant access, and can open battery-saver settings. System apps are never touched.",
                    style = BoosterTypography.bodyMedium,
                )
            }
        }
        item {
            GlassPanel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Active game", style = BoosterTypography.titleMedium)
                    if (!usageAccessGranted) {
                        Text("Usage access is needed to detect the game in the foreground.", style = BoosterTypography.bodyMedium)
                        SecondaryButton("Grant usage access", onOpenUsageAccess, Modifier.fillMaxWidth())
                    } else {
                        Text(
                            "Foreground: ${foregroundPackage ?: "unknown"}",
                            style = BoosterTypography.bodyMedium,
                        )
                    }
                    if (games.isEmpty()) {
                        Text("Add a game in Library first.", style = BoosterTypography.bodyMedium)
                    }
                }
            }
        }
        items(games, key = { it.packageName }) { game ->
            val selected = selectedGame?.packageName == game.packageName
            GlassPanel(modifier = Modifier.clickable { onSelectGame(game) }) {
                Column(Modifier.padding(16.dp)) {
                    Text(game.displayName, style = BoosterTypography.titleMedium, color = if (selected) Charcoal.NeonBlue else Charcoal.Text)
                    Text(if (selected) "Selected" else game.packageName, style = BoosterTypography.bodyMedium)
                }
            }
        }
        item {
            GlassPanel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Background apps to close", style = BoosterTypography.titleMedium)
                    if (closablePackages.isEmpty()) {
                        Text(
                            "No user apps available to close, or they could not be listed.",
                            style = BoosterTypography.bodyMedium,
                        )
                    }
                }
            }
        }
        items(closablePackages, key = { it.first }) { (pkg, label) ->
            val checked = pkg in profile.closeBackgroundPackages
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val next = profile.closeBackgroundPackages.toMutableSet()
                        if (checked) next.remove(pkg) else next.add(pkg)
                        onProfileChange(profile.copy(closeBackgroundPackages = next))
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { on ->
                        val next = profile.closeBackgroundPackages.toMutableSet()
                        if (on) next.add(pkg) else next.remove(pkg)
                        onProfileChange(profile.copy(closeBackgroundPackages = next))
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Charcoal.NeonBlue,
                        uncheckedColor = Charcoal.TextMuted,
                    ),
                )
                Column {
                    Text(label, style = BoosterTypography.titleMedium)
                    Text(pkg, style = BoosterTypography.bodyMedium)
                }
            }
        }
        item {
            GlassPanel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToggleRow(
                        title = "Do Not Disturb",
                        body = if (dndAccessGranted) "Priority interruptions only, restored when the session ends."
                        else "Grant notification-policy access first.",
                        checked = profile.enableDoNotDisturb && dndAccessGranted,
                        onToggle = { on ->
                            if (on && !dndAccessGranted) onOpenDndAccess()
                            onProfileChange(profile.copy(enableDoNotDisturb = on))
                        },
                    )
                    ToggleRow(
                        title = "Open battery saver settings",
                        body = "This app will not flip the switch itself.",
                        checked = profile.suggestBatterySaverSettings,
                        onToggle = { on -> onProfileChange(profile.copy(suggestBatterySaverSettings = on)) },
                    )
                    if (activeSession == null) {
                        PrimaryButton(
                            text = if (selectedGame == null) "Select a game first" else "Optimize ${selectedGame.displayName}",
                            onClick = onStart,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        SecondaryButton(
                            text = "Restore and end session",
                            onClick = onEnd,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    body: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = BoosterTypography.titleMedium)
            Text(body, style = BoosterTypography.bodyMedium)
        }
        Checkbox(
            checked = checked,
            onCheckedChange = onToggle,
            colors = CheckboxDefaults.colors(checkedColor = Charcoal.NeonBlue, uncheckedColor = Charcoal.TextMuted),
        )
    }
}

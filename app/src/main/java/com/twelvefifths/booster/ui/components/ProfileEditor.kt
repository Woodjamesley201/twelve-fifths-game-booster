package com.twelvefifths.booster.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.twelvefifths.booster.optimize.OptimizationProfile
import com.twelvefifths.booster.ui.format.PermissionKind
import com.twelvefifths.booster.ui.theme.BoosterTypography
import com.twelvefifths.booster.ui.theme.Charcoal
import com.twelvefifths.booster.ui.theme.PillButton

/**
 * Per-game optimization profile editor. Copy states exactly what each switch
 * does on Android; nothing here implies hidden system tweaks.
 *
 * [closableApps] must already exclude system packages (ClosableApps does).
 *
 * Owner: Ui isaac.
 */
@Composable
fun ProfileEditor(
    profile: OptimizationProfile,
    onChange: (OptimizationProfile) -> Unit,
    closableApps: List<Pair<String, String>>,
    dndGranted: Boolean,
    onGrantPermission: (PermissionKind) -> Unit,
    modifier: Modifier = Modifier,
    maxAppRows: Int = Int.MAX_VALUE,
) {
    Column(modifier.fillMaxWidth()) {
        ToggleRow(
            title = "Do Not Disturb during the session",
            subtitle = if (dndGranted) "Turned on at start, restored when you tap Restore."
            else "Needs Do Not Disturb access.",
            checked = profile.enableDoNotDisturb && dndGranted,
            enabled = dndGranted,
            onCheckedChange = { onChange(profile.copy(enableDoNotDisturb = it)) },
            trailingExtra = if (!dndGranted) {
                { PillButton("Grant permission", { onGrantPermission(PermissionKind.DND) }) }
            } else {
                null
            },
        )
        ToggleRow(
            title = "Open battery saver settings",
            subtitle = "Opens the system screen so you can switch it yourself.",
            checked = profile.suggestBatterySaverSettings,
            onCheckedChange = { onChange(profile.copy(suggestBatterySaverSettings = it)) },
        )
        ToggleRow(
            title = "Open game settings",
            subtitle = "Android has no shared game-mode switch; this opens a nearby Settings screen.",
            checked = profile.suggestGameModeSettings,
            onCheckedChange = { onChange(profile.copy(suggestGameModeSettings = it)) },
        )
        Spacer(Modifier.height(10.dp))
        val candidates = closableApps.filter { it.first != profile.gamePackage }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Close these background apps", style = BoosterTypography.titleMedium)
                Text(
                    "Only apps you tick. System apps are never listed. Android may restart some of them.",
                    style = BoosterTypography.bodyMedium,
                    color = Charcoal.TextDim,
                )
            }
            Text("${profile.closeBackgroundPackages.count { p -> candidates.any { it.first == p } }} selected", style = BoosterTypography.labelLarge, color = Charcoal.NeonBlue)
        }
        Spacer(Modifier.height(6.dp))
        if (candidates.isEmpty()) {
            Text("No closable apps found.", style = BoosterTypography.bodyMedium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            candidates.take(maxAppRows).forEach { (pkg, label) ->
                val checked = pkg in profile.closeBackgroundPackages
                CheckRow(
                    title = label,
                    subtitle = pkg,
                    checked = checked,
                    onCheckedChange = { on ->
                        val next = if (on) profile.closeBackgroundPackages + pkg else profile.closeBackgroundPackages - pkg
                        onChange(profile.copy(closeBackgroundPackages = next))
                    },
                    leading = { AppIcon(pkg, label, size = 28.dp) },
                )
            }
        }
    }
}

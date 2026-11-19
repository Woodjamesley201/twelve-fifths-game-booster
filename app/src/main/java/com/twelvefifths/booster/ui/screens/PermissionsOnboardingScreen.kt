package com.twelvefifths.booster.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.twelvefifths.booster.ui.components.AdaptiveGrid
import com.twelvefifths.booster.ui.components.StatusPill
import com.twelvefifths.booster.ui.components.gridColumns
import com.twelvefifths.booster.ui.format.PermissionKind
import com.twelvefifths.booster.ui.theme.BoosterTypography
import com.twelvefifths.booster.ui.theme.Charcoal
import com.twelvefifths.booster.ui.theme.GlassPanel
import com.twelvefifths.booster.ui.theme.PillButton
import com.twelvefifths.booster.ui.theme.PrimaryButton
import com.twelvefifths.booster.ui.theme.appBackdrop

/**
 * First-run onboarding and the later "Access" screen. Shows live status for
 * each special permission with a grant button; the host refreshes status on
 * resume so returning from Settings updates the cards.
 *
 * Owner: Ui isaac.
 */
data class PermissionItem(
    val kind: PermissionKind,
    val title: String,
    val why: String,
    val granted: Boolean,
    val required: Boolean,
    val icon: ImageVector,
    val notApplicable: Boolean = false,
)

fun permissionItems(
    overlayGranted: Boolean,
    usageGranted: Boolean,
    notificationsGranted: Boolean,
    dndGranted: Boolean,
    notificationsRuntime: Boolean,
): List<PermissionItem> = listOf(
    PermissionItem(
        PermissionKind.OVERLAY, "Display over other apps",
        "Draws the HUD on top of your game.", overlayGranted, required = true, icon = Icons.Outlined.Layers,
    ),
    PermissionItem(
        PermissionKind.NOTIFICATIONS, "Notifications",
        if (notificationsRuntime) "Shows the HUD's ongoing notification with a Stop button."
        else "Not needed on this Android version.",
        notificationsGranted, required = true, icon = Icons.Outlined.Notifications,
        notApplicable = !notificationsRuntime,
    ),
    PermissionItem(
        PermissionKind.USAGE_ACCESS, "Usage access",
        "Detects which game is in the foreground. Android does not let it read other apps' FPS.",
        usageGranted, required = false, icon = Icons.Outlined.BarChart,
    ),
    PermissionItem(
        PermissionKind.DND, "Do Not Disturb access",
        "Optional: silences notifications during a boost and restores them afterwards.",
        dndGranted, required = false, icon = Icons.Outlined.DoNotDisturbOn,
    ),
)

@Composable
fun PermissionsOnboardingScreen(
    overlayGranted: Boolean,
    usageGranted: Boolean,
    notificationsGranted: Boolean,
    dndGranted: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestUsage: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestDnd: () -> Unit,
    onContinue: (() -> Unit)?,
    modifier: Modifier = Modifier,
    notificationsRuntime: Boolean = android.os.Build.VERSION.SDK_INT >= 33,
    isOnboarding: Boolean = onContinue != null,
) {
    val items = permissionItems(overlayGranted, usageGranted, notificationsGranted, dndGranted, notificationsRuntime)
    val request: (PermissionKind) -> Unit = { kind ->
        when (kind) {
            PermissionKind.OVERLAY -> onRequestOverlay()
            PermissionKind.USAGE_ACCESS -> onRequestUsage()
            PermissionKind.NOTIFICATIONS -> onRequestNotifications()
            PermissionKind.DND -> onRequestDnd()
        }
    }
    val grantedCount = items.count { it.granted }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .then(if (isOnboarding) Modifier.background(appBackdrop()).windowInsetsPadding(WindowInsets.safeDrawing) else Modifier),
        contentAlignment = Alignment.TopCenter,
    ) {
        val cols = gridColumns(maxWidth.value - 48f, minCellDp = 320f, max = 2)
        Column(
            Modifier
                .widthIn(max = 980.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = if (isOnboarding) 36.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (isOnboarding) {
                Box(
                    Modifier
                        .size(56.dp)
                        .background(Brush.linearGradient(listOf(Charcoal.NeonBlue, Charcoal.NeonPurple)), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("12/5", style = BoosterTypography.titleMedium, color = Color.White) }
                Text("Welcome to Twelve Fifths", style = BoosterTypography.headlineMedium)
                Text(
                    "A game HUD, monitor, and session optimizer that only does what Android allows. " +
                        "Grant what you want to use; you can change these later under Access.",
                    style = BoosterTypography.bodyLarge,
                    color = Charcoal.TextMuted,
                )
            } else {
                Text("Access", style = BoosterTypography.displaySmall)
                Text("Permissions refresh automatically when you return from Settings.", style = BoosterTypography.bodyMedium)
            }
            StatusPill("$grantedCount of ${items.size} granted", if (grantedCount == items.size) Charcoal.Ok else Charcoal.NeonBlue)
            AdaptiveGrid(items, columns = cols) { item, m -> PermissionCard(item, { request(item.kind) }, m) }
            GlassPanel {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("What Android does not allow", style = BoosterTypography.titleMedium)
                    Text(
                        "Reading other games' FPS, GPU load, or GPU memory needs root or an ADB capture, so those show " +
                            "\"Not available on Android\". Temperatures appear only if this tablet exposes a readable sensor.",
                        style = BoosterTypography.bodyMedium,
                    )
                }
            }
            if (onContinue != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton(
                        if (items.filter { it.required }.all { it.granted }) "Continue" else "Continue without all permissions",
                        onContinue,
                        Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PermissionCard(item: PermissionItem, onGrant: () -> Unit, modifier: Modifier) {
    GlassPanel(modifier.animateContentSize(), accent = if (item.granted) Charcoal.Ok else Charcoal.NeonPurple) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(item.icon, contentDescription = null, tint = if (item.granted) Charcoal.Ok else Charcoal.NeonBlue, modifier = Modifier.size(26.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = BoosterTypography.titleMedium)
                    Text(if (item.required) "Needed for the HUD" else "Optional", style = BoosterTypography.labelSmall)
                }
            }
            Text(item.why, style = BoosterTypography.bodyMedium)
            AnimatedContent(item.granted to item.notApplicable, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "perm") { (granted, na) ->
                when {
                    na -> StatusPill("Not required", Charcoal.TextDim)
                    granted -> StatusPill("Granted", Charcoal.Ok)
                    else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusPill("Not granted", Charcoal.Warn)
                        PillButton("Grant permission", onGrant)
                    }
                }
            }
        }
    }
}


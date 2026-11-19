package com.twelvefifths.booster.ui.screens

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.twelvefifths.booster.ui.theme.BoosterTypography
import com.twelvefifths.booster.ui.theme.Charcoal
import com.twelvefifths.booster.ui.theme.GlassPanel
import com.twelvefifths.booster.ui.theme.PrimaryButton
import com.twelvefifths.booster.ui.theme.SecondaryButton

/**
 * Setup / permissions. Each row explains why the permission exists and what
 * still will not work even after it is granted.
 *
 * Owner: Ui isaac.
 */
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
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("TWELVE FIFTHS", style = BoosterTypography.labelSmall, color = Charcoal.NeonBlue)
                Text("Set up", style = BoosterTypography.displaySmall)
                Text(
                    "This booster only uses APIs Android allows an unprivileged app. Grant what you want to use; everything else is clearly marked unsupported.",
                    style = BoosterTypography.bodyMedium,
                )
            }
        }
        item {
            PermissionCard(
                title = "Display over other apps",
                body = "Required for the always-on-top HUD. Android will not let this app read another game's FPS even after this is granted.",
                granted = overlayGranted,
                action = "Allow overlay",
                onAction = onRequestOverlay,
            )
        }
        item {
            PermissionCard(
                title = "Usage access",
                body = "Lets the booster see which app is in the foreground so it can tell which game is active.",
                granted = usageGranted,
                action = "Allow usage access",
                onAction = onRequestUsage,
            )
        }
        item {
            PermissionCard(
                title = "Notifications",
                body = "Needed on Android 13+ so the HUD can run as a foreground service.",
                granted = notificationsGranted,
                action = "Allow notifications",
                onAction = onRequestNotifications,
            )
        }
        item {
            PermissionCard(
                title = "Do Not Disturb access",
                body = "Optional. When a session starts, the booster can switch to priority interruptions and restore your previous filter when it ends.",
                granted = dndGranted,
                action = "Allow DND access",
                onAction = onRequestDnd,
            )
        }
        item {
            PrimaryButton("Continue to the booster", onContinue, Modifier.fillMaxWidth())
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    body: String,
    granted: Boolean,
    action: String,
    onAction: () -> Unit,
) {
    GlassPanel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = BoosterTypography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    if (granted) "On" else "Off",
                    style = BoosterTypography.labelSmall,
                    color = if (granted) Charcoal.Ok else Charcoal.TextDim,
                )
            }
            Text(body, style = BoosterTypography.bodyMedium)
            if (!granted) {
                SecondaryButton(action, onAction, Modifier.fillMaxWidth())
            }
        }
    }
}

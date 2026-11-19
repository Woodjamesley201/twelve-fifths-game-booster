package com.twelvefifths.booster.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.twelvefifths.booster.ui.navigation.AppDestination
import com.twelvefifths.booster.ui.theme.BoosterTypography
import com.twelvefifths.booster.ui.theme.Charcoal
import com.twelvefifths.booster.ui.theme.appBackdrop

/**
 * Adaptive app chrome: a glass navigation rail on wide/landscape tablets
 * (Tab A9 landscape is ~890dp wide), a glass bottom bar in portrait. Tab
 * changes slide + fade in the direction of travel.
 *
 * Owner: Ui isaac.
 */
private data class NavItem(val dest: AppDestination, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem(AppDestination.Dashboard, "Dashboard", Icons.Outlined.Home),
    NavItem(AppDestination.Library, "Library", Icons.Outlined.SportsEsports),
    NavItem(AppDestination.Optimize, "Optimize", Icons.Outlined.Speed),
    NavItem(AppDestination.HudSettings, "HUD", Icons.Outlined.Tune),
    NavItem(AppDestination.Permissions, "Access", Icons.Outlined.AdminPanelSettings),
)

const val RAIL_MIN_WIDTH_DP = 720

@Composable
fun BoosterChrome(
    destination: AppDestination,
    onNavigate: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    onDismissMessage: () -> Unit = {},
    content: @Composable (AppDestination) -> Unit,
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(appBackdrop()),
    ) {
        val wide = maxWidth.value >= RAIL_MIN_WIDTH_DP
        val body: @Composable (Modifier) -> Unit = { m ->
            AnimatedContent(
                targetState = destination,
                transitionSpec = {
                    val forward = targetState.ordinal >= initialState.ordinal
                    val dir = if (forward) 1 else -1
                    val enter = tween<androidx.compose.ui.unit.IntOffset>(320, easing = FastOutSlowInEasing)
                    if (wide) {
                        (slideInVertically(enter) { it / 12 * dir } + fadeIn(tween(260))) togetherWith
                            (slideOutVertically(tween(220)) { -it / 16 * dir } + fadeOut(tween(160)))
                    } else {
                        (slideInHorizontally(enter) { it / 10 * dir } + fadeIn(tween(260))) togetherWith
                            (slideOutHorizontally(tween(220)) { -it / 14 * dir } + fadeOut(tween(160)))
                    }
                },
                label = "tabs",
                modifier = m,
            ) { dest -> content(dest) }
        }
        if (wide) {
            Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                NavRail(destination, onNavigate)
                body(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                body(Modifier.weight(1f).fillMaxWidth())
                BottomBar(destination, onNavigate)
            }
        }
        MessageBanner(
            message,
            onDismissMessage,
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = if (wide) 24.dp else 96.dp, start = 24.dp, end = 24.dp),
        )
    }
}

/** Glass toast for short results (e.g. why the HUD didn't start). Auto-hides after 5 s. */
@Composable
private fun MessageBanner(message: String?, onDismiss: () -> Unit, modifier: Modifier) {
    androidx.compose.runtime.LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(5_000L)
            onDismiss()
        }
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        val shape = RoundedCornerShape(18.dp)
        Row(
            Modifier
                .widthIn(max = 560.dp)
                .clip(shape)
                .background(Charcoal.NightHigh, shape)
                .border(1.dp, Charcoal.NeonBlueSoft, shape)
                .clickable(onClick = onDismiss)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message.orEmpty(), style = BoosterTypography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(14.dp))
            Text("OK", style = BoosterTypography.labelLarge, color = Charcoal.NeonBlue)
        }
    }
}

@Composable
private fun NavRail(current: AppDestination, onNavigate: (AppDestination) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
            .width(92.dp)
            .fillMaxHeight()
            .clip(shape)
            .background(Charcoal.NightRaised.copy(alpha = 0.72f), shape)
            .border(1.dp, Charcoal.LineSoft, shape)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BrandMark()
        Spacer(Modifier.height(18.dp))
        navItems.forEach { item -> NavButton(item, current == item.dest, onNavigate, vertical = true) }
    }
}

@Composable
private fun BottomBar(current: AppDestination, onNavigate: (AppDestination) -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(shape)
            .background(Charcoal.NightRaised.copy(alpha = 0.85f), shape)
            .border(1.dp, Charcoal.LineSoft, shape)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        navItems.forEach { item -> NavButton(item, current == item.dest, onNavigate, vertical = true, modifier = Modifier.weight(1f)) }
    }
}

@Composable
private fun NavButton(
    item: NavItem,
    selected: Boolean,
    onNavigate: (AppDestination) -> Unit,
    vertical: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val bg by animateColorAsState(if (selected) Charcoal.NeonBlueFaint else Color.Transparent, label = "navBg")
    val fg by animateColorAsState(if (selected) Charcoal.NeonBlue else Charcoal.TextMuted, label = "navFg")
    Column(
        modifier
            .clip(shape)
            .background(bg, shape)
            .clickable(role = Role.Tab) { onNavigate(item.dest) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(item.icon, contentDescription = item.label, tint = fg, modifier = Modifier.size(24.dp))
        if (vertical) Text(item.label, style = BoosterTypography.labelSmall, color = fg, maxLines = 1)
    }
}

@Composable
private fun BrandMark() {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .size(44.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Charcoal.NeonBlue, Charcoal.NeonPurple)), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text("12/5", style = BoosterTypography.labelLarge, color = Color.White)
    }
}

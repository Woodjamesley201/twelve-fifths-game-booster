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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.twelvefifths.booster.optimize.GameEntry
import com.twelvefifths.booster.ui.theme.BoosterTypography
import com.twelvefifths.booster.ui.theme.Charcoal
import com.twelvefifths.booster.ui.theme.GlassPanel
import com.twelvefifths.booster.ui.theme.PrimaryButton
import com.twelvefifths.booster.ui.theme.SecondaryButton
import java.text.DateFormat
import java.util.Date

/**
 * Game library placeholder: detected games, manual add, launch, last played.
 *
 * Owner: Ui isaac.
 */
@Composable
fun LibraryScreen(
    games: List<GameEntry>,
    onRefresh: () -> Unit,
    onAddManual: (packageName: String, displayName: String) -> Unit,
    onRemoveManual: (packageName: String) -> Unit,
    onLaunch: (GameEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var packageName by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Charcoal.Text,
        unfocusedTextColor = Charcoal.Text,
        focusedBorderColor = Charcoal.NeonBlue,
        unfocusedBorderColor = Charcoal.Line,
        cursorColor = Charcoal.NeonBlue,
        focusedContainerColor = Charcoal.NightRaised,
        unfocusedContainerColor = Charcoal.NightRaised,
        focusedLabelColor = Charcoal.TextMuted,
        unfocusedLabelColor = Charcoal.TextMuted,
    )
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Library", style = BoosterTypography.displaySmall)
                Text(
                    "Games with category GAME are detected automatically. You can also add any launchable package.",
                    style = BoosterTypography.bodyMedium,
                )
            }
        }
        item {
            GlassPanel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Add a game", style = BoosterTypography.titleMedium)
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Package name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        colors = fieldColors,
                    )
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Display name (optional)") },
                        singleLine = true,
                        colors = fieldColors,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton(
                            text = "Add",
                            onClick = {
                                if (packageName.isNotBlank()) {
                                    onAddManual(packageName, displayName)
                                    packageName = ""
                                    displayName = ""
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(
                            text = "Rescan",
                            onClick = onRefresh,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        if (games.isEmpty()) {
            item {
                GlassPanel {
                    Text(
                        "No games yet. Launch a title once or add its package name.",
                        modifier = Modifier.padding(16.dp),
                        style = BoosterTypography.bodyMedium,
                    )
                }
            }
        }
        items(games, key = { it.packageName }) { game ->
            GameRow(
                game = game,
                onLaunch = { onLaunch(game) },
                onRemove = if (game.isManual) ({ onRemoveManual(game.packageName) }) else null,
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun GameRow(
    game: GameEntry,
    onLaunch: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    GlassPanel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(game.displayName, style = BoosterTypography.titleMedium)
            Text(game.packageName, style = BoosterTypography.bodyMedium)
            val played = game.lastPlayedMs?.let {
                "Last played ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))}"
            } ?: "Not played from here yet"
            Text(played, style = BoosterTypography.bodyMedium, color = Charcoal.TextDim)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Launch", onLaunch, Modifier.weight(1f))
                if (onRemove != null) {
                    SecondaryButton("Remove", onRemove, Modifier.weight(1f))
                }
            }
        }
    }
}

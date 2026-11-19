package com.twelvefifths.booster.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.twelvefifths.booster.ui.theme.BoosterTypography
import com.twelvefifths.booster.ui.theme.Charcoal
import com.twelvefifths.booster.ui.theme.glassBrush
import com.twelvefifths.booster.ui.theme.glassRim

/**
 * Dialog with an opaque charcoal + glass veil body (dialogs sit over content,
 * so the fill is near-opaque for legibility). No blur behind it.
 *
 * Owner: Ui isaac.
 */
@Composable
fun GlassDialog(
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(26.dp)
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .clip(shape)
                .background(Charcoal.NightRaised, shape)
                .background(glassBrush(), shape)
                .border(1.dp, glassRim(Charcoal.NeonBlue), shape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = BoosterTypography.titleLarge)
            Column(Modifier.weight(1f, fill = false)) { content() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, androidx.compose.ui.Alignment.End)) {
                actions()
            }
        }
    }
}

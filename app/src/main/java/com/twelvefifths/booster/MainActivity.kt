package com.twelvefifths.booster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.twelvefifths.booster.ui.navigation.BoosterApp
import com.twelvefifths.booster.ui.theme.Charcoal
import com.twelvefifths.booster.ui.theme.TwelveFifthsTheme

/**
 * Single activity. The first Compose frame is a flat charcoal fill — no blur,
 * no shaders, no metric collection — so Mali / Android 16 can settle before
 * the real UI is composed.
 *
 * Owner: Goji.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var pastFirstFrame by remember { mutableStateOf(false) }
            TwelveFifthsTheme {
                if (!pastFirstFrame) {
                    Box(Modifier.fillMaxSize().background(Charcoal.Night))
                    LaunchedEffect(Unit) {
                        withFrameNanos { }
                        pastFirstFrame = true
                    }
                } else {
                    val app = application as BoosterApplication
                    BoosterApp(
                        metricsSource = app.metricsSource,
                        gameLibrary = app.gameLibrary,
                        foregroundAppDetector = app.foregroundAppDetector,
                        optimizer = app.optimizer,
                        store = app.store,
                    )
                }
            }
        }
    }
}

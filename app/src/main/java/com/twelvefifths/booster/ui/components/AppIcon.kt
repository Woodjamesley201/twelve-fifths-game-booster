package com.twelvefifths.booster.ui.components

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.twelvefifths.booster.ui.theme.BoosterTypography
import com.twelvefifths.booster.ui.theme.Charcoal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Loads an installed app's icon off the main thread (small LRU cache) and shows
 * a monogram tile until it arrives or if the app has no icon.
 *
 * Owner: Ui isaac.
 */
private object IconCache {
    private val cache = object : android.util.LruCache<String, ImageBitmap>(64) {}

    fun get(key: String): ImageBitmap? = cache.get(key)

    fun put(key: String, value: ImageBitmap) {
        cache.put(key, value)
    }
}

private fun loadIcon(context: Context, packageName: String, px: Int): ImageBitmap? = try {
    context.packageManager.getApplicationIcon(packageName)
        .toBitmap(px, px)
        .asImageBitmap()
} catch (_: Throwable) {
    null
}

@Composable
fun AppIcon(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.toPx().roundToInt().coerceAtLeast(1) }
    val key = "$packageName@$px"
    val icon by produceState(initialValue = IconCache.get(key), key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { loadIcon(context, packageName, px) }?.also { IconCache.put(key, it) }
        }
    }
    val shape = RoundedCornerShape(size * 0.28f)
    val bmp = icon
    if (bmp != null) {
        Image(bmp, contentDescription = label, modifier = modifier.size(size).clip(shape))
    } else {
        Box(
            modifier
                .size(size)
                .clip(shape)
                .background(Brush.linearGradient(listOf(Charcoal.NeonBlueSoft, Charcoal.NeonPurpleSoft)), shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(label.take(1).uppercase(), style = BoosterTypography.titleLarge, color = Charcoal.Text)
        }
    }
}

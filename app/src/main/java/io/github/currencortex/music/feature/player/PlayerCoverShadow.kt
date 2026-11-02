package io.github.currencortex.music.feature.player

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** A circular black falloff, cached in cover space and transformed with the artwork.
 * No bitmap mask or blur is rebuilt when the flight changes opacity or size. */
internal fun Modifier.playerCoverShadow(progress: () -> Float = { 1f }): Modifier {
    val tint = Color.Black
    return drawWithCache {
        val edge = size.minDimension / 2f
        val spread = (edge * .18f).coerceIn(8.dp.toPx(), 28.dp.toPx())
        val radius = edge + spread
        val brush = Brush.radialGradient(
            0f to tint.copy(alpha = .32f),
            (edge / radius) to tint.copy(alpha = .32f),
            ((edge + spread * .3f) / radius) to tint.copy(alpha = .18f),
            ((edge + spread * .65f) / radius) to tint.copy(alpha = .05f),
            1f to tint.copy(alpha = 0f),
            center = Offset(size.width / 2f, size.height / 2f), radius = radius)
        onDrawWithContent {
            val opacity = progress().coerceIn(0f, 1f)
            if (opacity > 0f) drawCircle(brush, radius = radius, alpha = opacity)
            drawContent()
        }
    }
}

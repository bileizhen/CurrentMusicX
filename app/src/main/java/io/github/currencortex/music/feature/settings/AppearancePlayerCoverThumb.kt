package io.github.currencortex.music.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.data.settings.PlayerCoverStyle

/** CurrentMusic's three player compositions, rather than another app's interface thumbnail. */
@Composable internal fun AppearancePlayerCoverThumb(style: PlayerCoverStyle) {
    Canvas(Modifier.size(44.dp, 36.dp).clip(RoundedCornerShape(7.dp))) {
        withTransform({ scale(size.width / 44f, size.height / 36f, Offset.Zero) }) {
            drawRect(Color(0xFF1C2432), size = Size(44f, 36f))
            val art = Brush.linearGradient(listOf(Color(0xFF72C7DC), Color(0xFF7652B0)), Offset.Zero, Offset(28f, 26f))
            when (style) {
                PlayerCoverStyle.CIRCLE -> drawCircle(art, 10f, Offset(22f, 13f))
                PlayerCoverStyle.IMMERSIVE -> {
                    drawRect(art, size = Size(44f, 28f))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF1C2432)), 10f, 30f), size = Size(44f, 36f))
                }
                PlayerCoverStyle.SQUARE -> drawRoundRect(art, Offset(12f, 3f), Size(20f, 20f), CornerRadius(1.2f))
            }
            drawRoundRect(Color.White, Offset(9f, 26f), Size(19f, 2f), CornerRadius(1f))
            drawLine(Color.White.copy(alpha = .5f), Offset(9f, 31f), Offset(35f, 31f), 1f)
            drawCircle(Color.White, 1.8f, Offset(22f, 33f))
        }
    }
}

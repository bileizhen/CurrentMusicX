package io.github.currencortex.music.feature.settings

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.LaunchStyle
import io.github.currencortex.music.ui.theme.LocalCustomTypeface
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Static launch scenes in the same 96:66 frame as the other appearance choices. */
@Composable internal fun AppearanceLaunchThumb(style: LaunchStyle, config: AppearanceSettings) {
    val colors = MiuixTheme.colorScheme
    if (style == LaunchStyle.NONE) {
        AppearanceMiniThumb(colors, true, config.liquidGlass)
        return
    }
    if (style == LaunchStyle.SIGNATURE) {
        io.github.currencortex.music.ui.component.SignatureLaunchFrame("Music", { 1500f },
            Modifier.width((30f * 96 / 66).dp).height(30.dp).clip(RoundedCornerShape(8.dp)))
        return
    }
    val font = LocalCustomTypeface.current
    val label = remember(font) { Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = font ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        textSize = 9f
    } }
    Canvas(Modifier.width((30f * 96 / 66).dp).height(30.dp).clip(RoundedCornerShape(8.dp))) {
        withTransform({ scale(size.width / 96f, size.height / 66f, pivot = Offset.Zero) }) {
            val frame = Size(96f, 66f)
            if (style == LaunchStyle.CLASSIC) {
                drawRect(colors.background, size = frame)
                drawRect(Brush.radialGradient(listOf(colors.primary.copy(alpha = .25f), Color.Transparent),
                    Offset(65f, 18f), 65f), size = frame)
                drawCircle(colors.primary.copy(alpha = .15f), 15f, Offset(48f, 20f))
                drawCircle(colors.primary, 10f, Offset(48f, 20f))
                // Avatar/wordmark arrangement from the classic launch's opening scene.
                drawCircle(colors.onPrimary, 2.1f, Offset(46f, 23f))
                drawLine(colors.onPrimary, Offset(48f, 23f), Offset(48f, 14f), 1.8f)
                drawLine(colors.onPrimary, Offset(48f, 14f), Offset(52f, 15f), 1.8f)
                label.color = colors.onSurface.toArgb()
                drawContext.canvas.nativeCanvas.drawText("Current Music", 48f, 43f, label)
                drawRoundRect(colors.onSurfaceVariantSummary.copy(alpha = .45f), Offset(34f, 50f), Size(28f, 2.5f), CornerRadius(2f))
            } else {
                drawRect(Color.Black, size = frame)
                drawRect(Brush.radialGradient(listOf(Color(0xFF123C53), Color.Black), Offset(44f, 25f), 62f), size = frame)
                val cyan = Color(0xFF66E6FF)
                // The halo, hot rim and crest are a frozen representative frame.
                for ((width, alpha) in listOf(9f to .06f, 5f to .14f, 2.5f to .4f, 1f to .95f)) {
                    drawOval(cyan.copy(alpha = alpha), Offset(23f, 10f), Size(50f, 30f), style = Stroke(width))
                }
                drawLine(Color.White, Offset(48f, 7f), Offset(48f, 19f), 1.2f)
                drawLine(Color.White, Offset(42f, 13f), Offset(54f, 13f), 1.2f)
                listOf(Offset(17f, 33f), Offset(76f, 21f), Offset(68f, 43f)).forEach {
                    drawCircle(cyan.copy(alpha = .6f), 1f, it)
                }
                label.color = android.graphics.Color.WHITE
                drawContext.canvas.nativeCanvas.drawText("Current Music", 48f, 55f, label)
            }
            drawRoundRect(colors.onSurface.copy(alpha = .13f), size = frame, cornerRadius = CornerRadius(16f), style = Stroke(1.2f))
        }
    }
}

package io.github.currencortex.music.ui.component

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.*
import top.yukonga.miuix.kmp.theme.*

internal val MusicDialogShape = RoundedCornerShape(32.dp)
internal val LocalMusicDialogBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
internal val LocalMusicDialogEdgeToEdge = staticCompositionLocalOf { false }

@Composable internal fun musicDialogColors() = if (isInDarkTheme())
    darkColorScheme(primary = Color(0xFF6BA5FF), background = Color(0xFF292A2D),
        surface = Color(0xFF37383C), onBackground = Color(0xFFF5F5F7))
else lightColorScheme(primary = Color(0xFF3485FF), background = Color(0xFFF2F3F5),
    surface = Color.White, onBackground = Color(0xFF17181B))

/** Capture page content, never the root overlay itself (which would feed back into the blur). */
@Composable internal fun rememberMusicDialogBackdrop(): LayerBackdrop? =
    if (Build.VERSION.SDK_INT >= 33 && LocalView.current.isHardwareAccelerated && isRuntimeShaderSupported())
        rememberLayerBackdrop() else null

@Composable internal fun Modifier.musicDialogBackdropSource(): Modifier {
    val backdrop = LocalMusicDialogBackdrop.current
    return if (backdrop != null) then(Modifier.layerBackdrop(backdrop)) else this
}

@Composable internal fun Modifier.musicDialogMaterial(backdrop: Backdrop? = LocalMusicDialogBackdrop.current): Modifier {
    val dark = isInDarkTheme()
    val material = if (Build.VERSION.SDK_INT >= 33 && backdrop != null) Modifier.frostedMusicDialog(backdrop, dark)
    else Modifier.background(if (dark) Color(0xF22C2D30) else Color(0xDDF2F3F5), MusicDialogShape)
    return then(material).border(.7.dp, Color.White.copy(alpha = if (dark) .12f else .65f), MusicDialogShape)
}

@RequiresApi(33)
private fun Modifier.frostedMusicDialog(backdrop: Backdrop, dark: Boolean): Modifier =
    then(Modifier.drawBackdrop(backdrop, shape = { MusicDialogShape }, effects = {
        colorControls(brightness = 0f, contrast = 1f, saturation = .35f)
        blur(28.dp.toPx(), 28.dp.toPx())
    }, onDrawSurface = {
        drawRect(if (dark) Color(0xFF292A2D).copy(alpha = .86f) else Color(0xFFF3F4F6).copy(alpha = .68f))
    }))

/** Full panel-width hit target and press wash; text, divider and radio share a 24dp inset. */
@Composable fun MusicDialogChoiceRow(label: String, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, summary: String? = null, enabled: Boolean = true, showDivider: Boolean = true) {
    val colors = MiuixTheme.colorScheme
    val dark = isInDarkTheme()
    val press = rememberMusicDialogPress()
    val clicks = remember { MutableInteractionSource() }
    val progress = animateFloatAsState(if (selected) 1f else 0f, tween(180), label = "dialog choice ring")
    Row(modifier.fillMaxWidth().drawBehind {
        drawRect(colors.onBackground.copy(alpha = .06f * press.amount.value))
        if (showDivider) drawLine(colors.onBackground.copy(alpha = if (dark) .065f else .045f),
            Offset(24.dp.toPx(), size.height), Offset(size.width - 24.dp.toPx(), size.height), .5.dp.toPx())
    }.then(if (enabled) Modifier.musicDialogRowPress(press) else Modifier)
        .selectable(selected, enabled = enabled, role = Role.RadioButton, interactionSource = clicks, indication = null, onClick = onClick)
        .heightIn(min = 48.dp).padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, color = colors.onBackground.copy(alpha = if (enabled) 1f else .4f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            summary?.let { Text(it, color = colors.onSurfaceVariantSummary.copy(alpha = if (enabled) 1f else .4f), fontSize = 12.sp, lineHeight = 18.sp) }
        }
        Spacer(Modifier.width(16.dp))
        Canvas(Modifier.size(20.dp)) {
            val stroke = 1.5.dp.toPx()
            drawCircle(colors.onSurfaceVariantSummary.copy(alpha = if (dark) .32f else .27f),
                radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
            val fraction = progress.value
            if (fraction > 0f) {
                val ring = androidx.compose.ui.graphics.lerp(colors.primary, colors.onBackground, .12f)
                drawCircle(ring.copy(alpha = .78f * fraction * if (enabled) 1f else .4f),
                    radius = size.minDimension / 2 * (.75f + .25f * fraction))
                drawCircle(Color.White.copy(alpha = fraction), radius = 6.dp.toPx() * (.75f + .25f * fraction))
            }
        }
    }
}

@Composable fun MusicChoiceDialog(title: String, onDismiss: () -> Unit,
    footer: @Composable ColumnScope.() -> Unit = { MusicDialogButton("取消", onDismiss) },
    content: @Composable ColumnScope.() -> Unit) {
    MusicDialog(title, onDismiss, footer, edgeToEdgeContent = true, content = content)
}

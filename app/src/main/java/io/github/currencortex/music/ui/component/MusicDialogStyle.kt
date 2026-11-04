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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.ui.theme.isInDarkTheme
import io.github.currencortex.music.ui.component.nativeui.LocalLiquidGlassEnabled
import io.github.currencortex.music.ui.component.nativeui.LocalGlassBlurEnabled
import io.github.currencortex.music.ui.component.nativeui.LiquidGlassOpticsKey
import io.github.currencortex.music.ui.component.nativeui.glassSurface
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

@Composable internal fun musicDialogFallbackColor() = if (isInDarkTheme()) Color(0xF22C2D30) else Color(0xDDF2F3F5)

/** Capture page content, never the root overlay itself (which would feed back into the blur). */
@Composable internal fun rememberMusicDialogBackdrop(): LayerBackdrop? =
    if (Build.VERSION.SDK_INT >= 33 && LocalView.current.isHardwareAccelerated && isRuntimeShaderSupported())
        rememberLayerBackdrop() else null

@Composable internal fun Modifier.musicDialogBackdropSource(): Modifier {
    val backdrop = LocalMusicDialogBackdrop.current
    return if (backdrop != null) then(Modifier.layerBackdrop(backdrop)) else this
}

@Composable internal fun Modifier.musicDialogMaterial(backdrop: Backdrop? = LocalMusicDialogBackdrop.current,
    shape: Shape = MusicDialogShape, drawFallback: Boolean = true, visibility: (() -> Float)? = null): Modifier {
    val dark = isInDarkTheme()
    val glass = LocalLiquidGlassEnabled.current && backdrop != null && Build.VERSION.SDK_INT >= 33
    val fadeContent = if (visibility != null) Modifier.graphicsLayer {
        alpha = visibility().coerceIn(0f, 1f)
    } else Modifier
    if (glass) return then(Modifier
        // Keep the shadow outside the content's alpha layer: that offscreen layer clips
        // overflow while alpha < 1, making a shadow inside it pop in after the entrance.
        .dropShadow(shape) {
            radius = 29.dp.toPx()
            offset = Offset(0f, 8.dp.toPx())
            color = Color.Black
            alpha = (if (dark) .177f else .118f) * (visibility?.invoke()?.coerceIn(0f, 1f) ?: 1f)
        }
        .then(fadeContent)
        .glassSurface(backdrop, shape, menu = true, frost = { 16f }, alert = true)
        .alertGlassRim(shape, dark))
    val material = if (LocalGlassBlurEnabled.current && Build.VERSION.SDK_INT >= 33 && backdrop != null)
        Modifier.frostedMusicDialog(backdrop, dark, shape)
    else if (drawFallback) Modifier.background(musicDialogFallbackColor(), shape) else Modifier
    return then(fadeContent).then(material).semantics { this[LiquidGlassOpticsKey] = false }
        .border(.7.dp, Color.White.copy(alpha = if (dark) .12f else .65f), shape)
}

/** LiquidGlassRim.alert from FlClash: stronger sides, a quiet flat edge and a separate optical shine. */
private fun Modifier.alertGlassRim(shape: Shape, dark: Boolean): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val radius = (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 32.dp.toPx()
    val across = (radius / size.width.coerceAtLeast(1f)).coerceIn(0f, .4f)
    val side = if (dark) .75f else .35f
    val corner = if (dark) .1f else .22f
    val flat = if (dark) .03f else .12f
    val rim = Brush.horizontalGradient(
        0f to Color.Black.copy(alpha = side), across / 4f to Color.Black.copy(alpha = corner),
        across to Color.Black.copy(alpha = flat), 1f - across to Color.Black.copy(alpha = flat),
        1f - across / 4f to Color.Black.copy(alpha = corner), 1f to Color.Black.copy(alpha = side))
    val stroke = Stroke(.67.dp.toPx())
    onDrawWithContent { drawContent(); drawOutline(outline, rim, style = stroke) }
}

@RequiresApi(33)
private fun Modifier.frostedMusicDialog(backdrop: Backdrop, dark: Boolean, shape: Shape): Modifier =
    then(Modifier.drawBackdrop(backdrop, shape = { shape }, effects = {
        colorControls(brightness = 0f, contrast = 1f, saturation = .35f)
        blur(28.dp.toPx(), 28.dp.toPx())
    }, onDrawSurface = {
        drawRect(if (dark) Color(0xFF292A2D).copy(alpha = .86f) else Color(0xFFF3F4F6).copy(alpha = .68f))
    }))

/** Full panel-width hit target and press wash; text, divider and radio share a 24dp inset. */
@Composable fun MusicDialogChoiceRow(label: String, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, summary: String? = null, enabled: Boolean = true, showDivider: Boolean = true,
    leadingContent: (@Composable () -> Unit)? = null) {
    val colors = MiuixTheme.colorScheme
    if(io.github.currencortex.music.ui.component.nativeui.LocalSettingsComponents.current) {
        io.github.currencortex.music.ui.component.nativeui.SettingsChoiceRow(label,selected,onClick,
            modifier.drawBehind {if(showDivider)drawLine(colors.onBackground.copy(alpha=.06f),
                Offset((if(leadingContent==null)16.dp else 52.dp).toPx(),size.height),
                Offset(size.width-16.dp.toPx(),size.height),.5.dp.toPx())},summary,enabled,leadingContent)
        return
    }
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
        if (leadingContent != null) {
            leadingContent()
            Spacer(Modifier.width(12.dp))
        }
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

// SPDX-License-Identifier: GPL-3.0-only
// Native port of chen08209's upstream project lib/widgets/palette.dart
// @ 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7. HCT grid, HSL spectrum, HSV sliders.
package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.hct.Hct
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.*

internal data class PaletteHsv(val hue: Float, val saturation: Float, val value: Float) {
    val color: Color get() = Color.hsv(hue.coerceIn(0f, 360f), saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f))
    companion object {
        fun from(color: Color): PaletteHsv {
            val hi = maxOf(color.red, color.green, color.blue)
            val lo = minOf(color.red, color.green, color.blue)
            val delta = hi - lo
            val hue = when {
                delta == 0f -> 0f
                hi == color.red -> 60f * (((color.green - color.blue) / delta) % 6)
                hi == color.green -> 60f * ((color.blue - color.red) / delta + 2)
                else -> 60f * ((color.red - color.green) / delta + 4)
            }
            return PaletteHsv((hue + 360f) % 360f, if (hi == 0f) 0f else delta / hi, hi)
        }
    }
}

internal object PaletteGrid {
    val hues = listOf(230.0, 262.0, 285.0, 310.0, 340.0, 20.0, 40.0, 60.0, 80.0, 100.0, 120.0, 145.0)
    val rows: List<List<Color>> by lazy {
        listOf(hues.indices.map { Color(Hct.from(0.0, 0.0, 100 - it * 100.0 / (hues.size - 1)).toInt()) }) +
            listOf(25.0, 33.0, 41.0, 49.0, 57.0, 65.0, 73.0, 81.0, 89.0).map { tone ->
                hues.map { hue -> Color(Hct.from(hue, 200.0, tone).toInt()) }
            }
    }
    fun at(point: Offset, size: Size): Color =
        rows[(point.y / size.height * 10).toInt().coerceIn(0, 9)][(point.x / size.width * 12).toInt().coerceIn(0, 11)]
}

/** Color stays local to its owner; changing modes never changes the picked color. */
@Composable internal fun ColorPalette(color: Color, onChanged: (Color) -> Unit, modifier: Modifier = Modifier) {
    var mode by remember { mutableIntStateOf(0) }
    var hsv by remember { mutableStateOf(PaletteHsv.from(color)) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // Gray and black must retain the hue/saturation just chosen with the sliders.
    LaunchedEffect(color) { if (hsv.color.toArgb() != color.toArgb()) hsv = PaletteHsv.from(color) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsSegmentedControl(listOf("网格", "光谱", "滑块"), mode, {
            focus.clearFocus(); keyboard?.hide(); mode = it
        }, tag = "appearance_palette_mode")
        Box(Modifier.fillMaxWidth().animateContentSize(tween(250, easing = CubicBezierEasing(.215f, .61f, .355f, 1f)))) {
            when (mode) {
                0 -> ColorGrid(color, onChanged)
                1 -> ColorSpectrum(color, onChanged)
                else -> HsvSliders(hsv) { next -> hsv = next; onChanged(next.color) }
            }
        }
    }
}

/** Consume contact immediately, before a sheet/list can claim a vertical drag. */
@Composable private fun Modifier.pickArea(onPick: (Offset, Size) -> Unit, onPressed: (Boolean) -> Unit = {}): Modifier {
    val pick by rememberUpdatedState(onPick)
    val pressed by rememberUpdatedState(onPressed)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            down.consume()
            focus.clearFocus(); keyboard?.hide()
            fun update(point: Offset) = pick(Offset(point.x.coerceIn(0f, size.width.toFloat()), point.y.coerceIn(0f, size.height.toFloat())),
                Size(size.width.toFloat(), size.height.toFloat()))
            pressed(true); update(down.position)
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.pressed) update(change.position)
                    event.changes.forEach { it.consume() }
                } while (change.pressed)
            } finally { pressed(false) }
        }
    }
}

@Composable private fun ColorGrid(color: Color, onChanged: (Color) -> Unit) {
    val all = PaletteGrid.rows.flatten()
    val index = all.indexOfFirst { it.toArgb() == color.toArgb() }
    Canvas(Modifier.fillMaxWidth().aspectRatio(6f / 5).clip(RoundedCornerShape(8.dp))
        .testTag("appearance_color_grid").pickArea({ point, size -> onChanged(PaletteGrid.at(point, size)) })
        .semantics {
            contentDescription = "颜色网格"; stateDescription = hexDigits(color)
            progressBarRangeInfo = ProgressBarRangeInfo(index.coerceAtLeast(0).toFloat(), 0f..119f, 118)
            setProgress { onChanged(all[it.roundToInt().coerceIn(0, 119)]); true }
        }.onKeyEvent {
            if (it.type != KeyEventType.KeyDown) false else {
                val delta = when (it.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; Key.DirectionUp -> -12; Key.DirectionDown -> 12; else -> 0 }
                if (delta != 0) { onChanged(all[(index.coerceAtLeast(0) + delta).coerceIn(0, 119)]); true } else false
            }
        }.focusable()) {
        val width = size.width / 12; val height = size.height / 10
        PaletteGrid.rows.forEachIndexed { row, cells -> cells.forEachIndexed { column, fill ->
            // Snap cell edges together; fractional antialiasing must not expose seams.
            val left = (column * width).roundToInt().toFloat(); val top = (row * height).roundToInt().toFloat()
            drawRect(fill, Offset(left, top), Size(((column + 1) * width).roundToInt() - left, ((row + 1) * height).roundToInt() - top))
        } }
        if (index >= 0) {
            val inset = 1.5.dp.toPx()
            drawRoundRect(if (isDarkSwatch(color)) Color.White else Color.Black,
                Offset(index % 12 * width + inset, index / 12 * height + inset), Size(width - 2 * inset, height - 2 * inset),
                CornerRadius(4.dp.toPx()), style = Stroke(3.dp.toPx()))
        }
    }
}

@Composable private fun ColorSpectrum(color: Color, onChanged: (Color) -> Unit) {
    var picked by remember { mutableStateOf<Pair<Offset, Int>?>(null) }
    val hue = PaletteHsv.from(color).hue
    val lightness = (maxOf(color.red, color.green, color.blue) + minOf(color.red, color.green, color.blue)) / 2
    val position = picked?.takeIf { it.second == color.toArgb() }?.first ?: Offset(hue / 360f, 1 - lightness)
    val hueColors = remember { (0..6).map { Color.hsl((it * 60 % 360).toFloat(), 1f, .5f) } }
    Canvas(Modifier.fillMaxWidth().aspectRatio(6f / 5).testTag("appearance_color_spectrum")
        .pickArea({ point, size ->
            val fraction = Offset(point.x / size.width, point.y / size.height)
            val next = Color.hsl((fraction.x * 360).coerceIn(0f, 359.99f), 1f, 1 - fraction.y)
            picked = fraction to next.toArgb(); onChanged(next)
        }).semantics {
            contentDescription = "颜色光谱"; stateDescription = hexDigits(color)
            customActions = listOf(
                CustomAccessibilityAction("增加色相") { onChanged(Color.hsl((hue + 5) % 360, 1f, lightness)); true },
                CustomAccessibilityAction("减少色相") { onChanged(Color.hsl((hue + 355) % 360, 1f, lightness)); true },
                CustomAccessibilityAction("提高亮度") { onChanged(Color.hsl(hue, 1f, (lightness + .05f).coerceAtMost(1f))); true },
                CustomAccessibilityAction("降低亮度") { onChanged(Color.hsl(hue, 1f, (lightness - .05f).coerceAtLeast(0f))); true })
        }) {
        drawRoundRect(Brush.horizontalGradient(hueColors), cornerRadius = CornerRadius(8.dp.toPx()))
        drawRoundRect(Brush.verticalGradient(0f to Color.White, .5f to Color.White.copy(alpha = 0f),
            .5f to Color.Black.copy(alpha = 0f), 1f to Color.Black), cornerRadius = CornerRadius(8.dp.toPx()))
        colorThumb(Offset(position.x * size.width, position.y * size.height), 14.dp.toPx(), color)
    }
}

@Composable private fun HsvSliders(hsv: PaletteHsv, onChanged: (PaletteHsv) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val hueStops = remember { (0..6).map { Color.hsv((it * 60 % 360).toFloat(), 1f, 1f) } }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainer)
        .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        GradientColorSlider("色相", "${hsv.hue.roundToInt()}°", hsv.hue, 360f, hueStops,
            hsv.copy(saturation = 1f, value = 1f).color, "appearance_color_hue") { onChanged(hsv.copy(hue = it)) }
        GradientColorSlider("饱和度", "${(hsv.saturation * 100).roundToInt()}%", hsv.saturation, 1f,
            listOf(hsv.copy(saturation = 0f).color, hsv.copy(saturation = 1f).color), hsv.color,
            "appearance_color_saturation") { onChanged(hsv.copy(saturation = it)) }
        GradientColorSlider("亮度", "${(hsv.value * 100).roundToInt()}%", hsv.value, 1f,
            listOf(Color.Black, hsv.copy(value = 1f).color), hsv.color,
            "appearance_color_brightness") { onChanged(hsv.copy(value = it)) }
    }
}

@Composable private fun GradientColorSlider(label: String, text: String, value: Float, max: Float,
    stops: List<Color>, thumb: Color, tag: String, onChanged: (Float) -> Unit) {
    val colors = MiuixTheme.colorScheme
    var pressed by remember { mutableStateOf(false) }
    val edgePx = with(LocalDensity.current) { 14.dp.toPx() }
    val activation by animateFloatAsState(if (pressed) 1f else 0f, tween(100), label = "color slider press")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariantSummary)
            Text(text, fontSize = 15.sp, lineHeight = 20.sp, color = colors.onSurfaceVariantSummary)
        }
        Canvas(Modifier.fillMaxWidth().height(28.dp).testTag(tag)
            .pickArea({ point, size ->
                val edge = edgePx
                onChanged(((point.x - edge) / (size.width - edge * 2)).coerceIn(0f, 1f) * max)
            }, { pressed = it }).semantics {
                contentDescription = label; stateDescription = text
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..max)
                setProgress { onChanged(it.coerceIn(0f, max)); true }
            }.onKeyEvent {
                if (it.type != KeyEventType.KeyDown) false else {
                    val delta = when (it.key) { Key.DirectionLeft, Key.DirectionDown -> -max / 100; Key.DirectionRight, Key.DirectionUp -> max / 100; else -> 0f }
                    if (delta != 0f) { onChanged((value + delta).coerceIn(0f, max)); true } else false
                }
            }.focusable()) {
            val edge = 14.dp.toPx(); val trackHeight = 20.dp.toPx(); val top = (size.height - trackHeight) / 2
            val brush = Brush.horizontalGradient(stops, edge, size.width - edge)
            drawRoundRect(brush, Offset(0f, top), Size(size.width, trackHeight), CornerRadius(trackHeight / 2))
            drawRoundRect(colors.dividerLine.copy(alpha = .6f), Offset(.5.dp.toPx(), top + .5.dp.toPx()),
                Size(size.width - 1.dp.toPx(), trackHeight - 1.dp.toPx()), CornerRadius(trackHeight / 2), style = Stroke(1.dp.toPx()))
            colorThumb(Offset(edge + (size.width - 2 * edge) * value / max, size.height / 2), (14 + activation * 2).dp.toPx(), thumb)
        }
    }
}

private fun DrawScope.colorThumb(center: Offset, radius: Float, color: Color) {
    // The original white 3dp ring stays white on both light and dark sheets.
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.argb(71, 0, 0, 0)
        maskFilter = android.graphics.BlurMaskFilter(2.5.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    drawContext.canvas.nativeCanvas.drawCircle(center.x, center.y + 1.dp.toPx(), radius, paint)
    drawCircle(Color.White, radius, center)
    drawCircle(color, radius - 3.dp.toPx(), center)
}

internal fun hexDigits(color: Color) = "%06X".format(color.toArgb() and 0xFFFFFF)
private fun isDarkSwatch(color: Color): Boolean { val l = color.luminance() + .05f; return l * l <= .15f }

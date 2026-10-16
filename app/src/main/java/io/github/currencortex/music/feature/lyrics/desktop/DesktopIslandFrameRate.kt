package io.github.currencortex.music.feature.lyrics.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** Actual callback cadence, not the panel's advertised refresh rate or another app's FPS. */
internal class DesktopFrameRateSampler {
    private val frames = LongArray(256)
    private var first = 0
    private var count = 0
    private var last = 0L
    private var published = 0L
    var rate: Float? = null
        private set
    fun reset() { first = 0; count = 0; last = 0; published = 0; rate = null }
    fun record(nanos: Long): Boolean {
        if (nanos <= 0) { reset(); return true }
        val interrupted = last > 0 && (nanos <= last || nanos - last > 1_000_000_000L)
        if (interrupted) reset()
        while (count > 0 && nanos - frames[first] > 1_000_000_000L) {
            first = (first + 1) % frames.size; count--
        }
        if (count == frames.size) { first = (first + 1) % frames.size; count-- }
        frames[(first + count) % frames.size] = nanos; count++; last = nanos
        if (count >= 8 && (published == 0L || nanos - published >= 500_000_000L)) {
            rate = ((count - 1) * 1_000_000_000.0 / (nanos - frames[first])).toFloat()
            published = nanos
            return true
        }
        return interrupted
    }
}
internal class DesktopFrameRateMeter {
    private val sampler = DesktopFrameRateSampler()
    var rate by mutableStateOf<Float?>(null)
        private set
    fun onFrame(nanos: Long) { if (sampler.record(nanos)) rate = sampler.rate }
}
@Composable internal fun rememberIslandFrameRate() = remember { DesktopFrameRateMeter() }
private fun islandFpsStyle(font: Float, color: Color) = TextStyle(fontFamily = FontFamily.Monospace,
    fontFeatureSettings = "tnum", fontSize = font.sp, lineHeight = (font * 1.2f).sp,
    color = color, textAlign = TextAlign.Center)

/** Match the frame's 30 dp body minimum, FPS padding and 6 dp outer/inner insets. */
@Composable internal fun rememberIslandFpsHeight(font: Float): androidx.compose.ui.unit.Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val text = measurer.measure(AnnotatedString("000.0 FPS"), islandFpsStyle(font, Color.White),
        softWrap = false, maxLines = 1)
    return with(density) { maxOf(30.dp, text.size.height.toDp() + 6.dp) + 6.dp }
}

@Composable internal fun DesktopIslandFrameRate(rate: Float?, font: Float, color: Color) {
    val label = if (rate == null) "--.- FPS" else String.format(Locale.ROOT, "%.1f FPS", rate)
    BasicText(label, Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp).testTag("desktop_island_fps"),
        style = islandFpsStyle(font, color), maxLines = 1, softWrap = false)
}

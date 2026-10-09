package io.github.currencortex.music.feature.visualizer

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import io.github.currencortex.music.data.visualizer.VisualizerQuality
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.platform.LocalView
import io.github.currencortex.music.core.visualizer.*
import io.github.currencortex.music.feature.player.PlayerViewModel
import io.github.currencortex.music.feature.visualizer.render.VisualizerPresetRenderer
import io.github.currencortex.music.ui.component.coverRequestUrl
import top.yukonga.miuix.kmp.basic.Text

@Composable fun VisualizerPresetSelector(config: VisualizerEffectConfig, onSelect: (VisualizerPreset) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        VisualizerPreset.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { preset ->
                    Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (preset == config.presetId) Color(0xFF264853) else Color(0xFF161D29))
                        .testTag("visualizer_preset_${preset.name}").semantics { selected = preset == config.presetId }
                        .clickable { onSelect(preset) }.padding(8.dp), contentAlignment = Alignment.Center) {
                        Text(preset.displayName, fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

/** Preview only: the existing circular pager and standard player transition are untouched. */
@Composable fun VisualizerPresetPreview(vm: PlayerViewModel, render: VisualizerRenderState, visible: Boolean,
    rendererObserver: (VisualizerPresetRenderer?) -> Unit = {}, showMetrics: Boolean = false) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val song by vm.currentSong.collectAsStateWithLifecycle()
    val c = settings.visualizerEffects
    val stats by render.statistics
    val renderer = remember(c.presetId) { VisualizerPresetRenderer(c.presetId) }
    val context = LocalContext.current
    val view = LocalView.current
    val preference = remember(view) { context.visualizerWindow()?.let { window ->
        VisualizerRefreshPreference({ window.attributes.preferredRefreshRate }) { value ->
            try { window.attributes = window.attributes.apply { preferredRefreshRate = value } }
            catch (_: RuntimeException) { /* Window hints are optional; music/rendering continue. */ }
        }
    } }
    SideEffect {
        val target = if (Build.VERSION.SDK_INT >= 34) render.preferredFps else
            view.display?.supportedModes?.map { it.refreshRate }?.minByOrNull { kotlin.math.abs(it - render.preferredFps) }?.takeIf { render.preferredFps > 0f } ?: 0f
        preference?.update(target, view.display?.refreshRate ?: 60f)
    }
    DisposableEffect(preference) { onDispose { preference?.close() } }
    val scope = rememberCoroutineScope()
    var coverLoaded by remember(song?.cover) { mutableStateOf(false) }
    var coverColor by remember(song?.cover) { mutableStateOf(Color(0xFF183B50)) }
    SideEffect { renderer.config = c; renderer.coverLoaded = coverLoaded; renderer.coverColor = coverColor; renderer.hardwareAccelerated = view.isHardwareAccelerated; renderer.systemReduceMotion = Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled() }
    DisposableEffect(renderer) {
        rendererObserver(renderer)
        onDispose { renderer.close(); rendererObserver(null) }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp))
        .preferredFrameRate(render.preferredFps).testTag("visualizer_preset_stage"), contentAlignment = Alignment.Center) {
        VisualizerRenderLayer(vm.audioAnalysis, Modifier.matchParentSize().testTag("visualizer_spectrum"),
            settings.visualizerRender, visible, render, renderer)
        // Only the expensive shader background is rasterized into a smaller cached layer.
        // Frequency lines, borders and artwork retain the stage's native resolution.
        val shaderSize = minOf(maxWidth, if (stats.qualityLevel == VisualizerQuality.ULTRA) 240.dp else 144.dp)
        val shaderScale = maxWidth / shaderSize
        if (!c.presetId.circular) Canvas(Modifier.requiredSize(shaderSize).preferredFrameRate(render.preferredFps).graphicsLayer {
            render.revision
            scaleX = shaderScale; scaleY = shaderScale
            compositingStrategy = CompositingStrategy.Offscreen
            alpha = if (renderer.active && c.enabled) 1f else 0f
        }) { render.revision; with(renderer) { shaderBackground(render.frame) } }
        val artworkSize = maxWidth * .47f
        val artworkShape = remember(c.presetId) { if (c.presetId.circular) CircleShape else RoundedCornerShape(8.dp) }
        val gray = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
        Box(Modifier.size(artworkSize).preferredFrameRate(render.preferredFps).graphicsLayer {
            render.revision // Read in the layer, never in composition or measurement.
            scaleX = renderer.effects.coverScale; scaleY = scaleX
            translationX = renderer.effects.shakeX; translationY = renderer.effects.shakeY
            alpha = .75f + renderer.effects.transition * .25f
            shape = artworkShape
            clip = true
            renderEffect = renderer.coverEffect()
        }.drawWithContent {
            render.revision
            drawContent()
            if (c.presetId == VisualizerPreset.DARK_GLITCH && renderer.effects.glitch > 0f) {
                for (i in 0..1) clipRect(0f, size.height * (.22f + i * .39f), size.width, size.height * (.265f + i * .39f)) {
                    translate(left = renderer.effects.glitch * 12f) { this@drawWithContent.drawContent() }
                }
            }
        }.background(Color(0xFF1B2630)).testTag("visualizer_artwork"), contentAlignment = Alignment.Center) {
            Text("♪", fontSize = 48.sp, color = Color(0xFF658998))
            Crossfade(song?.cover.orEmpty(), animationSpec = tween(240), label = "visualizer-cover") { url ->
                AsyncImage(model = remember(context, url) { ImageRequest.Builder(context)
                    .data(coverRequestUrl(url, 650)).size(650).allowHardware(false).crossfade(240).build() },
                    contentDescription = "当前歌曲封面", contentScale = ContentScale.Crop,
                    colorFilter = if (c.presetId == VisualizerPreset.DARK_GLITCH) gray else null,
                    onSuccess = { result ->
                        coverLoaded = true
                        scope.launch {
                            coverColor = withContext(Dispatchers.Default) {
                                val bitmap = result.result.image.toBitmap()
                                var red = 0; var green = 0; var blue = 0
                                for (y in 0 until 12) for (x in 0 until 12) {
                                    val pixel = bitmap.getPixel((x * bitmap.width / 12).coerceAtMost(bitmap.width - 1), (y * bitmap.height / 12).coerceAtMost(bitmap.height - 1))
                                    red += android.graphics.Color.red(pixel); green += android.graphics.Color.green(pixel); blue += android.graphics.Color.blue(pixel)
                                }
                                val sampled = Color(red / 144, green / 144, blue / 144)
                                sampled // Borrowed from Coil: never recycle a shared cached image.
                            }
                        }
                    }, onError = { coverLoaded = false },
                    modifier = Modifier.fillMaxSize())
            }
        }
        Canvas(Modifier.matchParentSize().preferredFrameRate(render.preferredFps)) { render.revision; with(renderer) { foreground() } }
    }
    Text(song?.name ?: "请先播放歌曲", fontSize = 14.sp)
    if (showMetrics) Text("目标 ${stats.effectiveTargetFps} · Canvas %.1f · 显示 %.0f Hz · ${stats.qualityLevel.name}".format(stats.canvasDrawRate, stats.displayRefreshRate), fontSize = 11.sp)
    Text("低音 %.0f%% · 鼓点 ${render.audioReadout.value.kickSequence}".format(render.audioReadout.value.bass * 100f), fontSize = 11.sp)
    if (showMetrics) Text("${renderer.shaderStatus} · Bass %.2f · Kick ${render.audioReadout.value.kickSequence}".format(render.audioReadout.value.bass), fontSize = 11.sp)
}

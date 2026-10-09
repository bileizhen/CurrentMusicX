package io.github.currencortex.music.feature.visualizer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.feature.player.PlayerViewModel
import io.github.currencortex.music.feature.visualizer.render.VisualizerPresetRenderer
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme

/** Local MIUIX palette and bounded safe-inset dialog. No route, player or second clock. */
@Composable internal fun VisualizerPreviewDialog(vm: PlayerViewModel, render: VisualizerRenderState,
    visible: Boolean, status: String, denied: Boolean, details: Boolean, toggleDetails: () -> Unit,
    toggleCapture: () -> Unit, showDiagnostics: () -> Unit, dismiss: () -> Unit,
    observer: (VisualizerPresetRenderer?) -> Unit, viewport: DpSize? = null) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val song by vm.currentSong.collectAsStateWithLifecycle()
    val colors = remember { darkColorScheme(background = Color(0xFF0D111A), surface = Color(0xFF141A26), surfaceContainer = Color(0xFF1C2331)) }
    Dialog(dismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        MiuixTheme(colors = colors, textStyles = MiuixTheme.textStyles) {
          CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp), contentAlignment = Alignment.Center) {
                val bounds = if (viewport == null) Modifier.widthIn(max = 1100.dp).fillMaxWidth().fillMaxHeight(.97f) else Modifier.size(viewport)
                Column(bounds
                    .clip(RoundedCornerShape(24.dp)).background(colors.background).padding(14.dp).testTag("visualizer_preview_container"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("音乐可视化", fontSize = 19.sp)
                            Text(if (denied) "请允许音频采集权限后重试" else status, Modifier.testTag("visualizer_status"), fontSize = 11.sp, color = Color(0xFF929EB2))
                        }
                        TextButton("关闭", dismiss, Modifier.testTag("visualizer_preview_close"))
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val areaHeight = maxHeight
                        val landscape = maxWidth > maxHeight * 1.25f
                        @Composable fun stage(modifier: Modifier) {
                            BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
                                val edge = minOf(maxHeight, maxWidth)
                                VisualizerPresetPreview(vm, render, visible, rendererObserver = observer, showMetrics = false,
                                    modifier = Modifier.size(edge), showSong = false)
                            }
                        }
                        @Composable fun controls(modifier: Modifier) {
                            Column(modifier.verticalScroll(rememberScrollState()).testTag("visualizer_preview_controls"), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text(song?.name ?: "请先播放歌曲", fontSize = 16.sp)
                                if (!song?.artists.isNullOrBlank()) Text(song!!.artists, fontSize = 11.sp, color = Color(0xFF929EB2))
                                VisualizerPresetSelector(settings.visualizerEffects) { selected -> vm.visualizerEffects { it.copy(presetId = selected) } }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(if (settings.visualizerEnabled) "关闭特效" else "开启特效", toggleCapture, Modifier.weight(1f).testTag("visualizer_toggle"))
                                    TextButton(if (details) "收起参数" else "参数与性能", toggleDetails, Modifier.weight(1f).testTag("visualizer_details"))
                                }
                                if (details) {
                                    VisualizerEffectControls(vm, settings.visualizerEffects, settings.visualizerRender.preferredQuality)
                                    VisualizerFrameRateControls(vm)
                                    VisualizerPerformanceReadout(render)
                                    Text("低音 %.0f%% · 鼓点 ${render.audioReadout.value.kickSequence}".format(render.audioReadout.value.bass * 100), fontSize = 11.sp)
                                    TextButton(if (settings.visualizerRender.automaticOptimization) "自动优化：开" else "自动优化：关", {
                                        vm.visualizerRender { it.copy(automaticOptimization = !it.automaticOptimization) }
                                    }, Modifier.testTag("visualizer_auto_performance"))
                                    TextButton("音频调试", showDiagnostics, Modifier.testTag("visualizer_preview_toggle"))
                                }
                            }
                        }
                        if (landscape) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            stage(Modifier.weight(1.2f).fillMaxHeight())
                            controls(Modifier.weight(1f).fillMaxHeight())
                        } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            stage(Modifier.weight(1f).fillMaxWidth())
                            controls(Modifier.fillMaxWidth().heightIn(max = areaHeight * if (details) .49f else .34f))
                        }
                    }
                }
            }
          }
        }
    }
}

@Composable private fun VisualizerFrameRateControls(vm: PlayerViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        io.github.currencortex.music.data.visualizer.VisualizerFrameRate.entries.forEach { rate ->
            TextButton(rate.label, { vm.visualizerFrameRate(rate) }, Modifier.weight(1f).testTag("visualizer_fps_${rate.name}"))
        }
    }
}

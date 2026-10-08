package io.github.currencortex.music.feature.visualizer

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import top.yukonga.miuix.kmp.basic.Text
import java.util.Locale

@Composable internal fun VisualizerPerformanceReadout(render: VisualizerRenderState) {
    val stats by render.statistics
    fun f(value: Float?) = value?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "—"
    Text("请求 ${stats.requestedFps ?: "Auto"} · 偏好 ${stats.preferenceFps} · 有效 ${stats.effectiveTargetFps}\n" +
        "显示 ${f(stats.displayRefreshRate)} Hz · 时钟 ${f(stats.renderTickRate)} Hz · Canvas ${f(stats.canvasDrawRate)} FPS\n" +
        "Window 帧报告 ${f(stats.measuredFrameRate)} FPS · 屏幕呈现：需外部测量\n" +
        "间隔均值 / P95 / P99 ${f(stats.frameIntervalAverageMs)} / ${f(stats.frameIntervalP95Ms)} / ${f(stats.frameIntervalP99Ms)} ms\n" +
        "Window 超时 ${f(stats.jankPercentage)}% · CPU 绘制 ${f(stats.canvasCpuAverageMs)} ms\n" +
        "绘制迟帧 ${f(stats.renderJankPercentage)}% · 估计缺失 ${stats.estimatedDroppedFrames} 帧 / 5 秒\n" +
        "音频 ${f(stats.audioCaptureRate)} Hz · 帧龄 ${f(stats.audioFrameAgeMs)} ms · ${stats.qualityLevel}\n" +
        "${if (stats.running) "运行中" else "已停止"} · 绘制 ${stats.canvasDraws} · 绘制层重组 ${stats.compositions}" +
        (stats.limitation?.let { "\n$it" } ?: ""), Modifier.testTag("visualizer_performance"))
}

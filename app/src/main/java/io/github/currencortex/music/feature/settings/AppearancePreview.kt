// SPDX-License-Identifier: GPL-3.0-only
// Preview frame sizing and transitions adapted from upstream theme_preview.dart
// @ 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7; interface scenes are CurrentMusic.
package io.github.currencortex.music.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.RootTabAnimation
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun AppearanceLivePreview(config: AppearanceSettings) {
    val colors = MiuixTheme.colorScheme
    val renderer = rememberCurrentMusicPreviewRenderer()
    var selected by remember { mutableIntStateOf(0) }
    var previous by remember { mutableStateOf<Int?>(null) }
    val progress = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var transition by remember { mutableStateOf<Job?>(null) }
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(28.dp))
        .background(colors.surfaceContainer).padding(vertical = 24.dp).testTag("theme_preview"),
        contentAlignment = Alignment.Center) {
        Box(Modifier.width(168.dp).aspectRatio(9f / 17f).clip(RoundedCornerShape(28.dp))) {
            val paper by animateColorAsState(colors.background, tween(300), label = "preview paper")
            val card by animateColorAsState(colors.surfaceContainer, tween(300), label = "preview card")
            val primary by animateColorAsState(colors.primary, tween(300), label = "preview accent")
            Canvas(Modifier.fillMaxSize()) {
                renderer.draw(this, colors, config.floatingBar, config.liquidGlass, selected, previous, progress.value,
                    paper = paper, card = card, primary = primary, fade = config.tabAnimation == RootTabAnimation.FADE)
            }
            val unit = 168.dp / CurrentMusicPreviewRenderer.Width
            Row(Modifier.align(Alignment.BottomCenter)
                .padding(start = unit * CurrentMusicPreviewRenderer.DockMargin, end = unit * CurrentMusicPreviewRenderer.DockMargin,
                    bottom = unit * CurrentMusicPreviewRenderer.DockBottom)
                .height(unit * CurrentMusicPreviewRenderer.NavHeight).fillMaxWidth()) {
                listOf("首页", "发现", "我的", "设置").forEachIndexed { index, title ->
                    Box(Modifier.weight(1f).fillMaxHeight().testTag("appearance_preview_tab_$index")
                        .semantics { contentDescription = "预览$title"; this.selected = selected == index }
                        .clickable(role = Role.Tab) {
                            if (index != selected) {
                                transition?.cancel()
                                transition = scope.launch {
                                    previous = selected; progress.snapTo(0f); selected = index
                                    progress.animateTo(1f, if (config.tabAnimation == RootTabAnimation.FADE)
                                        tween(220, easing = CubicBezierEasing(.42f, 0f, .58f, 1f))
                                        else tween(300, easing = CubicBezierEasing(0f, 0f, .58f, 1f)))
                                    previous = null
                                }
                            }
                        })
                }
            }
        }
    }
}

@Composable internal fun AppearanceMiniThumb(colors: Colors, floating: Boolean, glass: Boolean,
    split: Colors? = null, modifier: Modifier = Modifier, motion: RootTabAnimation? = null, selected: Int = 0) {
    val renderer = rememberCurrentMusicPreviewRenderer()
    Canvas(modifier.width((30f * 96 / 66).dp).height(30.dp).clip(RoundedCornerShape(8.dp))) {
        fun paint(palette: Colors) = withTransform({
            scale(size.width / 96, size.width / 96, pivot = Offset.Zero)
            translate(0f, -142f)
        }) { renderer.draw(this, palette, floating, glass, if (motion == null) selected else 1, if (motion == null) null else 0,
            if (motion == null) 1f else .55f, Size(96f, 208f), fade = motion == RootTabAnimation.FADE) }
        paint(colors)
        split?.let { dark ->
            val cut = Path().apply {
                moveTo(size.width * .7f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height)
                lineTo(size.width * .3f, size.height); close()
            }
            clipPath(cut) { paint(dark) }
        }
    }
}

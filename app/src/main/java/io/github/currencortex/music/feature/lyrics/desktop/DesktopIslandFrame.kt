package io.github.currencortex.music.feature.lyrics.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.MultiMeasureLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.SemanticsProperties
import io.github.currencortex.music.data.song.Song

/** Layout and material only. Both hosts supply the existing timed lyric renderer. */
internal val DesktopIslandReveal = SemanticsPropertyKey<Float>("DesktopIslandReveal")
@Composable internal fun DesktopIslandFrame(song: Song?, modifier: Modifier = Modifier,
    metadata: androidx.compose.ui.unit.Dp? = null, presentation: Float = 1f,
    coverPosition: State<Long>? = null, motion: Boolean = false,
    durationMs: Long = song?.durationMs ?: 0L,
    transition: DesktopIslandCoverTransition? = null,
    onCoverTap: (() -> Unit)? = null, coverPlaying: Boolean = false,
    onCoverBounds: (Rect) -> Unit = {},
    content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(50)
    val reveal = presentation.coerceIn(0f, 1f)
    val coverTransition = transition ?: rememberIslandCoverTransition(song, motion)
    BoxWithConstraints(modifier.padding(2.dp).testTag("desktop_island")) {
        val fullMetadata = (metadata ?: 120.dp).coerceIn(28.dp, maxOf(28.dp, maxWidth - 10.dp))
        val infoWidth = (fullMetadata - 28.dp) * (1f - reveal)
        // A single-line cue has height independent of available width. Measure its
        // natural height first, then allocate the cover and final viewport in the
        // same layout pass. No onSizeChanged state or next-frame size correction.
        @Suppress("DEPRECATION")
        MultiMeasureLayout(Modifier.fillMaxWidth()
            .shadow(2.dp, shape, clip = false)
            .background(Brush.verticalGradient(listOf(Color(0xFF11141B), Color(0xFF080B10))), shape)
            .border(.6.dp, Brush.verticalGradient(listOf(Color.White.copy(.16f), Color.White.copy(.035f))), shape)
            // Cover radius + 5 dp inset equals the pill end radius: concentric.
            .padding(start = 5.dp, end = 5.dp, top = 1.dp, bottom = 1.dp),
            content = {
                DesktopIslandCover(song, coverPosition, motion, coverTransition,
                    Modifier.onGloballyPositioned { onCoverBounds(it.boundsInRoot()) }.then(
                        if (onCoverTap != null) Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                            role = Role.Button, onClickLabel = if (coverPlaying) "暂停歌曲" else "播放歌曲",
                            onClick = onCoverTap) else Modifier))
                Column(Modifier.testTag("desktop_island_metadata").then(if (reveal >= .55f) Modifier.clearAndSetSemantics {} else Modifier).clipToBounds().graphicsLayer { alpha = (1f - reveal / .55f).coerceIn(0f, 1f) },
                    verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    val name = song?.name?.takeIf(String::isNotBlank) ?: "CurrentMusic"
                    BasicText(name,
                        Modifier.testTag("desktop_island_title"),
                        style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(song?.artists?.takeIf(String::isNotBlank) ?: if (song == null) "等待播放" else "未知歌手",
                        Modifier.testTag("desktop_island_artist"),
                        style = TextStyle(color = Color.White.copy(.65f), fontSize = 9.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            DesktopIslandProgress(coverPosition.takeIf { song != null }, durationMs.takeIf { song != null } ?: 0L,
                Modifier.clipToBounds().graphicsLayer {
                    alpha = (reveal / .25f).coerceIn(0f, 1f)
                })
            val lyricSemantics = if (reveal <= .12f) Modifier.clearAndSetSemantics {
                this[DesktopIslandReveal] = reveal; this[SemanticsProperties.TestTag] = "desktop_island_lyrics"
            } else Modifier.testTag("desktop_island_lyrics").semantics { this[DesktopIslandReveal] = reveal }
            // Warm the shared layouts/halo during the heading hold, before movement starts.
            Column(Modifier.then(lyricSemantics)
                .graphicsLayer { alpha = ((reveal - .28f) / .37f).coerceIn(0f, 1f) },
                horizontalAlignment = Alignment.CenterHorizontally) { content() }
        }) { children, constraints ->
            val width = constraints.maxWidth
            val gap = desktopIslandCoverGap.roundToPx()
            val lyricInset = (desktopIslandLyricInset * reveal).roundToPx()
            val dividerWidth = (desktopIslandProgressWidth * reveal).roundToPx()
            val infoPixels = infoWidth.roundToPx().coerceAtMost((width - 28.dp.roundToPx()).coerceAtLeast(0))
            val info = children[1].measure(Constraints.fixedWidth(infoPixels))
            val minCover = 22.dp.roundToPx()
            fun lyricConstraints(cover: Int) = Constraints.fixedWidth((width - cover - gap - infoPixels - dividerWidth + lyricInset * 2).coerceAtLeast(1))
            var lyric = children[3].measure(lyricConstraints(minCover))
            // Hidden lyrics are warmed, but do not reserve a bilingual shell.
            val growth = (reveal / .25f).coerceIn(0f, 1f)
            val naturalHeight = maxOf(30.dp.roundToPx(), if (reveal < .55f) info.height else 0,
                (lyric.height * growth).roundToInt())
            // A new heading must not shrink a bilingual cover on the first frame.
            // Capture the previously rendered geometry synchronously with the song
            // identity, then resize, move and flip using the same finite progress.
            val p = coverTransition.progress.value
            val start = coverTransition.start
            val height = (start?.let { it.height * (1f - p) + naturalHeight * p } ?: naturalHeight.toFloat()).roundToInt()
            val coverSize = (height - 8.dp.roundToPx()).coerceAtLeast(minCover)
            if (coverSize != minCover) lyric = children[3].measure(lyricConstraints(coverSize))
            val cover = children[0].measure(Constraints.fixed(coverSize, coverSize))
            val direction = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
            val slot = Offset(direction * (coverSize - width) / 2f, height / 2f)
            val destination = Offset(direction * ((naturalHeight - 8.dp.roundToPx()).coerceAtLeast(minCover) - width) / 2f, naturalHeight / 2f)
            val origin = start?.center ?: destination
            // The reserved outer drawing room bounds the arc even on a rapid retarget.
            val room = minOf(start?.let { it.center.y - it.diameter / 2f } ?: 4.dp.toPx(), 4.dp.toPx()) +
                (desktopIslandDrawingSpace + 3.dp).toPx()
            val arc = 4f * p * (1f - p) * minOf(10.dp.toPx(), room.coerceAtLeast(0f))
            val center = origin * (1f - p) + destination * p + Offset(arc * .5f, -arc)
            coverTransition.translation = center - slot
            coverTransition.placement = DesktopIslandCoverPlacement(height.toFloat(), coverSize.toFloat(), center)
            val divider = children[2].measure(Constraints.fixed(dividerWidth, 22.dp.roundToPx()))
            layout(width, height) {
                cover.placeRelative(0, (height - cover.height) / 2)
                info.placeRelative(coverSize + gap, (height - info.height) / 2)
                divider.placeRelative(coverSize + gap + infoPixels, (height - divider.height) / 2)
                lyric.placeRelative(coverSize + gap + infoPixels + dividerWidth - lyricInset, (height - lyric.height) / 2)
            }
        }
    }
}

package io.github.currencortex.music.feature.lyrics.desktop

import androidx.compose.foundation.Image
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.MultiMeasureLayout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalContext
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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.coverRequestUrl

/** Layout and material only. Both hosts supply the existing timed lyric renderer. */
internal val DesktopIslandReveal = SemanticsPropertyKey<Float>("DesktopIslandReveal")
@Composable internal fun DesktopIslandFrame(song: Song?, modifier: Modifier = Modifier,
    metadata: androidx.compose.ui.unit.Dp? = null, presentation: Float = 1f,
    coverPosition: State<Long>? = null, motion: Boolean = false,
    content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(50)
    val reveal = presentation.coerceIn(0f, 1f)
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
                val context = LocalContext.current
                val cover = song?.cover.orEmpty()
                Box(Modifier.testTag("desktop_island_cover").clip(CircleShape).background(Color(0xFF2B303B)).graphicsLayer {
                    rotationZ = if (motion && cover.isNotBlank()) ((coverPosition?.value ?: 0L) % 20000L) / 20000f * 360f else 0f
                },
                    contentAlignment = Alignment.Center) {
                    val icon = remember(context) { context.packageManager.getApplicationIcon(context.applicationInfo).toBitmap(256, 256).asImageBitmap() }
                    Image(icon, if (song == null) "应用图标" else "占位封面", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    if (cover.isNotBlank()) AsyncImage(
                        remember(context, cover) { ImageRequest.Builder(context).data(coverRequestUrl(cover, 256)).size(256).build() },
                        contentDescription = "歌曲封面", contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize())
                }
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
            Box(Modifier.height(22.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(1.dp).fillMaxHeight().testTag("desktop_island_divider").graphicsLayer {
                    alpha = (reveal / .25f).coerceIn(0f, 1f)
                }.background(Color.White.copy(.19f)))
            }
            val lyricSemantics = if (reveal <= .12f) Modifier.clearAndSetSemantics {
                this[DesktopIslandReveal] = reveal; this[SemanticsProperties.TestTag] = "desktop_island_lyrics"
            } else Modifier.testTag("desktop_island_lyrics").semantics { this[DesktopIslandReveal] = reveal }
            // Warm the shared layouts/halo during the heading hold, before movement starts.
            Column(Modifier.then(lyricSemantics)
                .graphicsLayer { alpha = ((reveal - .28f) / .37f).coerceIn(0f, 1f) },
                horizontalAlignment = Alignment.CenterHorizontally) { content() }
        }) { children, constraints ->
            val width = constraints.maxWidth
            val gap = 6.dp.roundToPx()
            val dividerWidth = (6.dp * reveal).roundToPx()
            val infoPixels = infoWidth.roundToPx().coerceAtMost((width - 28.dp.roundToPx()).coerceAtLeast(0))
            val info = children[1].measure(Constraints.fixedWidth(infoPixels))
            val minCover = 22.dp.roundToPx()
            fun lyricConstraints(cover: Int) = Constraints.fixedWidth((width - cover - gap - infoPixels - dividerWidth).coerceAtLeast(1))
            var lyric = children[3].measure(lyricConstraints(minCover))
            // Hidden lyrics are warmed, but do not reserve a bilingual shell.
            val growth = (reveal / .25f).coerceIn(0f, 1f)
            val height = maxOf(30.dp.roundToPx(), if (reveal < .55f) info.height else 0,
                (lyric.height * growth).roundToInt())
            val coverSize = (height - 8.dp.roundToPx()).coerceAtLeast(minCover)
            if (coverSize != minCover) lyric = children[3].measure(lyricConstraints(coverSize))
            val cover = children[0].measure(Constraints.fixed(coverSize, coverSize))
            val divider = children[2].measure(Constraints.fixed(dividerWidth, 22.dp.roundToPx()))
            layout(width, height) {
                cover.placeRelative(0, (height - cover.height) / 2)
                info.placeRelative(coverSize + gap, (height - info.height) / 2)
                divider.placeRelative(coverSize + gap + infoPixels, (height - divider.height) / 2)
                lyric.placeRelative(coverSize + gap + infoPixels + dividerWidth, (height - lyric.height) / 2)
            }
        }
    }
}

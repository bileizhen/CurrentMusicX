package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.ui.component.MusicCover
import io.github.currencortex.music.ui.component.PlaylistContainerMotion
import io.github.currencortex.music.ui.component.LocalPageContainerSource
import io.github.currencortex.music.ui.component.playlistMotionElement
import io.github.currencortex.music.ui.component.rememberPageNavigationSource
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared cover tile for home recommendations, playlists and artist albums. */
@Composable internal fun MusicArtworkCard(cover: String, title: String, subtitle: String,
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, pixels: Int = 320,
    onLongClick: ((Rect) -> Unit)? = null, longClickLabel: String = "预览歌曲与操作",
    playlistTransition: Boolean = false) {
    val motion = if (playlistTransition && LocalPageContainerSource.current != null)
        remember(cover, title) { PlaylistContainerMotion(cover, title) } else null
    val source = if (motion != null) rememberPageNavigationSource(title, playlistMotion = motion) else null
    val open = { if (source != null) source.open(onClick) else onClick() }
    var coverBounds by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current
    val coverInset = with(density) { 10.dp.toPx() }
    // Font fallback (notably Japanese) can change measured text height despite minLines.
    // Reserve the same scaled line boxes for every card, including short/empty labels.
    // Include fallback-font top/bottom extents so a two-line Japanese title is not
    // ellipsized down to one line just to fit the shared slot.
    val titleHeight = with(density) { 20.sp.toDp() * 2 + 8.sp.toDp() }
    val subtitleHeight = with(density) { 20.sp.toDp() }
    val labelStyle = MiuixTheme.textStyles.main.copy(
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    val interactions = remember { MutableInteractionSource() }
    val click = if (onLongClick == null) Modifier.clickable(enabled = enabled, role = Role.Button,
        interactionSource = interactions, indication = null, onClick = open)
    else Modifier.combinedClickable(enabled = enabled, role = Role.Button,
        interactionSource = interactions, indication = null, onClick = open,
        onLongClickLabel = longClickLabel, onLongClick = { if (!coverBounds.isEmpty) onLongClick(coverBounds) })
    // Measure from the resting container, outside ElasticPress's animated child layer.
    // The pressed cover bounds would otherwise become the return target and snap on dismissal.
    ElasticPress(modifier.then(source?.modifier ?: Modifier).then(click).onGloballyPositioned { coordinates ->
        coverBounds = Rect(
            coordinates.localToWindow(Offset(coverInset, coverInset)),
            coordinates.localToWindow(Offset(coordinates.size.width - coverInset,
                coordinates.size.width - coverInset)),
        )
    }, enabled = enabled) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MusicCover(cover, Modifier.fillMaxWidth().aspectRatio(1f).then(playlistMotionElement(motion, 0)), pixels = pixels, cornerRadius = 12.dp)
            Text(title, Modifier.height(titleHeight).then(playlistMotionElement(motion, 1)), style = labelStyle,
                fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
                minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, Modifier.height(subtitleHeight), style = labelStyle, fontSize = 12.sp, lineHeight = 16.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary, minLines = 1, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

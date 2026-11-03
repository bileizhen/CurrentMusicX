package io.github.currencortex.music.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.data.song.SearchLyricContext
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.library.LibraryViewModel
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.GlassIconButton
import io.github.currencortex.music.ui.component.nativeui.GlassButtonGroup
import io.github.currencortex.music.ui.component.nativeui.GlassGroupIconButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Only literal query matches receive accent ink; metadata stays readable in both themes. */
internal fun searchMatchText(text: String, query: String, accent: Color): AnnotatedString {
    val builder = AnnotatedString.Builder(text)
    val word = query.trim()
    if (word.isNotEmpty()) {
        var start = text.indexOf(word, ignoreCase = true)
        while (start >= 0) {
            builder.addStyle(SpanStyle(color = accent), start, start + word.length)
            start = text.indexOf(word, start + word.length, ignoreCase = true)
        }
    }
    return builder.toAnnotatedString()
}

/** Keeps the accent match inside the single-line viewport by windowing long hits around the query. */
internal fun lyricMatchWindow(text: String, query: String, limit: Int = 24): String {
    if (text.length <= limit) return text
    val word = query.trim()
    val hit = if (word.isEmpty()) -1 else text.indexOf(word, ignoreCase = true)
    if (hit < 0) return text.take(limit) + "…"
    // Keep the entire query so searchMatchText can still highlight it. Longer queries
    // use a larger window; shorter ones reserve only the available leading context.
    val width = maxOf(limit, word.length)
    val leading = minOf(4, width - word.length)
    val start = (hit - leading).coerceIn(0, (text.length - width).coerceAtLeast(0))
    val end = (start + width).coerceAtMost(text.length)
    return (if (start > 0) "…" else "") + text.substring(start, end) + (if (end < text.length) "…" else "")
}

@Composable internal fun SearchResultSongRow(song: Song, query: String, active: Boolean, onPlay: () -> Unit,
    onNext: () -> Unit, onQueue: () -> Unit, lyric: SearchLyricContext? = null,
    quickActions: (@Composable () -> Unit)? = null, extraActions: (@Composable () -> Unit)? = null,
    compactActions: Boolean = false) {
    val colors = MiuixTheme.colorScheme
    val request = LocalRoomSongRequest.current
    val pending = request?.pending?.invoke(song.id) == true
    val primary = { if (request != null) request.request(song) else onPlay() }
    val host = LocalSongMenu.current
    var localMenu by remember(song.id) { mutableStateOf<SongMenu?>(null) }
    val openMenu = {
        val menu = SongMenu(song, primary, onNext, onQueue, request != null, extraActions)
        if (host == null) localMenu = menu else host(menu)
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(enabled = !pending, role = Role.Button, onClick = primary)
        .padding(vertical = 10.dp), verticalAlignment = if (compactActions) Alignment.Top else Alignment.CenterVertically) {
        MusicCover(song.cover, Modifier.size(52.dp).testTag("search_result_cover_${song.id}"), cornerRadius = 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(end = if (compactActions) 0.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val subtitle = if (pending) "正在提交点歌…" else listOf(song.artists, song.album).filter(String::isNotBlank).joinToString(" · ")
            val subtitleBlock = @Composable {
                if (subtitle.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (song.mv > 0) Text("MV", fontSize = 9.sp, color = colors.primary,
                        modifier = Modifier.background(colors.primary.copy(alpha = .08f)).padding(horizontal = 3.dp, vertical = 1.dp))
                    Text(searchMatchText(subtitle, query, colors.primary.copy(alpha = .8f)), fontSize = 12.sp,
                        color = colors.onSurface.copy(alpha = .5f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("search_result_subtitle_${song.id}"))
                }
            }
            val lyricBlock = @Composable {
                lyric?.let { match ->
                    // Fixed three-row NetEase-style block; absent neighbours stay as the placeholder.
                    Text(match.before ?: "……", fontSize = 12.sp, color = colors.onSurface.copy(alpha = .58f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("search_lyric_before_${song.id}"))
                    Text(searchMatchText(lyricMatchWindow(match.matched, query), query, colors.primary), fontSize = 12.sp,
                        color = colors.onSurface.copy(alpha = .58f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("search_lyric_${song.id}"))
                    Text(match.after ?: "……", fontSize = 12.sp, color = colors.onSurface.copy(alpha = .58f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("search_lyric_after_${song.id}"))
                }
            }
            if (compactActions) {
                // Header block: title and artist share the row with the actions on the right;
                // the lyric block sits below at full column width, independent of the buttons.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(searchMatchText(song.name, query, colors.primary), fontSize = 16.sp, fontWeight = FontWeight.Medium,
                            color = if (active) colors.primary else colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("search_result_title_${song.id}"))
                        subtitleBlock()
                    }
                    quickActions?.let { actions ->
                        GlassButtonGroup(Modifier.testTag("search_quick_actions_${song.id}")) { actions() }
                    }
                    SearchResultIconAction(MusicIcons.More, "更多操作：${song.name}",
                        Modifier.testTag("song_menu_${song.id}"), enabled = !pending) { openMenu() }
                }
                lyricBlock()
            } else {
                Text(searchMatchText(song.name, query, colors.primary), fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    color = if (active) colors.primary else colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("search_result_title_${song.id}"))
                subtitleBlock()
                lyricBlock()
            }
        }
        if (!compactActions) {
            quickActions?.let { actions ->
                GlassButtonGroup(Modifier.testTag("search_quick_actions_${song.id}")) { actions() }
                Spacer(Modifier.width(8.dp))
            }
            SearchResultIconAction(MusicIcons.More, "更多操作：${song.name}", Modifier.testTag("song_menu_${song.id}"), enabled = !pending) { openMenu() }
        }
    }
    localMenu?.let { SongActionsSheet(it) { localMenu = null } }
}

/** Reuses the selected heart destination and the existing playlist picker. */
@Composable internal fun SearchResultLibraryActions(vm: LibraryViewModel, song: Song, showLike: Boolean = true) {
    val statuses by vm.statuses.collectAsStateWithLifecycle()
    val nativeLikes by vm.container.neteaseSongActions.likedState.collectAsStateWithLifecycle()
    val native by vm.container.primaryLibrary.likesUseNetease.collectAsStateWithLifecycle()
    val liking by vm.primaryLikeBusy.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val liked = if (native) song.id in nativeLikes else statuses[song.id]?.liked == true
    if (showLike) {
        SearchResultIconAction(if (liked) Icons.Default.Favorite else MusicIcons.Heart,
            if (liked) "取消喜欢：${song.name}" else "喜欢：${song.name}",
            Modifier.testTag("search_like_${song.id}").semantics { stateDescription = if (liked) "已喜欢" else "未喜欢" },
            enabled = !liking && statuses[song.id]?.pending != true, accent = liked, grouped = true) { vm.togglePrimaryLike(song) }
    }
    SearchResultIconAction(playlistAddIcon, "加入歌单：${song.name}", Modifier.testTag("search_playlist_${song.id}"),
        enabled = !busy, grouped = true) { vm.choosePlaylist(song) }
}

@Composable internal fun SearchResultsToolbar(count: Int, total: Int, onPlayAll: () -> Unit, onQueueAll: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onPlayAll)
            .testTag("search_play_all"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(26.dp).background(colors.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(19.dp), tint = Color.White)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("播放全部", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("已加载 $count 首 · 共 $total 首", fontSize = 10.sp, color = colors.onSurface.copy(alpha = .4f))
            }
        }
        SearchResultIconAction(MusicIcons.List, "将已加载的 $count 首歌曲加入队列", Modifier.testTag("search_queue_all"), onClick = onQueueAll)
    }
}

@Composable private fun SearchResultIconAction(icon: ImageVector, description: String, modifier: Modifier = Modifier,
    enabled: Boolean = true, accent: Boolean = false, grouped: Boolean = false, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val content: @Composable () -> Unit = {
        Icon(icon, null, Modifier.size(21.dp),
            tint = (if (accent) colors.primary else colors.onSurfaceVariantSummary).let { it.copy(alpha = it.alpha * if (enabled) 1f else .38f) })
    }
    if (grouped) GlassGroupIconButton(onClick, description, modifier, enabled = enabled, content = content)
    else GlassIconButton(onClick, description, modifier, enabled = enabled, content = content)
}

private val playlistAddIcon = ImageVector.Builder("SearchPlaylistAdd", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(4f, 5f); lineTo(18f, 5f); moveTo(4f, 11f); lineTo(14f, 11f)
        moveTo(4f, 17f); lineTo(10f, 17f); moveTo(18f, 13f); lineTo(18f, 21f)
        moveTo(14f, 17f); lineTo(22f, 17f)
    }
}.build()

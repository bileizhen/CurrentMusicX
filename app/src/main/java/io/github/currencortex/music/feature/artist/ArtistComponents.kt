package io.github.currencortex.music.feature.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.library.*
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun albumCategory(type: String): String = when (type.trim().lowercase(java.util.Locale.ROOT)) {
    "专辑", "album" -> "album"
    "单曲", "single" -> "single"
    "ep", "ep/单曲" -> "ep"
    else -> "other"
}
private fun albumTypeLabel(type: String) = when (albumCategory(type)) {
    "album" -> "专辑"; "single" -> "单曲"; "ep" -> "EP"; else -> type
}
internal fun artistAlbumsForDisplay(albums: List<Album>, filter: String): List<Album> =
    albums.filter { filter == "all" || albumCategory(it.type) == filter }

internal fun artistCount(value: Long): String = when {
    value >= 100_000_000 -> "%.1f亿".format(java.util.Locale.ROOT, value / 100_000_000.0)
    value >= 10_000 -> "%.1f万".format(java.util.Locale.ROOT, value / 10_000.0)
    else -> value.toString()
}

private fun albumDate(album: Album): String = if (album.publishTime > 0)
    Instant.ofEpochMilli(album.publishTime).atZone(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ISO_LOCAL_DATE) else "发行日期未知"
private fun albumCaption(album: Album) = listOf(albumDate(album), if (album.size > 0) "${album.size} 首" else "")
    .filter(String::isNotBlank).joinToString(" · ")

@Composable internal fun ArtistHero(artist: Artist?, fans: ArtistFans?, onAbout: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(288.dp).clip(RoundedCornerShape(28.dp)).testTag("artist_hero")) {
        if (artist == null) MusicPlaceholder(Modifier.matchParentSize()) else {
            MusicCover(artist.cover, Modifier.matchParentSize(), 900, cornerRadius = 28.dp)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .86f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(artist.name, Modifier.testTag("artist_name"), fontSize = 30.sp, fontWeight = FontWeight.SemiBold,
                    color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (artist.description.isNotBlank()) Text(artist.description, fontSize = 13.sp,
                    color = Color.White.copy(alpha = .8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${artist.songTotal} 首歌曲 · ${artist.albumTotal} 张专辑 · ${artist.mvTotal} 支 MV",
                    fontSize = 12.sp, color = Color.White.copy(alpha = .85f))
                fans?.let { Text("${artistCount(it.fans)} 粉丝", fontSize = 13.sp, color = Color.White.copy(alpha = .85f)) }
                Text("艺人百科 ›", Modifier.clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = .16f)).clickable(role = Role.Button, onClick = onAbout)
                    .testTag("artist_open_about").padding(horizontal = 16.dp, vertical = 12.dp),
                    fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
            }
        }
    }
}

@Composable internal fun ArtistSectionTitle(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        if (action != null) MusicTextAction("$action ›", onClick)
    }
}
@Composable internal fun ArtistEmpty(message: String) {
    Text(message, Modifier.padding(vertical = 12.dp), fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}
@Composable internal fun ArtistSectionError(message: String, retry: String, onRetry: () -> Unit, loading: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(message, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        MusicTextAction(retry, onRetry, enabled = !loading)
    }
}
@Composable internal fun ArtistPlayHeader(title: String, count: Int, enabled: Boolean, onPlay: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("play_library_all"), verticalAlignment = Alignment.CenterVertically) {
        MusicTransportButton(title, onPlay, Modifier.size(48.dp), prominent = true, enabled = enabled)
        Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text("$count 首", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}
@Composable internal fun ArtistAlbumCard(album: Album, modifier: Modifier, navigate: (String) -> Unit) {
    MusicArtworkCard(album.cover, album.name, albumDate(album), { navigate("lib/album/${album.id}") },
        modifier.testTag("catalog_entry_${album.id}"), enabled = album.id > 0, pixels = 480)
}
@Composable internal fun ArtistAlbumRow(album: Album, navigate: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button) { navigate("lib/album/${album.id}") }
        .testTag("catalog_entry_${album.id}").padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        MusicCover(album.cover, Modifier.size(88.dp), 240, cornerRadius = 12.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(album.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(albumCaption(album), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            if (album.type.isNotBlank()) Text(albumTypeLabel(album.type), fontSize = 11.sp, color = MiuixTheme.colorScheme.primary)
        }
    }
}
@Composable internal fun ArtistAlbumFilters(filter: String, onFilter: (String) -> Unit, order: String,
    onSort: (String) -> Unit, visible: Int, loaded: Int, total: Int, more: Boolean) {
    val colors = MiuixTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("all" to "全部", "album" to "专辑", "single" to "单曲", "ep" to "EP", "other" to "其他")) { (key, title) ->
                Text(title, Modifier.clip(RoundedCornerShape(24.dp))
                    .background(if (key == filter) colors.primary.copy(alpha = .14f) else colors.surfaceContainer)
                    .semantics { selected = key == filter }.clickable(role = Role.Tab) { onFilter(key) }
                    .testTag("artist_album_filter_$key").padding(horizontal = 22.dp, vertical = 14.dp),
                    color = if (key == filter) colors.primary else colors.onSurfaceVariantSummary, fontSize = 14.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (more) "已加载 $loaded / ${maxOf(total, loaded)} 张 · 显示 $visible 张" else "共 $visible 张",
                Modifier.weight(1f), fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
            MusicTextAction(if (order == "time") "按时间排序 ↓" else "按热度排序 ↓",
                { onSort(if (order == "time") "hot" else "time") }, Modifier.testTag("artist_album_sort"))
        }
        if (more) Text("类型筛选适用于已加载的专辑", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
    }
}
@Composable internal fun ArtistMvCard(mv: MvDto, modifier: Modifier, navigate: (String) -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button) { navigate("lib/mv/${mv.id}") }
        .testTag("artist_mv_${mv.id}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp))) {
            MusicCover(mv.cover, Modifier.matchParentSize(), 640, cornerRadius = 16.dp)
            Text("MV", Modifier.align(Alignment.TopStart).padding(10.dp).background(Color.Black.copy(alpha = .6f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, fontSize = 11.sp)
            if (mv.duration > 0) Text("${mv.duration / 60000}:${(mv.duration / 1000 % 60).toString().padStart(2, '0')}",
                Modifier.align(Alignment.BottomEnd).padding(10.dp).background(Color.Black.copy(alpha = .6f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, fontSize = 12.sp)
        }
        Text(mv.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
@Composable internal fun ArtistBiographyPreview(state: ArtistUiState, onOpen: () -> Unit) {
    ArtistSectionTitle("艺人百科", "查看详情", onOpen)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MiuixTheme.colorScheme.surfaceContainer)
        .clickable(role = Role.Button, onClick = onOpen).testTag("artist_bio_preview").padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MusicCover(state.displayArtist?.cover.orEmpty(), Modifier.size(48.dp), 160, cornerRadius = 24.dp)
            Text(state.displayArtist?.name ?: "艺人百科", fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
        val brief = state.biography?.briefDesc?.takeIf { it.isNotBlank() } ?: state.displayArtist?.briefDesc.orEmpty()
        when {
            brief.isNotBlank() -> Text(brief, fontSize = 14.sp, lineHeight = 23.sp, maxLines = 4,
                overflow = TextOverflow.Ellipsis, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            state.biographyLoading -> LoadingTextList(2)
            state.biographyError != null -> Text("简介暂未加载，点击查看详情并重试", fontSize = 14.sp)
            else -> Text("查看基本信息与艺人资料", fontSize = 14.sp)
        }
        state.overview?.takeIf { it.awardTitle.isNotBlank() || it.awardCount > 0 }?.let { ArtistAwards(it) }
    }
}
@Composable internal fun ArtistAwards(overview: ArtistOverview) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        overview.awardImages.firstOrNull()?.let { MusicCover(it, Modifier.size(48.dp), 160, cornerRadius = 8.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (overview.awardTitle.isNotBlank()) Text(overview.awardTitle, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (overview.awardCount > 0) Text("${overview.awardCount} 项成就", fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

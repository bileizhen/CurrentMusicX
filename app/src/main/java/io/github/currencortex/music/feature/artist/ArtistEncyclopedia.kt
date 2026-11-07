package io.github.currencortex.music.feature.artist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable internal fun ArtistEncyclopedia(state: ArtistUiState, onBack: () -> Unit, retry: () -> Unit, retryProfile: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf("basic") }
    val basicScroll = rememberLazyListState()
    val detailsScroll = rememberLazyListState()
    val artist = state.displayArtist
    val profile = state.profile
    val brief = state.biography?.briefDesc?.takeIf(String::isNotBlank) ?: artist?.briefDesc.orEmpty()
    val sections = state.biography?.introduction.orEmpty().filter { it.text.isNotBlank() }
    MusicSubpage("艺人百科", "artist_encyclopedia", onBack, backTag = "artist_bio_back") { topInset ->
        LazyColumn(Modifier.fillMaxSize().testTag("artist_bio_list"),
            state = if (tab == "basic") basicScroll else detailsScroll,
            contentPadding = musicScrollPadding(PaddingValues(24.dp, topInset + 24.dp, 24.dp, 32.dp)),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item("identity") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MusicCover(profile?.avatar?.ifBlank { artist?.cover.orEmpty() } ?: artist?.cover.orEmpty(), Modifier.size(72.dp), 240, cornerRadius = 36.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(artist?.name ?: "艺人", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        if (!artist?.description.isNullOrBlank()) Text(artist.description, fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        state.fans?.let { Text("${artistCount(it.fans)} 粉丝 · ${artistCount(it.follows)} 关注", fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
                    }
                }
            }
            item("tabs") { MusicCategoryTabs(listOf("basic" to "基本信息", "details" to "艺人资料"), tab, { tab = it }, "artist_bio_tab") }
            if (tab == "basic") {
                if (artist != null) item("basic") {
                    ArtistSectionTitle("基本信息")
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        profile?.identify?.takeIf(String::isNotBlank)?.let { BiographyLine(it) }
                        if (artist.description.isNotBlank()) BiographyLine("艺人别名：${artist.description}")
                        profile?.identities?.takeIf { it.isNotEmpty() }?.let { BiographyLine("音乐身份：${it.joinToString("、")}") }
                        when (profile?.gender) { 1 -> BiographyLine("性别：男"); 2 -> BiographyLine("性别：女") }
                        profile?.birthday?.takeIf { it != 0L && it > -2208988800000L && it < System.currentTimeMillis() }
                            ?.let { BiographyLine("生日：${artistProfileDate(it)}") }
                        BiographyLine("音乐作品：${artist.songTotal} 首歌曲 · ${artist.albumTotal} 张专辑")
                        BiographyLine("音乐视频：${artist.mvTotal} 支 MV")
                        profile?.createTime?.takeIf { it > 0 }?.let { BiographyLine("注册时间：${artistProfileDate(it)}") }
                        profile?.signature?.takeIf(String::isNotBlank)?.let { BiographyLine("个人简介：$it") }
                    }
                }
                if (state.profileLoading && profile == null) item("profile_loading") { LoadingTextList(2) }
                state.profileError?.let { error -> item("profile_error") {
                    ArtistSectionError("基本信息加载失败：$error", "重试基本信息", retryProfile, state.profileLoading)
                } }
                if (brief.isNotBlank()) item("brief") { ArtistSectionTitle("艺人简介"); BiographyLine(brief) }
                state.overview?.takeIf { it.awardTitle.isNotBlank() || it.awardCount > 0 }?.let { overview -> item("awards") {
                    ArtistSectionTitle("获奖成就")
                    ArtistAwards(overview)
                } }
            } else {
                itemsIndexed(sections, key = { index, _ -> "section_$index" }) { _, section ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(section.title.ifBlank { "艺人资料" }, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        BiographyLine(section.text)
                    }
                }
            }
            if (state.biographyLoading && state.biography == null) item("loading") { LoadingTextList(4) }
            state.biographyError?.let { error -> item("error") { ArtistSectionError("简介加载失败：$error", "重试简介", retry, state.biographyLoading) } }
            if (!state.biographyLoading && state.biographyError == null &&
                ((tab == "basic" && brief.isBlank()) || (tab == "details" && sections.isEmpty()))) item("empty") {
                ArtistEmpty(if (tab == "basic") "暂无艺人简介" else "暂无更多艺人资料")
            }
        }
    }
}
private fun artistProfileDate(value: Long) = Instant.ofEpochMilli(value).atZone(ZoneId.of("Asia/Shanghai"))
    .format(DateTimeFormatter.ISO_LOCAL_DATE)
@Composable private fun BiographyLine(value: String) {
    Text(value, fontSize = 15.sp, lineHeight = 26.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

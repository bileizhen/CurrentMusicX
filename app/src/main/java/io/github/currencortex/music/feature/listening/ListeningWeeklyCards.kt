package io.github.currencortex.music.feature.listening

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/** Weekly editorial cards use the official Re/We columns, not the month's hero layouts. */
@Composable internal fun ListeningWeeklyCards(
    report: ListeningDisplay,
    range: ListeningRange,
    onAction: (ListeningMonthlyAction) -> Unit = {},
    onRank: (() -> Unit)? = null,
) {
    val scale = LocalFootprintScale.current
    val week = report.weekly
    val covers = report.coverUrls.filter(String::isNotBlank).ifEmpty { report.tracks.map { it.cover }.filter(String::isNotBlank) }
    if (week == null) {
        if (covers.isNotEmpty() || report.songCount != null) FootprintRecordWall(covers, "本周", report.songCount, onRank)
        return
    }
    key(range.start) {
        Column(Modifier.fillMaxWidth().testTag("listening_week_details"), verticalArrangement = Arrangement.spacedBy((24 * scale).dp)) {
            if (week.complete) {
                if (!week.wallpaperBottom) FootprintRecordWall(covers, "本周", report.songCount, onRank)
                if (week.songs.isNotEmpty()) WeeklyEditorialColumns(week.songs, artist = false, onAction)
                if (week.friendSongs.size > 2) WeeklyFriendFavorites(week.friendSongs.take(3), onAction)
                if (week.friendKeywords.size > 1) MonthlyFriendKeywords(week.friendKeywords, onAction)
                if (week.longAudio.isNotEmpty()) WeeklyLongAudio(week.longAudio)
                if (week.artists.isNotEmpty()) WeeklyEditorialColumns(week.artists, artist = true, onAction)
                if (week.vip.isNotEmpty()) WeeklyVipSummary(week.vip, week.superVip)
                if (week.genres.isNotEmpty()) WeeklyGenres(week.genres, onAction)
                if (week.eras.isNotEmpty()) WeeklyEras(week.eras, onAction)
                if (week.languages.isNotEmpty()) WeeklyLanguages(week.languages)
                if (week.wallpaperBottom) FootprintRecordWall(covers, "本周", report.songCount, onRank)
            } else {
                if (week.currentFriends.isNotEmpty()) WeeklyCurrentFriends(week.currentFriends, onAction)
                Column(Modifier.fillMaxWidth().testTag("listening_week_pending"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
                    FootprintText("完整数据收集中...", size = 16f, color = FootprintInk,weight=FontWeight.Bold,fontFamily=androidx.compose.ui.text.font.FontFamily.SansSerif)
                    FootprintText("周日下午3点更新", size = 12f, color = FootprintMuted)
                }
            }
        }
    }
}

@Composable private fun WeeklyEditorialColumns(items: List<ListeningWeeklyColumn>, artist: Boolean, onAction: (ListeningMonthlyAction) -> Unit) {
    val scale = LocalFootprintScale.current
    WeeklyCard(if (artist) "本周音乐人" else "本周单曲", if (artist) "artists" else "songs") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            items.forEachIndexed { index, item ->
                Column(Modifier.width((76 * scale).dp).weeklyAction(item.action, onAction)
                    .testTag("listening_week_${if (artist) "artist" else "song"}_$index"), horizontalAlignment = Alignment.CenterHorizontally) {
                    WeeklyNumberText(item.value, numberSize = if (!artist && item.type == "maxShare") 24f else 30f,
                        modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally, unbounded = true))
                    FootprintText(item.field, Modifier.padding(top = (4 * scale).dp, bottom = (16 * scale).dp)
                        .wrapContentWidth(Alignment.CenterHorizontally, unbounded = true), size = 12f, color = FootprintRed,
                        weight = FontWeight.SemiBold, maxLines = 1, lineHeight = 12f)
                    FootprintCover(item.cover, item.name, Modifier.size((76 * scale).dp)
                        .clip(if (artist) CircleShape else RoundedCornerShape((10 * scale).dp)))
                    Box(Modifier.padding(top = (12 * scale).dp).requiredWidth((88 * scale).dp), contentAlignment = Alignment.Center) {
                        FootprintText(item.name, size = 14f, weight = FontWeight.SemiBold, maxLines = 1, lineHeight = 16f)
                    }
                }
            }
        }
    }
}

@Composable private fun WeeklyVipSummary(sections: List<ListeningSummarySection>, superVip: Boolean) {
    val scale = LocalFootprintScale.current
    val gold = Color(0xFFDCCAA8)
    // The artwork carries its own 20dp transparent corners and translucent highlights.
    // An opaque fallback underneath fills those corners and darkens the artwork.
    Box(Modifier.fillMaxWidth().height((147 * scale).dp).clip(RoundedCornerShape((20 * scale).dp))
        .testTag("listening_week_vip")) {
        AsyncImage(WeeklyMedia + if (superVip) "svip_privilege_week.16b0e4b3.png" else "vip_privilege_week.ae1582bc.png",
            if (superVip) "本周SVIP专享" else "本周黑胶VIP专享", Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
        Box(Modifier.align(Alignment.TopCenter).offset(y=(74*scale).dp).height((40*scale).dp)
            .width((.667f*scale).dp).background(Color.White.copy(alpha=.3f)))
        Row(Modifier.fillMaxWidth().padding(top = (69 * scale).dp), horizontalArrangement=Arrangement.SpaceEvenly) {
            sections.take(2).forEach { section ->
                Column(Modifier.width((148*scale).dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    WeeklyNumberText(section.value, color = gold, numberWeight=FontWeight.Bold, unitGap=3.667f)
                    FootprintText(section.label, Modifier.padding(top = (8 * scale).dp), size = 12f,
                        color = gold.copy(alpha = .8f), maxLines = 1, lineHeight = 12f)
                }
            }
        }
    }
}

@Composable private fun WeeklyGenres(items: List<ListeningMonthlyGenre>, onAction: (ListeningMonthlyAction) -> Unit) {
    val scale = LocalFootprintScale.current
    val first = items.firstOrNull()?.percent?.takeIf { it > 0 }
    val second = items.getOrNull(1)?.percent
    WeeklyCard("本周曲风", "genres") {
        Column(Modifier.padding(horizontal = (20 * scale).dp), verticalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
            items.take(3).forEachIndexed { index, item ->
                val width = if (first != null && item.percent != null) {
                    val width = (230 * item.percent / first).toFloat().coerceIn(0f, 230f)
                    when (index) { 1 -> width.coerceAtLeast(76f); 2 -> width.coerceAtLeast(if (item.percent == second) 76f else 61.333f); else -> width }
                } else null
                Row(Modifier.fillMaxWidth().weeklyAction(item.action, onAction).testTag("listening_week_genre_$index"), verticalAlignment = Alignment.CenterVertically) {
                    if (width != null) {
                        Box(Modifier.width((width * scale).dp).height((30 * scale).dp).clip(RoundedCornerShape((15 * scale).dp))
                            .background(Brush.horizontalGradient(listOf(FootprintRed, Color(0xFFFF6D6D))))) {
                            FootprintText(item.name, Modifier.align(Alignment.CenterStart).padding(horizontal = (12 * scale).dp),
                                size = ((width - 24) / item.name.length.coerceAtLeast(1)).coerceIn(10.333f, 16f), color = Color.White,
                                weight = FontWeight.SemiBold, maxLines = 2)
                        }
                        FootprintText("${weeklyPercent(requireNotNull(item.percent))}%", Modifier.padding(start = (10 * scale).dp).wrapContentWidth(Alignment.Start,unbounded=true),
                            size = 24f, numeric = true, weight = FontWeight.Bold, maxLines = 1, lineHeight = 24f)
                    } else FootprintText(item.name, size = 16f, weight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable private fun WeeklyEras(items: List<ListeningWeeklyEra>, onAction: (ListeningMonthlyAction) -> Unit) {
    val scale = LocalFootprintScale.current
    WeeklyCard("本周听歌年代", "eras") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            items.take(3).forEachIndexed { index, item ->
                Column(Modifier.width((76 * scale).dp).weeklyAction(item.action, onAction).testTag("listening_week_era_$index")) {
                    FootprintText("TOP${index + 1}", size = 15f, color = FootprintRed, numeric = true, weight = FontWeight.Black, maxLines = 1, lineHeight = 15f)
                    val isRange = '-' in item.label
                    val number = item.label.takeWhile(Char::isDigit)
                    val suffix = item.label.removePrefix(number)
                    Row(Modifier.padding(top = ((if (isRange) 8.667f else 7f) * scale).dp, bottom = ((if (isRange) 14.333f else 14f) * scale).dp),
                        verticalAlignment = Alignment.Bottom) {
                        if (isRange) FootprintText(item.label, Modifier.wrapContentWidth(Alignment.Start,unbounded=true),size = 16f, numeric = true, weight = FontWeight.Bold, maxLines = 1, lineHeight = 16f)
                        else {
                            FootprintText(number.ifBlank { item.label }, size = 18f, numeric = number.isNotEmpty(), weight = FontWeight.Bold, maxLines = 1, lineHeight = 18f)
                            if (number.isNotEmpty() && suffix.isNotEmpty()) FootprintText(suffix,
                                Modifier.padding(start = (2 * scale).dp, bottom = (1.333f * scale).dp), size = if (number.length == 2) 15f else 13f,
                                weight = FontWeight.SemiBold, maxLines = 1, lineHeight = if (number.length == 2) 15f else 13f)
                        }
                    }
                    FootprintCover(item.cover, item.song, Modifier.size((76 * scale).dp).clip(RoundedCornerShape((10 * scale).dp)))
                    Box(Modifier.padding(top = (12 * scale).dp).offset(x = (-6 * scale).dp).requiredWidth((88 * scale).dp), contentAlignment = Alignment.Center) {
                        FootprintText(item.song, size = 14f, weight = FontWeight.SemiBold, maxLines = 1, lineHeight = 16f)
                    }
                }
            }
        }
    }
}

@Composable private fun WeeklyLanguages(items: List<ListeningWeeklyLanguage>) {
    val scale = LocalFootprintScale.current
    val first = items.firstOrNull()?.percent?.takeIf { it > 0 }
    WeeklyCard("本周听歌语言", "languages") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            items.forEachIndexed { index, item ->
                val proportional = if (items.size >= 3 && first != null && item.percent != null) (100 * item.percent / first).toFloat().coerceIn(0f, 100f) else 0f
                val height = if (items.size < 3 || item.percent == null) 0f else when (index) {
                    1 -> proportional.coerceAtLeast(18f); 2 -> proportional.coerceAtLeast(11.333f); else -> proportional
                }
                Column(Modifier.width((83 * scale).dp).height((162 * scale).dp).testTag("listening_week_language_$index"), verticalArrangement = Arrangement.Bottom) {
                    item.percent?.let { FootprintText("${weeklyPercent(it)}%", Modifier.padding(bottom = scale.dp), size = 30f,
                        numeric = true, weight = FontWeight.Bold, maxLines = 1, lineHeight = 30f) }
                    val fontSize = when (item.name.length) { in 0..3 -> 21f; 4 -> 20f; 5 -> 16f; 6 -> 13.333f; else -> 11f }
                    FootprintText(item.name, Modifier.padding(bottom = (10 * scale).dp), size = fontSize,
                        weight = FontWeight.Bold, maxLines = 1, lineHeight = fontSize)
                    Box(Modifier.fillMaxWidth().height((height * scale).dp).clip(RoundedCornerShape((8 * scale).dp))
                        .background(Brush.verticalGradient(listOf(FootprintRed, Color(0xFFFF8F8F)))))
                }
            }
        }
    }
}

@Composable private fun WeeklyFriendFavorites(items: List<ListeningMonthlyFriendSong>, onAction: (ListeningMonthlyAction) -> Unit) {
    val scale = LocalFootprintScale.current
    WeeklyCard("本周好友挚爱", "friends") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            items.take(3).forEach { song ->
                Column(Modifier.width((88 * scale).dp).weeklyAction(song.action, onAction), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.weeklyAction(song.friend.action, onAction), verticalAlignment = Alignment.CenterVertically) {
                        FootprintCover(song.friend.avatar, song.friend.name, Modifier.size((16.667f * scale).dp).clip(CircleShape))
                        FootprintText(song.friend.name, Modifier.padding(start = (4 * scale).dp), size = 12f, maxLines = 1)
                    }
                    song.playCount?.let { WeeklyNumberText("${it}次收听", 29.333f, 15.333f,
                        modifier = Modifier.padding(top = (2 * scale).dp, bottom = (14.667f * scale).dp).wrapContentWidth(Alignment.CenterHorizontally, unbounded = true)) }
                    FootprintCover(song.cover, song.name, Modifier.size((76 * scale).dp).clip(RoundedCornerShape((10 * scale).dp)))
                    FootprintText(song.name, Modifier.padding(top = (12 * scale).dp), size = 14f, weight = FontWeight.SemiBold, maxLines = 1, lineHeight = 14f)
                }
            }
        }
    }
}

@Composable private fun WeeklyCurrentFriends(items: List<ListeningMonthlyFriendSong>, onAction: (ListeningMonthlyAction) -> Unit) {
    val scale = LocalFootprintScale.current
    WeeklyCard("本周好友在听", "current_friends") {
        Column(Modifier.padding(horizontal = (20 * scale).dp), verticalArrangement = Arrangement.spacedBy((18 * scale).dp)) {
            items.take(3).forEach { song ->
                Column {
                    Row(Modifier.weeklyAction(song.action, onAction), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((12 * scale).dp)) {
                        FootprintCover(song.cover, song.name, Modifier.size((48 * scale).dp).clip(RoundedCornerShape((4 * scale).dp)))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((4 * scale).dp)) {
                            FootprintText(song.name, size = 16f, weight = FontWeight.SemiBold, maxLines = 1)
                            if (song.artist.isNotBlank()) FootprintText(song.artist, size = 12f, color = FootprintMuted, maxLines = 1)
                        }
                        ListeningFriendLikeButton(song)
                    }
                    Row(Modifier.padding(top = (9 * scale).dp).weeklyAction(song.friend.action, onAction), verticalAlignment = Alignment.CenterVertically) {
                        FootprintCover(song.friend.avatar, song.friend.name, Modifier.size((16.667f * scale).dp).clip(CircleShape))
                        FootprintText(song.friend.name, Modifier.padding(start = (7.333f * scale).dp).weight(1f, fill = false), size = 12f, maxLines = 1)
                        FootprintText("最近在听", Modifier.padding(start = (4.667f * scale).dp), size = 12f, color = FootprintMuted)
                    }
                }
            }
        }
    }
}

@Composable private fun WeeklyLongAudio(items: List<ListeningWeeklyAudio>) {
    val scale = LocalFootprintScale.current
    key(items) {
        val pager = rememberPagerState { items.size }
        Column(Modifier.fillMaxWidth().weeklyShadow(scale).background(FootprintSurface, RoundedCornerShape((20 * scale).dp))
            .padding(bottom = (12 * scale).dp).testTag("listening_week_audio")) {
            HorizontalPager(pager, Modifier.fillMaxWidth().testTag("listening_week_audio_pager")) { index ->
                val audio = items[index]
                val podcast = audio.type == "podcast"
                val label = if (podcast) "播客" else "有声书"
                Column(Modifier.fillMaxWidth()) {
                    WeeklyTitle("本周$label")
                    Row(Modifier.fillMaxWidth().padding(top = (15 * scale).dp, bottom = (16 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
                        WeeklyAudioMetric(weeklyDuration(audio.durationMs), "${label}收听时长", Modifier.weight(1f))
                        Box(Modifier.width((.667f * scale).dp).height((40 * scale).dp).background(FootprintInk.copy(alpha = .1f)))
                        WeeklyAudioMetric("${if (audio.episodes > 999) "999+" else audio.episodes}${if (podcast) "期" else "集"}",
                            "${label}收听${if (podcast) "期数" else "集数"}", Modifier.weight(1f))
                    }
                }
            }
            if (items.size > 1) Row(Modifier.fillMaxWidth().padding(bottom = (4 * scale).dp), horizontalArrangement = Arrangement.Center) {
                repeat(items.size) { index -> Box(Modifier.padding(horizontal = (4.333f * scale).dp).size((6.667f * scale).dp).clip(CircleShape)
                    .background(if (index == pager.currentPage) FootprintRed else FootprintInk.copy(alpha = .3f))) }
            }
        }
    }
}

@Composable private fun WeeklyAudioMetric(value: String, label: String, modifier: Modifier) {
    val scale = LocalFootprintScale.current
    Column(modifier.height((50 * scale).dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        WeeklyNumberText(value, numberSize = if (value.length > 8) 27f else 30f)
        FootprintText(label, size = 12f, color = FootprintRed, weight = FontWeight.SemiBold, maxLines = 1, lineHeight = 12f)
    }
}

@Composable private fun WeeklyCard(title: String, tag: String, content: @Composable ColumnScope.() -> Unit) {
    val scale = LocalFootprintScale.current
    Column(Modifier.fillMaxWidth().weeklyShadow(scale).background(FootprintSurface, RoundedCornerShape((20 * scale).dp)).testTag("listening_week_$tag")) {
        WeeklyTitle(title)
        Column(Modifier.fillMaxWidth().padding(top = (27 * scale).dp, bottom = (30 * scale).dp), content = content)
    }
}

@Composable private fun WeeklyTitle(title: String) {
    val scale = LocalFootprintScale.current
    FootprintText(title, Modifier.padding(start = (20 * scale).dp, top = (20 * scale).dp), size = 34f,
        weight = FontWeight.SemiBold, maxLines = 1, lineHeight = 34f,
        brush = Brush.verticalGradient(listOf(FootprintInk.copy(alpha = .18f), FootprintInk.copy(alpha = .09f))))
}

@Composable private fun WeeklyNumberText(text: String, numberSize: Float = 30f, unitSize: Float = 16f,
    color: Color = FootprintInk, modifier: Modifier = Modifier, numberWeight:FontWeight=FontWeight.SemiBold,unitGap:Float=2f) {
    val scale = LocalFootprintScale.current
    val parts = Regex("[0-9]+(?:[.,:][0-9]+)*(?:\\+)?|[^0-9]+").findAll(text).map { it.value }.toList()
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        parts.forEach { part ->
            val numeric = part.firstOrNull()?.isDigit() == true
            FootprintText(part, if (numeric) Modifier else Modifier.padding(start = (unitGap * scale).dp, bottom = (4 * scale).dp),
                size = if (numeric) numberSize else unitSize, color = color, numeric = numeric, weight = if(numeric)numberWeight else FontWeight.SemiBold, maxLines = 1,
                lineHeight = if (numeric) numberSize else unitSize)
        }
    }
}

private fun Modifier.weeklyAction(action: ListeningMonthlyAction?, onAction: (ListeningMonthlyAction) -> Unit): Modifier =
    if (action == null) this else clickable(role = Role.Button) { onAction(action) }
private fun Modifier.weeklyShadow(scale: Float) = dropShadow(RoundedCornerShape((20 * scale).dp),
    Shadow(radius = (27.7706f * scale).dp, offset = DpOffset(0.dp, (4.13625f * scale).dp), color = Color.Black.copy(alpha = .06f)))
private fun weeklyPercent(value: Double): String = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
private fun weeklyDuration(ms: Long): String {
    val minutes = ms / 60_000
    return when { minutes / 60 > 999 -> "999+小时"; minutes >= 60 -> "${minutes / 60}小时${minutes % 60}分"; else -> "${minutes}分钟" }
}
private const val WeeklyMedia = "https://sg.music.163.com/st/listen-home/static/media/"

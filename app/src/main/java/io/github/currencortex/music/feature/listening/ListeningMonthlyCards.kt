package io.github.currencortex.music.feature.listening

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.currencortex.music.R

/** Native equivalents of the official monthly blocks, in the original server-defined order. */
@Composable internal fun ListeningMonthlyCards(
    report: ListeningDisplay,
    range: ListeningRange,
    onAction: (ListeningMonthlyAction) -> Unit = {},
    onRank: (() -> Unit)? = null,
) {
    val monthly = report.monthly
    if (monthly == null) {
        ListeningDetailCards(report, ListeningPeriod.MONTH, range)
        return
    }
    val scale = LocalFootprintScale.current
    if (!monthly.complete) {
        Box(Modifier.fillMaxWidth().padding(vertical = (32 * scale).dp).testTag("listening_month_pending"), contentAlignment = Alignment.Center) {
            FootprintText("每月1日更新", size = 14f, color = FootprintMuted)
        }
        return
    }
    val title = monthName(range.start.monthValue)
    val covers = report.coverUrls.filter(String::isNotBlank).ifEmpty { report.tracks.map { it.cover }.filter(String::isNotBlank) }
    val ages = report.facts.filter { it.title.startsWith("听歌年代 · ") }
    val languages = report.facts.filter { it.title.startsWith("听歌语言 · ") }
    key(range.start) {
    Column(Modifier.fillMaxWidth().testTag("listening_month_details"), verticalArrangement = Arrangement.spacedBy((24 * scale).dp)) {
        monthly.sendVipDays?.takeIf { it > 0 }?.let { MonthlyVipInvite(it, onAction) }
        if (!monthly.wallpaperBottom) FootprintRecordWall(covers, title, report.songCount, onRank)
        monthly.topSong?.let { MonthlyHighlight(it, title, artist = false, onAction) }
        if (monthly.friendSongs.isNotEmpty()) MonthlyFriendSongs(monthly.friendSongs, title, onAction)
        if (monthly.friendKeywords.size > 1) MonthlyFriendKeywords(monthly.friendKeywords, onAction)
        monthly.vip?.let { MonthlyVip(it, title, monthly.superVip, onAction) }
        if (monthly.longAudio.isNotEmpty()) MonthlyLongAudio(monthly.longAudio, title, onAction)
        monthly.topArtist?.let { MonthlyHighlight(it, title, artist = true, onAction) }
        monthly.style?.let { MonthlyStyle(it, title, onAction) }
        if (ages.isNotEmpty()) FootprintDecades(ages, title, onAction)
        if (languages.isNotEmpty()) FootprintLanguages(languages, title, onAction)
        if (monthly.wallpaperBottom) FootprintRecordWall(covers, title, report.songCount, onRank)
    }
    }
}

/** Calendar summary uses exactly the first two supplied sections, never reordered generic facts. */
@Composable internal fun ListeningSummarySections(sections: List<ListeningSummarySection>, modifier: Modifier = Modifier) {
    if (sections.size < 2) return
    val scale = LocalFootprintScale.current
    Row(modifier.fillMaxWidth().heightIn(min = (50 * scale).dp).testTag("listening_summary_sections"),
        verticalAlignment = Alignment.CenterVertically) {
        sections.take(2).forEachIndexed { index, section ->
            if (index == 1) Box(Modifier.width((.667f * scale).dp).height((41.667f * scale).dp).background(FootprintInk.copy(alpha = .06f)))
            Column(Modifier.weight(1f).testTag("listening_summary_${section.type}"), horizontalAlignment = Alignment.CenterHorizontally) {
                MonthlyNumberText(section.value, numberSize = 30f, unitSize = 16f)
                Row(Modifier.padding(top = (5 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
                    SummaryIcons[section.type]?.let { AsyncImage(OfficialMedia + it, null, Modifier.size((12 * scale).dp)) }
                    FootprintText(section.label, Modifier.padding(start = (3 * scale).dp), size = 14f, color = FootprintRed, weight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable private fun MonthlyHighlight(
    item: ListeningMonthlyHighlight, title: String, artist: Boolean, onAction: (ListeningMonthlyAction) -> Unit,
) {
    val scale = LocalFootprintScale.current
    val reveal = rememberListeningTopReveal(item, title, artist)
    FootprintCard(Modifier.testTag(if (artist) "listening_month_artist" else "listening_month_song")) {
        Box(Modifier.fillMaxWidth().height((207 * scale).dp)) {
            MonthlyBlockHeading(title, if (artist) "音乐人" else "单曲", Modifier.padding((20 * scale).dp))
            FootprintCover(item.cover, item.name, Modifier.align(Alignment.TopEnd).padding(top = (20 * scale).dp, end = (20 * scale).dp)
                .size((130 * scale).dp).clip(if (artist) CircleShape else RoundedCornerShape((12 * scale).dp)).monthlyAction(item.action, onAction))
            MonthlyTop(Modifier.offset((20 * scale).dp, (126 * scale).dp).testTag(if(artist) "listening_artist_top" else "listening_song_top"), revealProgress=reveal.progress)
            item.playCount?.let { count -> MonthlyNumberText("${count}次收听", 51f, 30f,
                modifier = Modifier.offset((20 * scale).dp, (156 * scale).dp).monthlyAction(item.action, onAction)) }
        }
        if (item.name.isNotBlank()) Row(
            Modifier.padding(start = (20 * scale).dp, end = (20 * scale).dp, top = (10.667f * scale).dp).then(reveal.trigger),
            verticalAlignment = Alignment.Bottom) {
            FootprintText(item.name, Modifier.weight(1f, fill = false).monthlyAction(item.action, onAction),
                size = 30f, weight = FontWeight.Bold, maxLines = 3)
            if (artist) (item.action as? ListeningMonthlyAction.Artist)?.let { action ->
                ListeningArtistFollowButton(action.id) { onAction(action) }
            }
        }
        if (item.sections.isNotEmpty()) {
            MonthlyDivider(Modifier.padding(top = (17 * scale).dp))
            Column(Modifier.padding(start = (20 * scale).dp, end = (20 * scale).dp, top = (20 * scale).dp, bottom = (8 * scale).dp),
                verticalArrangement = Arrangement.spacedBy((16 * scale).dp)) {
                item.sections.take(2).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = (8 * scale).dp).monthlyAction(row.action, onAction),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((16 * scale).dp)) {
                        FootprintCover(row.cover, row.name, Modifier.size((80 * scale).dp)
                            .clip(if (artist) CircleShape else RoundedCornerShape((10 * scale).dp)))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((4 * scale).dp)) {
                            if (row.field.isNotBlank()) FootprintText(row.field, size = 12f, color = FootprintRed, maxLines = 2)
                            if (row.text.isNotBlank()) MonthlyNumberText(row.text, 30f, 16f)
                            if (row.name.isNotBlank()) FootprintText(row.name, size = 16f, weight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
        } else Spacer(Modifier.height((24 * scale).dp))
    }
}

@Composable private fun MonthlyFriendSongs(items: List<ListeningMonthlyFriendSong>, title: String, onAction: (ListeningMonthlyAction) -> Unit) {
    val scale = LocalFootprintScale.current
    val pages = (if (items.size < 3) items else items.take(items.size / 3 * 3)).chunked(3)
    FootprintCard(Modifier.testTag("listening_month_friends_songs")) {
        MonthlyCardTitle("${title}好友挚爱")
        key(items) {
            val pager = rememberPagerState { pages.size }
            HorizontalPager(pager, modifier = Modifier.fillMaxWidth().testTag("listening_friend_pager"), verticalAlignment = Alignment.Top) { index ->
                Column(Modifier.padding(start = (20 * scale).dp, end = (20 * scale).dp, top = (27 * scale).dp, bottom = (10 * scale).dp),
                    verticalArrangement = Arrangement.spacedBy((16 * scale).dp)) {
                    pages[index].forEach { song ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((15.667f * scale).dp)) {
                            FootprintCover(song.cover, song.name, Modifier.size((80 * scale).dp).clip(RoundedCornerShape((6.667f * scale).dp))
                                .monthlyAction(song.action, onAction))
                            Column(Modifier.weight(1f)) {
                                Row(Modifier.monthlyAction(song.friend.action, onAction), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy((3.667f * scale).dp)) {
                                    FootprintCover(song.friend.avatar, song.friend.name, Modifier.size((16.667f * scale).dp).clip(CircleShape))
                                    FootprintText(song.friend.name, size = 12f, maxLines = 1)
                                }
                                song.playCount?.let { MonthlyNumberText("${it}次收听", 30f, 16f,
                                    modifier = Modifier.padding(top = (2.333f * scale).dp).monthlyAction(song.action, onAction)) }
                                FootprintText(song.name, Modifier.padding(top = (4 * scale).dp).monthlyAction(song.action, onAction),
                                    size = 16f, weight = FontWeight.SemiBold, maxLines = 1)
                            }
                            ListeningFriendLikeButton(song)
                        }
                    }
                }
            }
            MonthlyPagerDots(pages.size, pager.currentPage)
        }
    }
}

@Composable internal fun MonthlyFriendKeywords(items: List<ListeningMonthlyFriendKeyword>, onAction: (ListeningMonthlyAction) -> Unit) {
    val scale = LocalFootprintScale.current
    val visible = items.take(if (items.size >= 4) 4 else 2)
    FootprintCard(Modifier.testTag("listening_month_friends_keywords")) {
        MonthlyCardTitle("好友音乐关键词")
        Column(Modifier.padding(horizontal = (17.333f * scale).dp, vertical = (13.333f * scale).dp)
            .padding(top = (14 * scale).dp), verticalArrangement = Arrangement.spacedBy((10.667f * scale).dp)) {
            visible.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((12 * scale).dp)) {
                    pair.forEach { keyword ->
                        val tint = KeywordColors[keyword.code] ?: Color(0xFFDAE4F3)
                        Column(Modifier.weight(1f).height((164.333f * scale).dp).clip(RoundedCornerShape((15 * scale).dp))
                            .background(Brush.linearGradient(if (FootprintDark) listOf(FootprintSurface, androidx.compose.ui.graphics.lerp(FootprintSurface, tint, .16f)) else listOf(Color(0xFFFEFEFE), tint)))
                            .border((.4f * scale).dp, FootprintInk.copy(alpha = .06f), RoundedCornerShape((15 * scale).dp))
                            .padding(horizontal = (6 * scale).dp, vertical = (16.667f * scale).dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            FootprintText(keyword.title, size = 18.667f, weight = FontWeight.SemiBold, maxLines = 1)
                            if (keyword.subtitle.isNotBlank()) FootprintText(keyword.subtitle, Modifier.padding(top = (6 * scale).dp), size = 14f, maxLines = 2)
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                keyword.friends.take(3).forEachIndexed { index, friend ->
                                    FootprintCover(friend.avatar, friend.name,
                                        Modifier.offset(x = (-index * 4 * scale).dp).size(((if (keyword.friends.size == 1) 45 else 35) * scale).dp)
                                            .clip(CircleShape).monthlyAction(friend.action, onAction))
                                }
                            }
                            keyword.friends.firstOrNull()?.let { friend ->
                                FootprintText(friend.name + if (keyword.friends.size > 1) " 等好友" else "",
                                    Modifier.monthlyAction(friend.action, onAction), size = 12f, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun MonthlyVip(item: ListeningMonthlyHighlight, title: String, superVip: Boolean, onAction: (ListeningMonthlyAction) -> Unit) {
    val scale = LocalFootprintScale.current
    val gold = Color(0xFFDCCAA8)
    val reveal = rememberListeningTopReveal(item, title, superVip)
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape((20 * scale).dp)).background(Color(0xFF2C2525)).testTag("listening_month_vip")) {
        AsyncImage(VipBackground, null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height((176.667f * scale).dp)) {
                FootprintText("${title}最爱", Modifier.offset((16.667f * scale).dp, (20 * scale).dp), size = 30f, color = gold, weight = FontWeight.Bold)
                AsyncImage(OfficialMedia + if (superVip) "svip_privilege_month.887ba078.png" else "vip_privilege_month.4039c614.png", null,
                    Modifier.offset((16.667f * scale).dp, (53.333f * scale).dp).size((148 * scale).dp, ((if (superVip) 42.667f else 34.667f) * scale).dp),
                    contentScale = ContentScale.FillBounds)
                FootprintCover(item.cover, item.name, Modifier.align(Alignment.TopEnd).padding((20 * scale).dp)
                    .size((130 * scale).dp).clip(RoundedCornerShape((12 * scale).dp)).monthlyAction(item.action, onAction))
                MonthlyTop(Modifier.offset((20 * scale).dp, (150 * scale).dp).testTag("listening_vip_top"), gold = true, revealProgress=reveal.progress)
            }
            if (item.name.isNotBlank()) FootprintText(item.name,
                Modifier.padding(start = (20 * scale).dp, end = (20 * scale).dp, top = (10 * scale).dp).monthlyAction(item.action, onAction),
                size = 36f, color = gold, weight = FontWeight.Bold, maxLines = 3)
            if (item.sections.isNotEmpty()) {
                MonthlyDivider(Modifier.padding(top = (20 * scale).dp), Color.White.copy(alpha = .1f))
                Row(Modifier.fillMaxWidth().padding(top = (20 * scale).dp, bottom = (19 * scale).dp).then(reveal.trigger), verticalAlignment = Alignment.CenterVertically) {
                    item.sections.take(2).forEachIndexed { index, section ->
                        if (index == 1) Box(Modifier.height((80 * scale).dp).width(scale.dp).background(Color.White.copy(alpha = .1f)))
                        Column(Modifier.weight(1f).heightIn(min = (80 * scale).dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            if (section.text.isNotBlank()) MonthlyNumberText(section.text, 36f, 24f, gold)
                            val label = when (section.type) {
                                "vipSongCount" -> "会员歌曲收听数"
                                "vipSongDuration" -> "会员歌曲收听时长"
                                else -> section.field
                            }
                            if (label.isNotBlank()) FootprintText(label, Modifier.padding(top = (11 * scale).dp), size = 12f, color = gold.copy(alpha = .8f), maxLines = 2)
                        }
                    }
                }
            } else Spacer(Modifier.fillMaxWidth().height((24 * scale).dp).then(reveal.trigger))
        }
    }
}

@Composable private fun MonthlyLongAudio(items: List<ListeningMonthlyLongAudio>, title: String, onAction: (ListeningMonthlyAction) -> Unit) {
    key(items) {
        val pager = rememberPagerState { items.size }
        Column(Modifier.fillMaxWidth().testTag("listening_month_audio")) {
            HorizontalPager(pager, Modifier.fillMaxWidth().testTag("listening_audio_pager"), verticalAlignment = Alignment.Top) { index ->
                MonthlyLongAudioItem(items[index], title, onAction)
            }
            MonthlyPagerDots(items.size, pager.currentPage)
        }
    }
}

@Composable private fun MonthlyLongAudioItem(item: ListeningMonthlyLongAudio, title: String, onAction: (ListeningMonthlyAction) -> Unit) {
    val scale = LocalFootprintScale.current
    val reveal = rememberListeningTopReveal(item, title)
    val audiobook = item.type.equals("audiobook", ignoreCase = true)
    val name = if (audiobook) "有声书" else "播客"
    val unit = if (audiobook) "集" else "期"
    FootprintCard(Modifier.testTag("listening_month_audio_${item.type}")) {
        Box(Modifier.fillMaxWidth().height((150 * scale).dp)) {
            MonthlyBlockHeading(title, name, Modifier.padding((20 * scale).dp))
            FootprintCover(item.cover, item.name, Modifier.align(Alignment.TopEnd).padding(top = (20 * scale).dp, end = (20 * scale).dp)
                .size((130 * scale).dp).clip(RoundedCornerShape((12 * scale).dp)).monthlyAction(item.action, onAction))
            MonthlyTop(Modifier.offset((20 * scale).dp, (126 * scale).dp).testTag("listening_audio_top_${item.type}"), revealProgress=reveal.progress)
        }
        if (item.name.isNotBlank()) FootprintText(item.name,
            Modifier.padding(start = (20 * scale).dp, end = (20 * scale).dp, top = (10 * scale).dp).then(reveal.trigger).monthlyAction(item.action, onAction),
            size = 36f, weight = FontWeight.Bold, maxLines = 3)
        val topInfo = listOfNotNull(item.topDurationMs?.let(::monthlyDuration), item.topEpisodes?.let { "$it$unit" }).joinToString(" · ")
        if (topInfo.isNotBlank()) FootprintText("本月收听 $topInfo", Modifier.padding(start = (20 * scale).dp, top = (9.333f * scale).dp), size = 14f)
        if (item.durationMs != null || item.episodes != null) {
            MonthlyDivider(Modifier.padding(top = (24 * scale).dp))
            Row(Modifier.fillMaxWidth().padding(top = (28.333f * scale).dp, bottom = (16 * scale).dp)) {
                item.durationMs?.let { MonthlyAudioStat(monthlyDuration(it), "${name}收听时长", Modifier.weight(1f)) }
                item.episodes?.let { MonthlyAudioStat("$it$unit", "${name}收听${if (audiobook) "集数" else "期数"}", Modifier.weight(1f)) }
            }
        } else Spacer(Modifier.height((24 * scale).dp))
    }
}

@Composable private fun MonthlyAudioStat(value: String, label: String, modifier: Modifier) {
    val scale = LocalFootprintScale.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        MonthlyNumberText(value, 30f, 16f)
        FootprintText(label, Modifier.padding(top = (5 * scale).dp), size = 14f, color = FootprintRed, weight = FontWeight.SemiBold)
    }
}

@Composable private fun MonthlyStyle(item: ListeningMonthlyStyle, title: String, onAction: (ListeningMonthlyAction) -> Unit) {
    val scale = LocalFootprintScale.current
    val reveal = rememberListeningTopReveal(item, title)
    FootprintCard(Modifier.testTag("listening_month_style")) {
        MonthlyCardTitle("${title}曲风")
        MonthlyTop(Modifier.padding(start = (20 * scale).dp, top = (31.667f * scale).dp).testTag("listening_style_top"), revealProgress = reveal.progress, slide=false)
        FootprintText(item.main.name, Modifier.padding(start = (20 * scale).dp, top = (8.333f * scale).dp, end = (20 * scale).dp)
            .then(if (item.recommendation == null) reveal.trigger else Modifier)
            .monthlyAction(item.main.action, onAction), size = 42f, weight = FontWeight.Bold, maxLines = 2)
        item.secondary?.let { FootprintText(it.name, Modifier.padding(start = (20 * scale).dp, end = (20 * scale).dp, top = (12 * scale).dp)
            .monthlyAction(it.action, onAction), size = 30f, weight = FontWeight.Bold, maxLines = 2) }
        item.recommendation?.let { recommendation ->
            Row(Modifier.padding(start = (20 * scale).dp, end = (20 * scale).dp, top = (14 * scale).dp)
                .then(reveal.trigger)
                .monthlyAction(recommendation.action, onAction).testTag("listening_month_recommendation"), verticalAlignment = Alignment.CenterVertically) {
                FootprintText("「推荐歌单」 ${recommendation.text}", Modifier.weight(1f), size = 14f, color = FootprintInk.copy(alpha = .6f), maxLines = 2)
                if (recommendation.action != null) FootprintText("›", size = 20f, color = FootprintMuted)
            }
        }
        val first = item.genres.firstOrNull()?.percent?.takeIf { it > 0 }
        val second = item.genres.getOrNull(1)?.percent
        Column(Modifier.fillMaxWidth().padding(horizontal = (20 * scale).dp, vertical = (24 * scale).dp), verticalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
            item.genres.take(3).forEachIndexed { index, genre ->
                val width = if (first != null && genre.percent != null) {
                    val proportional = (230 * genre.percent / first).toFloat().coerceIn(0f, 230f)
                    when (index) {
                        1 -> proportional.coerceAtLeast(76f)
                        2 -> proportional.coerceAtLeast(if (genre.percent == second) 76f else 61.333f)
                        else -> proportional
                    }
                } else null
                if (width != null) {
                    Row(Modifier.fillMaxWidth().monthlyAction(genre.action, onAction), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width((width * scale).dp).height((30 * scale).dp).clip(RoundedCornerShape((15 * scale).dp))
                            .background(Brush.horizontalGradient(listOf(FootprintRed, Color(0xFFFF6D6D))))) {
                            val labelSize = ((width - 24) / genre.name.length.coerceAtLeast(1)).coerceIn(10.333f, 16f)
                            FootprintText(genre.name, Modifier.align(Alignment.CenterStart).padding(horizontal = (12 * scale).dp), size = labelSize,
                                color = Color.White, weight = FontWeight.SemiBold, maxLines = 2)
                        }
                        FootprintText("${java.math.BigDecimal.valueOf(requireNotNull(genre.percent)).stripTrailingZeros().toPlainString()}%",
                            Modifier.padding(start = (10 * scale).dp), size = 24f, numeric = true, weight = FontWeight.Bold, maxLines = 1)
                    }
                } else FootprintText(genre.name, Modifier.monthlyAction(genre.action, onAction), size = 16f, maxLines = 1)
            }
        }
    }
}

@Composable private fun MonthlyVipInvite(days: Long, onAction: (ListeningMonthlyAction) -> Unit) {
    val scale = LocalFootprintScale.current
    Box(Modifier.fillMaxWidth().height((144.333f * scale).dp).clip(RoundedCornerShape((20 * scale).dp))
        .background(Color(0xFF29212C)).monthlyAction(ListeningMonthlyAction.Url(VipInviteUrl), onAction).testTag("listening_month_vip_invite")) {
        AsyncImage(VipInviteBackground, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding((20 * scale).dp), verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    FootprintText("可送", size = 21.333f, color = Color(0xFFF1CFC5), weight = FontWeight.SemiBold)
                    FootprintText(days.toString(), Modifier.padding(horizontal = (1.333f * scale).dp), size = 28.667f, color = Color(0xFFF1CFC5), weight = FontWeight.Bold, numeric = true)
                    FootprintText("天", size = 21.333f, color = Color(0xFFF1CFC5), weight = FontWeight.SemiBold)
                }
                FootprintText("尊享免费会员赠送额度", size = 10f, color = Color(0xFFF1CFC5).copy(alpha = .7f))
            }
            Box(Modifier.size((99.667f * scale).dp, (38.333f * scale).dp).clip(CircleShape).background(FootprintRed), contentAlignment = Alignment.Center) {
                FootprintText("送好友", size = 16f, color = Color.White, weight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable private fun MonthlyBlockHeading(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        FootprintText(title, size = 34f, color = FootprintInk.copy(alpha = .18f), weight = FontWeight.SemiBold)
        FootprintText(subtitle, size = 34f, color = FootprintInk.copy(alpha = .10f), weight = FontWeight.SemiBold)
    }
}

@Composable private fun MonthlyCardTitle(title: String) {
    val scale = LocalFootprintScale.current
    FootprintText(title, Modifier.padding(start = (20 * scale).dp, top = (20 * scale).dp, end = (12 * scale).dp),
        size = 34f, color = FootprintPale, weight = FontWeight.SemiBold, maxLines = 2)
}

private val TopRevealEase = CubicBezierEasing(.25f, .1f, .25f, 1f)

@Composable private fun MonthlyTop(modifier: Modifier = Modifier, gold: Boolean = false, revealProgress: Float, slide:Boolean=true) {
    val scale = LocalFootprintScale.current
    val iconScale = if (revealProgress < .8f) 1.5f * TopRevealEase.transform(revealProgress/.8f)
        else 1.5f - .5f * TopRevealEase.transform((revealProgress-.8f)/.2f)
    Row(modifier.graphicsLayer { if(slide) translationY=(7.333f*scale).dp.toPx()*(1f-(revealProgress*2f).coerceIn(0f,1f)) },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((4.5f * scale).dp)) {
        Image(painterResource(if (gold) R.drawable.listening_top_gold else R.drawable.listening_top_red), null,
            Modifier.size((24 * scale).dp).testTag("listening_top_star").graphicsLayer { scaleX = iconScale; scaleY = iconScale })
        FootprintText("TOP", Modifier.graphicsLayer { alpha = (revealProgress*2f).coerceIn(0f,1f) }, size = 24f,
            color = if (gold) Color(0xFFDCCAA8) else FootprintRed, numeric = true, weight = FontWeight.Black, lineHeight=24f, maxLines=1)
    }
}

@Composable private fun MonthlyDivider(modifier: Modifier = Modifier, color: Color = FootprintInk.copy(alpha = .06f)) {
    val scale = LocalFootprintScale.current
    Box(modifier.fillMaxWidth().padding(horizontal = (20 * scale).dp).height(scale.dp).background(color))
}

/** Split numeric runs without discarding editorial prefixes, units, punctuation or multiple numbers. */
@Composable private fun MonthlyNumberText(
    text: String, numberSize: Float, unitSize: Float, color: Color = FootprintInk, modifier: Modifier = Modifier,
) {
    val scale = LocalFootprintScale.current
    val parts = Regex("[0-9]+(?:[.,:][0-9]+)*|[^0-9]+").findAll(text).map { it.value }.toList()
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        parts.forEach { part ->
            val numeric = part.firstOrNull()?.isDigit() == true
            FootprintText(part, if (numeric) Modifier else Modifier.padding(bottom = (2 * scale).dp),
                size = if (numeric) numberSize else unitSize, color = color, numeric = numeric, weight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable private fun MonthlyPagerDots(count: Int, selected: Int) {
    if (count < 2) return
    val scale = LocalFootprintScale.current
    Row(Modifier.fillMaxWidth().padding(top = (12 * scale).dp, bottom = (4 * scale).dp), horizontalArrangement = Arrangement.Center) {
        repeat(count) { index -> Box(Modifier.padding(horizontal = (3.333f * scale).dp).size((6.667f * scale).dp)
            .clip(CircleShape).background(if (index == selected) FootprintInk else FootprintInk.copy(alpha = .15f))) }
    }
}

private fun Modifier.monthlyAction(action: ListeningMonthlyAction?, onAction: (ListeningMonthlyAction) -> Unit): Modifier =
    if (action == null) this else clickable(role = Role.Button) { onAction(action) }

private fun monthlyDuration(milliseconds: Long): String {
    val minutes = milliseconds.coerceAtLeast(0) / 60_000
    return if (minutes >= 60) "${minutes / 60}小时${minutes % 60}分" else "${minutes}分"
}

private const val OfficialMedia = "https://sg.music.163.com/st/listen-home/static/media/"
private const val VipBackground = "https://p5.music.126.net/obj/wonDlsKUwrLClGjCm8Kx/32142462872/ccaf/f10e/265b/8ac2ab9849aa54990b989ac42e1705b2.png"
private const val VipInviteBackground = "https://p6.music.126.net/obj/wonDlsKUwrLClGjCm8Kx/62881947435/7898/486c/e10d/7bb2272286309199f9a85461d067c7b6.png"
private const val VipInviteUrl = "https://mp.music.163.com/66bc28095eb1d7a039fb17cc/invite/index.html?nm_style=sbt&bounce=false&extChannel=footprint"
private val SummaryIcons = mapOf(
    "lateSleep" to "latest_listen_icon_dark.535ed3fb.png", "earlySleep" to "earliest_listen_icon_dark.be643684.png",
    "maxPlayUserLikeChannel" to "like_listen_icon_dark.c4a6c5c4.png", "maxPlayRcmdChannel" to "recommend_listen_icon_dark.aa11e4db.png",
    "durationDiffPositive" to "compared_up_icon_dark.da1e7c32.png", "durationDiffNegative" to "compared_down_icon_dark.5d48fc76.png",
)
private val KeywordColors = mapOf(
    "DURATION1" to Color(0xFFDAE4F3), "DURATION2" to Color(0xFFF2F9DC), "DURATION3" to Color(0xFFFAE8E1),
    "EMOTION1" to Color(0xFFE7E7FC), "EMOTION2" to Color(0xFFFFEBEC), "EMOTION3" to Color(0xFFFFDEED),
    "TIME1" to Color(0xFFFCF7E7), "TIME2" to Color(0xFFE8E5FC), "HOBBIT1" to Color(0xFFE7F4FC),
    "HOBBIT2" to Color(0xFFF7EBFF), "HOBBIT3" to Color(0xFFE3EEFF),
)

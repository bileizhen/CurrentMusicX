package io.github.currencortex.music.feature.listening

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.MotionDurationScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import coil3.compose.AsyncImage
import java.text.NumberFormat
import java.util.Locale

internal val LocalListeningMotionEnabled = staticCompositionLocalOf { true }
internal val LocalListeningExportDepth = staticCompositionLocalOf<MutableIntState?> { null }

/** The lower report follows the official wall / decade / language order, using real report fields. */
@Composable internal fun ListeningDetailCards(report: ListeningDisplay, period: ListeningPeriod, range: ListeningRange,
    onAction: (ListeningMonthlyAction) -> Unit = {}, onRank: (() -> Unit)? = null) {
    val scale = LocalFootprintScale.current
    val ages = report.facts.filter { it.title.startsWith("听歌年代 · ") }.sortedBy { decadeSortKey(it.title.substringAfter(" · ")) }
    val languages = report.facts.filter { it.title.startsWith("听歌语言 · ") }
    val title = if (period == ListeningPeriod.MONTH) monthName(range.start.monthValue) else "本周"
    val covers = report.coverUrls.filter(String::isNotBlank).ifEmpty {
        report.tracks.map { it.cover }.filter(String::isNotBlank)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy((20 * scale).dp)) {
        if (covers.isNotEmpty() || report.songCount != null) FootprintRecordWall(covers, title, report.songCount, onRank)
        if (ages.isNotEmpty()) FootprintDecades(ages, title, onAction)
        if (languages.isNotEmpty()) FootprintLanguages(languages, title, onAction)
    }
}

@Composable internal fun FootprintRecordWall(covers: List<String>, title: String, songCount: Int?, onRank: (() -> Unit)? = null) {
    val scale = LocalFootprintScale.current
    val images = covers.filter(String::isNotBlank).take(20)
    Column(Modifier.fillMaxWidth().testTag("listening_record_wall")) {
        Box(Modifier.fillMaxWidth().aspectRatio(.8f).clip(RoundedCornerShape((20 * scale).dp))
            .background(FootprintPale).then(if (onRank != null && (songCount ?: 0) > 0)
                Modifier.clickable(role = Role.Button, onClick = onRank) else Modifier)) {
            if (images.isNotEmpty()) ListeningMovingWall(images)
            else AsyncImage("https://p6.music.126.net/obj/wonDlsKUwrLClGjCm8Kx/32164179079/c1e2/0b54/666f/642476dfe3e202823ed6ab944bbcf343.png",
                null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            if (images.isNotEmpty()) AsyncImage("https://p6.music.126.net/obj/wonDlsKUwrLClGjCm8Kx/32242611925/ea7c/9651/21c7/b24cfe74896a333ada1c448e1a2f721b.png",
                null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            FootprintText("${title}唱片墙", Modifier.align(Alignment.TopStart).padding((22.333f * scale).dp),
                size = 34f, color = Color.White, weight = FontWeight.SemiBold,
                brush = Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = .52f))))
            if (songCount == 0) FootprintText("${title}暂未听歌", Modifier.align(Alignment.Center), size = 20f,
                color = Color.White, weight = FontWeight.SemiBold)
            Column(Modifier.align(Alignment.BottomEnd).padding(end = (19.667f * scale).dp, bottom = (22.667f * scale).dp),
                horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    FootprintText(songCount?.toString() ?: "—", size = 51f,
                        color = Color.White, weight = FontWeight.Bold, numeric = true)
                    FootprintText("首", Modifier.padding(bottom = (6.667f * scale).dp), size = 29.333f,
                        color = Color.White, weight = FontWeight.SemiBold)
                }
                Row(Modifier.padding(top = (5 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
                    FootprintText("${title}收听歌曲", size = 14f, color = Color.White, weight = FontWeight.SemiBold)
                    if (onRank != null && (songCount ?: 0) > 0) AsyncImage("https://p6.music.126.net/obj/wonDlsKUwrLClGjCm8Kx/30967217913/297f/f016/b0a6/194ad6a3e8317809a45f526abaa4b1a5.png",
                        null, Modifier.size((13 * scale).dp))
                }
            }
        }
        ListeningWallExportButtons(images, title, songCount)
    }
}

@Composable internal fun ListeningMovingWall(images: List<String>, copies: Int = 2) {
    if(images.isEmpty()) return
    val columns = remember(images) {
        val count = images.size
        val entries = if (count >= 20) images.take(20) else {
            val total = if (count == 2 || count == 4) 30 else 24
            val repeated = List(total) { images[it % count] }
            when (count) {
                5 -> repeated.take(20) + repeated.take(4).reversed()
                10 -> repeated.take(20) + repeated.slice(2..5)
                else -> repeated
            }
        }
        val modulus = if (count == 2 || count == 4) 5 else 4
        List(4) { column -> entries.filterIndexed { index, _ -> index % modulus == column } }
    }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val motion = (rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
    val progress = if(motion && LocalListeningMotionEnabled.current && lifecycle.isAtLeast(Lifecycle.State.RESUMED))
        rememberInfiniteTransition(label = "record wall scroll").animateFloat(0f,1f,
            infiniteRepeatable(tween(60_000,easing=LinearEasing),RepeatMode.Restart),label="wall vertical position") else null
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cell = maxWidth / 4 * 1.01f
        Row(Modifier.wrapContentHeight(Alignment.Top, unbounded = true).graphicsLayer {
            translationY = -cell.toPx() * (if (images.size >= 20) 5 else 6) * (progress?.value ?: 0f)
        }) {
            columns.forEach { column -> Column(Modifier.width(cell)) {
                List(copies) { column }.flatten().forEach { cover ->
                    AsyncImage(cover, null, Modifier.size(cell), contentScale = ContentScale.Crop)
                }
            } }
        }
    }
}

@Composable internal fun FootprintDecades(sourceFacts: List<ListeningFact>, title: String, onAction: (ListeningMonthlyAction) -> Unit = {}) {
    val muted = FootprintMuted
    val facts = sourceFacts.sortedBy { decadeSortKey(it.title.substringAfter(" · ")) }
    val scale = LocalFootprintScale.current
    val top = facts.indices.maxByOrNull { facts[it].value.filter(Char::isDigit).toLongOrNull() ?: -1L }
        ?.takeIf { facts[it].value.any(Char::isDigit) }
    val reveal = rememberListeningTopReveal(facts, title)
    FootprintCard(Modifier.testTag("listening_decades")) {
        FootprintText("${title}听歌年代", Modifier.padding(start = (15 * scale).dp, top = (16.667f * scale).dp),
            size = 34f, color = FootprintPale, weight = FontWeight.Bold)
        Column(Modifier.fillMaxWidth().padding(vertical = (16.667f * scale).dp)) {
            facts.forEachIndexed { index, fact ->
                val isTop = index == top
                val rowHeight = 106.667f * scale
                val label = fact.title.substringAfter(" · ")
                Box(Modifier.fillMaxWidth().height(rowHeight.dp)) {
                    Box(Modifier.width((115.333f * scale).dp).align(Alignment.CenterStart),contentAlignment=Alignment.TopCenter) {
                        FootprintText(decadeLines(label), size = if ('–' in label) { if (isTop) 27f else 22f } else { if (isTop) 30f else 25.333f },
                            weight = FontWeight.Bold, numeric = true)
                        if (isTop) FootprintText("TOP", Modifier.offset(y=(-20*scale).dp).testTag("listening_decade_top")
                            .graphicsLayer { alpha=(reveal.progress*2f).coerceIn(0f,1f);translationY=(6.667f*scale).dp.toPx()*(1f-alpha) },
                            size = 16f, lineHeight=16f, maxLines=1, color = FootprintRed, weight = FontWeight.Black, numeric = true)
                    }
                    Canvas(Modifier.fillMaxSize()) {
                        val unit = scale.dp.toPx()
                        val center = Offset(100f * unit, size.height / 2)
                        if (index > 0) drawLine(muted.copy(alpha = .25f), Offset(center.x, 0f), center, strokeWidth = 2 * unit)
                        if (index < facts.lastIndex) drawLine(muted.copy(alpha = .25f), center, Offset(center.x, size.height), strokeWidth = 2 * unit)
                        drawCircle(muted.copy(alpha = .72f), (if (isTop) 5f else 2f) * unit, center)
                    }
                    FootprintCover(fact.cover, fact.detail.ifBlank { fact.title }, Modifier.align(Alignment.CenterStart)
                        .offset(x = ((if (isTop) 116f else 122f) * scale).dp).size(((if (isTop) 84f else 55f) * scale).dp)
                        .clip(RoundedCornerShape((6.667f * scale).dp)).clickable(enabled = fact.songId != null) {
                            fact.songId?.let { onAction(ListeningMonthlyAction.Song(it, fact.detail, cover = fact.cover)) }
                        })
                    Column(Modifier.align(Alignment.CenterStart).padding(start = ((if (isTop) 215f else 190f) * scale).dp,
                        end = (19 * scale).dp).then(if(isTop) reveal.trigger else Modifier), verticalArrangement = Arrangement.spacedBy((5 * scale).dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            val number = fact.value.filter(Char::isDigit)
                            FootprintText(number.ifBlank { fact.value }, size = if (number.isBlank()) 14f else if (isTop) 30f else 24f,
                                weight = FontWeight.Bold, numeric = number.isNotBlank())
                            if (number.isNotBlank()) FootprintText("首", Modifier.padding(start = (2.667f * scale).dp, bottom = (2 * scale).dp),
                                size = 16f, weight = FontWeight.SemiBold)
                        }
                        if (isTop && fact.detail.isNotBlank()) {
                            FootprintText("代表曲目", size = 14f, color = FootprintMuted)
                            FootprintText(fact.detail, Modifier.clickable(enabled = fact.songId != null) {
                                fact.songId?.let { onAction(ListeningMonthlyAction.Song(it, fact.detail, cover = fact.cover)) }
                            }, size = 16f, weight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun FootprintLanguages(facts: List<ListeningFact>, title: String, onAction: (ListeningMonthlyAction) -> Unit = {}) {
    val scale = LocalFootprintScale.current
    val ranked = facts.take(3)
    val reveal = rememberListeningTopReveal(facts, title)
    FootprintCard(Modifier.testTag("listening_languages")) {
        FootprintText("${title}听歌语言", Modifier.padding(start = (15 * scale).dp, top = (16.667f * scale).dp),
            size = 34f, color = FootprintPale, weight = FontWeight.Bold)
        Column(Modifier.fillMaxWidth().padding(start = (20 * scale).dp, end = (19 * scale).dp,
            top = (38.667f * scale).dp, bottom = (27 * scale).dp),
            verticalArrangement = Arrangement.spacedBy((24 * scale).dp)) {
            ranked.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().height((244 * scale).dp), horizontalArrangement = Arrangement.spacedBy((16 * scale).dp),
                    verticalAlignment = Alignment.Bottom) {
                    row.forEachIndexed { index, fact ->
                        val percentage = fact.value.removeSuffix("%").toFloatOrNull()
                        val width = listOf(130f, 80f, 54.667f)[index]
                        val language = fact.title.substringAfter(" · ")
                        Box(Modifier.width((width * scale).dp).fillMaxHeight().then(if(index==0) reveal.trigger else Modifier).clickable(enabled = fact.songId != null) {
                            fact.songId?.let { onAction(ListeningMonthlyAction.Song(it, fact.detail, cover = fact.cover)) }
                        }) {
                            if (index == 0) FootprintText("TOP", Modifier.offset(y=(-19.333f*scale).dp).testTag("listening_language_top")
                                .graphicsLayer { alpha=(reveal.progress*2f).coerceIn(0f,1f);translationY=(6.667f*scale).dp.toPx()*(1f-alpha) },
                                size = 16f, lineHeight=16f, maxLines=1, color = FootprintRed, weight = FontWeight.Black, numeric = true)
                            Column(Modifier.align(Alignment.BottomStart)) {
                            FootprintText(fact.value, size = if (percentage == null) 14f else listOf(42f, 34f, 28f)[index],
                                weight = FontWeight.Bold, numeric = percentage != null, maxLines = 1)
                            FootprintText(language, size = listOf(30f, 24f, 16f)[index].let { if (language.length > 3) it * .8f else it },
                                weight = FontWeight.Bold, maxLines = if (index == 2) 2 else 1)
                            Spacer(Modifier.height((listOf(16f, 10f, 5f)[index] * scale).dp))
                            FootprintCover(fact.cover, fact.detail.ifBlank { fact.title }, Modifier.size((width * scale).dp)
                                .clip(RoundedCornerShape((listOf(12f, 8f, 6f)[index] * scale).dp)))
                            if (fact.detail.isNotBlank()) FootprintText(fact.detail, Modifier.padding(top = (8 * scale).dp),
                                size = 16f, weight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun ListeningYearCards(report: ListeningDisplay, year: Int, isCloud: Boolean = false) {
    val scale = LocalFootprintScale.current
    val uriHandler = LocalUriHandler.current
    val cloudYears = if (isCloud) report.yearFacts.mapNotNull { it.title.take(4).toIntOrNull() }.toSet() else emptySet()
    val entries = report.yearFacts.mapNotNull { fact ->
        fact.title.take(4).toIntOrNull()?.let {
            val hours = Regex("^(\\d+)\\s*小时").find(fact.value)?.groupValues?.get(1)
            val count = fact.detail.removePrefix("收听 ").removeSuffix(" 首歌曲")
            YearCard(it, hours?.let { value -> "$value 小时" } ?: fact.value,
                count.toLongOrNull()?.let(::formatFootprintCount) ?: count.ifBlank { "暂无数据" })
        }
    }.sortedByDescending { it.year }.ifEmpty {
        listOf(YearCard(year, report.totalMs?.let { milliseconds ->
            if (milliseconds >= 3_600_000) "${milliseconds / 3_600_000} 小时" else "${milliseconds / 60_000} 分钟"
        } ?: "暂无数据", report.songCount?.let { formatFootprintCount(it.toLong()) } ?: "暂无数据"))
    }
    val paper = FootprintBackground
    // The annual illustration is an opaque light asset. Reverse its neutral luminance
    // while retaining its red hues, mapping its exact paper color to the app background.
    val annualFilter = if (FootprintDark) remember(paper) {
        val weights = floatArrayOf(.213f, .715f, .072f)
        val sourcePaper = floatArrayOf(248f, 247f, 249f)
        val targetPaper = floatArrayOf(paper.red, paper.green, paper.blue)
        val matrix = FloatArray(20)
        for (row in 0..2) {
            for (column in 0..2) matrix[row * 5 + column] =
                .65f * ((if (row == column) 1f else 0f) - 2f * weights[column])
            matrix[row * 5 + 4] = targetPaper[row] * 255f -
                (0..2).sumOf { (matrix[row * 5 + it] * sourcePaper[it]).toDouble() }.toFloat()
        }
        matrix[18] = 1f
        androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(matrix))
    } else null
    Column(Modifier.fillMaxWidth().testTag("listening_year_cards")) {
        Box(Modifier.fillMaxWidth().height((200.333f * scale).dp)) {
            AsyncImage(OfficialAnnualHeader, null, Modifier.testTag("listening_year_illustration").offset(x = (-20 * scale).dp, y = (32 * scale).dp)
                .wrapContentSize(Alignment.TopStart, unbounded = true).size((375 * scale).dp, (174.333f * scale).dp),
                contentScale = ContentScale.FillBounds, colorFilter = annualFilter)
            Column(Modifier.padding(start = (13.333f * scale).dp, top = (26 * scale).dp), verticalArrangement = Arrangement.spacedBy((10 * scale).dp)) {
                FootprintText("年度听歌足迹", size = 30f, weight = FontWeight.Bold)
                FootprintText(if (isCloud) "年数据随每年听歌报告发布" else "记录每一年与音乐相伴的时光",
                    size = 13f, color = FootprintMuted.copy(alpha = .6f))
            }
        }
        entries.forEachIndexed { index, entry ->
            if (index > 0) Spacer(Modifier.height((30 * scale).dp))
            FootprintText(entry.year.toString(), Modifier.align(Alignment.CenterHorizontally), size = 68.667f,
                color = FootprintRed, weight = FontWeight.Black, numeric = true, brush = Brush.verticalGradient(listOf(Color(0xFFFF2222), Color(0xFFFF8080))))
            FootprintCard(Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val overlap = (10 * scale).dp.roundToPx()
                layout(placeable.width, (placeable.height - overlap).coerceAtLeast(0)) {
                    placeable.placeRelative(0, -overlap)
                }
            }.dropShadow(RoundedCornerShape((20 * scale).dp), Shadow(
                radius = (25.5015f * scale).dp, color = Color.Black.copy(alpha = .06f),
                offset = DpOffset(0.dp, (3.798f * scale).dp)))
                .testTag("listening_year_${entry.year}")) {
                val metricSize = if (entry.count.length < 5) 42f else if (entry.count.length == 5) 35.333f else 30f
                val gap = if (entry.count.length < 5) 12f else if (entry.count.length == 5) 11f else 10f
                val bottom = if (entry.count.length < 5) 24f else if (entry.count.length == 5) 20f else 16f
                val links = if (entry.year in cloudYears) OfficialAnnualReports.filter { it.year == entry.year } else emptyList()
                Row(Modifier.fillMaxWidth().padding(top = (26 * scale).dp, bottom = ((bottom + if (links.isEmpty()) 30 else 0) * scale).dp)) {
                    FootprintYearValue("听歌时长", entry.duration, "", metricSize, gap, Modifier.weight(1f))
                    FootprintYearValue("听歌总数", entry.count, if (entry.count.any(Char::isDigit)) "首" else "", metricSize, gap, Modifier.weight(1f))
                }
                if (links.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = (20 * scale).dp).height(scale.dp)
                        .background(FootprintInk.copy(alpha = .06f)))
                    links.forEach { link ->
                        Row(Modifier.fillMaxWidth().clickable(role = Role.Button,
                            onClickLabel = "打开${link.title}", onClick = { uriHandler.openUri(link.url) })
                            .testTag("listening_annual_link_${link.activity}")
                            .padding(start = (20 * scale).dp, end = (15 * scale).dp, top = (23.333f * scale).dp),
                            horizontalArrangement = Arrangement.spacedBy((13.333f * scale).dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(link.cover, link.title, Modifier.size((108.333f * scale).dp, (74 * scale).dp)
                                .clip(RoundedCornerShape((6.667f * scale).dp)), contentScale = ContentScale.Crop)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((7 * scale).dp)) {
                                FootprintText(link.title, size = 20f, weight = FontWeight.Black, maxLines = 2)
                                FootprintText(link.subtitle, size = 15f, weight = FontWeight.Medium, color = FootprintMuted, maxLines = 2)
                            }
                        }
                    }
                    Spacer(Modifier.height((30 * scale).dp))
                }
            }
        }
    }
}

private data class YearCard(val year: Int, val duration: String, val count: String)

@Composable private fun FootprintYearValue(title: String, value: String, unit: String, metricSize: Float, gap: Float, modifier: Modifier) {
    val scale = LocalFootprintScale.current
    val number = Regex("^[0-9,]+").find(value)?.value
    val suffix = if (number == null) "" else value.removePrefix(number).trim().ifBlank { unit }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((gap * scale).dp)) {
        FootprintText(title, size = 14f, color = FootprintRed, weight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.Bottom) {
            FootprintText(number ?: value, size = if (number == null) 17f else metricSize, weight = FontWeight.Bold, numeric = number != null)
            if (suffix.isNotEmpty()) FootprintText(suffix, Modifier.padding(start = (2 * scale).dp, bottom = (5 * scale).dp),
                size = 20f, weight = FontWeight.SemiBold, maxLines = 2)
        }
    }
}

// URLs and captions are from the official listen-home annual bundle (154.87e8de20.js).
// The host opens the real HTTPS report; local statistics never imply a NetEase annual report.
private const val OfficialAnnualHeader = "https://p6.music.126.net/obj/wo3DlcOGw6DClTvDisK1/77078634267/cdb9/e574/ae52/54eb52301c0cd6ebbef268146dd89ade.png"
private data class AnnualReportLink(val year: Int, val activity: String, val title: String,
    val subtitle: String, val cover: String, val url: String)
private val OfficialAnnualReports = listOf(
    AnnualReportLink(2025, "2025_year_report", "2025年度听歌报告", "总有一首歌，是为了此刻",
        "https://p6.music.126.net/obj/wonDlsKUwrLClGjCm8Kx/77379509149/a8d3/c50f/4e39/e4982fb1da61edc234db243f25d2c208.jpg",
        "https://sg.music.163.com/st/year2025?full_screen=true&nm_style=sbt&market=zuji"),
    AnnualReportLink(2024, "2024_year_report", "2024年度听歌报告", "人生是旷野，音乐也是",
        "https://sg.music.163.com/st/listen-home/static/media/2024.cc1d5808.png",
        "https://sg.music.163.com/st/year2024?full_screen=true&nm_style=sbt&market=zuji"),
    AnnualReportLink(2023, "2023_year_report", "2023年度听歌报告", "我们终将在音乐里相遇",
        "https://sg.music.163.com/st/listen-home/static/media/2023.7e4e869f.png",
        "https://sg.music.163.com/st/year2023?full_screen=true&nm_style=sbt&market=foot"),
    AnnualReportLink(2023, "tenth_year_report", "十年听歌报告", "你的十年听歌之最",
        "https://sg.music.163.com/st/listen-home/static/media/ten_years.f0bdf2c2.png",
        "https://sg.music.163.com/st/ten-years-anniversary?nm_style=sbt"),
    AnnualReportLink(2022, "2022_year_report", "2022年度听歌报告", "每一种情绪都是真的",
        "https://sg.music.163.com/st/listen-home/static/media/2022.2e67b70e.png", "https://sg.music.163.com/c/year2022?nm_style=sbt"),
    AnnualReportLink(2021, "2021_year_report", "2021年度听歌报告", "原来音乐都记得",
        "https://sg.music.163.com/st/listen-home/static/media/2021.5dc19fef.png", "https://sg.music.163.com/c/year2021?nm_style=sbt"),
    AnnualReportLink(2020, "2020_year_report", "2020年度听歌报告", "遇见时光里的自己",
        "https://sg.music.163.com/st/listen-home/static/media/2020.6d015e88.png", "https://st.music.163.com/c/year2020?nm_style=sbt"),
    AnnualReportLink(2019, "2019_year_report", "2019年度听歌报告", "你的世界有我一起",
        "https://sg.music.163.com/st/listen-home/static/media/2019.c1c1189e.png", "https://st.music.163.com/c/year2019report?nm_style=sbt&page=report"),
    AnnualReportLink(2018, "2018_year_report", "2018年度听歌报告", "遇见你真好",
        "https://sg.music.163.com/st/listen-home/static/media/2018.7931cac6.png", "https://st.music.163.com/c/year2018/z/index.html"),
    AnnualReportLink(2017, "2017_year_report", "2017年度听歌报告", "陪你温暖同行",
        "https://sg.music.163.com/st/listen-home/static/media/2017.e0426d5f.png", "https://st.music.163.com/c/year2017"),
)

private fun decadeLines(value: String): String = when {
    '–' in value -> value.split('–').joinToString("\n") { it.trim() }
    value.endsWith("年后") -> value.replace(" 年后", "\n年后")
    else -> value
}
private fun decadeSortKey(label: String): Int {
    if (label == "20 世纪初") return 1900
    val value = label.takeWhile(Char::isDigit).toIntOrNull() ?: return Int.MAX_VALUE
    return if (value < 100 && label.endsWith("年代")) 1900 + value else value
}
internal fun monthName(month: Int): String = listOf("一月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "十一月", "十二月")[month - 1]
private fun formatFootprintCount(count: Long): String = NumberFormat.getIntegerInstance(Locale.US).format(count)

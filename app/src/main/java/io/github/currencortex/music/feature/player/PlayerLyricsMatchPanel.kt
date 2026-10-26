package io.github.currencortex.music.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.LyricsCandidate
import io.github.currencortex.music.feature.lyrics.data.LyricsMatchSource
import io.github.currencortex.music.feature.lyrics.data.OnlineLyricsMatcher
import io.github.currencortex.music.feature.lyrics.data.SavedLyrics
import io.github.currencortex.music.feature.lyrics.data.SongLyricsStore
import io.github.currencortex.music.feature.lyrics.domain.LyricsReferenceScore
import io.github.currencortex.music.ui.component.MusicCover
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
import kotlin.math.roundToInt

/** A bounded, state-only matching workspace. Network requests and persistence belong to its owner. */
@Composable internal fun PlayerLyricsMatchPanel(
    song: Song,
    source: LyricsMatchSource,
    query: String,
    results: List<LyricsCandidate>,
    selectedCandidate: LyricsCandidate?,
    preview: SavedLyrics?,
    assessment: LyricsReferenceScore?,
    assessmentNote: String?,
    searching: Boolean,
    previewing: Boolean,
    busy: Boolean,
    message: String?,
    messageError: Boolean,
    hasSearched: Boolean,
    onSource: (LyricsMatchSource) -> Unit,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onAutomatic: () -> Unit,
    onCandidate: (LyricsCandidate) -> Unit,
    onCancelLoading: () -> Unit,
    onBack: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val songKey = remember(song) { SongLyricsStore.identity(song) }
    var showingPreview by rememberSaveable(songKey) { mutableStateOf(preview != null || selectedCandidate != null) }
    LaunchedEffect(selectedCandidate, preview) {
        if (selectedCandidate != null || preview != null) showingPreview = true
    }
    val loading = searching || previewing
    fun submit() {
        if (busy || searching || query.isBlank()) return
        focus.clearFocus(); keyboard?.hide(); showingPreview = false; onSearch()
    }
    BoxWithConstraints(modifier.fillMaxSize().testTag("lyrics_match_panel")) {
        val wide = maxWidth >= 600.dp
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (wide || !showingPreview) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("lyrics_match_sources"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LyricsMatchSource.entries.forEach { choice ->
                    val chosen = source == choice
                    Box(Modifier.height(44.dp)
                            .drawBehind {
                                if (chosen) drawLine(colors.primary,
                                    Offset(12.dp.toPx(), size.height - 2.dp.toPx()),
                                    Offset(size.width - 12.dp.toPx(), size.height - 2.dp.toPx()),
                                    strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                            }
                            .selectable(chosen, enabled = !busy, role = Role.Tab, onClick = {
                                if (!chosen) { showingPreview = false; onSource(choice) }
                                focus.clearFocus(); keyboard?.hide()
                            }).testTag("lyrics_match_source_${choice.name.lowercase(Locale.ROOT)}")
                            .padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(choice.shortLabel, fontSize = 13.sp,
                            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (chosen) colors.primary else colors.onSurfaceVariantSummary)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(query, {
                    showingPreview = false; onQuery(it.take(OnlineLyricsMatcher.MAX_QUERY_LENGTH))
                }, Modifier.weight(1f).testTag("lyrics_match_query"),
                    label = "歌名 / 歌手", useLabelAsPlaceholder = true, singleLine = true,
                    insideMargin = DpSize(12.dp, 10.dp), cornerRadius = 12.dp,
                    textStyle = MiuixTheme.textStyles.main.copy(fontSize = 14.sp), enabled = !busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }))
                LyricsActionButton("搜索", ::submit, Modifier.testTag("lyrics_match_search"),
                    enabled = !busy && !searching && query.isNotBlank(), primary = true)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (searching) "正在搜索…" else if (previewing && preview != null) "正在核对网易云基准…" else if (previewing) "正在读取歌词…"
                    else if (hasSearched) "${results.size} 个候选 · 信息匹配度" else "以网易云歌词为内容基准",
                    Modifier.weight(1f), fontSize = 12.sp, color = colors.onSurfaceVariantSummary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                LyricsActionButton("自动择优", {
                    focus.clearFocus(); keyboard?.hide(); showingPreview = true; onAutomatic()
                }, Modifier.testTag("lyrics_match_auto"), enabled = !busy && !loading)
                if (loading) LyricsActionButton("停止", onCancelLoading,
                    Modifier.testTag("lyrics_match_cancel_loading"), enabled = !busy)
            }
            } else if (loading) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (preview != null) "正在核对网易云基准…" else "正在读取歌词…", Modifier.weight(1f),
                    fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                LyricsActionButton("停止", onCancelLoading, Modifier.testTag("lyrics_match_cancel_loading"), enabled = !busy)
            }
            if (message != null) Text(message,
                Modifier.fillMaxWidth().testTag("lyrics_match_message").semantics { liveRegion = LiveRegionMode.Polite },
                fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (messageError) colors.error else colors.onSurfaceVariantSummary)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val choose: (LyricsCandidate) -> Unit = {
                    focus.clearFocus(); keyboard?.hide(); showingPreview = true; onCandidate(it)
                }
                if (wide) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LyricsMatchResults(song, source, results, selectedCandidate, searching, busy, hasSearched,
                        choose, Modifier.weight(.60f).fillMaxHeight())
                    Box(Modifier.width(1.dp).fillMaxHeight().background(colors.onSurface.copy(alpha = .08f)))
                    LyricsMatchPreview(selectedCandidate, preview, assessment, assessmentNote, previewing,
                        onBackToResults = null, wide = true, modifier = Modifier.weight(.40f).fillMaxHeight())
                } else if (showingPreview) {
                    LyricsMatchPreview(selectedCandidate, preview, assessment, assessmentNote, previewing,
                        onBackToResults = { showingPreview = false }, wide = false, modifier = Modifier.fillMaxSize())
                } else LyricsMatchResults(song, source, results, selectedCandidate, searching, busy, hasSearched,
                    choose, Modifier.fillMaxSize())
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.onSurface.copy(alpha = .08f)))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LyricsActionButton("取消", {
                    focus.clearFocus(); keyboard?.hide(); onBack()
                }, Modifier.weight(1f).testTag("lyrics_match_back"), enabled = !busy)
                LyricsActionButton(if (busy) "保存中…" else "保存歌词", onApply,
                    Modifier.weight(1f).testTag("lyrics_match_apply"), enabled = preview != null && !loading && !busy,
                    primary = true)
            }
        }
    }
}

/** Local compact action style, also used by the management view without changing global dialogs. */
@Composable internal fun LyricsActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, primary: Boolean = false) {
    val colors = MiuixTheme.colorScheme
    Box(modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
        .background(if (primary && enabled) colors.primary else colors.surfaceContainer)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            color = (if (primary && enabled) colors.onPrimary else colors.onSurface).copy(alpha = if (enabled) 1f else .38f))
    }
}

@Composable private fun LyricsMatchResults(song: Song, source: LyricsMatchSource, results: List<LyricsCandidate>,
    selected: LyricsCandidate?, searching: Boolean, busy: Boolean, hasSearched: Boolean,
    onCandidate: (LyricsCandidate) -> Unit, modifier: Modifier) {
    val colors = MiuixTheme.colorScheme
    val scroll = rememberLazyListState()
    LaunchedEffect(source, results) { scroll.scrollToItem(0) }
    LazyColumn(modifier.testTag("lyrics_match_results"), state = scroll,
        verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
        if (results.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (searching) "正在搜索 ${source.shortLabel}…" else if (hasSearched) "没有找到候选" else "搜索这首歌的歌词",
                    fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(if (searching) "可随时停止" else if (hasSearched) "修改关键词或切换来源重试" else "也可以自动比较各来源的歌词",
                    fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
            }
        }
        itemsIndexed(results, key = { index, candidate -> "${candidate.source}:${candidate.reference.ifEmpty { candidate.id }}:$index" }) { index, candidate ->
            val chosen = candidate == selected
            val metadataPercent = OnlineLyricsMatcher.metadataMatchPercent(song, candidate)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (chosen) colors.primary.copy(alpha = .10f) else colors.surfaceContainer.copy(alpha = .35f))
                .then(if (chosen) Modifier.border(1.dp, colors.primary.copy(alpha = .45f), RoundedCornerShape(12.dp)) else Modifier)
                .selectable(chosen, enabled = !busy, role = Role.RadioButton, onClick = { onCandidate(candidate) })
                .testTag("lyrics_match_candidate_$index").padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MusicCover(candidate.cover, Modifier.size(42.dp).testTag("lyrics_match_candidate_cover_$index"), cornerRadius = 10.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(candidate.title, Modifier.weight(1f, fill = false), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$metadataPercent%", Modifier.testTag("lyrics_match_metadata_$index")
                            .clearAndSetSemantics { text = AnnotatedString("信息匹配 $metadataPercent%") }
                            .clip(RoundedCornerShape(6.dp)).background(colors.primary.copy(alpha = .12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp,
                            fontWeight = FontWeight.Medium, color = colors.primary)
                    }
                    Text(listOf(candidate.artists.ifBlank { "歌手信息缺失" }, candidate.album.takeIf(String::isNotBlank))
                        .filterNotNull().joinToString(" · "), fontSize = 12.sp,
                        color = colors.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    if (chosen) Text("✓", Modifier.semantics { contentDescription = "已选中" },
                        fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
                }
            }
        }
    }
}

@Composable private fun LyricsMatchPreview(candidate: LyricsCandidate?, preview: SavedLyrics?,
    assessment: LyricsReferenceScore?, assessmentNote: String?, previewing: Boolean,
    onBackToResults: (() -> Unit)?, wide: Boolean, modifier: Modifier) {
    val colors = MiuixTheme.colorScheme
    val scroll = rememberLazyListState()
    LaunchedEffect(candidate, preview) { scroll.scrollToItem(0) }
    Column(modifier.testTag("lyrics_match_preview"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onBackToResults != null) LyricsActionButton("‹ 返回结果", onBackToResults,
            Modifier.testTag("lyrics_match_back_results"))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = scroll,
            contentPadding = PaddingValues(bottom = 12.dp)) {
            if (candidate != null || preview != null) item {
                if (wide) Column(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MusicCover(candidate?.cover.orEmpty(), Modifier.size(128.dp).testTag("lyrics_match_preview_cover"), cornerRadius = 16.dp)
                    LyricsMatchPreviewIdentity(candidate, preview, centered = true)
                } else Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MusicCover(candidate?.cover.orEmpty(), Modifier.size(80.dp).testTag("lyrics_match_preview_cover"), cornerRadius = 12.dp)
                    LyricsMatchPreviewIdentity(candidate, preview, centered = false, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
            }
            if (previewing) item {
                Text(if (preview != null) "正在核对网易云基准…" else "正在读取歌词…", Modifier.testTag("lyrics_match_preview_loading").semantics { liveRegion = LiveRegionMode.Polite },
                    fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                Spacer(Modifier.height(12.dp))
            }
            if (preview != null) {
                item {
                    val realWords = preview.document.withoutEstimatedTiming().hasWordTiming
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceContainer.copy(alpha = .65f)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(preview.origin.label, Modifier.weight(1f), fontSize = 12.sp,
                                color = colors.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (realWords) "逐字歌词" else "逐行歌词", Modifier.testTag("lyrics_match_timing_kind"),
                                fontSize = 11.sp, color = colors.primary)
                        }
                        // The bounded matcher can reject before measuring edit similarity. Its
                        // zero sentinel is a failed eligibility check, not a measured 0% agreement.
                        Text("歌词内容 ${if (previewing) "校验中…" else if (assessment?.accepted == false && assessment.similarity == 0.0) "未通过" else assessment?.similarity?.let(::lyricsMatchPercent) ?: "未校验"}",
                            Modifier.testTag("lyrics_match_content_score"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        if (assessment != null && !previewing) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("时间轴 ${if (!assessment.accepted && assessment.timingSimilarity == 0.0) "未通过" else lyricsMatchPercent(assessment.timingSimilarity)}", Modifier.testTag("lyrics_match_timing_score"),
                                fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                            Text("歌词质量 ${lyricsMatchPercent(assessment.quality)}", Modifier.testTag("lyrics_match_quality_score"),
                                fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                        }
                        Text(if (previewing) "正在校验自动条件…" else when (assessment?.accepted) {
                            true -> "符合自动条件"
                            false -> "不符合自动条件 · 可手动保存"
                            null -> "未校验 · 可手动保存"
                        }, Modifier.testTag("lyrics_match_policy"), fontSize = 12.sp,
                            color = if (!previewing && assessment?.accepted == false) colors.error else colors.onSurfaceVariantSummary)
                        assessmentNote?.takeIf { !previewing && it.isNotBlank() }?.let { Text(it, fontSize = 12.sp, lineHeight = 17.sp,
                            color = colors.onSurfaceVariantSummary, modifier = Modifier.testTag("lyrics_match_assessment_note")) }
                    }
                    Text("歌词预览", Modifier.padding(top = 16.dp, bottom = 12.dp), fontSize = 13.sp,
                        color = colors.onSurfaceVariantSummary, fontWeight = FontWeight.Medium)
                }
                if (preview.document.lines.isEmpty()) item {
                    Text("这份歌词为空", fontSize = 14.sp, color = colors.onSurfaceVariantSummary)
                }
                itemsIndexed(preview.document.lines, key = { index, _ -> index }) { index, line ->
                    val shape = RoundedCornerShape(topStart = if (index == 0) 12.dp else 0.dp,
                        topEnd = if (index == 0) 12.dp else 0.dp,
                        bottomStart = if (index == preview.document.lines.lastIndex) 12.dp else 0.dp,
                        bottomEnd = if (index == preview.document.lines.lastIndex) 12.dp else 0.dp)
                    Column(Modifier.fillMaxWidth().clip(shape).background(colors.surfaceContainer.copy(alpha = .65f))
                        .padding(horizontal = 16.dp, vertical = 12.dp).testTag("lyrics_match_preview_line_$index"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(lyricsMatchTime(line.startTimeMs), fontSize = 10.sp, color = colors.onSurfaceVariantSummary)
                        if (line.text.isNotBlank()) Text(line.text, fontSize = 15.sp, lineHeight = 22.sp)
                        (listOf(line.translation) + line.translations.map { it.text }).filter(String::isNotBlank).distinct().take(2).forEach {
                            Text(it, fontSize = 12.sp, lineHeight = 18.sp, color = colors.onSurfaceVariantSummary)
                        }
                        if (line.romanization.isNotBlank()) Text(line.romanization, fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                        line.backgroundVocals.filter { it.text.isNotBlank() }.forEach {
                            Text("和声 · ${it.text}", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                        }
                    }
                }
            } else if (!previewing) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("选择候选预览", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("查看原文、翻译与匹配度后保存", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                }
            }
        }
    }
}

@Composable private fun LyricsMatchPreviewIdentity(candidate: LyricsCandidate?, preview: SavedLyrics?,
    centered: Boolean, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        Text(candidate?.title ?: "已匹配歌词", Modifier.fillMaxWidth(), fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start)
        Text(candidate?.artists?.ifBlank { null } ?: preview?.origin?.label.orEmpty(), Modifier.fillMaxWidth(),
            fontSize = 12.sp, color = colors.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start)
        candidate?.album?.takeIf(String::isNotBlank)?.let {
            Text(it, Modifier.fillMaxWidth(), fontSize = 11.sp, color = colors.onSurfaceVariantSummary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start)
        }
    }
}

private fun lyricsMatchPercent(value: Double): String =
    if (value.isFinite()) "${(value.coerceIn(0.0, 1.0) * 100).roundToInt()}%" else "未校验"

private fun lyricsMatchTime(value: Long): String {
    val seconds = value.coerceAtLeast(0) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

// Per-song lyric management informed by Folia Major, AGPL-3.0.
// chthollyphile/folia-major aecd330b720eb8fc728e6b10ebcec261b144d683:
// components/panelTab/{OnlineLyricsTab,LyricFileDialog,LyricTimelineOffsetControl}.tsx.
package io.github.currencortex.music.feature.player

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.ui.component.LocalDocumentRequests
import io.github.currencortex.music.data.settings.LyricsSource
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.*
import io.github.currencortex.music.feature.lyrics.model.LyricsException
import io.github.currencortex.music.feature.lyrics.model.LyricsErrorCode
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

/** Per-song editing state; the root document host owns any outstanding picker request. */
@Composable internal fun PlayerLyricsManagementDialog(container: AppContainer, song: Song, onDismiss: () -> Unit) {
    key(container, SongLyricsStore.identity(song)) { PlayerLyricsManagementContent(container, song, onDismiss) }
}

private enum class LyricExportFormat(val extension: String) { FIA("fia"), LRC("lrc") }

@Composable private fun PlayerLyricsManagementContent(container: AppContainer, song: Song, onDismiss: () -> Unit) {
    val documents = LocalDocumentRequests.current
    val scope = rememberCoroutineScope()
    val choices by container.songLyrics.state.collectAsStateWithLifecycle()
    val playback by container.playbackLyrics.state.collectAsStateWithLifecycle()
    val musicSettings by container.musicSettings.state.collectAsStateWithLifecycle()
    val options = choices[SongLyricsStore.identity(song)] ?: LyricsSongSettings()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageError by remember { mutableStateOf(false) }
    var mutationJob by remember { mutableStateOf<Job?>(null) }
    val blocked = busy || documents.busy
    var offsetInput by remember(options.offsetMs) { mutableStateOf(options.offsetMs.toString()) }

    var matching by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf(LyricsMatchSource.NETEASE) }
    var query by remember { mutableStateOf(listOf(song.name, song.artists, song.album).filter(String::isNotBlank)
        .joinToString(" - ").take(OnlineLyricsMatcher.MAX_QUERY_LENGTH)) }
    var results by remember { mutableStateOf<List<LyricsCandidate>>(emptyList()) }
    var selectedCandidate by remember { mutableStateOf<LyricsCandidate?>(null) }
    var preview by remember { mutableStateOf<SavedLyrics?>(null) }
    var assessment by remember { mutableStateOf<LyricsMatchAssessment?>(null) }
    var hasSearched by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var previewJob by remember { mutableStateOf<Job?>(null) }
    var epoch by remember { mutableLongStateOf(0L) }

    fun notify(text: String, error: Boolean = false) { message = text; messageError = error }
    fun cancelMatching(clearResults: Boolean = true) {
        epoch++
        searchJob?.cancel(); previewJob?.cancel()
        searching = false; previewing = false
        selectedCandidate = null; preview = null; assessment = null
        if (clearResults) { results = emptyList(); hasSearched = false }
    }
    fun close() {
        cancelMatching(); mutationJob?.cancel()
        onDismiss()
    }
    fun operation(success: String, action: suspend () -> Unit) {
        if (busy) return
        busy = true; message = null
        mutationJob = scope.launch {
            try {
                action()
                ensureActive()
                notify(success)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { notify(lyricManagementError(failure), true) }
            finally { busy = false }
        }
    }
    fun runSearch() {
        if (blocked || query.isBlank()) return
        cancelMatching()
        val requested = ++epoch
        val requestedSource = source
        val requestedQuery = query
        searching = true; message = null
        searchJob = scope.launch {
            try {
                val found = container.onlineLyrics.search(requestedSource, requestedQuery)
                ensureActive()
                if (epoch == requested) {
                    results = OnlineLyricsMatcher.rankCandidates(song, found)
                    hasSearched = true
                    if (found.isEmpty()) notify("这个来源没有找到候选，可修改关键词或切换来源")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (epoch == requested) notify(lyricManagementError(failure), true) }
            finally { if (epoch == requested) searching = false }
        }
    }
    fun loadPreview(candidate: LyricsCandidate) {
        if (blocked) return
        cancelMatching(clearResults = false)
        val requested = ++epoch
        selectedCandidate = candidate
        previewing = true; message = null
        previewJob = scope.launch {
            try {
                val loaded = container.onlineLyrics.align(song, container.onlineLyrics.load(candidate))
                ensureActive()
                if (epoch == requested) preview = loaded
                val checked = container.onlineLyrics.assess(song, candidate, loaded)
                ensureActive()
                if (epoch == requested) assessment = checked
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (epoch == requested) notify(lyricManagementError(failure), true) }
            finally { if (epoch == requested) previewing = false }
        }
    }
    fun findAutomatically() {
        if (blocked) return
        cancelMatching()
        val requested = ++epoch
        previewing = true; message = null
        previewJob = scope.launch {
            try {
                val found = container.onlineLyrics.bestMatchDetailed(song)
                ensureActive()
                if (epoch == requested) {
                    preview = found?.saved
                    selectedCandidate = found?.candidate
                    assessment = found?.assessment
                    notify(if (found == null) "没有找到身份信息足够吻合的歌词，请手动搜索选择"
                        else "已找到候选，请预览并确认应用")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (epoch == requested) notify(lyricManagementError(failure), true) }
            finally { if (epoch == requested) previewing = false }
        }
    }

    fun leaveMatching() {
        cancelMatching(); matching = false; message = null
    }

    fun startExport(format: LyricExportFormat) {
        if (blocked) return
        // This is application-owned source data, before any renderer filters or visual effects.
        val capturedPlayback = playback.takeIf { it.matches(song) && !it.loading }
        val capturedOffset = options.offsetMs
        busy = true; message = null
        mutationJob = scope.launch {
            try {
                val saved = container.songLyrics.active(song) ?: capturedPlayback?.let {
                    SavedLyrics(it.sourceDocument ?: it.document, it.origin ?: LyricsOrigin("默认歌词"), it.timingAlignment)
                } ?: container.lyricsRepository.load(song).let {
                    SavedLyrics(it.sourceDocument ?: it.document, it.origin ?: LyricsOrigin("默认歌词"), it.timingAlignment)
                }
                require(format == LyricExportFormat.FIA || saved.document.lines.any { it.text.isNotBlank() || it.backgroundVocals.isNotEmpty() }) {
                    "这首歌没有可导出的 LRC 歌词；FIA 可以保留纯音乐的空歌词选择"
                }
                val bytes = withContext(Dispatchers.Default) {
                    (if (format == LyricExportFormat.FIA) LyricsFileCodec.encodeFia(song, saved.document, saved.origin,
                        capturedOffset, saved.timingAlignment)
                        else LyricsFileCodec.encodeLrc(saved.document)).toByteArray(Charsets.UTF_8)
                }
                currentCoroutineContext().ensureActive()
                val name = lyricExportName(song, format.extension)
                documents.exportBytes(bytes, name, if (format == LyricExportFormat.FIA) "application/json" else "text/plain")
                notify("请选择保存位置")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { notify(lyricManagementError(failure), true) }
            finally { busy = false }
        }
    }
    DisposableEffect(Unit) { onDispose {
        searchJob?.cancel(); previewJob?.cancel(); mutationJob?.cancel()
    } }

    val automatic = musicSettings.lyricsSource == LyricsSource.AUTO
    val defaultLabel = if (!automatic && song.privateLibrary) (if (song.emby != null) "Emby" else "Navidrome") else musicSettings.lyricsSource.label
    val restoredMessage = if (automatic) "已恢复自动择优，按来源顺序查找，逐字优先、逐行兜底；单曲偏移保留" else "已恢复默认歌词来源，单曲偏移保留"
    val chosenLabel = options.activeOrigin?.label ?: defaultLabel
    val displayedLabel = if (playback.matches(song) && !playback.loading) playback.origin?.label ?: chosenLabel else chosenLabel
    val canExport = options.activeOrigin != null || (playback.matches(song) && !playback.loading && playback.error == null)
    PlayerLyricsDialogFrame(song, matching, onBack = ::leaveMatching, onClose = ::close) {
        if (matching) {
            PlayerLyricsMatchPanel(song, source, query, results, selectedCandidate, preview,
                assessment, assessment?.unavailableReason, searching, previewing, blocked,
                message, messageError, hasSearched,
                onSource = { value -> if (source != value) { cancelMatching(); source = value; message = null } },
                onQuery = { value -> if (!blocked && value.length <= OnlineLyricsMatcher.MAX_QUERY_LENGTH) {
                    cancelMatching(); query = value; message = null
                } }, onSearch = ::runSearch, onAutomatic = ::findAutomatically, onCandidate = ::loadPreview,
                onCancelLoading = { cancelMatching(clearResults = false); message = "已停止匹配" },
                onBack = ::leaveMatching,
                onApply = { preview?.let { saved -> operation("匹配歌词已应用") {
                    container.songLyrics.saveMatched(song, saved)
                    currentCoroutineContext().ensureActive()
                    matching = false
                    cancelMatching()
                } } })
        } else {
            PlayerLyricsManagementPanel(
                currentSource = displayedLabel, defaultSource = defaultLabel, automatic = automatic,
                options = options, blocked = blocked, offsetInput = offsetInput,
                loading = playback.matches(song) && playback.loading,
                loadError = playback.error.takeIf { playback.matches(song) },
                recoveryMessage = container.songLyrics.recoveryMessage,
                message = message, messageError = messageError, canExport = canExport,
                onOffsetInput = { if (!blocked && it.matches(Regex("[+-]?[0-9]{0,6}"))) offsetInput = it },
                onOffsetStep = { delta ->
                    val value = ((offsetInput.toLongOrNull() ?: options.offsetMs) + delta).coerceIn(-60_000, 60_000)
                    operation("偏移已保存") {
                        container.songLyrics.setOffset(song, value)
                        currentCoroutineContext().ensureActive()
                        offsetInput = value.toString()
                    }
                }, onOffsetApply = { offsetInput.toLongOrNull()?.takeIf { it in -60_000..60_000 }?.let { value ->
                    operation("偏移已保存") { container.songLyrics.setOffset(song, value) }
                } }, onOffsetReset = { operation("偏移已归零") { container.songLyrics.setOffset(song, 0) } },
                onDefault = { operation(restoredMessage) {
                    if (options.matchedOrigin != null) container.songLyrics.reset(song)
                    else container.songLyrics.selectImported(song, false)
                } }, onMatched = { operation("已切换到匹配歌词") { container.songLyrics.selectImported(song, false) } },
                onImported = { operation("已切换到导入歌词") { container.songLyrics.selectImported(song, true) } },
                onReset = { operation(restoredMessage) { container.songLyrics.reset(song) } },
                onMatch = { matching = true; message = null },
                onImport = {
                    if (!blocked) {
                        cancelMatching()
                        try { documents.importLyrics(song) }
                        catch (failure: Exception) { notify("无法打开文件选择器", true) }
                    }
                }, onExportFia = { startExport(LyricExportFormat.FIA) },
                onExportLrc = { startExport(LyricExportFormat.LRC) }, onClose = ::close,
                alignmentLabel = playback.takeIf { it.matches(song) }?.timingAlignment?.let(::lyricsAlignmentLabel))
        }
    }
}

/** Result processing is independent of the current song and the original dialog's lifetime. */
internal suspend fun importLyricsDocument(container: AppContainer, resolver: ContentResolver, uri: Uri, song: Song) {
    try {
        val (name, bytes) = withContext(Dispatchers.IO) { readLyricImport(resolver, uri) }
        val decoded = withContext(Dispatchers.Default) { LyricsFileCodec.decode(bytes, name) }
        currentCoroutineContext().ensureActive()
        container.songLyrics.saveImported(song, SavedLyrics(decoded.document,
            LyricsOrigin(name.take(256), "imported", name), decoded.timingAlignment), decoded.offsetMs)
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (failure: Exception) { throw IllegalStateException(lyricManagementError(failure), failure) }
}

private suspend fun readLyricImport(resolver: ContentResolver, uri: Uri): Pair<String, ByteArray> {
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) null else {
            val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) require(cursor.getLong(sizeColumn) <= LyricsFileCodec.MAX_BYTES) { "歌词文件过大，最多支持 2 MB" }
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0) cursor.getString(column)?.take(512) else null
        }
    }?.takeIf(String::isNotBlank) ?: "lyrics.lrc"
    currentCoroutineContext().ensureActive()
    val input = resolver.openInputStream(uri) ?: error("无法打开歌词文件")
    val bytes = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = it.read(buffer)
            if (count < 0) break
            require(output.size() + count <= LyricsFileCodec.MAX_BYTES) { "歌词文件过大，最多支持 2 MB" }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
    return name to bytes
}

private fun lyricExportName(song: Song, extension: String): String = listOf(song.name, song.artists)
    .filter(String::isNotBlank).joinToString(" - ").replace(Regex("""[<>:"/\\|?*\p{Cntrl}]"""), "_")
    .take(80).trim(' ', '.').ifEmpty { "lyrics" } + ".$extension"

internal fun lyricManagementError(failure: Exception): String = when (failure) {
    is LyricsException -> when (failure.code) {
        LyricsErrorCode.NO_LYRICS -> "此来源没有提供可识别的歌词"
        LyricsErrorCode.NETWORK_ERROR -> "歌词网络请求失败，请稍后重试"
        LyricsErrorCode.PARSE_ERROR -> "歌词文件格式有误，无法解析"
        LyricsErrorCode.UNSUPPORTED_FORMAT -> "暂不支持此歌词文件格式"
        LyricsErrorCode.INVALID_TIMELINE -> "歌词时间轴有误，请选择其他候选"
    }
    is LyricsMatchException, is IllegalArgumentException, is IllegalStateException -> failure.message?.take(180) ?: "歌词处理失败，请重试"
    is SecurityException -> "没有文件访问权限，请重新选择文件或保存位置"
    else -> "无法读取或保存歌词，请重试"
}

// Per-song lyric management informed by Folia Major, AGPL-3.0.
// chthollyphile/folia-major aecd330b720eb8fc728e6b10ebcec261b144d683:
// components/panelTab/{OnlineLyricsTab,LyricFileDialog,LyricTimelineOffsetControl}.tsx.
package io.github.currencortex.music.feature.player

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.data.settings.LyricsSource
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.*
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

/** Keep picker callbacks attached to the song for which the dialog was opened. */
@Composable internal fun PlayerLyricsManagementDialog(container: AppContainer, song: Song, onDismiss: () -> Unit) {
    key(container, SongLyricsStore.identity(song)) { PlayerLyricsManagementContent(container, song, onDismiss) }
}

private data class LyricImportRequest(val song: Song)
private enum class LyricExportFormat(val extension: String) { FIA("fia"), LRC("lrc") }
private data class LyricExportRequest(val song: Song, val format: LyricExportFormat, val name: String, val bytes: ByteArray)

@Composable private fun PlayerLyricsManagementContent(container: AppContainer, song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val scope = rememberCoroutineScope()
    val choices by container.songLyrics.state.collectAsStateWithLifecycle()
    val playback by container.playbackLyrics.state.collectAsStateWithLifecycle()
    val musicSettings by container.musicSettings.state.collectAsStateWithLifecycle()
    val options = choices[SongLyricsStore.identity(song)] ?: LyricsSongSettings()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageError by remember { mutableStateOf(false) }
    var mutationJob by remember { mutableStateOf<Job?>(null) }
    var pendingImport by remember { mutableStateOf<LyricImportRequest?>(null) }
    var pendingExport by remember { mutableStateOf<LyricExportRequest?>(null) }
    val blocked = busy || pendingImport != null || pendingExport != null
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
                val loaded = container.onlineLyrics.load(candidate)
                ensureActive()
                if (epoch == requested) preview = loaded
                val checked = container.onlineLyrics.assess(song, loaded)
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

    // A picker result uses the request's immutable song/payload, never whatever is playing later.
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val request = pendingImport
        pendingImport = null
        if (uri != null && request != null) operation("歌词已导入") {
            val (name, bytes) = withContext(Dispatchers.IO) { readLyricImport(resolver, uri) }
            val decoded = withContext(Dispatchers.Default) { LyricsFileCodec.decode(bytes, name) }
            currentCoroutineContext().ensureActive()
            container.songLyrics.saveImported(request.song, SavedLyrics(decoded.document, LyricsOrigin(name.take(256), "imported", name)), decoded.offsetMs)
        }
    }
    fun finishExport(uri: Uri?, format: LyricExportFormat) {
        val request = pendingExport?.takeIf { it.format == format }
        pendingExport = null
        if (uri != null && request != null) operation("已导出 ${request.name}") {
            withContext(Dispatchers.IO) {
                ensureActive()
                val output = resolver.openOutputStream(uri, "w") ?: error("无法打开保存位置")
                output.use {
                    var cursor = 0
                    while (cursor < request.bytes.size) {
                        ensureActive()
                        val count = minOf(8192, request.bytes.size - cursor)
                        it.write(request.bytes, cursor, count)
                        cursor += count
                    }
                    it.flush()
                }
            }
        }
    }
    val exportFia = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { finishExport(it, LyricExportFormat.FIA) }
    val exportLrc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { finishExport(it, LyricExportFormat.LRC) }
    fun startExport(format: LyricExportFormat) {
        if (blocked) return
        // This is application-owned source data, before any renderer filters or visual effects.
        val capturedPlayback = playback.takeIf { it.matches(song) && !it.loading }
        val capturedOffset = options.offsetMs
        busy = true; message = null
        mutationJob = scope.launch {
            var preparing = true
            try {
                val saved = container.songLyrics.active(song) ?: capturedPlayback?.let {
                    SavedLyrics(it.document, it.origin ?: LyricsOrigin("默认歌词"))
                } ?: container.lyricsRepository.load(song).let {
                    SavedLyrics(it.document, it.origin ?: LyricsOrigin("默认歌词"))
                }
                require(format == LyricExportFormat.FIA || saved.document.lines.any { it.text.isNotBlank() || it.backgroundVocals.isNotEmpty() }) {
                    "这首歌没有可导出的 LRC 歌词；FIA 可以保留纯音乐的空歌词选择"
                }
                val bytes = withContext(Dispatchers.Default) {
                    (if (format == LyricExportFormat.FIA) LyricsFileCodec.encodeFia(song, saved.document, saved.origin, capturedOffset)
                        else LyricsFileCodec.encodeLrc(saved.document)).toByteArray(Charsets.UTF_8)
                }
                currentCoroutineContext().ensureActive()
                val name = lyricExportName(song, format.extension)
                pendingExport = LyricExportRequest(song, format, name, bytes)
                notify("请选择保存位置")
                // The callback may be immediate in an ActivityResultRegistry. It owns the write
                // operation's busy flag once launched; preparation must not reset it afterwards.
                busy = false; preparing = false
                if (format == LyricExportFormat.FIA) exportFia.launch(name) else exportLrc.launch(name)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { pendingExport = null; notify(lyricManagementError(failure), true) }
            finally { if (preparing) busy = false }
        }
    }
    DisposableEffect(Unit) { onDispose {
        searchJob?.cancel(); previewJob?.cancel(); mutationJob?.cancel()
    } }

    val automatic = musicSettings.lyricsSource == LyricsSource.AUTO
    val defaultLabel = if (!automatic && song.musicSource == MusicSource.NAVIDROME) "Navidrome" else musicSettings.lyricsSource.label
    val restoredMessage = if (automatic) "已恢复自动择优，按网易云歌词匹配；单曲偏移保留" else "已恢复默认歌词来源，单曲偏移保留"
    val chosenLabel = options.activeOrigin?.label ?: defaultLabel
    val displayedLabel = if (playback.matches(song) && !playback.loading) playback.origin?.label ?: chosenLabel else chosenLabel
    val canExport = options.activeOrigin != null || (playback.matches(song) && !playback.loading && playback.error == null)
    PlayerLyricsDialogFrame(song, matching, onBack = ::leaveMatching, onClose = ::close) {
        if (matching) {
            PlayerLyricsMatchPanel(song, source, query, results, selectedCandidate, preview,
                assessment?.score, assessment?.unavailableReason, searching, previewing, blocked,
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
                        cancelMatching(); pendingImport = LyricImportRequest(song)
                        try { importer.launch(arrayOf("*/*")) }
                        catch (failure: Exception) { pendingImport = null; notify("无法打开文件选择器", true) }
                    }
                }, onExportFia = { startExport(LyricExportFormat.FIA) },
                onExportLrc = { startExport(LyricExportFormat.LRC) }, onClose = ::close)
        }
    }
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

private fun lyricManagementError(failure: Exception): String = when (failure) {
    is LyricsMatchException, is IllegalArgumentException, is IllegalStateException -> failure.message?.take(180) ?: "歌词处理失败，请重试"
    is SecurityException -> "没有文件访问权限，请重新选择文件或保存位置"
    else -> "无法读取或保存歌词，请重试"
}

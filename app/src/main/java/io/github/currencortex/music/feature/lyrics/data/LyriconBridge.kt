package io.github.currencortex.music.feature.lyrics.data

import android.content.Context
import android.os.Build
import android.os.SystemClock
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.settings.MusicSettings
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import io.github.currencortex.music.feature.lyrics.timeline.LyricsPlaybackClock
import io.github.currencortex.music.feature.lyrics.timeline.PlaybackAnchor
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.Closeable
import io.github.proify.lyricon.lyric.model.Song as LyriconSong

/**
 * Publishes the playing song, its timed lyrics and the playback clock to 词幕 (Lyricon), which
 * renders them in the system status bar through its LSPosed module.
 *
 * The player supplies the clock itself: Lyricon reads the position from shared memory at display
 * frequency, so [startTicker] writes the interpolated position roughly every frame while a song is
 * loaded. Lyrics keep this app's own offsets, so every surface stays in sync with the player page.
 * All provider calls are serialized on a single background thread; binder calls and lyric
 * serialization never run on the main thread.
 */
internal class LyriconBridge(
    private val context: Context,
    settings: StateFlow<MusicSettings>,
    private val playback: StateFlow<PlayerState>,
    lyrics: StateFlow<PlaybackLyricsState>,
    scope: CoroutineScope,
) : Closeable {
    private data class Content(
        val song: Song?,
        val durationMs: Long,
        val document: LyricsDocument?,
        val offsetMs: Long,
    )

    private sealed interface Command {
        data class Enabled(val value: Boolean) : Command
        data object ContentChanged : Command
        data object PlaybackChanged : Command
        data object Seeked : Command
    }

    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val dispatcher = Dispatchers.Default.limitedParallelism(1)
    private val workerScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val worker = workerScope.launch {
        for (command in commands) {
            try {
                when (command) {
                    is Command.Enabled -> setEnabled(command.value)
                    Command.ContentChanged -> refreshContent()
                    Command.PlaybackChanged -> syncPlayback()
                    Command.Seeked -> activeProvider?.player?.seekTo(playback.value.positionMs.coerceAtLeast(0))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A failed provider command must not stop later song or position updates.
            }
        }
    }

    @Volatile private var content = Content(null, 0, null, 0)
    private var activeProvider: LyriconProvider? = null
    private var ticker: Job? = null
    private var enabled = false
    private var lastSongKey: String? = null
    private var lastSong: Song? = null
    private var lastDurationMs = Long.MIN_VALUE
    private var lastDocument: LyricsDocument? = null
    private var lastOffsetMs = Long.MIN_VALUE

    init {
        scope.launch {
            settings.map { it.lyriconStatusBar }.distinctUntilChanged()
                .collect { commands.trySend(Command.Enabled(it)) }
        }
        scope.launch {
            combine(playback, lyrics, settings) { state, lyricState, config ->
                val song = state.song
                val ready = song != null && !lyricState.loading && lyricState.matches(song)
                Content(
                    song = song,
                    durationMs = state.durationMs.takeIf { it > 0 } ?: song?.durationMs ?: 0,
                    document = if (ready) lyricState.document else null,
                    offsetMs = config.lyricsOffsetMs - lyricState.songOffsetMs,
                )
            }.distinctUntilChanged { old, next ->
                old.song == next.song && old.document === next.document &&
                    old.durationMs == next.durationMs && old.offsetMs == next.offsetMs
            }.collect { next ->
                content = next
                commands.trySend(Command.ContentChanged)
            }
        }
        scope.launch {
            playback.map { it.playing && !it.loading }.distinctUntilChanged()
                .collect { commands.trySend(Command.PlaybackChanged) }
        }
        scope.launch {
            playback.map { it.timelineRevision }.distinctUntilChanged()
                .collect { commands.trySend(Command.Seeked) }
        }
    }

    private fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (!value) {
            stopTicker()
            activeProvider?.let { provider ->
                runCatching { provider.player.setSong(null) }
                runCatching { provider.unregister() }
                runCatching { provider.destroy() }
            }
            activeProvider = null
            forgetContent()
            return
        }
        refreshContent()
        syncPlayback()
    }

    private fun refreshContent() {
        if (!enabled) return
        val snapshot = content
        val song = snapshot.song
        if (song == null) {
            activeProvider?.let { provider ->
                if (lastSongKey != null) {
                    provider.player.setSong(null)
                    forgetContent()
                }
            }
            return
        }
        val provider = activeProvider ?: createProvider() ?: return
        val key = SongLyricsStore.identity(song)
        val document = snapshot.document
        if (key != lastSongKey) {
            lastSongKey = key
            lastDocument = null
            lastOffsetMs = snapshot.offsetMs
            if (document == null) {
                provider.player.setSong(placeholder(song, snapshot.durationMs))
                provider.player.setDisplayTranslation(false)
                provider.player.setDisplayRoma(false)
                provider.player.seekTo(playback.value.positionMs.coerceAtLeast(0))
                return
            }
        }
        if (document == null) return
        if (document === lastDocument && snapshot.offsetMs == lastOffsetMs &&
            song == lastSong && snapshot.durationMs == lastDurationMs) return
        val converted = lyriconSong(song, snapshot.durationMs, document, snapshot.offsetMs)
        provider.player.setSong(converted)
        provider.player.setDisplayTranslation(converted.lyrics.orEmpty().any { !it.translation.isNullOrBlank() })
        provider.player.setDisplayRoma(converted.lyrics.orEmpty().any { !it.roma.isNullOrBlank() })
        provider.player.seekTo(playback.value.positionMs.coerceAtLeast(0))
        lastDocument = document
        lastOffsetMs = snapshot.offsetMs
        lastSong = song
        lastDurationMs = snapshot.durationMs
    }

    private fun syncPlayback() {
        val provider = activeProvider ?: return
        val state = playback.value
        provider.player.setPlaybackState(state.playing && !state.loading)
        provider.player.setPosition(state.positionMs.coerceAtLeast(0))
    }

    private fun createProvider(): LyriconProvider? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
        val created = runCatching { LyriconFactory.createProvider(context.applicationContext) }.getOrNull() ?: return null
        created.autoSync = true
        if (!runCatching { created.register() }.getOrDefault(false)) {
            runCatching { created.destroy() }
            return null
        }
        activeProvider = created
        forgetContent()
        syncPlayback()
        startTicker()
        return created
    }

    private fun forgetContent() {
        lastSongKey = null
        lastSong = null
        lastDurationMs = Long.MIN_VALUE
        lastDocument = null
        lastOffsetMs = Long.MIN_VALUE
    }

    /** Lyricon samples the shared position at ~24 FPS; interpolate between player anchors. */
    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = workerScope.launch {
            val clock = LyricsPlaybackClock()
            var identitySong: Song? = null
            var identity: String? = null
            var writtenPosition = Long.MIN_VALUE
            while (isActive) {
                val provider = activeProvider ?: break
                val state = playback.value
                val song = state.song
                if (song == null) {
                    identitySong = null
                    identity = null
                    writtenPosition = Long.MIN_VALUE
                    delay(250)
                    continue
                }
                if (song !== identitySong) {
                    identitySong = song
                    identity = SongLyricsStore.identity(song)
                }
                val now = SystemClock.elapsedRealtime()
                val playing = state.playing && !state.loading
                clock.update(
                    identity,
                    PlaybackAnchor(state.positionMs, state.anchorRealtimeMs.takeIf { it > 0 } ?: now,
                        playing, state.durationMs, state.playbackSpeed, state.timelineRevision),
                    now,
                )
                val position = clock.positionAt(now)
                if (playing || position != writtenPosition) {
                    provider.player.setPosition(position)
                    writtenPosition = position
                }
                delay(if (playing) POSITION_INTERVAL_MS else 250)
            }
            ticker = null
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    override fun close() {
        commands.close()
        worker.cancel()
        runCatching {
            runBlocking(dispatcher) {
                stopTicker()
                activeProvider?.let { provider ->
                    runCatching { provider.player.setSong(null) }
                    runCatching { provider.unregister() }
                    runCatching { provider.destroy() }
                }
                activeProvider = null
            }
        }
        workerScope.cancel()
    }

    private companion object {
        const val POSITION_INTERVAL_MS = 41L

        fun placeholder(song: Song, durationMs: Long) = LyriconSong(
            id = SongLyricsStore.identity(song),
            name = song.name,
            artist = song.artists,
            duration = durationMs,
        )

        fun lyriconSong(song: Song, durationMs: Long, document: LyricsDocument, offsetMs: Long): LyriconSong {
            val shift = -offsetMs
            val lines = document.lines.mapNotNull { line ->
                val vocals = line.backgroundVocals.filter { it.text.isNotBlank() }
                val vocalText = vocals.joinToString(" ") { it.text }.takeIf(String::isNotBlank)
                val text = line.text.takeIf(String::isNotBlank) ?: vocalText ?: return@mapNotNull null
                val begin = shifted(
                    minOf(line.startTimeMs, vocals.minOfOrNull { it.startTimeMs } ?: line.startTimeMs), shift,
                ).coerceAtLeast(0)
                val end = shifted(
                    maxOf(line.endTimeMs, vocals.maxOfOrNull { it.endTimeMs } ?: line.endTimeMs), shift,
                )
                if (end <= begin) return@mapNotNull null
                RichLyricLine(
                    begin = begin,
                    end = end,
                    text = text,
                    isAlignedRight = line.isDuet,
                    words = line.words.takeIf { it.isNotEmpty() && !line.wordsEstimated }?.mapNotNull { word ->
                        val wordBegin = shifted(word.startTimeMs, shift).coerceIn(begin, end - 1)
                        val wordEnd = shifted(word.endTimeMs, shift).coerceIn(wordBegin + 1, end)
                        if (word.text.isBlank() || wordEnd <= wordBegin) null
                        else LyricWord(begin = wordBegin, end = wordEnd, text = word.text)
                    }?.takeIf { it.isNotEmpty() },
                    secondary = vocalText?.takeIf { it != text },
                    translation = line.translation.takeIf(String::isNotBlank),
                    roma = line.romanization.takeIf(String::isNotBlank),
                )
            }.sortedBy { it.begin }
            return LyriconSong(
                id = SongLyricsStore.identity(song),
                name = song.name,
                artist = song.artists,
                duration = durationMs,
                lyrics = lines,
            )
        }

        fun shifted(value: Long, shift: Long): Long = when {
            shift > 0 && value > Long.MAX_VALUE - shift -> Long.MAX_VALUE
            shift < 0 && value < Long.MIN_VALUE - shift -> Long.MIN_VALUE
            else -> value + shift
        }
    }
}

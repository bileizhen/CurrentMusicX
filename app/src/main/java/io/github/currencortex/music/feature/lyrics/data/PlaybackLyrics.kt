package io.github.currencortex.music.feature.lyrics.data

import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PlaybackLyricsState(val document: LyricsDocument = LyricsDocument(), val loading: Boolean = false,
    val error: String? = null, val issues: List<LyricsIssue> = emptyList(), val songId: Long? = null,
    val songKey: String? = null, val origin: LyricsOrigin? = null, val songOffsetMs: Long = 0) {
    fun matches(song: Song?): Boolean = song != null &&
        if (songKey != null) songKey == SongLyricsStore.identity(song) else songId == song.id
}
data class LyricsRequest(val song: Song?, val session: List<Any?> = emptyList())

/** Content changes reload the current song; timing-only edits update the clock without a fetch. */
fun managedLyricsRequests(requests: Flow<LyricsRequest>, settings: Flow<Map<String, LyricsSongSettings>>): Flow<LyricsRequest> =
    combine(requests, settings) { request, choices ->
        val revision = request.song?.let { choices[SongLyricsStore.identity(it)]?.contentRevision } ?: 0L
        request.copy(session = request.session + revision)
    }.distinctUntilChanged()

/** Application-owned data, not tied to an Activity, renderer, or PlayerViewModel subscriber. */
class PlaybackLyrics(requests: Flow<LyricsRequest>, private val load: suspend (Song) -> LyricsResult,
    scope: CoroutineScope, songSettings: StateFlow<Map<String, LyricsSongSettings>>? = null) {
    private val mutable = MutableStateFlow(PlaybackLyricsState())
    val state: StateFlow<PlaybackLyricsState> = if (songSettings == null) mutable.asStateFlow() else
        combine(mutable, songSettings) { lyrics, settings ->
            lyrics.copy(songOffsetMs = settings[lyrics.songKey]?.offsetMs ?: 0)
        }.stateIn(scope, SharingStarted.Eagerly, PlaybackLyricsState())
    private var epoch = 0L
    private var pending: Job? = null
    init { scope.launch {
        try { requests.distinctUntilChanged().collect { request ->
            val current = ++epoch
            pending?.cancel()
            val song = request.song?.takeUnless { it.video }
            val key = song?.let(SongLyricsStore::identity)
            mutable.value = if (song == null) PlaybackLyricsState() else PlaybackLyricsState(loading = true, songId = song.id, songKey = key)
            if (song != null) pending = launch {
                try {
                    val result = load(song)
                    ensureActive()
                    if (epoch == current) mutable.value = PlaybackLyricsState(result.document, error = result.error,
                        issues = result.issues, songId = song.id, songKey = key, origin = result.origin)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { if (epoch == current) mutable.value = PlaybackLyricsState(error = "歌词暂时无法读取", songId = song.id, songKey = key) }
            }
        } } finally { pending?.cancel() }
    } }
}

package io.github.currencortex.music.feature.lyrics.data

import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PlaybackLyricsState(val document: LyricsDocument = LyricsDocument(), val loading: Boolean = false,
    val error: String? = null, val issues: List<LyricsIssue> = emptyList(), val songId: Long? = null)
data class LyricsRequest(val song: Song?, val session: List<Any?> = emptyList())

/** Application-owned data, not tied to an Activity, renderer, or PlayerViewModel subscriber. */
class PlaybackLyrics(requests: Flow<LyricsRequest>, private val load: suspend (Song) -> LyricsResult,
    scope: CoroutineScope) {
    private val mutable = MutableStateFlow(PlaybackLyricsState())
    val state = mutable.asStateFlow()
    private var epoch = 0L
    private var pending: Job? = null
    init { scope.launch {
        try { requests.distinctUntilChanged().collect { request ->
            val current = ++epoch
            pending?.cancel()
            val song = request.song?.takeUnless { it.video }
            mutable.value = if (song == null) PlaybackLyricsState() else PlaybackLyricsState(loading = true, songId = song.id)
            if (song != null) pending = launch {
                try {
                    val result = load(song)
                    ensureActive()
                    if (epoch == current) mutable.value = PlaybackLyricsState(result.document, error = result.error,
                        issues = result.issues, songId = song.id)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { if (epoch == current) mutable.value = PlaybackLyricsState(error = "歌词暂时无法读取", songId = song.id) }
            }
        } } finally { pending?.cancel() }
    } }
}

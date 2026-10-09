package io.github.currencortex.music.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import io.github.currencortex.music.core.media.HighSpecWarning
import io.github.currencortex.music.core.media.PlayerMode
import io.github.currencortex.music.core.media.PlaybackMode
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.song.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LyricsUiState(val document: LyricsDocument = LyricsDocument(), val loading: Boolean = false, val error: String? = null,
    val issues: List<io.github.currencortex.music.feature.lyrics.data.LyricsIssue> = emptyList(), val songId: Long? = null)
data class NavigationPlayback(val mode: PlayerMode, val error: String?, val warning: HighSpecWarning?)
data class PlayerSongActions(val songId: Long? = null, val likeCount: Long? = null, val commentCount: Long? = null,
    val liked: Boolean? = null, val liking: Boolean = false, val error: String? = null)
data class PlayerComments(val songId: Long? = null, val total: Long? = null, val hot: List<NeteaseComment> = emptyList(),
    val latest: List<NeteaseComment> = emptyList(), val more: Boolean = false, val nextOffset: Int = 0,
    val loading: Boolean = false, val error: String? = null)
class PlayerViewModel(private val container: AppContainer) : ViewModel() {
    val player = container.playerController
    val sleepTimer = container.sleepTimer
    val state = player.state
    val queue = player.queue.state
    // The navigation host needs only low-frequency state, never the playback position.
    val navigation = state.map { NavigationPlayback(it.mode, it.error, it.warning) }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NavigationPlayback(state.value.mode, state.value.error, state.value.warning))
    val currentSong = queue.map { it.current }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), queue.value.current)
    val settings = container.musicSettings.state
    val audioAnalysis = io.github.currencortex.music.core.visualizer.AudioAnalysisEngine(
        viewModelScope, io.github.currencortex.music.core.visualizer.VisualizerCaptureSource())
    private val captureVisibility = MutableStateFlow(false to false)
    private val captureOwners = io.github.currencortex.music.core.visualizer.CaptureVisibilityRegistry()
    private val debugCaptureOwner = Any()
    init {
        viewModelScope.launch {
            val playback = state.map { Triple(it.mode != PlayerMode.CAST && it.song?.video != true, it.playing, it.song?.id) }.distinctUntilChanged()
            combine(settings.map { it.visualizerEnabled }.distinctUntilChanged(), container.audioSessionId,
                playback, captureVisibility, container.audioTimelineRevision) { enabled, session, playback, visibility, revision ->
                io.github.currencortex.music.core.visualizer.CaptureRequest(enabled, visibility.first,
                    visibility.second, playback.first, playback.second, session, revision, playback.third)
            }.distinctUntilChanged().collect(audioAnalysis::request)
        }
    }
    fun visualizerVisible(visible: Boolean, permission: Boolean) = visualizerVisible(debugCaptureOwner, visible, permission)
    fun visualizerVisible(owner: Any, visible: Boolean, permission: Boolean) {
        captureVisibility.value = captureOwners.update(owner, visible, permission)
    }
    fun visualizerEnabled(enabled: Boolean) = viewModelScope.launch { container.musicSettings.setVisualizerEnabled(enabled) }
    fun visualizerFrameRate(value: io.github.currencortex.music.data.visualizer.VisualizerFrameRate) =
        viewModelScope.launch { container.musicSettings.setVisualizerFrameRate(value) }
    fun visualizerRender(transform: (io.github.currencortex.music.data.visualizer.VisualizerRenderSettings) ->
        io.github.currencortex.music.data.visualizer.VisualizerRenderSettings) =
        viewModelScope.launch { container.musicSettings.editVisualizerRender(transform) }
    fun visualizerEffects(transform: (io.github.currencortex.music.core.visualizer.VisualizerEffectConfig) ->
        io.github.currencortex.music.core.visualizer.VisualizerEffectConfig) =
        viewModelScope.launch { container.musicSettings.editVisualizerEffects(transform) }
    val libraryStatuses = container.libraryRepository.statuses
    val usesNeteaseLibrary = container.primaryLibrary.usesNetease
    private val _lyrics = MutableStateFlow(LyricsUiState())
    val lyrics: StateFlow<LyricsUiState> = _lyrics.asStateFlow()
    private val _actions = MutableStateFlow(PlayerSongActions())
    val actions = _actions.asStateFlow()
    private val _comments = MutableStateFlow(PlayerComments())
    val comments = _comments.asStateFlow()
    private val _heartLoading = MutableStateFlow(false)
    val heartLoading = _heartLoading.asStateFlow()
    private var actionEpoch = 0L
    private var commentsJob: Job? = null
    private var heartStartJob: Job? = null
    private val heartMutex = Mutex()
    private val netease = container.neteaseSongActions
    init { viewModelScope.launch {
        queue.map { it.current?.takeUnless { song -> song.video } }.distinctUntilChanged().collectLatest { song ->
            if (song == null) { _lyrics.value = LyricsUiState(); return@collectLatest }
            _lyrics.value = LyricsUiState(loading = true, songId = song.id)
            val result = container.lyricsRepository.load(song)
            _lyrics.value = LyricsUiState(result.document, error = result.error, issues = result.issues, songId = song.id)
        }
    } }
    init {
        viewModelScope.launch {
            combine(queue.map { NeteaseSongActionsRepository.songId(it.current) }.distinctUntilChanged(),
                container.accountRepository.sessionRevision, container.musicSettings.state.map { it.server }.distinctUntilChanged(),
                netease.revision, container.libraryRepository.revision, container.primaryLibrary.identity) { values ->
                    values.first() as Long? to values.drop(1)
                }.collectLatest { (id, _) ->
                actionEpoch++
                commentsJob?.cancel()
                _comments.value = PlayerComments(songId = id)
                _actions.value = PlayerSongActions(songId = id)
                if (id == null) return@collectLatest
                supervisorScope {
                    launch { appResult { container.libraryRepository.refreshStatus(listOf(queue.value.current?.id ?: id)) } }
                    launch { val result = appResult { netease.likeCount(id) }; if (result is AppResult.Success) _actions.update { it.copy(likeCount = result.value) } }
                    launch { val result = appResult { netease.comments(id, limit = 1) }; if (result is AppResult.Success) _actions.update { it.copy(commentCount = result.value.total?.takeIf { n -> n >= 0 }) } }
                    launch { val result = appResult { netease.isLiked(id) }; if (result is AppResult.Success) _actions.update { it.copy(liked = result.value) } }
                }
            }
        }
        viewModelScope.launch {
            combine(queue.map { Triple(it.mode, it.heartPlaylistId, if (it.songs.lastIndex - it.index <= 2) NeteaseSongActionsRepository.songId(it.current) else null) }.distinctUntilChanged(),
                state.map { it.mode }.distinctUntilChanged(), container.accountRepository.sessionRevision,
                settings.map { it.server }.distinctUntilChanged()) { context, playerMode, _, _ -> context to playerMode }
                .collectLatest { (context, playerMode) ->
                    val (mode, playlistId, id) = context
                    if (mode != PlaybackMode.HEART || playlistId <= 0 || id == null || playerMode != PlayerMode.LOCAL) return@collectLatest
                    val epoch = actionEpoch
                    heartMutex.withLock {
                        _heartLoading.value = true
                        try {
                            when (val result = appResult { netease.heartList(id, playlistId) }) {
                                is AppResult.Success -> if (epoch == actionEpoch && state.value.mode == PlayerMode.LOCAL) player.queue.appendHeart(result.value.songs, playlistId)
                                is AppResult.Failure -> if (epoch == actionEpoch) _actions.update { it.copy(error = "心动推荐：${result.kind.message}") }
                            }
                        } finally { _heartLoading.value = false }
                    }
                }
        }
    }
    fun togglePrimaryLike() {
        val song = queue.value.current?.takeUnless { it.video } ?: return
        if (_actions.value.liking) return
        val epoch = actionEpoch
        val expected = RequestSession(container.accountRepository.server, container.accountRepository.token)
        _actions.update { it.copy(liking = true, error = null) }
        viewModelScope.launch {
            try {
                when (val result = appResult {
                    val native = container.primaryLibrary.resolveNetease()
                    if (expected != RequestSession(container.accountRepository.server, container.accountRepository.token)) throw ApiException(ErrorKind.Unauthorized)
                    if (native) {
                        val id = NeteaseSongActionsRepository.songId(song) ?: throw ApiException(ErrorKind.NotFound)
                        netease.toggleLiked(id, song)
                    } else when (val write = container.libraryRepository.toggleLike(song)) {
                        is AppResult.Success -> container.libraryRepository.statuses.value[song.id]?.liked == true
                        is AppResult.Failure -> throw ApiException(write.kind)
                    }
                }) {
                    is AppResult.Success -> if (epoch == actionEpoch) {
                        _actions.update { it.copy(liked = result.value) }
                        container.libraryRepository.invalidate()
                        val count = NeteaseSongActionsRepository.songId(song)?.let { appResult { netease.likeCount(it, fresh = true) } }
                        if (epoch == actionEpoch && count is AppResult.Success) _actions.update { it.copy(likeCount = count.value) }
                    }
                    is AppResult.Failure -> if (epoch == actionEpoch) _actions.update { it.copy(error = result.kind.message) }
                }
            } finally { if (epoch == actionEpoch) _actions.update { it.copy(liking = false) } }
        }
    }
    fun loadComments(more: Boolean = false) {
        val id = NeteaseSongActionsRepository.songId(queue.value.current) ?: return
        if (_comments.value.loading || more && !_comments.value.more) return
        val epoch = actionEpoch
        val previous = _comments.value
        _comments.value = if (more) previous.copy(loading = true, error = null) else PlayerComments(songId = id, loading = true)
        commentsJob = viewModelScope.launch {
            val offset = if (more) previous.nextOffset else 0
            when (val result = appResult { netease.comments(id, offset, fresh = true) }) {
                is AppResult.Success -> if (epoch == actionEpoch) {
                    val page = result.value
                    _comments.value = PlayerComments(id, page.total, if (more) previous.hot else page.hotComments,
                        ((if (more) previous.latest else emptyList()) + page.comments).distinctBy { it.commentId },
                        page.more && page.comments.isNotEmpty(), offset + page.comments.size)
                    _actions.update { it.copy(commentCount = page.total?.takeIf { n -> n >= 0 }) }
                }
                is AppResult.Failure -> if (epoch == actionEpoch) _comments.update { it.copy(loading = false, error = result.kind.message) }
            }
        }
    }
    fun cyclePlaybackMode() {
        setPlaybackMode(when (queue.value.mode) {
            PlaybackMode.LIST -> PlaybackMode.ONE
            PlaybackMode.ONE -> PlaybackMode.SHUFFLE
            PlaybackMode.SHUFFLE -> if (NeteaseSongActionsRepository.songId(queue.value.current) != null) PlaybackMode.HEART else PlaybackMode.LIST
            PlaybackMode.HEART -> PlaybackMode.LIST
        })
    }
    // Every entry point selects the same exclusive queue mode. HEART needs recommendations first.
    fun setPlaybackMode(mode: PlaybackMode) {
        if (state.value.mode != PlayerMode.LOCAL) { _actions.update { it.copy(error = "请先退出一起听或结束投屏") }; return }
        if (mode != PlaybackMode.HEART) {
            heartStartJob?.cancel()
            player.setMode(mode)
            _actions.update { it.copy(error = null) }
            return
        }
        if (queue.value.mode == PlaybackMode.HEART) return
        if (_heartLoading.value) return
        val id = NeteaseSongActionsRepository.songId(queue.value.current) ?: return
        val epoch = actionEpoch
        _actions.update { it.copy(error = null) }
        heartStartJob = viewModelScope.launch {
            heartMutex.withLock {
                _heartLoading.value = true
                try {
                    when (val result = appResult { netease.heartList(id) }) {
                        is AppResult.Success -> if (epoch == actionEpoch && state.value.mode == PlayerMode.LOCAL) player.startHeartMode(result.value.songs, result.value.playlistId)
                        is AppResult.Failure -> if (epoch == actionEpoch) _actions.update { it.copy(error = result.kind.message) }
                    }
                } finally { _heartLoading.value = false }
            }
        }
    }
    fun quality(value: io.github.currencortex.music.core.media.AudioQuality) = viewModelScope.launch {
        container.musicSettings.setQuality(value)
        player.qualityChanged()
    }
    fun suppressWarning() = viewModelScope.launch { container.musicSettings.setWarning(false); player.acceptHighSpec() }
    fun lyricsFontSize(value: Float) = viewModelScope.launch { container.musicSettings.setLyricsFontSize(value) }
    fun lyricsWeight(value: io.github.currencortex.music.data.settings.LyricsWeight) = viewModelScope.launch { container.musicSettings.setLyricsWeight(value) }
    fun lyricsDisplay(transform: (io.github.currencortex.music.data.settings.LyricsDisplayOptions) -> io.github.currencortex.music.data.settings.LyricsDisplayOptions) =
        viewModelScope.launch { container.musicSettings.editLyricsDisplay(transform) }
}

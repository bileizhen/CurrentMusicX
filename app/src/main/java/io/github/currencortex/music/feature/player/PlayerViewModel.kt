package io.github.currencortex.music.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import io.github.currencortex.music.core.media.PlayerMode
import io.github.currencortex.music.core.media.PlaybackMode
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.song.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

typealias LyricsUiState = io.github.currencortex.music.feature.lyrics.data.PlaybackLyricsState

data class NavigationPlayback(val mode: PlayerMode, val error: String?)
data class PlayerSongActions(val songId: Long? = null, val likeCount: Long? = null, val commentCount: Long? = null,
    val liked: Boolean? = null, val liking: Boolean = false, val error: String? = null)
data class PlayerLikePreview(val song: Song, val liked: Boolean)
data class PlayerComments(val songId: Long? = null, val total: Long? = null, val hot: List<NeteaseComment> = emptyList(),
    val latest: List<NeteaseComment> = emptyList(), val more: Boolean = false, val nextOffset: Int = 0,
    val loading: Boolean = false, val error: String? = null)
data class PlayerCommentComposer(val draft: String = "", val replyTo: NeteaseComment? = null,
    val sending: Boolean = false, val error: String? = null, val message: String? = null)
class PlayerViewModel(internal val container: AppContainer) : ViewModel() {
    val player = container.playerController
    val sleepTimer = container.sleepTimer
    val state = player.state
    val queue = player.queue.state
    val together = container.listenTogether
    suspend fun queuePlaylists() = container.neteaseLibrary.playlists(fresh = true).filter { it.nativeOwned }
    suspend fun collectQueue(playlistId: Long, songs: List<Song>) = container.neteaseLibrary.addAll(playlistId, songs)
    // The navigation host needs only low-frequency state, never the playback position.
    val navigation = state.map { NavigationPlayback(it.mode, it.error) }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NavigationPlayback(state.value.mode, state.value.error))
    val currentSong = queue.map { it.current }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), queue.value.current)
    val settings = container.musicSettings.state
    val ai = PlayerAiController(io.github.currencortex.music.data.profile.PlayerAiRepository(session = { container.audioSession() }), viewModelScope) { container.audioSession() }
    fun openAi() { queue.value.current?.let(ai::open) }
    fun playAiCard(card: io.github.currencortex.music.data.profile.PostAttachment) {
        val song = Song(card.id, card.name, card.sub, cover = card.pic)
        if (player.external != null) player.external?.request(song) else player.playList(listOf(song), 0)
    }
    val libraryStatuses = container.libraryRepository.statuses
    val usesNeteaseLibrary = container.primaryLibrary.likesUseNetease
    val lyrics = container.playbackLyrics.state
    private val _actions = MutableStateFlow(PlayerSongActions())
    val actions = _actions.asStateFlow()
    private val _comments = MutableStateFlow(PlayerComments())
    val comments = _comments.asStateFlow()
    val commentComposer = MutableStateFlow(PlayerCommentComposer())
    private val _heartLoading = MutableStateFlow(false)
    val heartLoading = _heartLoading.asStateFlow()
    private val _likePreview = MutableStateFlow<PlayerLikePreview?>(null)
    val likePreview = _likePreview.asStateFlow()
    private class LikeWrite(val song: Song, val owner: List<Any?>, val session: RequestSession,
        val likeMode: io.github.currencortex.music.data.settings.LikeMode, var confirmed: Boolean, var desired: Boolean) {
        var revision = 0L
    }
    private var likeWrite: LikeWrite? = null
    private val likeMutex = Mutex()
    private var actionsOwner: List<Any?>? = null
    private fun likeOwner(): List<Any?> = listOf(queue.value.current?.id, queue.value.current?.musicSource,
        queue.value.current?.navidrome?.identity, container.musicSession(), settings.value.likeMode,
        usesNeteaseLibrary.value, container.primaryLibrary.identity.value.second)
    private var actionEpoch = 0L
    private var commentsJob: Job? = null
    private var commentSendJob: Job? = null
    private var commentsEpoch = 0L
    private var commentsOwner: Pair<Long?, RequestSession>? = null
    private var heartStartJob: Job? = null
    private var heartStartRevision = 0L
    private val heartMutex = Mutex()
    private val netease = container.neteaseSongActions
    init {
        viewModelScope.launch { container.accountRepository.sessionRevision.drop(1).collect { ai.close() } }
        viewModelScope.launch { settings.map { it.server }.distinctUntilChanged().drop(1).collect { ai.close() } }
        viewModelScope.launch {
            combine(queue.map { if (it.current?.musicSource == MusicSource.NAVIDROME) it.current?.id else NeteaseSongActionsRepository.songId(it.current) }.distinctUntilChanged(),
                container.accountRepository.sessionRevision, container.musicSettings.state.map { it.server to it.likeMode }.distinctUntilChanged(),
                netease.revision, container.libraryRepository.revision, container.primaryLibrary.identity,
                container.navidrome.likes.map { if (queue.value.current?.musicSource == MusicSource.NAVIDROME) it else emptyMap() }.distinctUntilChanged(),
                container.navidrome.state.map { if (queue.value.current?.musicSource == MusicSource.NAVIDROME) it.revision else "" }.distinctUntilChanged(),
                container.neteaseSessions.state.map { it.revision }.distinctUntilChanged()) { values ->
                    values.first() as Long? to values.drop(1)
                }.collectLatest { (id, _) ->
                actionEpoch++
                val owner = id to container.musicSession()
                if (owner != commentsOwner) {
                    commentsOwner = owner; commentsEpoch++
                    commentsJob?.cancel(); commentSendJob?.cancel()
                    _comments.value = PlayerComments(songId = id)
                    commentComposer.value = PlayerCommentComposer()
                }
                val nextOwner = likeOwner()
                if (actionsOwner != nextOwner) {
                    _actions.value = PlayerSongActions(songId = id)
                    if (likeWrite?.owner != nextOwner) _likePreview.value = null
                }
                actionsOwner = nextOwner
                if (id == null) return@collectLatest
                if (queue.value.current?.musicSource == MusicSource.NAVIDROME) {
                    _actions.value = PlayerSongActions(songId = id, liked = container.navidrome.likes.value[id] == true)
                    return@collectLatest
                }
                supervisorScope {
                    if (container.accountRepository.token != null)
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
        val owner = likeOwner()
        likeWrite?.takeIf { it.owner == owner }?.let { pending ->
            pending.desired = !pending.desired; pending.revision++
            _likePreview.value = PlayerLikePreview(song, pending.desired)
            _actions.update { it.copy(liked = pending.desired, liking = true, error = null) }
            return
        }
        val actionId = if (song.musicSource == MusicSource.NAVIDROME) song.id else NeteaseSongActionsRepository.songId(song)
        val before = if (song.musicSource == MusicSource.NAVIDROME || usesNeteaseLibrary.value)
            _actions.value.takeIf { it.songId == actionId }?.liked == true else libraryStatuses.value[song.id]?.liked == true
        val write = LikeWrite(song, owner, container.musicSession(), settings.value.likeMode, before, !before)
        likeWrite = write
        _likePreview.value = PlayerLikePreview(song, write.desired)
        _actions.update { it.copy(liked = write.desired, liking = true, error = null) }
        viewModelScope.launch {
            try {
                likeMutex.withLock {
                    while (likeWrite === write) {
                        val desired = write.desired
                        val revision = write.revision
                        val result = appResult {
                            if (write.session != container.musicSession() || write.likeMode != settings.value.likeMode)
                                throw ApiException(ErrorKind.Unauthorized)
                            if (song.musicSource == MusicSource.NAVIDROME) return@appResult container.navidrome.setLiked(song, desired)
                            val native = container.primaryLibrary.resolveLikeNetease()
                            if (write.session != container.musicSession() || write.likeMode != settings.value.likeMode)
                                throw ApiException(ErrorKind.Unauthorized)
                            if (native) {
                                val id = NeteaseSongActionsRepository.songId(song) ?: throw ApiException(ErrorKind.NotFound)
                                netease.setLiked(id, desired, write.session, song)
                                desired
                            } else when (val saved = container.libraryRepository.setLiked(song, desired)) {
                                is AppResult.Success -> container.libraryRepository.statuses.value[song.id]?.liked == true
                                is AppResult.Failure -> throw ApiException(saved.kind)
                            }
                        }
                        if (result is AppResult.Success) write.confirmed = result.value
                        if (likeWrite !== write) break
                        // A response to an earlier tap cannot undo a newer visible choice.
                        if (revision != write.revision) continue
                        if (owner == likeOwner()) _actions.update { it.copy(liked = write.confirmed,
                            error = (result as? AppResult.Failure)?.kind?.message) }
                        break
                    }
                }
            } finally {
                if (likeWrite === write) {
                    likeWrite = null; _likePreview.value = null
                    if (owner == likeOwner()) _actions.update { it.copy(liking = false) }
                }
            }
            if (owner == likeOwner()) {
                container.libraryRepository.invalidate()
                val count = NeteaseSongActionsRepository.songId(song)?.let { appResult { netease.likeCount(it, fresh = true) } }
                if (owner == likeOwner() && count is AppResult.Success) _actions.update { it.copy(likeCount = count.value) }
            }
        }
    }
    fun loadComments(more: Boolean = false) {
        val id = NeteaseSongActionsRepository.songId(queue.value.current) ?: return
        if (_comments.value.loading || more && !_comments.value.more) return
        val epoch = commentsEpoch
        val previous = _comments.value
        _comments.value = previous.copy(songId = id, loading = true, error = null)
        commentsJob = viewModelScope.launch {
            val offset = if (more) previous.nextOffset else 0
            when (val result = appResult { netease.comments(id, offset, fresh = true) }) {
                is AppResult.Success -> if (epoch == commentsEpoch) {
                    val page = result.value
                    _comments.value = PlayerComments(id, page.total, if (more) previous.hot else page.hotComments,
                        ((if (more) previous.latest else emptyList()) + page.comments).distinctBy { it.commentId },
                        page.more && page.comments.isNotEmpty(), offset + page.comments.size)
                    _actions.update { it.copy(commentCount = page.total?.takeIf { n -> n >= 0 }) }
                }
                is AppResult.Failure -> if (epoch == commentsEpoch) _comments.update { it.copy(loading = false, error = result.kind.message) }
            }
        }
    }
    fun editComment(value: String) { if (!commentComposer.value.sending) commentComposer.update { it.copy(draft = value.take(1000), error = null, message = null) } }
    fun replyTo(comment: NeteaseComment?) { if (!commentComposer.value.sending) commentComposer.update { it.copy(replyTo = comment, error = null, message = null) } }
    fun sendComment() {
        val id = NeteaseSongActionsRepository.songId(queue.value.current) ?: return
        val draft = commentComposer.value
        if (draft.sending || draft.draft.isBlank()) return
        val epoch = commentsEpoch
        commentComposer.update { it.copy(sending = true, error = null, message = null) }
        commentSendJob = viewModelScope.launch {
            val result = appResult { netease.sendComment(id, draft.draft, draft.replyTo?.commentId) }
            if (epoch != commentsEpoch) return@launch
            when (result) {
                is AppResult.Success -> {
                    commentComposer.value = PlayerCommentComposer(message = "评论已发送")
                    commentsJob?.cancel(); _comments.update { it.copy(loading = false) }; loadComments()
                }
                is AppResult.Failure -> commentComposer.update { it.copy(sending = false, error =
                    if (result.kind in setOf(ErrorKind.Timeout, ErrorKind.Network, ErrorKind.Server))
                        "发送结果未确认，请先刷新评论核对后再重试"
                    else "发送失败：${result.kind.message}") }
            }
        }
    }
    fun cyclePlaybackMode() {
        if (together.state.value.active) {
            together.setQueueMode(when (together.queue.value.mode) {
                PlaybackMode.LIST -> PlaybackMode.ONE; PlaybackMode.ONE -> PlaybackMode.SHUFFLE; else -> PlaybackMode.LIST
            })
            return
        }
        setPlaybackMode(when (queue.value.mode) {
            PlaybackMode.LIST -> PlaybackMode.ONE
            PlaybackMode.ONE -> PlaybackMode.SHUFFLE
            PlaybackMode.SHUFFLE -> if (NeteaseSongActionsRepository.songId(queue.value.current) != null) PlaybackMode.HEART else PlaybackMode.LIST
            PlaybackMode.HEART -> PlaybackMode.LIST
        })
    }
    // Select immediately; recommendations fill the queue later or restore the previous mode on failure.
    fun setPlaybackMode(mode: PlaybackMode) {
        if (together.state.value.active) { together.setQueueMode(mode); return }
        if (state.value.mode != PlayerMode.LOCAL) { _actions.update { it.copy(error = "请先退出一起听或结束投屏") }; return }
        if (mode != PlaybackMode.HEART) {
            heartStartRevision++
            heartStartJob?.cancel()
            player.setMode(mode)
            _actions.update { it.copy(error = null) }
            return
        }
        if (queue.value.mode == PlaybackMode.HEART) return
        val id = NeteaseSongActionsRepository.songId(queue.value.current) ?: return
        val expected = container.musicSession()
        val previousMode = queue.value.mode
        val revision = ++heartStartRevision
        heartStartJob?.cancel()
        player.setMode(PlaybackMode.HEART)
        _actions.update { it.copy(error = null) }
        heartStartJob = viewModelScope.launch {
            heartMutex.withLock {
                _heartLoading.value = true
                try {
                    when (val result = appResult { netease.heartList(id) }) {
                        is AppResult.Success -> if (revision == heartStartRevision && state.value.mode == PlayerMode.LOCAL && queue.value.mode == PlaybackMode.HEART) {
                            if (expected == container.musicSession()) player.startHeartMode(result.value.songs, result.value.playlistId)
                            else player.setMode(previousMode)
                        }
                        is AppResult.Failure -> if (revision == heartStartRevision && queue.value.mode == PlaybackMode.HEART) {
                            player.setMode(previousMode)
                            _actions.update { it.copy(error = result.kind.message) }
                        }
                    }
                } finally { _heartLoading.value = false }
            }
        }
    }
    fun quality(value: io.github.currencortex.music.core.media.AudioQuality) = viewModelScope.launch {
        container.musicSettings.setQuality(value)
        player.qualityChanged()
    }
    fun lyricsFontSize(value: Float) = viewModelScope.launch { container.musicSettings.setLyricsFontSize(value) }
    fun lyricsWeight(value: io.github.currencortex.music.data.settings.LyricsWeight) = viewModelScope.launch { container.musicSettings.setLyricsWeight(value) }
    fun lyricsDisplay(transform: (io.github.currencortex.music.data.settings.LyricsDisplayOptions) -> io.github.currencortex.music.data.settings.LyricsDisplayOptions) =
        viewModelScope.launch { container.musicSettings.editLyricsDisplay(transform) }
}

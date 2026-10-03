package io.github.currencortex.music.core.media

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import androidx.media3.ui.PlayerView
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.channels.Channel
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.session.*
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.Futures
import io.github.currencortex.music.CurrentMusicApplication
import io.github.currencortex.music.MainActivity
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.local.QueueSnapshotEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.guava.future
import kotlinx.serialization.encodeToString

@androidx.annotation.OptIn(UnstableApi::class)
class MusicService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container get() = (application as CurrentMusicApplication).container
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private var loading: Job? = null
    private var pendingPosition = 0L
    private var pendingPlay = true
    private var approvedSong: Long? = null
    private val listening = ListeningTracker()
    private var trackedSong: Song? = null
    private var trackedSession = RequestSession("", null)
    private var recorded = false
    private var lastFlush = 0L
    private var videoView: PlayerView? = null
    private data class Report(val song: Song, val session: RequestSession, val ms: Long? = null)
    private val reports = Channel<Report>(Channel.UNLIMITED)
    private fun currentSession() = RequestSession(container.accountRepository.server, container.accountRepository.token)
    private fun flushListening() {
        val ms = listening.drain(SystemClock.elapsedRealtime())
        trackedSong?.takeIf { recorded && ms > 0 && !it.video }?.let { reports.trySend(Report(it, trackedSession, ms)) }
    }
    inner class VideoBinder : Binder() {
        fun attach(view: PlayerView) {
            if (videoView !== view) { videoView?.player = null; videoView = view }
            if (view.player !== player) view.player = player
        }
        fun detach(view: PlayerView) { if (videoView === view) { view.player = null; videoView = null } }
    }
    override fun onBind(intent: Intent?): IBinder? = if (intent?.action == VIDEO_SURFACE) VideoBinder() else super.onBind(intent)
    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(okhttp3.OkHttpClient())))
            .build().apply {
            setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(), true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
        }
        val forwarding = object : ForwardingSimpleBasePlayer(player) {
            // Rebuild listener state as well as getters: ExoPlayer's single-item timeline
            // must not remove the transport commands for the independent business queue.
            override fun getState(): State {
                val state = super.getState()
                return state.buildUpon().setAvailableCommands(state.availableCommands.buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM).add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM).build()).build()
            }
            override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
                when (seekCommand) {
                    Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> nextSong()
                    Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> previousSong()
                    else -> return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
                }
                return Futures.immediateVoidFuture()
            }
            override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
                if (!playWhenReady) {
                    pendingPlay = false
                    loading?.cancel()
                    container.playerController.state.value = container.playerController.state.value.copy(loading = false, resolving = false)
                } else if (container.playerController.state.value.warning != null) {
                    pendingPlay = true
                    return Futures.immediateVoidFuture()
                } else if (player.currentMediaItem?.mediaId != container.playbackQueue.state.value.current?.id?.toString()) {
                    prepareSong(true, container.playbackQueue.state.value.positionMs)
                    return Futures.immediateVoidFuture()
                }
                return super.handleSetPlayWhenReady(playWhenReady)
            }
            override fun handleStop(): ListenableFuture<*> {
                loading?.cancel()
                return super.handleStop()
            }
        }
        session = MediaSession.Builder(this, forwarding).setSessionActivity(
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        ).setCallback(MusicSessionCallback()).build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                listening.update(SystemClock.elapsedRealtime(), isPlaying)
                val song = trackedSong
                if (isPlaying && !recorded && song != null && !song.video && container.accountRepository.token != null) {
                    recorded = true
                    reports.trySend(Report(song, trackedSession))
                }
                if (!isPlaying) flushListening()
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) { container.playbackQueue.next(automatic = true); prepareSong(true, 0) }
            }
            override fun onPlayerError(error: PlaybackException) {
                container.playerController.state.value = container.playerController.state.value.copy(error = "音频播放失败，请重试或降低音质")
                container.logger.warn("Player", "Playback failed code=${error.errorCode}")
            }
        })
        scope.launch(Dispatchers.IO) {
            for (report in reports) if (report.session == currentSession()) {
                val result = appResult {
                    if (report.ms == null) container.libraryRepository.recordPlay(report.song, report.session)
                    else container.libraryRepository.listen(report.song, report.ms, report.session)
                }
                if (result is AppResult.Failure) container.logger.warn("Player", "Listening report failed: ${result.kind}")
            }
        }
        scope.launch {
            while (isActive) {
                delay(1500)
                val now = SystemClock.elapsedRealtime()
                listening.update(now, player.isPlaying)
                if (now - lastFlush >= 15_000) { flushListening(); lastFlush = now }
                val queue = container.playbackQueue
                if (player.currentMediaItem?.mediaId == queue.state.value.current?.id?.toString())
                    queue.state.value = queue.state.value.copy(positionMs = player.currentPosition.coerceAtLeast(0), quality = container.musicSettings.state.value.quality)
                persistQueue()
            }
        }
        scope.launch { container.playbackQueue.state.collectLatest { persistQueue() } }
    }
    private suspend fun persistQueue() {
        container.database.music().saveQueue(QueueSnapshotEntity(payload = ApiJson.encodeToString(container.playerController.queueForPersistence())))
    }
    private fun nextSong() { container.playbackQueue.next(); prepareSong(true, 0) }
    private fun previousSong() { container.playbackQueue.previous(); prepareSong(true, 0) }
    private fun prepareSong(play: Boolean, position: Long) {
        loading?.cancel()
        player.pause()
        val song = container.playbackQueue.state.value.current ?: return
        if (trackedSong?.id != song.id || trackedSession != currentSession()) {
            flushListening(); trackedSong = song; trackedSession = currentSession(); recorded = false
        }
        container.playerController.state.value = PlayerState(song = song, loading = true, resolving = true, positionMs = position, durationMs = song.durationMs)
        loading = scope.launch {
            when (val result = appResult { withContext(Dispatchers.IO) {
                if (song.video) io.github.currencortex.music.data.song.AudioSource(container.libraryRepository.mvSource(song.mv), "video", 0, 0)
                else container.musicRepository.source(song.id, container.musicSettings.snapshot().quality)
            } }) {
                is AppResult.Failure -> container.playerController.state.value = container.playerController.state.value.copy(loading = false, resolving = false, error = result.kind.message)
                is AppResult.Success -> {
                    if (container.playbackQueue.state.value.current?.id != song.id) return@launch
                    val source = result.value
                    if (source.highSpec && container.musicSettings.snapshot().warnHighSpec && approvedSong != song.id) {
                        pendingPlay = play; pendingPosition = position
                        container.playerController.state.value = container.playerController.state.value.copy(loading = false, resolving = false, warning = HighSpecWarning(song.id, source))
                        return@launch
                    }
                    container.playerController.state.value = container.playerController.state.value.copy(resolving = false, warning = null, error = null)
                    player.setMediaItem(MediaItemFactory.create(song, source.url), position)
                    player.prepare(); player.playWhenReady = play
                }
            }
        }
    }
    inner class MusicSessionCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(LOAD, Bundle.EMPTY)).add(SessionCommand(ACCEPT_SPEC, Bundle.EMPTY)).build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(commands).build()
        }
        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo,
                                     command: SessionCommand, args: Bundle): ListenableFuture<SessionResult> = scope.future {
            // Queue mutation commands are reserved for this application's controller.
            if (controller.packageName != packageName) return@future SessionResult(SessionError.ERROR_PERMISSION_DENIED)
            when (command.customAction) {
                LOAD -> prepareSong(args.getBoolean("play", true), args.getLong("position", 0))
                ACCEPT_SPEC -> {
                    approvedSong = container.playerController.state.value.warning?.songId
                    prepareSong(pendingPlay, pendingPosition)
                }
                else -> return@future SessionResult(SessionError.ERROR_NOT_SUPPORTED)
            }
            SessionResult(SessionResult.RESULT_SUCCESS)
        }
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady) { container.playerController.disconnect(); stopSelf() }
    }
    override fun onDestroy() {
        flushListening(); reports.close(); videoView?.player = null; videoView = null
        loading?.cancel()
        scope.cancel()
        session?.release(); player.release(); session = null
        super.onDestroy()
    }
    companion object { const val LOAD = "currentmusic.load"; const val ACCEPT_SPEC = "currentmusic.acceptSpec"; const val VIDEO_SURFACE = "io.github.currencortex.music.VIDEO_SURFACE" }
}

package io.github.currencortex.music.core.media

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import androidx.media3.ui.PlayerView
import io.github.currencortex.music.data.song.Song
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.guava.future
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@androidx.annotation.OptIn(UnstableApi::class)
class MusicService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container get() = (application as CurrentMusicApplication).container
    private lateinit var transitions: PlaybackTransitions
    private val player get() = transitions.player
    private fun directOutput() = container.audioOutput.state.value.mode.bypassProcessing
    private lateinit var audioFocus: PlaybackAudioFocus
    private lateinit var audioFocusReady: Deferred<Unit>
    private var refreshSession: () -> Unit = {}
    private var userVolume = 1f
    private var userMuted = false
    private var transportGain = 1f
    private val deckVolumes = mutableMapOf<ExoPlayer, Float>()
    private var roomPausePosition: Long? = null
    private val pauseFade = PlaybackPauseFade(scope, applyGain = { gain ->
        transportGain = gain; applyVolumes()
    }, pausePlayer = {
        val roomAnchor = roomPausePosition
        roomPausePosition = null
        transitions.pauseCompanion()
        transitions.cancel()
        player.pause()
        // The audible tail must not accumulate 200 ms of room drift on every pause.
        if (roomAnchor != null && container.playerController.state.value.mode == PlayerMode.ROOM)
            player.seekTo(roomAnchor)
    }, changed = { refreshSession() })
    private fun resetPauseFade() { roomPausePosition = null; pauseFade.reset() }
    private fun applyVolumes() {
        deckVolumes.toList().forEach { (source, gain) -> source.volume = if (userMuted) 0f else gain * userVolume * transportGain }
    }
    private fun deckVolume(source: ExoPlayer, gain: Float) {
        deckVolumes[source] = gain
        source.volume = if (userMuted) 0f else gain * userVolume * transportGain
    }
    private data class PlayerSlot(val meter: DockAudioLevel, val equalizer: PlaybackEqualizer,
        var originalProcessed: NeteaseOriginalPreset? = null,
        var originalFailed: Pair<NeteaseOriginalPreset, String>? = null)
    private val playerSlots = mutableMapOf<ExoPlayer, PlayerSlot>()
    private val outputManager by lazy { getSystemService(android.media.AudioManager::class.java) }
    private val outputDevicesCallback = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>) { applyOutputDevice() }
        override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>) { applyOutputDevice() }
    }
    private fun applyOutputDevice() {
        val device = outputManager.preferredOutput(container.audioOutput.state.value.deviceKey)
        playerSlots.keys.toList().forEach { it.setPreferredAudioDevice(device) }
    }
    private var desktopLyrics: io.github.currencortex.music.feature.lyrics.desktop.DesktopLyricsHost? = null
    private var session: MediaSession? = null
    private var loading: Job? = null
    private val listening = ListeningTracker()
    private var trackedSong: Song? = null
    private var trackedSession = RequestSession("", null)
    private var recorded = false
    private var lastFlush = 0L
    private var videoView: PlayerView? = null
    private val readyForPreload = MutableStateFlow(false)
    private data class PreloadPlan(val request: AudioRequest, val metered: Boolean)
    private fun currentSession(song: Song? = container.playbackQueue.state.value.current) =
        if (song?.musicSource == io.github.currencortex.music.data.song.MusicSource.NAVIDROME) container.navidrome.session() else container.musicSession()
    private fun audioRequest(song: Song, quality: AudioQuality) = container.audioRequest(song, quality)
    private fun flushListening() {
        val ms = listening.drain(SystemClock.elapsedRealtime())
        if (trackedSong?.video == false && ms > 0) container.todayListening.add(ms)
        trackedSong?.takeIf { recorded && ms > 0 && !it.video }?.let { container.listeningReports.report(it, trackedSession, ms) }
    }
    private fun updateListening(isPlaying: Boolean) {
        val song = trackedSong
        val owner = currentSession(song)
        if (song != null && owner != trackedSession) {
            flushListening()
            trackedSession = owner
            recorded = false
        }
        listening.update(SystemClock.elapsedRealtime(), isPlaying)
        if (isPlaying && !recorded && song != null && !song.video &&
            (if (song.musicSource == io.github.currencortex.music.data.song.MusicSource.NAVIDROME) container.navidrome.accepts(song)
            else container.accountRepository.token != null ||
                container.nativeNetease != null && container.neteaseSessions.state.value.loggedIn && !container.neteaseSessions.state.value.stale)) {
            recorded = true
            container.listeningReports.report(song, trackedSession)
        }
        if (!isPlaying) flushListening()
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
        desktopLyrics = io.github.currencortex.music.feature.lyrics.desktop.DesktopLyricsHost(this, container.playbackLyrics.state,
            container.playerController.state, container.desktopLyrics, container.desktopLyricsStatus, container.desktopLyricsSettingsVisible,
            changeSong = { direction ->
                val controller = container.playerController
                if (direction > 0) controller.next(controller.state.value.showPause) else controller.previous(controller.state.value.showPause)
            }, togglePlayback = { container.playerController.toggle() }, appearance = container.settings.state,
            appForeground = container.appForeground, effectNotices = container.islandNotices,
            installUpdate = { io.github.currencortex.music.core.update.UpdateInstallAction.open(this) },
            pageHidden = container.desktopIslandPageHidden)
        val probe = TrackEnergyProbe()
        transitions = PlaybackTransitions(createPlaybackPlayer(probe), probe, scope, ::createPlaybackPlayer,
            ::transitionOffer, resolve = { offer ->
                val resolved = container.audioSources.resolve(offer.request)
                MediaItemFactory.create(offer.next, resolved.source.url, resolved.key.takeIf(String::isNotBlank))
            }, promote = ::promoteTransition, discard = { playerSlots.remove(it)?.equalizer?.close(); deckVolumes.remove(it) },
            currentSong = { container.playbackQueue.state.value.current },
            readVolume = { deckVolumes[it] ?: 1f }, writeVolume = ::deckVolume)
        audioFocus = PlaybackAudioFocus(player, container.musicSettings.concurrentPlaybackEnabled, scope)
        audioFocusReady = audioFocus.ready
        outputManager.registerAudioDeviceCallback(outputDevicesCallback, android.os.Handler(mainLooper))
        scope.launch {
            var previous: io.github.currencortex.music.data.settings.AudioOutputSettings? = null
            container.audioOutput.state.collect { output ->
                applyOutputDevice()
                if (previous != null && (previous?.sampleRate != output.sampleRate || previous?.mode != output.mode || previous?.deviceKey != output.deviceKey) &&
                    container.playerController.state.value.mode != PlayerMode.CAST &&
                    !container.playerController.state.value.resolving && player.currentMediaItem != null) {
                    // Reconfigure the sink at the same position. Cancel the prepared companion
                    // so a crossfade cannot promote an old output configuration afterward.
                    transitions.cancel()
                    resetPauseFade()
                    if (directOutput()) player.setPlaybackSpeed(1f)
                    val position = player.currentPosition
                    val playing = player.playWhenReady
                    player.stop()
                    player.seekTo(position)
                    player.prepare()
                    player.playWhenReady = playing
                }
                previous = output
            }
        }
        session = MediaSession.Builder(this, sessionPlayer()).setSessionActivity(
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        ).setCallback(MusicSessionCallback()).build()
        scope.launch {
            var wasExternal = false
            container.playerController.state.collect { state ->
                val external = state.mode != PlayerMode.LOCAL
                if (external || wasExternal) refreshSession()
                wasExternal = external
            }
        }
        scope.launch { combine(container.smartTuning.effective, container.originalProcessingPreset, container.originalSpeakerEffects.state) { settings, original, speaker ->
            if (original == null && !speaker.requestedEnabled) settings else settings.copy(enabled = false)
        }.combine(container.audioOutput.state) { settings, output ->
            if (output.mode != io.github.currencortex.music.data.settings.AudioOutputMode.AUDIO_TRACK) settings.copy(enabled = false) else settings
        }.collect { settings ->
            container.equalizer.configure(settings)
            playerSlots.values.forEach { it.equalizer.configure(settings) }
        } }
        scope.launch {
            while (isActive) {
                val settings = container.musicSettings.state.value
                // A fading pause must not arm or promote an automatic next track.
                if (!pauseFade.pausedIntent && !directOutput()) transitions.tick(settings.transitionMode, settings.crossfadeSeconds)
                playerSlots.forEach { (source, slot) ->
                    slot.meter.enabled = source === player && container.dockAudioLevel.enabled
                    if (source === player) slot.meter.sample.let { container.dockAudioLevel.publish(it.energy, it.bass, it.timestampNanos) }
                }
                delay(if (settings.transitionMode != io.github.currencortex.music.data.settings.MusicTransitionMode.OFF || container.dockAudioLevel.enabled) 40 else 250)
            }
        }
        scope.launch {
            while (isActive) {
                delay(1500)
                val now = SystemClock.elapsedRealtime()
                updateListening(player.isPlaying)
                if (now - lastFlush >= 15_000) { flushListening(); lastFlush = now }
                val queue = container.playbackQueue
                if (player.currentMediaItem?.mediaId == queue.state.value.current?.id?.toString())
                    queue.state.value = queue.state.value.copy(positionMs = player.currentPosition.coerceAtLeast(0), quality = container.musicSettings.state.value.quality)
                val item = player.currentMediaItem
                val cacheKey = item?.localConfiguration?.customCacheKey
                if (container.playerController.state.value.mode == PlayerMode.LOCAL && cacheKey != null &&
                    item.mediaId == queue.state.value.current?.id?.toString()) {
                    // Inspect disk on IO, then reject the result if the track changed during the read.
                    val complete = withContext(Dispatchers.IO) {
                        !container.audioCache.clearing.value && container.audioCache.completeSource(cacheKey) != null
                    }
                    val state = container.playerController.state.value
                    if (player.currentMediaItem == item && item.mediaId == queue.state.value.current?.id?.toString() &&
                        state.mode == PlayerMode.LOCAL && !state.resolving)
                        container.playerController.state.value = state.copy(audioFullyCached = complete)
                }
                persistQueue()
            }
        }
        scope.launch { container.playbackQueue.state.collectLatest { persistQueue() } }
        scope.launch {
            val preloadBudget = AudioPreloadBudget()
            combine(container.playbackQueue.state, container.musicSettings.state, container.accountRepository.state) { _, settings, account ->
                settings to account.account
            }.combine(container.audioSettings.state) { data, _ -> data }
            .combine(container.neteaseSessions.state) { data, _ -> data }
            .combine(container.navidrome.state) { data, _ -> data }
            .combine(container.playerController.state) { (settings, account), state ->
                val next = if (settings.preloadAudio && state.song?.video != true && (account != null || container.nativeNetease != null || container.navidrome.state.value.configured) && state.mode == PlayerMode.LOCAL && state.playing &&
                    !state.loading && !state.resolving &&
                    player.currentMediaItem?.mediaId == container.playbackQueue.state.value.current?.id?.toString()) container.playbackQueue.previewNext() else null
                if (next == null || next.musicSource == io.github.currencortex.music.data.song.MusicSource.LOCAL) { preloadBudget.reset(); null } else {
                    val request = audioRequest(next, settings.quality)
                    if (preloadBudget.allow("${state.song?.id}:${request.key}", state.positionMs, state.bufferedPositionMs, state.durationMs))
                        PreloadPlan(request, settings.preloadMetered) else null
                }
            }.combine(readyForPreload) { plan, ready -> plan.takeIf { ready } }
                .combine(preloadNetwork(this@MusicService)) { plan, network -> plan.takeIf { network == 2 || network == 1 && it?.metered == true } }
                .combine(container.audioCache.clearing) { plan, clearing -> plan.takeUnless { clearing } }
                .distinctUntilChanged().collectLatest { plan ->
                    if (plan == null) return@collectLatest
                    delay(800)
                    NextAudioPreloader(
                        resolve = { refresh -> container.audioSources.resolve(plan.request, refresh) },
                        preload = { resolved ->
                            container.audioCache.preload(resolved.key, resolved.source)
                            true
                        },
                        onFailure = { container.logger.info("Player", "Next audio preload unavailable; foreground playback is unaffected") },
                    ).run()
                }
        }
    }
    private fun createPlaybackPlayer(probe: TrackEnergyProbe): ExoPlayer {
        val meter = DockAudioLevel()
        val secondaryEqualizer = PlaybackEqualizer().apply {
            val settings = container.smartTuning.effective.value
            configure(if (container.audioOutput.state.value.mode == io.github.currencortex.music.data.settings.AudioOutputMode.AUDIO_TRACK &&
                container.originalProcessingPreset.value == null && !container.originalSpeakerEffects.state.value.requestedEnabled) settings else settings.copy(enabled = false))
        }
        val source = ExoPlayer.Builder(this, DockRenderersFactory(this, meter, probe,
            originalPreset = { container.originalProcessingPreset.value },
            originalFailure = { failedPreset, message -> scope.launch {
                val slot = playerSlots.values.firstOrNull { it.meter === meter } ?: return@launch
                if (failedPreset != null) {
                    slot.originalFailed = failedPreset to message
                    if (slot.originalProcessed === failedPreset) slot.originalProcessed = null
                    if (::transitions.isInitialized && playerSlots[player] === slot)
                        reportOriginalFailure(failedPreset, message)
                }
            } },
            originalProcessing = { processedPreset -> scope.launch {
                val slot = playerSlots.values.firstOrNull { it.meter === meter }
                if (slot != null) {
                    slot.originalProcessed = processedPreset
                    slot.originalFailed = null
                    if (::transitions.isInitialized && playerSlots[player] === slot)
                        acknowledgeOriginalProcessing(processedPreset)
                }
            } }, outputSampleRate = { container.audioOutput.state.value.sampleRate },
            outputSettings = { container.audioOutput.state.value }, outputStatus = { container.audioOutput.status.value = it }))
            .setLoadControl(playbackLoadControl())
            .setMediaSourceFactory(DefaultMediaSourceFactory(container.audioCache.dataSourceFactory)).build().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), false)
                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_LOCAL)
                setPreferredAudioDevice(outputManager.preferredOutput(container.audioOutput.state.value.deviceKey))
            }
        playerSlots[source] = PlayerSlot(meter, secondaryEqualizer)
        deckVolume(source, 1f)
        source.addListener(object : Player.Listener {
            private fun primary() = ::transitions.isInitialized && source === player
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (primary()) container.equalizer.attach(audioSessionId) else secondaryEqualizer.attach(audioSessionId)
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!primary()) return
                if (!isPlaying) transitions.pauseCompanion()
                if (!isPlaying && source.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE)
                    pauseFade.finishPause()
                updateListening(isPlaying)
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // Android's focus/noisy safety pauses remain immediate, even during a user fade.
                if (primary() && !playWhenReady && reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                    pauseFade.finishPause()
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (!::transitions.isInitialized) return
                if (state == Player.STATE_ENDED && pauseFade.pausedIntent) { pauseFade.finishPause(); return }
                if (state == Player.STATE_ENDED && transitions.ended(source)) return
                if (!primary()) return
                readyForPreload.value = state == Player.STATE_READY
                if (state == Player.STATE_ENDED && container.playerController.state.value.mode == PlayerMode.ROOM) container.playerController.external?.ended()
                if (state == Player.STATE_ENDED && container.playerController.state.value.mode == PlayerMode.LOCAL) {
                    if (container.playbackQueue.state.value.current?.video == true) player.pause()
                    else if (container.playbackQueue.next(automatic = true)) prepareSong(true, 0, newPlayback = true)
                    else if (container.playbackQueue.state.value.radioSession != null) container.playerController.onRadioBoundary?.invoke()
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                if (!::transitions.isInitialized || transitions.failed(source)) return
                if (container.audioOutput.state.value.mode.rawPcm)
                    CurrentMusicAudioDiagnostics.record("player.finalState=ERROR errorCode=${error.errorCode}")
                if (container.audioOutput.state.value.mode.rawPcm &&
                    !container.audioOutput.status.value.contains("已停止"))
                    container.audioOutput.status.value = "${container.audioOutput.state.value.mode.label} 已停止：音源解码或输出失败，请检查设备与音源格式"
                player.playWhenReady = false
                pauseFade.finishPause()
                container.playerController.state.value = container.playerController.state.value.copy(playing = false, playRequested = false,
                    loading = false, resolving = false, error = if (container.audioOutput.state.value.mode.rawPcm)
                        container.audioOutput.status.value else if (container.playbackQueue.state.value.current?.video == true)
                        "MV 播放失败，请重试或降低清晰度" else "音频播放失败，请重试或降低音质")
                container.logger.warn("Player", "Playback failed code=${error.errorCode}")
                if (container.playerController.state.value.mode == PlayerMode.ROOM) container.playerController.external?.failed()
            }
        })
        return source
    }
    private fun acknowledgeOriginalProcessing(preset: NeteaseOriginalPreset) {
        if (container.originalProcessingPreset.value !== preset) return
        when {
            container.originalAiMasterEffects.selection.value === preset -> container.originalAiMasterEffects.reportProcessing(preset.id)
            container.originalSpeakerEffects.selection.value === preset -> container.originalSpeakerEffects.reportProcessing(preset.id)
            container.originalMoodEffects.selection.value === preset -> container.originalMoodEffects.reportProcessing(preset.id)
            container.originalSmartEffects.selection.value === preset -> container.originalSmartEffects.reportProcessing(preset.id)
            container.originalSoundEffects.selection.value === preset -> container.originalSoundEffects.reportProcessing(preset.id)
        }
    }
    private fun reportOriginalFailure(preset: NeteaseOriginalPreset, message: String) {
        if (container.originalProcessingPreset.value !== preset) return
        when {
            container.originalAiMasterEffects.selection.value === preset -> container.originalAiMasterEffects.reportFailure(message)
            container.originalSpeakerEffects.selection.value === preset -> container.originalSpeakerEffects.reportFailure(message)
            container.originalMoodEffects.selection.value === preset -> container.originalMoodEffects.reportFailure(message)
            container.originalSmartEffects.selection.value === preset -> container.originalSmartEffects.reportFailure(message)
            container.originalSoundEffects.selection.value === preset -> container.originalSoundEffects.reportFailure(message)
        }
    }
    private fun transitionOffer(): TransitionOffer? {
        val state = container.playerController.state.value
        val from = container.playbackQueue.state.value.current ?: return null
        if (state.mode != PlayerMode.LOCAL || state.resolving || state.error != null || from.video ||
            container.sleepTimer.state.value.waitingForSongEnd || container.audioCache.clearing.value ||
            player.currentMediaItem?.mediaId != from.id.toString()) return null
        val next = container.playbackQueue.previewNext()?.takeUnless { it.video } ?: return null
        return TransitionOffer(from, next, audioRequest(next, container.musicSettings.state.value.quality))
    }
    private fun promoteTransition(song: Song, outgoing: ExoPlayer, incoming: ExoPlayer) {
        flushListening()
        trackedSong = song; trackedSession = currentSession(song); recorded = false
        container.playbackQueue.next(automatic = true)
        container.playbackQueue.state.value = container.playbackQueue.state.value.copy(positionMs = incoming.currentPosition)
        audioFocus.replace(incoming)
        playerSlots[incoming]?.equalizer?.close()
        container.equalizer.attach(incoming.audioSessionId)
        playerSlots[outgoing]?.equalizer?.attach(outgoing.audioSessionId)
        videoView?.player = incoming
        readyForPreload.value = incoming.playbackState == Player.STATE_READY
        container.playerController.state.value = PlayerState(song = song, playing = incoming.isPlaying,
            playRequested = incoming.playWhenReady, positionMs = incoming.currentPosition,
            durationMs = incoming.duration.coerceAtLeast(0), bufferedPositionMs = incoming.bufferedPosition,
            anchorRealtimeMs = SystemClock.elapsedRealtime())
        session?.setPlayer(sessionPlayer())
        // A staged sink can produce its first native PCM before becoming primary.
        // Reuse that exact-preset evidence on promotion, including a staged failure.
        playerSlots[incoming]?.let { slot ->
            slot.originalFailed?.let { (preset, message) -> reportOriginalFailure(preset, message) }
            slot.originalProcessed?.let(::acknowledgeOriginalProcessing)
        }
        // Media3 publishes this timeline handoff as AUTO; PlayerController counts that event once.
        updateListening(incoming.isPlaying)
    }
    private fun sessionPlayer(): Player {
        val forwarding = object : ForwardingSimpleBasePlayer(player) {
            // Rebuild listener state as well as getters: ExoPlayer's single-item timeline
            // must not remove the transport commands for the independent business queue.
            override fun getState(): State {
                val state = super.getState()
                val builder = state.buildUpon().setAvailableCommands(state.availableCommands.buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM).add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM).build())
                val remote = container.playerController.state.value
                builder.setVolume(if (userMuted) 0f else userVolume)
                if (remote.mode != PlayerMode.CAST && pauseFade.pausedIntent)
                    builder.setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                if (remote.mode == PlayerMode.CAST && remote.song != null) {
                    val metadata = MediaItemFactory.create(remote.song, "")
                    builder.setPlaylist(listOf(SimpleBasePlayer.MediaItemData.Builder("cast-${remote.song.id}")
                        .setMediaItem(metadata).setMediaMetadata(metadata.mediaMetadata).setDurationUs(remote.durationMs.coerceAtLeast(0) * 1000)
                        .setIsSeekable(true).build())).setCurrentMediaItemIndex(0).setContentPositionMs(remote.positionMs)
                        .setPlayWhenReady(remote.playing, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
                        .setPlaybackState(Player.STATE_READY).setPlaybackSuppressionReason(Player.PLAYBACK_SUPPRESSION_REASON_NONE)
                        .setAvailableCommands(state.availableCommands.buildUpon().add(Player.COMMAND_PLAY_PAUSE)
                            .add(Player.COMMAND_STOP).add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                            .add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_PREVIOUS).build())
                }
                if (remote.mode == PlayerMode.ROOM && !remote.canControlPlayback) {
                    builder.setAvailableCommands(builder.build().availableCommands.buildUpon().removeAll(
                        Player.COMMAND_PLAY_PAUSE, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_DEFAULT_POSITION, Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                        Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD,
                        Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
                    ).build())
                }
                return builder.build()
            }
            fun remoteChanged() { invalidateState() }
            override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
                container.playerController.external?.let {
                    when (seekCommand) {
                        Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> it.next()
                        Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> it.previous()
                        else -> it.seek(positionMs)
                    }
                    return Futures.immediateVoidFuture()
                }
                transitions.cancel()
                if (pauseFade.fadingOut) pauseFade.finishPause()
                when (seekCommand) {
                    Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> nextSong()
                    Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> previousSong()
                    else -> return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
                }
                return Futures.immediateVoidFuture()
            }
            override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
                container.playerController.external?.let { it.play(playWhenReady); return Futures.immediateVoidFuture() }
                if (!playWhenReady) {
                    loading?.cancel()
                    container.playerController.state.value = container.playerController.state.value.copy(playing = false, playRequested = false, loading = false, resolving = false)
                    pauseFade.pause(!directOutput() && player.isPlaying && container.playbackQueue.state.value.current?.video != true)
                    return Futures.immediateVoidFuture()
                } else if (player.currentMediaItem?.mediaId != container.playbackQueue.state.value.current?.id?.toString()) {
                    prepareSong(true, container.playbackQueue.state.value.positionMs)
                    return Futures.immediateVoidFuture()
                }
                if (directOutput()) resetPauseFade() else pauseFade.resume()
                return super.handleSetPlayWhenReady(playWhenReady)
            }
            override fun handleSetVolume(volume: Float, volumeOperationType: Int): ListenableFuture<*> {
                when (volumeOperationType) {
                    C.VOLUME_OPERATION_TYPE_SET_VOLUME -> {
                        val target = volume.coerceIn(0f, 1f)
                        if (target > 0f) userVolume = target
                        userMuted = target == 0f
                    }
                    C.VOLUME_OPERATION_TYPE_MUTE -> userMuted = true
                    C.VOLUME_OPERATION_TYPE_UNMUTE -> userMuted = false
                }
                applyVolumes(); refreshSession()
                return Futures.immediateVoidFuture()
            }
            override fun handleStop(): ListenableFuture<*> {
                container.playerController.external?.let { it.stop(); return Futures.immediateVoidFuture() }
                loading?.cancel()
                transitions.cancel()
                player.stop(); resetPauseFade()
                return Futures.immediateVoidFuture()
            }
        }
        refreshSession = { forwarding.remoteChanged() }
        return forwarding
    }
    private suspend fun persistQueue() {
        container.database.music().saveQueue(QueueSnapshotEntity(payload = ApiJson.encodeToString(container.playerController.queueForPersistence())))
    }
    private fun nextSong() {
        if (container.playbackQueue.next()) prepareSong(true, 0)
        else if (container.playbackQueue.state.value.radioSession != null) container.playerController.onRadioBoundary?.invoke()
    }
    private fun previousSong() { container.playbackQueue.previous(); prepareSong(true, 0) }
    private fun prepareSong(play: Boolean, position: Long, newPlayback: Boolean = false) {
        if (container.playerController.state.value.mode != PlayerMode.LOCAL) return
        loading?.cancel()
        transitions.cancel()
        player.pause()
        resetPauseFade()
        val song = container.playbackQueue.state.value.current ?: return
        if (newPlayback || trackedSong?.id != song.id || trackedSong?.musicSource != song.musicSource ||
            trackedSong?.video != song.video || trackedSession != currentSession(song)) {
            flushListening(); trackedSong = song; trackedSession = currentSession(); recorded = false
        }
        container.playerController.state.value = PlayerState(song = song, loading = true, resolving = true,
            playRequested = play, positionMs = position, durationMs = song.durationMs)
        loading = scope.launch {
            audioFocusReady.await()
            when (val result = appResult { withContext(Dispatchers.IO) {
                if (song.video) ResolvedAudio(io.github.currencortex.music.data.song.AudioSource(container.libraryRepository.mvSource(song.mv, song.videoQuality), "video", 0, 0), "")
                else container.audioSources.resolve(audioRequest(song, container.musicSettings.snapshot().quality))
            } }) {
                is AppResult.Failure -> container.playerController.state.value = container.playerController.state.value.copy(playing = false,
                    playRequested = false, loading = false, resolving = false, error = result.kind.message)
                is AppResult.Success -> {
                    if (container.playbackQueue.state.value.current?.id != song.id) return@launch
                    val source = result.value.source
                    container.playerController.state.value = container.playerController.state.value.copy(resolving = false, error = null,
                        audioFullyCached = source.url.startsWith("cache://audio/"))
                    player.setMediaItem(MediaItemFactory.create(song, source.url, result.value.key.takeIf(String::isNotBlank)), position)
                    player.prepare(); player.playWhenReady = play
                }
            }
        }
    }
    inner class MusicSessionCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(LOAD, Bundle.EMPTY)).build()
            val remoteCommands = commands.buildUpon().add(SessionCommand(QUIESCE, Bundle.EMPTY))
                .add(SessionCommand(ROOM_TRACK, Bundle.EMPTY)).add(SessionCommand(ROOM_SYNC, Bundle.EMPTY)).build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(remoteCommands).build()
        }
        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo,
                                     command: SessionCommand, args: Bundle): ListenableFuture<SessionResult> = scope.future {
            // Queue mutation commands are reserved for this application's controller.
            if (controller.packageName != packageName) return@future SessionResult(SessionError.ERROR_PERMISSION_DENIED)
            audioFocusReady.await()
            when (command.customAction) {
                QUIESCE -> { loading?.cancel(); transitions.cancel(); player.pause(); resetPauseFade(); player.clearMediaItems(); player.setPlaybackSpeed(1f); flushListening(); trackedSong = null }
                ROOM_TRACK -> if (container.playerController.state.value.mode == PlayerMode.ROOM) {
                    val song = ApiJson.decodeFromString<Song>(args.getString("song") ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE))
                    val url = args.getString("url") ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE)
                    loading?.cancel(); transitions.cancel(); player.pause(); resetPauseFade(); flushListening(); trackedSong = song; trackedSession = currentSession(song); recorded = false
                    player.setPlaybackSpeed(1f); player.setMediaItem(MediaItemFactory.create(song, url), args.getLong("position")); player.prepare()
                    container.playerController.state.value = container.playerController.state.value.copy(resolving = false)
                    player.playWhenReady = args.getBoolean("play")
                }
                ROOM_SYNC -> if (container.playerController.state.value.mode == PlayerMode.ROOM && player.currentMediaItem != null) {
                    player.setPlaybackSpeed(if (directOutput()) 1f else args.getFloat("speed", 1f))
                    val position = args.getLong("position", player.currentPosition).coerceAtLeast(0)
                    if (args.getBoolean("play")) {
                        roomPausePosition = null
                        if (args.getBoolean("seek") && player.playbackState == Player.STATE_READY) player.seekTo(position)
                        if (directOutput()) resetPauseFade() else pauseFade.resume()
                        player.play()
                    } else if (!pauseFade.pausedIntent || pauseFade.fadingOut) {
                        roomPausePosition = position
                        pauseFade.pause(!directOutput() && player.isPlaying)
                    } else if (args.getBoolean("seek") && player.playbackState == Player.STATE_READY) player.seekTo(position)
                }
                LOAD -> prepareSong(args.getBoolean("play", true), args.getLong("position", 0))
                else -> return@future SessionResult(SessionError.ERROR_NOT_SUPPORTED)
            }
            SessionResult(SessionResult.RESULT_SUCCESS)
        }
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (container.playerController.state.value.mode == PlayerMode.LOCAL && !player.playWhenReady) { container.playerController.disconnect(); stopSelf() }
    }
    override fun onDestroy() {
        outputManager.unregisterAudioDeviceCallback(outputDevicesCallback)
        desktopLyrics?.close(); desktopLyrics = null
        listening.update(SystemClock.elapsedRealtime(), false)
        flushListening(); trackedSong = null; videoView?.player = null; videoView = null
        loading?.cancel()
        scope.cancel()
        resetPauseFade()
        transitions.close()
        playerSlots.remove(player)?.equalizer?.close()
        session?.release(); player.release(); session = null
        container.equalizer.close()
        container.dockAudioLevel.reset()
        super.onDestroy()
    }
    companion object {
        const val LOAD = "currentmusic.load"
        const val QUIESCE = "currentmusic.quiesce"; const val ROOM_TRACK = "currentmusic.roomTrack"; const val ROOM_SYNC = "currentmusic.roomSync"
        const val VIDEO_SURFACE = "io.github.currencortex.music.VIDEO_SURFACE"
    }
}

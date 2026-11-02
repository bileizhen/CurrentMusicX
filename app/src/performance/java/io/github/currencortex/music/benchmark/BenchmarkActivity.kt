package io.github.currencortex.music.benchmark

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.Trace
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.AudioOutputMode
import io.github.currencortex.music.feature.home.DailyRecommendationHero
import io.github.currencortex.music.feature.home.HomeLibraryShortcuts
import io.github.currencortex.music.feature.library.PlaylistPageLayout
import io.github.currencortex.music.feature.library.PlaylistTrack
import io.github.currencortex.music.feature.lyrics.ui.LyricsScreen
import io.github.currencortex.music.feature.lyrics.ui.rememberLyricsPosition
import io.github.currencortex.music.feature.player.PlayerScreen
import io.github.currencortex.music.feature.player.PlayerViewModel
import io.github.currencortex.music.feature.settings.AudioOutputScreen
import io.github.currencortex.music.feature.settings.SettingsScreen
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Deterministic production-component CUJs, separate from real MainActivity launch measurements. */
@androidx.annotation.OptIn(UnstableApi::class)
class BenchmarkActivity : ComponentActivity() {
    private val app get() = application as BenchmarkApplication
    private var scene by mutableStateOf("home")
    private var ready by mutableStateOf(false)
    private var failure by mutableStateOf<String?>(null)
    private var smokeSummary by mutableStateOf("")
    private var serial = 0
    private var activeTrace: Pair<String, Int>? = null
    private lateinit var playerVm: PlayerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.playerOwner = System.identityHashCode(this)
        scene = intent.getStringExtra("scene")?.takeIf { it == "smoke" } ?: "home"
        playerVm = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PlayerViewModel(app.container) as T
        })[PlayerViewModel::class.java]
        startTrace(scene)
        setContent {
            LeiTheme(AppearanceSettings()) {
                val sample by app.container.playerController.state.collectAsState()
                Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
                    if (failure != null) Text("Unsupported: $failure", Modifier.semantics {
                        contentDescription = "benchmark_unsupported_$scene"
                    })
                    else if (initialized) {
                        when (scene) {
                            "home" -> LocalHome(sample)
                            "player", "play" -> PlayerScreen(playerVm, onBack = { change("home") },
                                onToggle = { if (app.localPlayer.isPlaying) app.localPlayer.pause() else app.localPlayer.play() })
                            "lyrics" -> LyricsScreen(BenchmarkData.lyrics, rememberLyricsPosition(sample), {},
                                Modifier.fillMaxSize().padding(top = 72.dp, start = 24.dp, end = 24.dp),
                                fontSize = 32f)
                            "playlist" -> LocalPlaylist()
                            "settings" -> SettingsScreen({}, {}, {}, {}, {}, {}, onAudioOutput = { change("output") })
                            "output" -> AudioOutputScreen(app.container)
                            "smoke" -> Text(smokeSummary, Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)
                                .semantics { contentDescription = "benchmark_result_smoke" })
                        }
                        if (ready) Box(Modifier.size(1.dp).semantics { contentDescription = "benchmark_ready_$scene" })
                        LaunchedEffect(scene, pendingFrame) {
                            if (pendingFrame) {
                                // Finish only after composition/layout has crossed display frames.
                                repeat(3) { withFrameNanos { } }
                                pendingFrame = false
                                finishTrace()
                                ready = true
                                if (scene == "home") reportFullyDrawn()
                            }
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            try {
                require(Build.VERSION.SDK_INT >= 29) { "Fixture async tracing requires API 29+" }
                app.container.ready.await()
                if (scene == "smoke") {
                    if (app.localPlayerCreated) app.localPlayer.stop()
                    smokeSummary = MinifiedRuntimeSmoke.run(this@BenchmarkActivity, app.container)
                    android.util.Log.i("CurrentMusicRuntimeSmoke", smokeSummary)
                    initialized = true
                    pendingFrame = true
                    return@launch
                }
                app.localPlayer.stop()
                app.container.audioOutput.setMode(AudioOutputMode.AUDIO_TRACK)
                app.container.audioOutput.state.first { it.mode == AudioOutputMode.AUDIO_TRACK }
                app.prepareLocal()
                awaitPlayer()
                publishState()
                initialized = true
                pendingFrame = true
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    while (true) { publishState(); delay(100) }
                }
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) { unsupported(error) }
        }
    }

    private var initialized by mutableStateOf(false)
    private var pendingFrame by mutableStateOf(false)

    private fun change(next: String) {
        if (!ready) return
        ready = false
        scene = next
        startTrace(next)
        lifecycleScope.launch {
            try {
                when (next) {
                    "home" -> app.localPlayer.pause()
                    "play", "lyrics" -> { app.localPlayer.seekTo(0); app.localPlayer.play(); awaitPlayer(playing = true) }
                    "playlist" -> app.container.playbackQueue.replace(app.songs.reversed(), 0)
                    "output" -> {
                        app.localPlayer.stop()
                        app.container.audioOutput.setMode(AudioOutputMode.OBOE)
                        app.container.audioOutput.state.first { it.mode == AudioOutputMode.OBOE }
                        app.prepareLocal()
                        app.localPlayer.play()
                        awaitPlayer(playing = true)
                    }
                }
                publishState()
                pendingFrame = true
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) { unsupported(error) }
        }
    }

    private suspend fun awaitPlayer(playing: Boolean = false) = withTimeout(8_000) {
        while (app.localPlayer.playbackState != Player.STATE_READY || (playing && !app.localPlayer.isPlaying)) {
            app.localPlayer.playerError?.let { throw it }
            delay(10)
        }
    }

    private fun publishState() {
        val player = app.localPlayer
        app.container.playerController.state.value = PlayerState(song = app.container.playbackQueue.state.value.current,
            playing = player.isPlaying, positionMs = player.currentPosition, durationMs = 10_000,
            anchorRealtimeMs = SystemClock.elapsedRealtime(), bufferedPositionMs = player.bufferedPosition,
            audioFullyCached = true)
    }

    private fun startTrace(name: String) {
        finishTrace()
        if (Build.VERSION.SDK_INT >= 29) {
            activeTrace = "cm_benchmark_$name" to ++serial
            activeTrace?.let { Trace.beginAsyncSection(it.first, it.second) }
        }
    }

    private fun finishTrace() {
        if (Build.VERSION.SDK_INT >= 29) activeTrace?.let { Trace.endAsyncSection(it.first, it.second) }
        activeTrace = null
    }

    private fun unsupported(error: Exception) {
        finishTrace()
        ready = false
        failure = "${error.javaClass.simpleName}: ${error.message}"
    }

    override fun onStop() {
        if (app.localPlayerCreated && app.playerOwner == System.identityHashCode(this)) app.localPlayer.stop()
        super.onStop()
    }

    override fun onDestroy() {
        finishTrace()
        if (app.playerOwner == System.identityHashCode(this)) app.playerOwner = 0
        super.onDestroy()
    }

    @Composable private fun LocalHome(sample: PlayerState) {
        SettingsUiScope {
            SettingsScrollLayout(Modifier.fillMaxSize(), topBar = { SettingsRootTopBar("Current Music") }) { top ->
                LazyColumn(contentPadding = PaddingValues(16.dp, top + 8.dp, 16.dp, 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("player", "play", "lyrics").forEach { Action(it, Modifier.weight(1f)) }
                    } }
                    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("playlist", "settings", "output").forEach { Action(it, Modifier.weight(1f)) }
                    } }
                    item { DailyRecommendationHero(app.songs.take(5), false, null, false, sample,
                        onPlay = { change("play") }, onOpen = { change("playlist") }, onRetry = {}, onConnect = {},
                        dateLabel = "Local fixed fixture") }
                    item { HomeLibraryShortcuts({ change("playlist") }, false) }
                    item { LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(app.songs.take(8), key = { it.id }) { song ->
                            MusicArtworkCard(song.cover, song.name, song.artists, { change("play") }, Modifier.width(148.dp))
                        }
                    } }
                }
            }
        }
    }

    @Composable private fun Action(name: String, modifier: Modifier) {
        GlassActionButton(name, { change(name) }, modifier.semantics { contentDescription = "benchmark_action_$name" })
    }

    @Composable private fun LocalPlaylist() {
        val list = rememberLazyListState()
        val songs = app.container.playbackQueue.state.collectAsState().value.songs
        PlaylistPageLayout(app.songs.first().cover, list, 0, "benchmark_playlist",
            toolbar = { SettingsTopBar("Local playlist", { change("home") }) }) { top ->
            LazyColumn(state = list, contentPadding = PaddingValues(16.dp, top + 12.dp, 16.dp, 24.dp)) {
                item { MusicArtworkCard(app.songs.first().cover, "Fixed local playlist", "32 tracks",
                    {}, Modifier.width(180.dp)) }
                items(songs, key = { it.id }) { song ->
                    PlaylistTrack(song, song.id == songs.firstOrNull()?.id, false,
                        play = { change("play") }, next = {}, enqueue = {}, extra = {})
                }
            }
        }
    }
}

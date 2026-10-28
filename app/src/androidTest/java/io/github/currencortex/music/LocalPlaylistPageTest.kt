package io.github.currencortex.music

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.library.*
import io.github.currencortex.music.ui.component.LocalRoomSongRequest
import io.github.currencortex.music.ui.component.RoomSongRequest
import io.github.currencortex.music.ui.theme.LeiTheme
import io.github.currencortex.music.ui.util.viewModelFactory
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import java.io.File

class LocalPlaylistPageTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var library: LibraryViewModel
    private lateinit var detail: LibraryDetailViewModel
    private val store = ViewModelStore()
    private val tracks = listOf(Song(-11, "Zulu local", "Singer Z", "Album", durationMs = 120_000,
        musicSource = MusicSource.LOCAL, localUri = "content://media/external/audio/media/11"),
        Song(-22, "Alpha local", "Singer A", "Album", durationMs = 180_000,
            musicSource = MusicSource.LOCAL, localUri = "content://media/external/audio/media/22"))
    private var played = emptyList<Song>()
    private var playedIndex = -1
    private var scans = 0
    private var grants = 0
    private var settings = 0
    private var roomRequests = 0

    @Before fun setup() = runBlocking {
        container = AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(), "local-playlist-${UUID.randomUUID()}")
        container.ready.await(); container.sessionRestored.await()
        container.updateSettings.setAutoCheck(false)
        container.settings.edit { AppearanceSettings(themeMode = ThemeMode.LIGHT, blur = false) }
        compose.runOnUiThread {
            library = ViewModelProvider(store, viewModelFactory { LibraryViewModel(container) })[LibraryViewModel::class.java]
            detail = ViewModelProvider(store, viewModelFactory { LibraryDetailViewModel(container, "lib/local") })[LibraryDetailViewModel::class.java]
        }
    }
    @After fun close() {
        compose.runOnUiThread { store.clear() }
        container.close()
    }
    private fun open(allowed: Boolean = true, inRoom: Boolean = false) {
        compose.setContent {
            LeiTheme(AppearanceSettings(themeMode = ThemeMode.LIGHT, blur = false)) {
                top.yukonga.miuix.kmp.basic.Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) {
                CompositionLocalProvider(LocalRoomSongRequest provides if (inRoom) RoomSongRequest({ roomRequests++ }, { false }) else null) {
                    PlaylistDetailBody(LibraryDetailState(loading = false, title = "本地音乐", songs = if (allowed) tracks else emptyList()),
                        detail, library, {}, {}, { songs, index -> played = songs; playedIndex = index }, {}, {},
                        LocalMusicPlaylistActions(allowed, { scans++ }, { grants++ }, { settings++ }, if (allowed) null else "未获得音频权限"))
                }
                }
            }
        }
    }
    @Test fun localUsesPlaylistHeroSearchSortAndVisibleQueue() {
        open()
        compose.onNodeWithTag("playlist_hero").assertIsDisplayed()
        compose.onNodeWithTag("playlist_playback_panel").assertIsDisplayed()
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        compose.onNodeWithTag("local_music_screen").captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.getExternalFilesDir(null), "local-playlist-shared-page.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithTag("playlist_search_toggle").performClick()
        compose.onNodeWithTag("playlist_query").performTextInput("Alpha")
        compose.onNodeWithTag("playlist_song_-11").assertDoesNotExist()
        compose.onNodeWithTag("play_library_all").performClick()
        compose.runOnIdle { assertEquals(listOf(-22L), played.map { it.id }); assertEquals(0, playedIndex) }
        compose.onNodeWithContentDescription("关闭歌单搜索").performClick()
        compose.onNodeWithTag("playlist_sort").performClick()
        compose.onNodeWithTag("playlist_order_1").performClick()
        compose.onNodeWithTag("play_library_all").performClick()
        compose.runOnIdle { assertEquals(listOf(-22L, -11L), played.map { it.id }) }
        compose.onNodeWithTag("song_menu_-22").performClick()
        compose.onNodeWithText("下一首播放").assertIsDisplayed()
        compose.onNodeWithText("加入队列").assertIsDisplayed()
        compose.onNodeWithTag("song_like_-22").assertDoesNotExist()
        compose.onNodeWithTag("song_playlist_-22").assertDoesNotExist()
        compose.onNodeWithTag("song_download").assertDoesNotExist()
    }
    @Test fun permissionAndScanUseLocalActionsInsideTheSharedPage() {
        open(allowed = false)
        compose.onNodeWithTag("playlist_hero").assertIsDisplayed()
        compose.onNodeWithTag("play_library_all").assertIsNotEnabled()
        compose.onNodeWithTag("playlist_tracks").performScrollToNode(hasTestTag("local_music_grant"))
        compose.onNodeWithTag("local_music_grant").performClick()
        compose.onNodeWithTag("local_music_open_app_settings").performClick()
        compose.runOnIdle { assertEquals(1, grants); assertEquals(1, settings); assertEquals(0, scans) }
    }
    @Test fun localTracksNeverBecomeRoomRequestsAndRefreshDoesNotUseCloudReload() {
        open(inRoom = true)
        compose.onNodeWithTag("playlist_song_-11").performClick()
        compose.runOnIdle { assertEquals(0, roomRequests); assertEquals(tracks, played); assertEquals(0, playedIndex) }
        compose.onNodeWithTag("playlist_more").performClick()
        compose.onNodeWithText("重新扫描本地音乐").performClick()
        compose.runOnIdle { assertEquals(1, scans); assertEquals(0, grants) }
        compose.onNodeWithTag("playlist_import_netease_likes").assertDoesNotExist()
        compose.onNodeWithTag("delete_playlist").assertDoesNotExist()
    }
}

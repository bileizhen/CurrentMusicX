package io.github.currencortex.music.feature.qqmusic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.core.network.appResult
import io.github.currencortex.music.data.library.Playlist
import io.github.currencortex.music.data.profile.ProfileUser
import io.github.currencortex.music.data.qqmusic.QqHomeSnapshot
import io.github.currencortex.music.data.qqmusic.QqListeningRankEntry
import io.github.currencortex.music.data.qqmusic.QqListeningRankPage
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.home.DailyRecommendationHero
import io.github.currencortex.music.feature.home.HomeLibraryShortcuts
import io.github.currencortex.music.feature.home.HomePersonalRoamingEntry
import io.github.currencortex.music.feature.home.HomeSectionHeader
import io.github.currencortex.music.feature.library.LibrarySongActions
import io.github.currencortex.music.feature.library.LibraryViewModel
import io.github.currencortex.music.feature.library.PlaylistArtworkPreview
import io.github.currencortex.music.feature.library.SongArtworkPreview
import io.github.currencortex.music.feature.library.rememberPlaylistPreviewPlayback
import io.github.currencortex.music.ui.component.HomeProfileAvatar
import io.github.currencortex.music.ui.component.HomeBrandTitle
import io.github.currencortex.music.ui.component.MusicPlatformSwitcher
import io.github.currencortex.music.ui.component.LoadingArtworkCards
import io.github.currencortex.music.ui.component.LocalMusicBottomInset
import io.github.currencortex.music.ui.component.LocalSongDownload
import io.github.currencortex.music.ui.component.LoadingSongList
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.MusicSearchBar
import io.github.currencortex.music.ui.component.MusicTextAction
import io.github.currencortex.music.ui.component.PreloadMusicCovers
import io.github.currencortex.music.ui.component.SongRow
import io.github.currencortex.music.ui.component.SongRowUi
import io.github.currencortex.music.ui.component.nativeui.ElasticLazyRow
import io.github.currencortex.music.ui.component.nativeui.MusicArtworkCard
import io.github.currencortex.music.ui.component.nativeui.SettingsListHeader
import io.github.currencortex.music.ui.component.nativeui.SettingsRootTopBar
import io.github.currencortex.music.ui.component.nativeui.SettingsScrollLayout
import io.github.currencortex.music.ui.component.nativeui.SettingsUiScope
import io.github.currencortex.music.ui.component.rootMotionItems
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map as flowMap
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate

private data class QqHomeState(
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val daily: List<Song> = emptyList(),
    val forYou: List<Song> = emptyList(),
    val recent: List<Song> = emptyList(),
    val recentCounts: Map<Long, Long> = emptyMap(),
    val recommendedPlaylists: List<Playlist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val errors: Map<String, String> = emptyMap(),
)

private fun QqHomeSnapshot.toUiState(loggedIn: Boolean) = QqHomeState(
    loading = !complete,
    loaded = complete,
    daily = daily?.songs.orEmpty(),
    forYou = if (loggedIn) forYou else daily?.forYou.orEmpty(),
    recent = recent?.entries?.map(QqListeningRankEntry::song).orEmpty(),
    recentCounts = recent?.entries?.associate { it.song.id to it.listenCount }.orEmpty(),
    recommendedPlaylists = recommendedPlaylists,
    playlists = playlists,
)

@Composable
fun QqMusicHomeScreen(
    libraryVm: LibraryViewModel,
    onSearch: () -> Unit,
    onProfile: (String?, Rect?) -> Unit,
    navigate: (String) -> Unit,
    play: (List<Song>, Int) -> Unit,
    profile: ProfileUser? = null,
    onSearchBounds: (Rect) -> Unit = {},
    hideSearch: Boolean = false,
    searchQuery: String = "",
    onSearchSubmit: () -> Unit = onSearch,
) {
    val container = libraryVm.container
    val account by container.accountRepository.state.collectAsStateWithLifecycle()
    val preferences by container.musicSettings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val qqAccount by container.qqMusic.state.collectAsStateWithLifecycle()
    val sessionRevision by container.accountRepository.sessionRevision.collectAsStateWithLifecycle()
    val avatarVersions by container.profileRepository.avatarVersions.collectAsStateWithLifecycle()
    val scales by container.profileRepository.scales.collectAsStateWithLifecycle()
    val user = profile?.takeIf { it.id == account.account?.id }
    val avatar = user?.avatar ?: account.account?.avatar.orEmpty()
    val decoration = user?.decoration.orEmpty()
    val decorationUrl = remember(preferences.server, decoration) {
        container.profileRepository.decorationUrl(preferences.server, decoration)
    }
    val avatarUrl = remember(preferences.server, avatar, avatarVersions) {
        container.profileRepository.avatarUrl(preferences.server, avatar, avatarVersions)
    }
    val bottom = LocalMusicBottomInset.current
    val playbackSummary = remember(container.playerController) {
        container.playerController.state.flowMap {
            PlayerState(
                song = it.song,
                playing = it.playing,
                loading = it.loading,
                playRequested = it.playRequested,
                mode = it.mode,
                canControlPlayback = it.canControlPlayback,
                error = it.error,
            )
        }.distinctUntilChanged()
    }
    val playback by playbackSummary.collectAsStateWithLifecycle(initialValue = PlayerState())
    var refresh by remember { mutableIntStateOf(0) }
    val initialHome = remember { container.qqMusic.cachedHome() }
    var state by remember(initialHome) { mutableStateOf(initialHome.toUiState(qqAccount.loggedIn)) }
    var artworkPreview by remember { mutableStateOf<Pair<Song, Rect>?>(null) }
    var playlistPreview by remember { mutableStateOf<Pair<Playlist, Rect>?>(null) }
    var sharingSong by remember { mutableStateOf<Song?>(null) }
    val downloadSong = LocalSongDownload.current
    val playlistPlayback = rememberPlaylistPreviewPlayback(libraryVm, play)
    val identity = "${preferences.server}|$sessionRevision|${qqAccount.revision}|${qqAccount.loggedIn}"

    LaunchedEffect(identity, refresh) {
        val cached = container.qqMusic.cachedHome()
        state = if (cached.complete) cached.toUiState(qqAccount.loggedIn) else QqHomeState()
        coroutineScope {
            val dailyTask = async { appResult { container.qqMusic.daily() } }
            val recommendedTask = async { appResult { container.qqMusic.recommendedPlaylists() } }
            val forYouTask = if (qqAccount.loggedIn) async {
                appResult { container.qqMusic.guessYouLike() }
            } else null
            val playlistsTask = if (qqAccount.loggedIn) async {
                appResult { container.qqMusic.playlists() }
            } else null
            val recentTask = if (qqAccount.loggedIn) async {
                appResult { container.qqMusic.listeningRankPage() }
            } else null

            var next = state
            when (val result = dailyTask.await()) {
                is AppResult.Success -> next = next.copy(
                    daily = result.value.songs,
                    forYou = if (qqAccount.loggedIn) next.forYou else result.value.forYou,
                )
                is AppResult.Failure -> next = next.copy(errors = next.errors + ("每日推荐" to result.message))
            }
            state = next
            when (val result = recommendedTask.await()) {
                is AppResult.Success -> next = next.copy(recommendedPlaylists = result.value)
                is AppResult.Failure -> next = next.copy(errors = next.errors + ("推荐歌单" to result.message))
            }
            state = next
            forYouTask?.let { task ->
                when (val result = task.await()) {
                    is AppResult.Success -> next = next.copy(forYou = result.value)
                    is AppResult.Failure -> next = next.copy(errors = next.errors + ("猜你喜欢" to result.message))
                }
                state = next
            }
            playlistsTask?.let { task ->
                when (val result = task.await()) {
                    is AppResult.Success -> next = next.copy(playlists = result.value)
                    is AppResult.Failure -> next = next.copy(errors = next.errors + ("我的歌单" to result.message))
                }
                state = next
            }
            recentTask?.let { task ->
                when (val result = task.await()) {
                    is AppResult.Success -> next = next.copy(
                        recent = result.value.entries.map { it.song },
                        recentCounts = result.value.entries.associate { it.song.id to it.listenCount },
                    )
                    is AppResult.Failure -> next = next.copy(errors = next.errors + ("本周常听" to result.message))
                }
            }
            state = next.copy(loading = false, loaded = true)
            container.qqMusic.cacheHome(QqHomeSnapshot(
                daily = io.github.currencortex.music.data.library.Daily(next.daily, next.forYou, emptyList()),
                recommendedPlaylists = next.recommendedPlaylists,
                forYou = next.forYou,
                playlists = next.playlists,
                recent = QqListeningRankPage(next.recent.map { QqListeningRankEntry(it, next.recentCounts[it.id] ?: 0) }),
                complete = next.errors.isEmpty(),
            ))
        }
    }

    PreloadMusicCovers(state.daily.take(8).map(Song::cover), 320)
    PreloadMusicCovers(state.forYou.take(8).map(Song::cover), 320)
    PreloadMusicCovers(state.recommendedPlaylists.take(4).map(Playlist::cover), 320)
    PreloadMusicCovers(state.playlists.take(4).map(Playlist::cover), 320)

    SettingsUiScope {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val artworkCardWidth = (maxWidth.coerceAtMost(840.dp) - 64.dp) / 2.5f
            SettingsScrollLayout(
                Modifier.widthIn(max = 840.dp).fillMaxSize().align(Alignment.TopCenter),
                glassOverContent = false,
                topBar = {
                    SettingsRootTopBar(
                        "Current Music",
                        Modifier.testTag("home_root_toolbar"),
                        titleContent = { HomeBrandTitle() },
                    ) {
                        HomeProfileAvatar(
                            avatarUrl,
                            state.loaded,
                            onProfile,
                            decorationUrl = decorationUrl,
                            decorationScale = scales[decoration] ?: 1.0,
                        )
                    }
                },
            ) { topInset ->
                LazyColumn(
                    Modifier.fillMaxSize().testTag("qq_music_home"),
                    contentPadding = PaddingValues(start = 16.dp, top = topInset + 8.dp, end = 16.dp, bottom = 24.dp + bottom),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    rootMotionItems(skipKeys = setOf("for-you", "recommended-playlists", "playlists")) {
                        item(key = "search") {
                            MusicSearchBar(
                                searchQuery,
                                onSearchSubmit,
                                Modifier.testTag("open_qq_home_search")
                                    .onGloballyPositioned { onSearchBounds(it.boundsInRoot()) }
                                    .graphicsLayer { alpha = if (hideSearch) 0f else 1f },
                                onActivate = onSearch,
                                placeholder = "搜索 QQ 音乐歌曲、歌手、专辑、歌单",
                                inputTag = "home_search_field",
                                leadingContent = {
                                    MusicPlatformSwitcher(preferences.musicPlatform, { platform ->
                                        scope.launch { container.musicSettings.setMusicPlatform(platform) }
                                    }, enabled = !hideSearch)
                                },
                            )
                        }
                        if (state.errors.isNotEmpty() && !state.loading) item(key = "retry") {
                            MusicTextAction("部分内容加载失败，重新加载", { refresh++ }, enabled = !state.loading)
                        }
                        item(key = "daily") {
                            DailyRecommendationHero(
                                songs = state.daily,
                                loading = state.loading && state.daily.isEmpty(),
                                error = state.errors["每日推荐"],
                                discovery = !qqAccount.loggedIn,
                                playback = playback,
                                onPlay = { index -> state.daily.getOrNull(index)?.let { play(state.daily, index) } },
                                onOpen = { navigate("qq/daily") },
                                onRetry = { refresh++ },
                                onConnect = { navigate("qq/account") },
                                dateLabel = "${LocalDate.now().monthValue}月${LocalDate.now().dayOfMonth}日",
                                discoveryProvider = "QQ 音乐",
                                connectTag = "home_connect_qq",
                            )
                        }
                        item(key = "personal-roaming") {
                            HomePersonalRoamingEntry({ navigate("qq/roaming") }, qqMusic = true)
                        }
                        item(key = "shortcuts") {
                            HomeLibraryShortcuts(
                                navigate = navigate,
                                inRoom = false,
                                likesRoute = "qq/likes",
                                playlistsRoute = "qq/playlists",
                                fourthTitle = "排行榜",
                                fourthIcon = MusicIcons.Play,
                                fourthTag = "open_qq_charts",
                                fourthRoute = "qq/charts",
                            )
                        }
                        if (!qqAccount.loggedIn) item(key = "qq-login") {
                            MusicTextAction("登录 QQ 音乐，获取个人推荐与完整播放权限 ›", { navigate("qq/account") })
                        }
                        item(key = "for-you-header") {
                            HomeSectionHeader("猜你喜欢", "更多") { navigate("qq/foryou") }
                        }
                        if (state.loading && state.forYou.isEmpty()) item(key = "for-you-loading") {
                            LoadingArtworkCards(artworkCardWidth)
                        }
                        if (state.forYou.isNotEmpty()) item(key = "for-you") {
                            ElasticLazyRow(edgeToEdgeInset = 16.dp) {
                                itemsIndexed(state.forYou.take(8), key = { _, song -> song.id }) { index, song ->
                                    MusicArtworkCard(
                                        song.cover,
                                        song.name,
                                        song.artists,
                                        { play(state.forYou, index) },
                                        Modifier.width(artworkCardWidth).testTag("qq_home_recommendation_${song.id}"),
                                        onLongClick = { bounds -> artworkPreview = song to bounds },
                                    )
                                }
                            }
                        }
                        if (!state.loading && state.forYou.isEmpty() && state.errors["猜你喜欢"] == null) {
                            item(key = "for-you-empty") {
                                EmptyHomeText(if (qqAccount.loggedIn) "暂时没有猜你喜欢，稍后再来看看" else "登录后获取 QQ 音乐个性化推荐")
                            }
                        }
                        item(key = "recommended-playlists-header") {
                            SettingsListHeader("每日推荐歌单")
                        }
                        if (state.loading && state.recommendedPlaylists.isEmpty()) {
                            item(key = "recommended-playlists-loading") { LoadingArtworkCards(artworkCardWidth) }
                        }
                        if (state.recommendedPlaylists.isNotEmpty()) item(key = "recommended-playlists") {
                            ElasticLazyRow(edgeToEdgeInset = 16.dp) {
                                items(state.recommendedPlaylists, key = { it.remoteId.ifBlank { it.id.toString() } }) { playlist ->
                                    MusicArtworkCard(
                                        playlist.cover,
                                        playlist.name,
                                        playlist.description.ifBlank {
                                            if (playlist.count > 0) "${playlist.count} 首" else "为你推荐"
                                        },
                                        {
                                            navigate(
                                                qqRoute(
                                                    "playlist",
                                                    playlist.remoteId.ifBlank { playlist.id.toString() },
                                                ),
                                            )
                                        },
                                        Modifier.width(artworkCardWidth)
                                            .testTag("qq_home_recommended_playlist_${playlist.remoteId}"),
                                        onLongClick = { bounds -> playlistPreview = playlist to bounds },
                                        longClickLabel = "预览歌单与操作",
                                        playlistTransition = true,
                                    )
                                }
                            }
                        }
                        if (!state.loading && state.recommendedPlaylists.isEmpty() && state.errors["推荐歌单"] == null) {
                            item(key = "recommended-playlists-empty") { EmptyHomeText("今天暂无推荐歌单，稍后再来看看") }
                        }
                        item(key = "playlists-header") {
                            HomeSectionHeader("我的歌单", "管理") { navigate("qq/playlists") }
                        }
                        if (state.playlists.isNotEmpty()) item(key = "playlists") {
                            ElasticLazyRow(edgeToEdgeInset = 16.dp) {
                                items(state.playlists.take(8), key = { it.remoteId.ifBlank { it.id.toString() } }) { playlist ->
                                    MusicArtworkCard(
                                        playlist.cover,
                                        playlist.name,
                                        "${playlist.count} 首",
                                        {
                                            navigate(
                                                qqRoute(
                                                    "playlist",
                                                    playlist.remoteId.ifBlank { playlist.id.toString() },
                                                ),
                                            )
                                        },
                                        Modifier.width(artworkCardWidth)
                                            .testTag("qq_home_playlist_${playlist.remoteId}"),
                                        onLongClick = { bounds -> playlistPreview = playlist to bounds },
                                        longClickLabel = "预览歌单与操作",
                                        playlistTransition = true,
                                    )
                                }
                            }
                        }
                        if (!state.loading && state.playlists.isEmpty() && state.errors["我的歌单"] == null) {
                            item(key = "playlists-empty") {
                                EmptyHomeText(if (qqAccount.loggedIn) "把喜欢的 QQ 音乐收进自己的歌单" else "登录后查看自己的 QQ 音乐歌单")
                            }
                        }
                        item(key = "recent-header") {
                            HomeSectionHeader("本周常听", "全部") { navigate("qq/recent") }
                        }
                        if (state.loading && state.recent.isEmpty() && qqAccount.loggedIn) {
                            item(key = "recent-loading") { LoadingSongList(2) }
                        }
                        itemsIndexed(state.recent.take(3), key = { _, song -> "qq-recent-${song.id}" }) { index, song ->
                            SongRow(
                                SongRowUi(song, subtitle = qqHomeSongSubtitle(song, state.recentCounts[song.id])),
                                { play(state.recent, index) },
                                { container.playerController.add(song, true) },
                                { container.playerController.add(song) },
                            ) {
                                LibrarySongActions(libraryVm, song, navigate)
                            }
                        }
                        if (!state.loading && state.recent.isEmpty() && state.errors["本周常听"] == null) {
                            item(key = "recent-empty") {
                                EmptyHomeText(if (qqAccount.loggedIn) "QQ 音乐本周的常听歌曲会出现在这里" else "登录后查看 QQ 音乐本周常听")
                            }
                        }
                        item(key = "refresh") {
                            MusicTextAction(
                                if (state.loading) "正在更新 QQ 音乐…" else "刷新 QQ 音乐",
                                { refresh++ },
                                Modifier.fillMaxWidth().testTag("refresh_qq_home"),
                                enabled = !state.loading,
                            )
                        }
                    }
                }
            }
        }
    }
    artworkPreview?.let { (song, bounds) ->
        SongArtworkPreview(
            libraryVm,
            song,
            bounds,
            play = {
                val index = state.forYou.indexOfFirst { it.id == song.id }
                if (index >= 0) play(state.forYou, index) else play(listOf(song), 0)
            },
            queue = { container.playerController.add(song) },
            roomRequest = false,
            playVersion = { version -> play(listOf(version), 0) },
            navigate = navigate,
            share = { sharingSong = it },
            download = downloadSong,
            onDismiss = { artworkPreview = null },
        )
    }
    sharingSong?.let { song ->
        io.github.currencortex.music.feature.share.SongShareDialog(song) { sharingSong = null }
    }
    playlistPreview?.let { (playlist, bounds) ->
        PlaylistArtworkPreview(
            libraryVm,
            playlist,
            bounds,
            canPlay = playlistPlayback.enabled,
            onPlay = { shuffle -> playlistPlayback.play(playlist, shuffle) },
            onOpen = {
                navigate(qqRoute("playlist", playlist.remoteId.ifBlank { playlist.id.toString() }))
            },
            onDismiss = { playlistPreview = null },
        )
    }
}

@Composable
private fun EmptyHomeText(message: String) {
    Text(message, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
}

private fun qqHomeSongSubtitle(song: Song, listenCount: Long? = null): String = listOfNotNull(
    song.artists.takeIf(String::isNotBlank),
    song.album.takeIf(String::isNotBlank),
    listenCount?.takeIf { it > 0 }?.let { "$it 次" },
).joinToString(" · ")

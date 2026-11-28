package io.github.currencortex.music.feature.kugou

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import io.github.currencortex.music.data.kugou.KugouHomeSnapshot
import io.github.currencortex.music.data.kugou.KugouRank
import io.github.currencortex.music.data.library.Playlist
import io.github.currencortex.music.data.profile.ProfileUser
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.home.DailyRecommendationHero
import io.github.currencortex.music.feature.home.HomeLibraryShortcuts
import io.github.currencortex.music.feature.home.HomePersonalRoamingEntry
import io.github.currencortex.music.feature.home.HomeSectionHeader
import io.github.currencortex.music.feature.library.LibrarySongActions
import io.github.currencortex.music.feature.library.LibraryViewModel
import io.github.currencortex.music.ui.component.HomeBrandTitle
import io.github.currencortex.music.ui.component.HomeProfileAvatar
import io.github.currencortex.music.ui.component.LoadingArtworkCards
import io.github.currencortex.music.ui.component.LoadingSongList
import io.github.currencortex.music.ui.component.LocalMusicBottomInset
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.MusicPlatformSwitcher
import io.github.currencortex.music.ui.component.MusicSearchBar
import io.github.currencortex.music.ui.component.MusicTextAction
import io.github.currencortex.music.ui.component.PreloadMusicCovers
import io.github.currencortex.music.ui.component.SongRow
import io.github.currencortex.music.ui.component.SongRowUi
import io.github.currencortex.music.ui.component.SpringLazyColumn
import io.github.currencortex.music.ui.component.nativeui.ElasticLazyRow
import io.github.currencortex.music.ui.component.nativeui.MusicArtworkCard
import io.github.currencortex.music.ui.component.nativeui.SettingsListHeader
import io.github.currencortex.music.ui.component.nativeui.SettingsRootTopBar
import io.github.currencortex.music.ui.component.nativeui.SettingsScrollLayout
import io.github.currencortex.music.ui.component.nativeui.SettingsUiScope
import io.github.currencortex.music.ui.component.rootMotionItems
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged as distinctFlowUntilChanged
import kotlinx.coroutines.flow.map as flowMap
import kotlinx.coroutines.launch
import java.time.LocalDate

private data class KugouHomeState(
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val daily: List<Song> = emptyList(),
    val forYou: List<Song> = emptyList(),
    val recommendedPlaylists: List<Playlist> = emptyList(),
    val userPlaylists: List<Playlist> = emptyList(),
    val ranks: List<KugouRank> = emptyList(),
    val newSongs: List<Song> = emptyList(),
    val errors: Map<String, String> = emptyMap(),
)

private fun KugouHomeSnapshot.toState() = KugouHomeState(
    loading = !complete, loaded = complete, daily = daily.take(10), forYou = daily.drop(10),
    recommendedPlaylists = playlists, ranks = ranks, newSongs = newSongs,
)

/** KuGou's home: everyday recommendation, roaming, shortcuts, recommended playlists, charts and new releases. */
@Composable
fun KugouHomeScreen(
    container: AppContainer,
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
    val account by container.accountRepository.state.collectAsStateWithLifecycle()
    val preferences by container.musicSettings.state.collectAsStateWithLifecycle()
    val kugouAccount by container.kugou.state.collectAsStateWithLifecycle()
    val avatarVersions by container.profileRepository.avatarVersions.collectAsStateWithLifecycle()
    val scales by container.profileRepository.scales.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val user = profile?.takeIf { it.id == account.account?.id }
    val avatar = user?.avatar ?: account.account?.avatar.orEmpty()
    val decoration = user?.decoration.orEmpty()
    val decorationUrl = remember(preferences.server, decoration) { container.profileRepository.decorationUrl(preferences.server, decoration) }
    val avatarUrl = remember(preferences.server, avatar, avatarVersions) {
        container.profileRepository.avatarUrl(preferences.server, avatar, avatarVersions)
    }
    val bottom = LocalMusicBottomInset.current
    val playbackSummary = remember(container.playerController) {
        container.playerController.state.flowMap {
            PlayerState(song = it.song, playing = it.playing, loading = it.loading, playRequested = it.playRequested,
                mode = it.mode, canControlPlayback = it.canControlPlayback, error = it.error)
        }.distinctFlowUntilChanged()
    }
    val playback by playbackSummary.collectAsStateWithLifecycle(initialValue = PlayerState())
    var refresh by remember { mutableIntStateOf(0) }
    val initial = remember { container.kugou.cachedHome() }
    var state by remember(initial) { mutableStateOf(initial.toState()) }

    LaunchedEffect(kugouAccount.revision, refresh) {
        val cached = container.kugou.cachedHome()
        state = if (cached.complete) cached.toState() else KugouHomeState()
        coroutineScope {
            val daily = async { appResult { container.kugou.daily() } }
            val recommended = async { appResult { container.kugou.recommendedPlaylists() } }
            val ranks = async { appResult { container.kugou.ranks() } }
            val fresh = async { appResult { container.kugou.newSongs() } }
            val userLists = if (kugouAccount.loggedIn) async { appResult { container.kugou.playlists() } } else null
            var next = state
            when (val result = daily.await()) {
                is AppResult.Success -> next = next.copy(daily = result.value.songs.take(10), forYou = result.value.songs.drop(10))
                is AppResult.Failure -> next = next.copy(errors = next.errors + ("每日推荐" to result.message))
            }
            state = next
            when (val result = recommended.await()) {
                is AppResult.Success -> next = next.copy(recommendedPlaylists = result.value)
                is AppResult.Failure -> next = next.copy(errors = next.errors + ("推荐歌单" to result.message))
            }
            state = next
            when (val result = ranks.await()) {
                is AppResult.Success -> next = next.copy(ranks = result.value)
                is AppResult.Failure -> next = next.copy(errors = next.errors + ("排行榜" to result.message))
            }
            state = next
            when (val result = fresh.await()) {
                is AppResult.Success -> next = next.copy(newSongs = result.value)
                is AppResult.Failure -> next = next.copy(errors = next.errors + ("新歌速递" to result.message))
            }
            state = next
            userLists?.let { task ->
                when (val result = task.await()) {
                    is AppResult.Success -> next = next.copy(userPlaylists = result.value)
                    is AppResult.Failure -> Unit
                }
            }
            state = next.copy(loading = false, loaded = true)
            container.kugou.cacheHome(KugouHomeSnapshot(daily = next.daily + next.forYou, ranks = next.ranks,
                newSongs = next.newSongs, playlists = next.recommendedPlaylists, complete = next.errors.isEmpty()))
        }
    }

    PreloadMusicCovers(state.daily.take(8).map(Song::cover), 320)
    PreloadMusicCovers(state.recommendedPlaylists.take(6).map(Playlist::cover), 320)
    PreloadMusicCovers(state.ranks.take(6).map(KugouRank::cover), 320)

    SettingsUiScope {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cardWidth = (maxWidth.coerceAtMost(840.dp) - 64.dp) / 2.5f
            SettingsScrollLayout(
                Modifier.widthIn(max = 840.dp).fillMaxSize().align(Alignment.TopCenter),
                glassOverContent = false,
                topBar = {
                    SettingsRootTopBar("Current Music", Modifier.testTag("home_root_toolbar"), titleContent = { HomeBrandTitle() }) {
                        HomeProfileAvatar(avatarUrl, state.loaded, onProfile, decorationUrl = decorationUrl,
                            decorationScale = scales[decoration] ?: 1.0)
                    }
                },
            ) { topInset ->
                SpringLazyColumn(
                    Modifier.fillMaxSize().testTag("kugou_music_home"),
                    contentPadding = PaddingValues(start = 16.dp, top = topInset + 8.dp, end = 16.dp, bottom = 24.dp + bottom),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    rootMotionItems(skipKeys = setOf("for-you", "recommended-playlists", "ranks", "user-playlists")) {
                        item(key = "search") {
                            MusicSearchBar(
                                searchQuery, onSearchSubmit,
                                Modifier.testTag("open_kugou_home_search").onGloballyPositioned { onSearchBounds(it.boundsInRoot()) }
                                    .graphicsLayer { alpha = if (hideSearch) 0f else 1f },
                                onActivate = onSearch,
                                placeholder = "搜索酷狗音乐歌曲、歌手、专辑、歌单",
                                inputTag = "home_search_field",
                                leadingContent = {
                                    MusicPlatformSwitcher(preferences.musicPlatform, { platform ->
                                        scope.launch { container.musicSettings.setMusicPlatform(platform) }
                                    }, enabled = !hideSearch)
                                },
                            )
                        }
                        if (state.errors.isNotEmpty() && !state.loading && state.daily.isEmpty() && state.ranks.isEmpty() && state.recommendedPlaylists.isEmpty()) {
                            item(key = "retry") {
                                MusicTextAction("部分内容加载失败，重新加载", { refresh++ }, enabled = !state.loading)
                            }
                        }
                        item(key = "daily") {
                            DailyRecommendationHero(
                                songs = state.daily,
                                loading = state.loading && state.daily.isEmpty(),
                                error = state.errors["每日推荐"],
                                discovery = !kugouAccount.loggedIn,
                                playback = playback,
                                onPlay = { index -> state.daily.getOrNull(index)?.let { play(state.daily, index) } },
                                onOpen = { navigate("kugou/daily") },
                                onRetry = { refresh++ },
                                onConnect = { navigate("kugou/account") },
                                dateLabel = "${LocalDate.now().monthValue}月${LocalDate.now().dayOfMonth}日",
                                discoveryProvider = "酷狗音乐",
                                connectTag = "home_connect_kugou",
                            )
                        }
                        item(key = "personal-roaming") {
                            HomePersonalRoamingEntry({ navigate("kugou/roaming") }, guessYouLike = true)
                        }
                        item(key = "shortcuts") {
                            HomeLibraryShortcuts(
                                navigate = navigate,
                                inRoom = false,
                                likesRoute = "kugou/likes",
                                playlistsRoute = "kugou/playlists",
                                fourthTitle = "排行榜",
                                fourthIcon = MusicIcons.Play,
                                fourthTag = "open_kugou_ranks",
                                fourthRoute = "kugou/ranks",
                            )
                        }
                        if (!kugouAccount.loggedIn) item(key = "kugou-login") {
                            MusicTextAction("登录酷狗音乐，同步云歌单、我喜欢与 VIP 权益 ›", { navigate("kugou/account") })
                        }
                        if (state.recommendedPlaylists.isNotEmpty() || state.loading) {
                            item(key = "recommended-playlists-header") {
                                SettingsListHeader("推荐歌单")
                            }
                            if (state.loading && state.recommendedPlaylists.isEmpty()) {
                                item(key = "recommended-playlists-loading") { LoadingArtworkCards(cardWidth) }
                            }
                            if (state.recommendedPlaylists.isNotEmpty()) item(key = "recommended-playlists") {
                                ElasticLazyRow(edgeToEdgeInset = 16.dp) {
                                    items(state.recommendedPlaylists, key = { it.remoteId.ifBlank { it.id.toString() } }) { playlist ->
                                        MusicArtworkCard(
                                            playlist.cover,
                                            playlist.name,
                                            playlist.description.ifBlank { if (playlist.count > 0) "${playlist.count} 首" else "酷狗推荐" },
                                            { navigate(kugouRoute("playlist", playlist.remoteId.ifBlank { playlist.id.toString() })) },
                                            Modifier.width(cardWidth).testTag("kugou_home_playlist_${playlist.id}"),
                                        )
                                    }
                                }
                            }
                        }
                        if (state.userPlaylists.isNotEmpty()) {
                            item(key = "user-playlists-header") { HomeSectionHeader("我的酷狗歌单", "全部") { navigate("kugou/playlists") } }
                            item(key = "user-playlists") {
                                ElasticLazyRow(edgeToEdgeInset = 16.dp) {
                                    items(state.userPlaylists.take(10), key = { "user-${it.id}" }) { playlist ->
                                        MusicArtworkCard(
                                            playlist.cover,
                                            playlist.name,
                                            if (playlist.count > 0) "${playlist.count} 首" else "酷狗歌单",
                                            {
                                                if (playlist.nativeLiked) navigate("kugou/likes")
                                                else navigate("kugou/user-playlist/${playlist.id}")
                                            },
                                            Modifier.width(cardWidth).testTag("kugou_home_user_playlist_${playlist.id}"),
                                        )
                                    }
                                }
                            }
                        }
                        item(key = "ranks-header") { HomeSectionHeader("热门榜单", "全部") { navigate("kugou/ranks") } }
                        if (state.loading && state.ranks.isEmpty()) item(key = "ranks-loading") { LoadingArtworkCards(cardWidth) }
                        if (state.ranks.isNotEmpty()) item(key = "ranks") {
                            ElasticLazyRow(edgeToEdgeInset = 16.dp) {
                                items(state.ranks.take(12), key = { it.id }) { rank ->
                                    MusicArtworkCard(
                                        rank.cover, rank.name, rank.updateText.ifBlank { "酷狗榜单" },
                                        { navigate(kugouRoute("rank", rank.id)) },
                                        Modifier.width(cardWidth).testTag("kugou_home_rank_${rank.id}"),
                                    )
                                }
                            }
                        }
                        item(key = "for-you-header") { HomeSectionHeader("猜你喜欢", "更多") { navigate("kugou/daily") } }
                        if (state.loading && state.forYou.isEmpty()) item(key = "for-you-loading") { LoadingArtworkCards(cardWidth) }
                        if (state.forYou.isNotEmpty()) item(key = "for-you") {
                            ElasticLazyRow(edgeToEdgeInset = 16.dp) {
                                itemsIndexed(state.forYou.take(10), key = { _, song -> song.id }) { index, song ->
                                    MusicArtworkCard(song.cover, song.name, song.artists, { play(state.forYou, index) },
                                        Modifier.width(cardWidth).testTag("kugou_home_recommendation_${song.id}"))
                                }
                            }
                        }
                        item(key = "new-header") { HomeSectionHeader("新歌速递", "全部") { navigate("kugou/new") } }
                        if (state.loading && state.newSongs.isEmpty()) item(key = "new-loading") { LoadingSongList(3) }
                        itemsIndexed(state.newSongs.take(5), key = { _, song -> "kugou-new-${song.id}" }) { index, song ->
                            SongRow(SongRowUi(song), { play(state.newSongs, index) },
                                { container.playerController.add(song, true) }, { container.playerController.add(song) },
                                extraActions = { LibrarySongActions(libraryVm, song, navigate) })
                        }
                        if (!state.loading && state.newSongs.isEmpty() && state.errors["新歌速递"] == null) item(key = "new-empty") {
                            Text("暂时没有新歌，稍后再来看看", fontSize = 13.sp, color = COUITheme.colorScheme.labelPrimary.copy(alpha = .5f))
                        }
                        item(key = "refresh") {
                            MusicTextAction(if (state.loading) "正在更新酷狗音乐…" else "刷新酷狗音乐", { refresh++ },
                                Modifier.fillMaxWidth().testTag("refresh_kugou_home"), enabled = !state.loading)
                        }
                    }
                }
            }
        }
    }
}

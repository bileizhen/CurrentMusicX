package io.github.currencortex.music.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.feature.library.*
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.data.profile.ProfileUser
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import io.github.currencortex.music.core.media.PlayerState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map as mapPlayback
import java.time.LocalDate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@Composable fun MusicHomeScreen(vm: LibraryViewModel, onSearch: () -> Unit, onProfile: (String?, Rect?) -> Unit,
    navigate: (String) -> Unit, play: (List<Song>, Int) -> Unit, profile: ProfileUser? = null, onSearchBounds: (Rect) -> Unit = {}, hideSearch: Boolean = false, searchQuery: String = "", onSearchSubmit: () -> Unit = onSearch) {
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()
    val preferences by vm.container.musicSettings.state.collectAsStateWithLifecycle()
    val avatarVersions by vm.container.profileRepository.avatarVersions.collectAsStateWithLifecycle()
    val scales by vm.container.profileRepository.scales.collectAsStateWithLifecycle()
    val nativeAccount by vm.container.neteaseSessions.state.collectAsStateWithLifecycle()
    val direct = vm.container.nativeNetease != null
    val user = profile?.takeIf { it.id == account.account?.id }
    val avatar = user?.avatar ?: account.account?.avatar ?: nativeAccount.profile?.avatar.orEmpty()
    val decoration = user?.decoration.orEmpty()
    val decorationUrl = remember(preferences.server, decoration) { vm.container.profileRepository.decorationUrl(preferences.server, decoration) }
    val version = account.account?.id?.let(avatarVersions::get)
    val avatarUrl = remember(preferences.server, avatar, version) {
        vm.container.profileRepository.avatarUrl(preferences.server, avatar)?.let { url ->
            if (!avatar.startsWith("http") && version != null) url.toHttpUrlOrNull()?.newBuilder()
                ?.setQueryParameter("v", version.toString())?.build()?.toString() else url
        }
    }
    val request = LocalRoomSongRequest.current
    val bottomInset = LocalMusicBottomInset.current
    val playbackSummary = remember(vm) { vm.container.playerController.state.mapPlayback {
        PlayerState(song = it.song, playing = it.playing, loading = it.loading, playRequested = it.playRequested,
            mode = it.mode, canControlPlayback = it.canControlPlayback, error = it.error)
    }.distinctUntilChanged() }
    val playback by playbackSummary.collectAsStateWithLifecycle(initialValue = PlayerState())
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(vm) { vm.refreshForNewDay() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        today = LocalDate.now()
        vm.refreshForNewDay()
    }
    val dailyLoading = (!direct && account.loading) || (home.loading && "每日推荐" in home.pendingSections)
    val listState = rememberLazyListState()
    val avatarFlight = LocalAvatarFlight.current
    LaunchedEffect(listState, avatarFlight) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            // A lazy header is disposed after scrolling away; invalidate its cached hero slot.
            if (index > 0) avatarFlight?.homeLanding = null
        }
    }
    PreloadMusicCovers(home.forYou.take(8).map { it.cover }, 320)
    PreloadMusicCovers(home.playlists.take(4).map { it.cover }, 320)
    MusicPullToRefresh(home.loading, vm::refresh, Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().testTag("music_home"), state = listState, contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 24.dp + bottomInset), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item(key = "identity") {
                Row(Modifier.fillMaxWidth().then(LocalRootTabSwipe.current), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        HomeBrandTitle()
                        SplitText(account.account?.let { "欢迎，${it.nickname}" } ?: nativeAccount.profile?.let { "欢迎，${it.nickname}" } ?: "音乐，从这里开始",
                            ready = (!account.loading || direct) && home.loaded && LocalLaunchBrand.current?.active != true,
                            style = TextStyle(fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f)),
                            modifier = Modifier.testTag("home_welcome"))
                    }
                    HomeProfileAvatar(avatarUrl, (!account.loading || direct) && home.loaded, onProfile,
                        decorationUrl = decorationUrl, decorationScale = scales[decoration] ?: 1.0)
                }
            }
            item(key = "search") { MusicSearchBar(searchQuery, onSearchSubmit,
                Modifier.testTag("open_home_search").onGloballyPositioned { onSearchBounds(it.boundsInRoot()) }
                    .graphicsLayer { alpha = if (hideSearch) 0f else 1f }, onActivate = onSearch) }
            if (!home.loading && home.errors.isNotEmpty()) item(key = "retry") {
                MusicTextAction("部分内容加载失败，重新加载", vm::refresh, Modifier.testTag("home_retry"))
            }
            item(key = "daily") {
                DailyRecommendationHero(home.daily, dailyLoading, home.errors["每日推荐"],
                    discovery = direct && (!nativeAccount.loggedIn || nativeAccount.stale), playback = playback,
                    onPlay = { index ->
                        val song = home.daily.getOrNull(index)
                        if (song != null) {
                            if (request != null) request.request(song)
                            else if (vm.container.playerController.state.value.song?.id == song.id) vm.container.playerController.toggle()
                            else play(home.daily, index)
                        }
                    }, onOpen = { navigate("lib/daily") }, onRetry = vm::refresh,
                    onConnect = { navigate("user/binding") }, dateLabel = "${today.monthValue}月${today.dayOfMonth}日", room = request)
            }
            item(key = "shortcuts") { HomeLibraryShortcuts(navigate, request != null) }
            if (!direct && !account.loading && account.account == null) item(key = "login") {
                MusicTextAction("登录后查看推荐与音乐库 ›", { navigate("user/login") })
            }
            item(key = "for-you-header") { MusicSectionHeader("猜你喜欢", "更多 ›") { navigate("lib/foryou") } }
            if (dailyLoading && home.forYou.isEmpty()) item(key = "for-you-loading") { HomeCoverPlaceholders() }
            if (home.forYou.isNotEmpty()) item(key = "for-you") { LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                itemsIndexed(home.forYou.take(8), key = { _, song -> song.id }) { index, song ->
                        Column(Modifier.width(136.dp).clickable(enabled = request?.pending?.invoke(song.id) != true, role = Role.Button) {
                            if (request != null) request.request(song) else play(home.forYou, index)
                        }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            MusicCover(song.cover, Modifier.fillMaxWidth().aspectRatio(1f), pixels = 320, cornerRadius = 18.dp)
                            Text(song.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artists, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
                        }
                }
            } }
            if (!dailyLoading && home.forYou.isEmpty()) item(key = "for-you-empty") {
                Text(if (home.errors["每日推荐"] != null) "推荐暂时不可用，稍后重试" else "暂无推荐，先从每日精选开始听",
                    fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
            }
            item(key = "recent-header") { MusicSectionHeader("最近播放", "全部 ›") { navigate("lib/recent") } }
            home.errors["最近播放"]?.let { item { Text(it, fontSize = 13.sp) } }
            itemsIndexed(home.recent.take(3), key = { _, song -> "recent-${song.id}" }) { index, song ->
                SongRow(SongRowUi(song), { play(home.recent, index) }, { vm.container.playerController.add(song, true) }, { vm.container.playerController.add(song) }) {
                    LibrarySongActions(vm, song, navigate)
                }
            }
            if ("最近播放" in home.pendingSections && home.recent.isEmpty()) item { LoadingSongList(count = 2) }
            if ((!account.loading || direct) && "最近播放" !in home.pendingSections && home.recent.isEmpty() && home.errors["最近播放"] == null)
                item { Text("听过的音乐，会留在这里", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f)) }
            item(key = "playlists-header") { MusicSectionHeader("我的歌单", "管理 ›") { navigate("lib/playlists") } }
            home.errors["我的歌单"]?.let { item { Text(it, fontSize = 13.sp) } }
            if ("我的歌单" in home.pendingSections && home.playlists.isEmpty()) item { HomeCoverPlaceholders() }
            if (home.playlists.isNotEmpty()) item(key = "playlists") { LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(home.playlists.take(8), key = { "${it.source}-${it.id}" }) { playlist ->
                        Column(Modifier.width(136.dp).clickable(role = Role.Button) {
                            navigate("lib/${if (playlist.source == "netease") "ncmplaylist" else "playlist"}/${playlist.id}")
                        }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            MusicCover(playlist.cover, Modifier.fillMaxWidth().aspectRatio(1f), pixels = 320, cornerRadius = 18.dp)
                            Text(playlist.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${playlist.count} 首", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
                        }
                }
            } }
            if ((!account.loading || direct) && "我的歌单" !in home.pendingSections && home.playlists.isEmpty() && home.errors["我的歌单"] == null)
                item { Text("把喜欢的音乐，收进自己的歌单", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f)) }
            item(key = "refresh") { MusicTextAction(if (home.loading) "正在更新音乐库…" else "刷新音乐库", vm::refresh,
                Modifier.fillMaxWidth().testTag("refresh_home"), !home.loading) }
            account.error?.let { item { Text(it, fontSize = 13.sp) } }
        }
    }
}

@Composable private fun HomeCoverPlaceholders() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(2) { Column(Modifier.width(136.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MusicPlaceholder(Modifier.fillMaxWidth().aspectRatio(1f))
            MusicPlaceholder(Modifier.fillMaxWidth(.75f).height(15.dp))
        } }
    }
}

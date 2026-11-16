package io.github.currencortex.music.feature.qqmusic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.core.network.appResult
import io.github.currencortex.music.data.library.Playlist
import io.github.currencortex.music.data.qqmusic.*
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.data.song.formatNeteaseCount
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.util.viewModelFactory
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.net.URLDecoder
import java.net.URLEncoder

internal fun qqRoute(kind: String, id: String) = "qq/$kind/${URLEncoder.encode(id, "UTF-8")}"
private fun routeId(route: String) = URLDecoder.decode(route.substringAfterLast('/'), "UTF-8")
private val HTML_BLOCK_BOUNDARY = Regex("(?i)<\\s*/?\\s*(?:br|p|div|li|h[1-6]|blockquote|tr)\\b[^>]*>")
private val HTML_TAG = Regex("(?s)<[^>]+>")
private val HORIZONTAL_SPACE = Regex("[\\t\\r ]+")
private val SPACES_AROUND_NEWLINE = Regex(" *\\n *")
private val REPEATED_NEWLINES = Regex("\\n{2,}")

internal fun readableText(value: String): String = value
    .replace(HTML_BLOCK_BOUNDARY, "\n")
    .replace(HTML_TAG, "")
    .replace("&nbsp;", " ")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&amp;", "&")
    .replace(HORIZONTAL_SPACE, " ")
    .replace(SPACES_AROUND_NEWLINE, "\n")
    .replace(REPEATED_NEWLINES, "\n")
    .trim()
private data class QqPage(
    val title: String = "QQ 音乐", val description: String = "", val cover: String = "",
    val songs: List<Song> = emptyList(), val playlists: List<Playlist> = emptyList(),
    val entries: List<QqCatalogEntry> = emptyList(),
    val more: Boolean = false, val playlist: Playlist? = null, val nextRadioPage: Int? = null,
    val rankCounts: Map<Long, Long> = emptyMap(),
)
private fun qqSongSubtitle(song: Song, listenCount: Long?): String =
    listOfNotNull(
        song.artists.takeIf(String::isNotBlank),
        song.album.takeIf(String::isNotBlank),
        listenCount?.takeIf { it > 0 }?.let { "$it 次" },
    ).joinToString(" · ")

private fun qqPlaylistSubtitle(playlist: Playlist): String = when {
    playlist.count > 0 -> "${playlist.count} 首歌曲"
    playlist.playCount > 0 -> "${formatNeteaseCount(playlist.playCount)} 次播放"
    else -> "QQ 音乐歌单"
}

/** QQ pages keep string MIDs in their routes; player queues retain the full source-tagged song. */
@Composable fun QqMusicScreen(container: AppContainer, route: String = "home", onBack: (() -> Unit)? = null,
    navigate: (String) -> Unit, play: (List<Song>, Int) -> Unit,
    libraryVm: io.github.currencortex.music.feature.library.LibraryViewModel) {
    if (route == "qq/account") {
        QqAccountScreen(container, onBack ?: {}, navigate)
        return
    }
    if (route.startsWith("qq/comments/")) {
        QqCommentsScreen(container, routeId(route), onBack ?: {})
        return
    }
    if (route.startsWith("qq/mv/")) {
        QqMvScreen(container, routeId(route), onBack ?: {})
        return
    }
    if (route.startsWith("qq/artist/") || route.startsWith("qq/artist-albums/")) {
        val mid = routeId(route)
        val vm: QqArtistViewModel = viewModel(key = "qq_artist_$mid",
            factory = viewModelFactory { QqArtistViewModel(container, mid) })
        QqArtistScreen(vm, libraryVm, onBack ?: {}, navigate, play,
            initialTab = if (route.startsWith("qq/artist-albums/")) "albums" else "home")
        return
    }
    if (route.startsWith("qq/album/")) {
        val mid = routeId(route)
        val vm: QqAlbumViewModel = viewModel(key = "qq_album_$mid",
            factory = viewModelFactory { QqAlbumViewModel(container, mid) })
        QqAlbumScreen(vm, libraryVm, onBack ?: {}, navigate, play)
        return
    }
    val repository = container.qqMusic
    val account by repository.state.collectAsStateWithLifecycle()
    val sessionRevision by container.accountRepository.sessionRevision.collectAsStateWithLifecycle()
    val settings by container.musicSettings.state.collectAsStateWithLifecycle()
    val liked by repository.likedState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember(route) { mutableStateOf(true) }
    var busy by remember(route) { mutableStateOf(false) }
    var error by remember(route) { mutableStateOf<String?>(null) }
    var notice by remember(route, sessionRevision, account.revision) { mutableStateOf<String?>(null) }
    var page by remember(route) { mutableStateOf(QqPage()) }
    val initialQuery = if (route.startsWith("qq/search/")) routeId(route) else ""
    val initialType = if (route.startsWith("qq/search/")) route.removePrefix("qq/search/").substringBefore('/')
        .takeIf { it in setOf("song", "playlist", "album", "artist") } ?: "song" else "song"
    var query by rememberSaveable(route) { mutableStateOf(initialQuery) }
    var submitted by rememberSaveable(route) { mutableStateOf(initialQuery) }
    var searchType by rememberSaveable(route) { mutableStateOf(initialType) }
    var offset by rememberSaveable(route) { mutableIntStateOf(0) }
    var radioPage by rememberSaveable(route) { mutableIntStateOf(1) }
    var loadedIdentity by remember(route) { mutableStateOf<String?>(null) }
    var destinations by remember(route, sessionRevision, account.revision) { mutableStateOf<Pair<Song, List<Playlist>>?>(null) }
    var editName by remember(route, sessionRevision, account.revision) { mutableStateOf<String?>(null) }
    var confirmDelete by remember(route, sessionRevision, account.revision) { mutableStateOf(false) }
    var managePlaylistSubscription by remember(route, sessionRevision, account.revision) { mutableStateOf(false) }
    val isSearch = route == "qq/search" || route.startsWith("qq/search/")
    val root = route in setOf("home", "discover", "me")
    val bottom = LocalMusicBottomInset.current
    fun action(message: String? = null, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                when (val result = appResult(block)) {
                    is AppResult.Success -> { if (message != null) notice = message; refresh++ }
                    is AppResult.Failure -> error = result.message
                }
            } finally { busy = false }
        }
    }
    LaunchedEffect(route, account.revision, sessionRevision, settings.server, refresh, submitted, searchType, offset, radioPage) {
        loading = true
        error = null
        val identity = "$sessionRevision|${account.revision}|${settings.server}"
        if (loadedIdentity != identity) {
            loadedIdentity = identity
            page = QqPage()
            if (offset != 0 || radioPage != 1) {
                offset = 0
                radioPage = 1
                return@LaunchedEffect
            }
        }
        // Never retain personalized cards or another search page across an owner change.
        if (offset == 0 || !(isSearch || route.startsWith("qq/genre/") ||
                route in setOf("qq/albums", "qq/favorite-albums"))) page = QqPage()
        when (val result = appResult {
            when {
                route == "home" -> {
                    val dailySongs = when (val daily = appResult { repository.daily() }) {
                        is AppResult.Success -> daily.value.songs.ifEmpty { daily.value.forYou }
                        is AppResult.Failure -> { error = daily.message; emptyList() }
                    }
                    val lists = when (val lists = appResult { repository.recommendedPlaylists() }) {
                        is AppResult.Success -> lists.value
                        is AppResult.Failure -> { error = lists.message; emptyList() }
                    }
                    QqPage(if (account.loggedIn) "每日推荐" else "新歌推荐", songs = dailySongs, playlists = lists)
                }
                route == "qq/foryou" -> QqPage("猜你喜欢", "沿着喜欢的旋律继续探索",
                    songs = if (account.loggedIn) repository.guessYouLike() else emptyList())
                route == "qq/recent" -> if (account.loggedIn) repository.listeningRankPage().let {
                    QqPage("本周常听", "本周播放次数最多的歌曲",
                        songs = it.entries.map(QqListeningRankEntry::song),
                        rankCounts = it.entries.associate { entry -> entry.song.id to entry.listenCount })
                } else QqPage("本周常听", "登录后查看本周常听")
                route in setOf("discover", "qq/charts") -> QqPage("QQ 音乐排行榜", entries = repository.charts())
                route == "me" || route == "qq/playlists" -> QqPage("我的 QQ 音乐歌单",
                    playlists = if (account.loggedIn) repository.playlists() else emptyList())
                route == "qq/likes" -> QqPage("我喜欢",
                    songs = if (account.loggedIn) repository.likedSongs() else emptyList())
                route in setOf("qq/albums", "qq/favorite-albums") -> if (account.loggedIn)
                    repository.favoriteAlbums(offset).let { QqPage("收藏的专辑", entries = it.entries, more = it.hasMore) }
                else QqPage("收藏的专辑", "登录后查看收藏的专辑")
                route == "qq/radio" -> if (account.loggedIn) repository.radioPage(radioPage).let {
                    QqPage("私人电台", songs = it.songs, more = it.hasMore && it.nextPage != null, nextRadioPage = it.nextPage)
                } else QqPage("私人电台", "登录后播放个性化电台")
                isSearch -> {
                    if (submitted.isBlank()) QqPage("搜索 QQ 音乐")
                    else repository.search(submitted, offset, searchType).let {
                        QqPage("搜索结果", songs = it.songs, entries = it.entries, more = it.hasMore)
                    }
                }
                route.startsWith("qq/playlist/") -> repository.playlist(routeId(route)).let {
                    QqPage(it.name, it.description, it.cover, it.songs, playlist = it)
                }
                route.startsWith("qq/chart/") -> repository.chart(routeId(route)).let {
                    QqPage(it.name, it.description, it.cover, it.songs)
                }
                route.startsWith("qq/genre/") -> {
                    val categoryId = routeId(route)
                    val category = repository.categories().firstOrNull { it.id == categoryId }
                    repository.categoryPlaylists(categoryId, offset, 30, 3).let {
                        QqPage(category?.name?.ifBlank { "曲风歌单" } ?: "曲风歌单",
                            category?.group.orEmpty(), playlists = it.playlists, more = it.hasMore)
                    }
                }
                else -> QqPage()
            }
        }) {
            is AppResult.Success -> page = if (offset > 0 && (isSearch || route.startsWith("qq/genre/") ||
                    route in setOf("qq/albums", "qq/favorite-albums")))
                result.value.copy(songs = (page.songs + result.value.songs).distinctBy { it.id },
                    entries = (page.entries + result.value.entries).distinctBy { it.type to it.id },
                    playlists = (page.playlists + result.value.playlists).distinctBy {
                        it.remoteId.ifBlank { it.id.toString() }
                    }) else result.value
            is AppResult.Failure -> error = result.message
        }
        loading = false
    }
    val pageTitle = readableText(page.title)
    if (route == "qq/playlists") {
        io.github.currencortex.music.feature.library.PlaylistIndexBody(
            libraryVm, navigate, play, onBack,
            onCreate = { editName = "" },
            qqPlaylists = page.playlists,
            qqLoading = loading && page.playlists.isEmpty(), qqError = error,
            onQqRefresh = { refresh++ },
        )
        editName?.let { name -> MusicDialog("新建歌单", { editName = null }) {
            TextField(name, { editName = it.take(20) }, label = "歌单名称（最多 20 字）", singleLine = true)
            MusicTextAction("保存", {
                val title = editName.orEmpty().trim()
                editName = null
                action("歌单已保存") { repository.createPlaylist(title) }
            }, enabled = name.isNotBlank() && !busy)
        } }
        return
    }
    val qqSongActions: @Composable (Song) -> Unit = { song ->
        val dismiss = LocalDismissSongMenu.current
        MusicDestinationRow(if (song.id in liked) "取消喜欢" else "喜欢", {
            dismiss()
            action("收藏已更新") { repository.toggleLiked(song) }
        }, enabled = !busy, chevron = false)
        MusicDestinationRow("加入歌单", {
            dismiss()
            action { destinations = song to repository.playlists().filter { it.nativeOwned } }
        }, enabled = !busy, chevron = false)
        page.playlist?.takeIf { it.nativeOwned }?.let { playlist ->
            MusicDestinationRow("从歌单移除", {
                dismiss()
                action("已移除歌曲") {
                    repository.removeFromPlaylist(
                        playlist.remoteId.ifBlank { routeId(route) },
                        listOf(song),
                    )
                }
            }, enabled = !busy, chevron = false)
        }
        song.qqMusic?.singerMids?.forEachIndexed { index, mid ->
            val artist = song.artists.split(" / ").getOrNull(index).orEmpty()
            MusicDestinationRow(if (artist.isBlank()) "查看歌手" else "歌手 · $artist", {
                dismiss()
                navigate(qqRoute("artist", mid))
            }, chevron = false)
        }
        song.qqMusic?.albumMid?.takeIf(String::isNotBlank)?.let { mid ->
            MusicDestinationRow("查看专辑", {
                dismiss()
                navigate(qqRoute("album", mid))
            }, chevron = false)
        }
        MusicDestinationRow("评论", {
            dismiss()
            navigate(qqRoute("comments", song.externalIds.qqMusicId.orEmpty()))
        }, chevron = false)
        MusicDestinationRow("查看 MV", {
            dismiss()
            action {
                val mvs = repository.songMvs(song)
                if (mvs.isEmpty()) notice = "这首歌暂时没有 MV"
                else navigate(qqRoute("mv", mvs.first().id))
            }
        }, enabled = !busy, chevron = false)
    }
    SettingsUiScope {
    MusicPlatformScrollLayout(container, Modifier.fillMaxSize().testTag("qq_music_${route.substringAfterLast('/')}"),
        showPlatformMenu = root, topBar = { platformTitle ->
        if (root) SettingsRootTopBar("QQ 音乐", titleContent = platformTitle) {
            MusicTextAction("账号", { navigate("qq/account") })
        } else SettingsTopBar(if (isSearch) "搜索 QQ 音乐" else pageTitle, onBack)
    }) { inset ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, inset + 8.dp, 16.dp, 32.dp + bottom),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (route == "home" || isSearch) item {
                MusicSearchBar(query, {
                    if (isSearch) {
                        focus.clearFocus(); keyboard?.hide()
                        offset = 0; submitted = query.trim(); refresh++
                    }
                    else navigate("qq/search")
                }, onInput = if (isSearch) ({ query = it }) else null,
                    onActivate = { navigate("qq/search") }, placeholder = "搜索 QQ 音乐歌曲、歌手、专辑、歌单",
                    inputTag = "qq_search_input", submitTag = "qq_search_submit")
            }
            if (isSearch) item {
                MusicCategoryTabs(listOf("song" to "歌曲", "playlist" to "歌单", "album" to "专辑", "artist" to "歌手"),
                    searchType, { searchType = it; offset = 0 }, "qq_search_type")
            }
            if (route == "home") item {
                SettingsGroup {
                    SettingsPreference("我喜欢", "QQ 音乐收藏的歌曲", onClick = { navigate("qq/likes") })
                    SettingsDivider()
                    SettingsPreference("我的歌单", onClick = { navigate("qq/playlists") })
                    SettingsDivider()
                    SettingsPreference("私人电台", "按你的喜好播放音乐", onClick = { navigate("qq/radio") })
                }
            }
            if (route == "me") item {
                SettingsGroup {
                    SettingsPreference(account.nickname.ifBlank { "QQ 音乐账号" },
                        if (account.loggedIn) "已登录 · ${account.uin}" else "扫码登录后同步收藏、歌单与推荐",
                        onClick = { navigate("qq/account") })
                    SettingsDivider()
                    SettingsPreference("我喜欢", onClick = { navigate("qq/likes") })
                    SettingsDivider()
                    SettingsPreference("收藏的专辑", onClick = { navigate("qq/albums") })
                    SettingsDivider()
                    SettingsPreference("CurrentMusic 个人主页", "个人资料、动态与社区账号", onClick = { navigate("qq/community-profile") })
                }
            }
            if (route == "discover") item {
                MusicTextAction("CurrentMusic 社区 ›", { navigate("qq/community") })
            }
            if ((route == "me" || route == "qq/playlists") && account.loggedIn) item {
                MusicTextAction("新建歌单", { editName = "" }, enabled = !busy)
            }
            if (!account.loggedIn && route in setOf("home", "me", "qq/playlists", "qq/likes", "qq/radio",
                    "qq/foryou", "qq/recent", "qq/albums", "qq/favorite-albums")) item {
                SettingsGroup {
                    SettingsPreference("登录 QQ 音乐", "使用自己的 QQ 音乐账号访问个人音乐库及完整播放权限。",
                        onClick = { navigate("qq/account") })
                }
            }
            error?.let { message -> item {
                Text(message, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 14.sp)
                MusicTextAction("重新加载", { offset = 0; radioPage = 1; refresh++ }, enabled = !loading)
            } }
            notice?.let { item { SettingsHint(it) } }
            if (page.cover.isNotBlank()) item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MusicCover(page.cover, Modifier.size(112.dp), pixels = 320)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(pageTitle, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        if (page.description.isNotBlank()) Text(readableText(page.description), fontSize = 13.sp, maxLines = 5,
                            overflow = TextOverflow.Ellipsis, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
            }
            page.playlist?.let { playlist -> item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (playlist.nativeOwned) {
                        MusicTextAction("重命名", { editName = playlist.name }, enabled = !busy)
                        MusicTextAction("删除歌单", { confirmDelete = true }, enabled = !busy, destructive = true)
                    } else MusicTextAction(when {
                        !account.loggedIn -> "登录后收藏歌单"
                        !playlist.nativeSubscriptionKnown -> "管理歌单收藏"
                        playlist.nativeSubscribed -> "取消收藏歌单"
                        else -> "收藏歌单"
                    }, {
                        if (!account.loggedIn) navigate("qq/account")
                        else if (playlist.nativeSubscriptionKnown) action("歌单收藏已更新") {
                            repository.subscribePlaylist(playlist.remoteId.ifBlank { routeId(route) }, !playlist.nativeSubscribed)
                        } else managePlaylistSubscription = true
                    }, enabled = !busy)
                }
            } }
            page.playlist?.takeIf { it.count > page.songs.size }?.let { playlist -> item {
                SettingsHint("已加载 ${page.songs.size} 首，共 ${playlist.count} 首")
            } }
            if (page.songs.isNotEmpty()) item {
                MusicSectionHeader(if (route == "home") page.title else "${page.songs.size} 首歌曲", "播放全部") { play(page.songs, 0) }
            }
            items(page.songs, key = { "song:${it.id}" }) { song ->
                val index = page.songs.indexOf(song)
                SongRow(
                    row = SongRowUi(song, subtitle = qqSongSubtitle(song, page.rankCounts[song.id])),
                    onPlay = { play(page.songs, index) },
                    onNext = { container.playerController.add(song, true) },
                    onQueue = { container.playerController.add(song) },
                    extraActions = { qqSongActions(song) },
                )
            }
            if (page.playlists.isNotEmpty()) item { MusicSectionHeader(if (route == "home") "推荐歌单" else "歌单") }
            if (page.playlists.isNotEmpty()) item(key = "playlists") {
                SettingsGroup {
                    page.playlists.forEachIndexed { index, playlist ->
                        QqCatalogRow(playlist.name, qqPlaylistSubtitle(playlist), playlist.cover) {
                            navigate(qqRoute("playlist", playlist.remoteId.ifBlank { playlist.id.toString() }))
                        }
                        if (index != page.playlists.lastIndex) SettingsDivider(52.dp)
                    }
                }
            }
            if (page.entries.isNotEmpty()) item(key = "entries") {
                SettingsGroup {
                    page.entries.forEachIndexed { index, entry ->
                        QqCatalogRow(entry.name, entry.subtitle, entry.cover) {
                            navigate(qqRoute(entry.type, entry.id))
                        }
                        if (index != page.entries.lastIndex) SettingsDivider(52.dp)
                    }
                }
            }
            if (loading && page.songs.isEmpty() && page.entries.isEmpty() &&
                page.playlists.isEmpty()) item { LoadingSongList(5) }
            if (page.more) item { MusicTextAction(if (loading) "加载中…" else if (route == "qq/radio") "下一批" else "加载更多", {
                if (route == "qq/radio") page.nextRadioPage?.let { radioPage = it }
                else offset += when {
                    route in setOf("qq/albums", "qq/favorite-albums") -> page.entries.size.coerceAtLeast(1)
                    route.startsWith("qq/genre/") -> page.playlists.size.coerceAtLeast(1)
                    else -> 30
                }
            }, enabled = !loading) }
            if (!loading && error == null && page.songs.isEmpty() && page.entries.isEmpty() &&
                page.playlists.isEmpty()) item {
                SettingsHint(if (isSearch && submitted.isBlank()) "输入关键词，搜索 QQ 音乐曲库" else "暂时没有内容")
            }
        }
    }
    destinations?.let { (song, lists) -> MusicDialog("加入 QQ 音乐歌单", { destinations = null }) {
        if (lists.isEmpty()) Text("没有可编辑的歌单，请先新建歌单")
        lists.forEach { playlist -> MusicDestinationRow(playlist.name, {
            destinations = null
            action("已加入歌单") { repository.addToPlaylist(playlist.remoteId.ifBlank { playlist.id.toString() }, listOf(song)) }
        }, enabled = !busy) }
    } }
    editName?.let { name -> MusicDialog(if (page.playlist == null) "新建歌单" else "重命名歌单", { editName = null }) {
        TextField(name, { editName = it.take(20) }, label = "歌单名称（最多 20 字）", singleLine = true)
        MusicTextAction("保存", {
            val title = editName.orEmpty().trim()
            editName = null
            action("歌单已保存") {
                val playlist = page.playlist
                if (playlist == null) repository.createPlaylist(title)
                else repository.renamePlaylist(playlist.remoteId.ifBlank { routeId(route) }, title)
            }
        }, enabled = name.isNotBlank() && !busy)
    } }
    if (confirmDelete) MusicDialog("删除这个 QQ 音乐歌单？", { confirmDelete = false }) {
        Text("删除后无法恢复。")
        MusicTextAction("删除歌单", {
            confirmDelete = false
            action { repository.deletePlaylist(page.playlist?.remoteId?.ifBlank { routeId(route) } ?: routeId(route)); onBack?.invoke() }
        }, destructive = true, enabled = !busy)
    }
    val playlistForSubscription = page.playlist?.takeIf { !it.nativeOwned }
    if (managePlaylistSubscription && playlistForSubscription != null) MusicDialog("管理歌单收藏", {
        managePlaylistSubscription = false
    }) {
        MusicDestinationRow("收藏歌单", {
            managePlaylistSubscription = false
            action("已收藏歌单") { repository.subscribePlaylist(playlistForSubscription.remoteId.ifBlank { routeId(route) }, true) }
        }, enabled = !busy, chevron = false)
        MusicDestinationRow("取消收藏歌单", {
            managePlaylistSubscription = false
            action("已取消收藏歌单") { repository.subscribePlaylist(playlistForSubscription.remoteId.ifBlank { routeId(route) }, false) }
        }, enabled = !busy, chevron = false)
    }
    }
}

@Composable private fun QqCatalogRow(name: String, subtitle: String, cover: String, onClick: () -> Unit) {
    val title = readableText(name)
    val detail = readableText(subtitle)
    SettingsPreference(title, detail.ifBlank { null }, onClick = onClick,
        leading = { MusicCover(cover, Modifier.size(52.dp), pixels = 220) },
        trailing = { Text("›", fontSize = 24.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) })
}

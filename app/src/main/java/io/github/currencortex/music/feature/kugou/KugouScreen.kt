package io.github.currencortex.music.feature.kugou

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.core.network.ErrorKind
import io.github.currencortex.music.core.network.appResult
import io.github.currencortex.music.data.kugou.KugouCatalogEntry
import io.github.currencortex.music.data.library.Playlist
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.library.LibrarySongActions
import io.github.currencortex.music.feature.library.LibraryViewModel
import io.github.currencortex.music.feature.library.PlaylistIndexBody
import io.github.currencortex.music.ui.component.LoadingSongList
import io.github.currencortex.music.ui.component.LocalMusicBottomInset
import io.github.currencortex.music.ui.component.MusicCover
import io.github.currencortex.music.ui.component.MusicDialog
import io.github.currencortex.music.ui.component.MusicDialogButton
import io.github.currencortex.music.ui.component.MusicTextAction
import io.github.currencortex.music.ui.component.SongRow
import io.github.currencortex.music.ui.component.SongRowUi
import io.github.currencortex.music.ui.component.SpringLazyColumn
import io.github.currencortex.music.ui.component.nativeui.SettingsScrollLayout
import io.github.currencortex.music.ui.component.nativeui.SettingsTopBar
import io.github.currencortex.music.ui.component.nativeui.SettingsUiScope
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

/** KuGou routes: `kugou/{daily,new,ranks,likes,playlists,account}` and `kugou/{rank,playlist,user-playlist,album,artist,genre,comments,mv}/<id>`. */
internal fun kugouRoute(type: String, id: String) = "kugou/$type/$id"

private data class KugouListPage(
    val title: String = "",
    val subtitle: String = "",
    val intro: String = "",
    val songs: List<Song> = emptyList(),
    val entries: List<KugouCatalogEntry> = emptyList(),
    val hasMore: Boolean = false,
    val needsLogin: Boolean = false,
    val canCreatePlaylist: Boolean = false,
)

private suspend fun loadKugouPage(container: AppContainer, route: String, offset: Int): KugouListPage {
    val kugou = container.kugou
    val id = route.substringAfterLast('/')
    return when {
        route == "kugou/daily" -> KugouListPage("每日推荐", "酷狗音乐", songs = kugou.daily().songs)
        route == "kugou/new" -> KugouListPage("新歌速递", "酷狗音乐", songs = kugou.newSongs())
        route == "kugou/likes" -> {
            if (!kugou.state.value.loggedIn) KugouListPage("我喜欢", "登录酷狗音乐后同步“我喜欢”", needsLogin = true)
            else {
                val songs = kugou.likedSongs(fresh = true)
                KugouListPage("我喜欢", "${songs.size} 首歌曲", songs = songs)
            }
        }
        route == "kugou/playlists" -> {
            if (!kugou.state.value.loggedIn) KugouListPage("我的酷狗歌单", "登录酷狗音乐后查看与管理云歌单", needsLogin = true)
            else {
                val lists = kugou.playlists()
                KugouListPage("我的酷狗歌单", "${lists.size} 个歌单", entries = lists.map { playlist ->
                    KugouCatalogEntry(
                        id = if (playlist.nativeLiked) "likes" else playlist.remoteId.ifBlank { playlist.id.toString() },
                        name = playlist.name,
                        cover = playlist.cover,
                        subtitle = if (playlist.count > 0) "${playlist.count} 首" else "",
                        type = if (playlist.nativeLiked) "likes-root" else "user-playlist",
                        count = playlist.count,
                    )
                }, canCreatePlaylist = true)
            }
        }
        route.startsWith("kugou/user-playlist/") -> {
            val listId = id.toIntOrNull() ?: return KugouListPage("酷狗歌单")
            val playlist = kugou.userPlaylist(listId)
            KugouListPage(playlist.name, "${playlist.count} 首歌曲", songs = playlist.songs)
        }
        route == "kugou/ranks" -> KugouListPage("排行榜", entries = kugou.ranks().map {
            KugouCatalogEntry(it.id, it.name, it.cover, it.updateText, "rank")
        })
        route.startsWith("kugou/genre/") -> {
            val page = kugou.categoryPlaylists(id, offset)
            val entries = page.playlists.map { playlist ->
                KugouCatalogEntry(
                    id = playlist.remoteId.ifBlank { playlist.id.toString() },
                    name = playlist.name,
                    cover = playlist.cover,
                    subtitle = if (playlist.count > 0) "${playlist.count} 首" else playlist.description,
                    type = "playlist",
                    count = playlist.count,
                )
            }
            KugouListPage("分类歌单", "共 ${page.total} 个歌单", entries = entries, hasMore = page.hasMore)
        }
        route.startsWith("kugou/rank/") -> {
            val page = kugou.rankSongs(id, offset)
            val name = kugou.cachedHome().ranks.firstOrNull { it.id == id }?.name ?: "榜单"
            KugouListPage(name, "酷狗榜单", songs = page.songs, hasMore = page.hasMore)
        }
        route.startsWith("kugou/playlist/") -> if (offset == 0) {
            val playlist = kugou.playlist(id)
            KugouListPage(playlist.name, "${playlist.count} 首歌曲", playlist.description, playlist.songs, hasMore = playlist.songs.size < playlist.count)
        } else {
            val page = kugou.playlistSongs(id, offset)
            KugouListPage(songs = page.songs, hasMore = page.hasMore)
        }
        route.startsWith("kugou/album/") -> {
            val album = kugou.album(id)
            KugouListPage(album.entry.name, album.entry.subtitle, album.intro, album.songs)
        }
        route.startsWith("kugou/artist/") -> {
            val artist = kugou.artist(id, offset)
            val albums = if (offset == 0) kugou.artistAlbums(id) else emptyList()
            KugouListPage(artist.name, "${artist.songCount} 首歌曲 · ${artist.albumCount} 张专辑", artist.intro, artist.songs,
                albums, artist.hasMoreSongs)
        }
        else -> KugouListPage("酷狗音乐")
    }
}

/** One page for every KuGou collection, comments, MV, and account screen. */
@Composable
fun KugouScreen(
    container: AppContainer,
    route: String,
    onBack: (() -> Unit)?,
    navigate: (String) -> Unit,
    play: (List<Song>, Int) -> Unit,
    libraryVm: LibraryViewModel? = null,
) {
    if (route == "kugou/account") {
        KugouAccountScreen(container, onBack)
        return
    }
    if (route.startsWith("kugou/comments/")) {
        val id = route.substringAfterLast('/').toLongOrNull() ?: 0L
        KugouCommentsScreen(container, id, onBack ?: {})
        return
    }
    if (route.startsWith("kugou/mv/")) {
        val hash = route.substringAfterLast('/')
        KugouMvScreen(container, hash, onBack ?: {})
        return
    }
    if (route == "kugou/playlists") {
        val kugouAccount by container.kugou.state.collectAsStateWithLifecycle()
        val playlistRev by container.kugou.playlistRevision.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
        var loading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }
        var reload by remember { mutableIntStateOf(0) }
        var createDialog by remember { mutableStateOf(false) }
        var newPlaylistName by remember { mutableStateOf("") }
        var creating by remember { mutableStateOf(false) }

        LaunchedEffect(kugouAccount.revision, playlistRev, reload) {
            if (!kugouAccount.loggedIn) {
                playlists = emptyList()
                loading = false
                error = null
            } else {
                loading = true
                error = null
                when (val result = appResult { container.kugou.playlists() }) {
                    is AppResult.Success -> playlists = result.value
                    is AppResult.Failure -> error = result.message
                }
                loading = false
            }
        }
        libraryVm?.let { vm ->
            PlaylistIndexBody(
                vm = vm,
                navigate = navigate,
                play = play,
                onBack = onBack,
                onCreate = { newPlaylistName = ""; createDialog = true },
                qqPlaylists = playlists,
                qqLoading = loading && playlists.isEmpty(),
                qqError = error,
                onQqRefresh = { reload++ },
                externalPlatform = "kugou",
            )
            SettingsUiScope {
                if (createDialog) {
                    MusicDialog("新建酷狗歌单", { if (!creating) createDialog = false }) {
                        TextField(newPlaylistName, { newPlaylistName = it }, singleLine = true,
                            label = "歌单名称", modifier = Modifier.fillMaxWidth())
                        MusicDialogButton(if (creating) "创建中…" else "创建", onClick = {
                            val title = newPlaylistName.trim()
                            if (title.isBlank() || creating) return@MusicDialogButton
                            creating = true
                            scope.launch {
                                when (val result = appResult { container.kugou.createPlaylist(title) }) {
                                    is AppResult.Success -> { createDialog = false; reload++ }
                                    is AppResult.Failure -> container.errorNotices.show(result.message)
                                }
                                creating = false
                            }
                        }, enabled = newPlaylistName.isNotBlank() && !creating)
                    }
                }
            }
            return
        }
    }
    val kugouAccount by container.kugou.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var page by remember(route) { mutableStateOf(KugouListPage()) }
    var loading by remember(route) { mutableStateOf(true) }
    var loadingMore by remember(route) { mutableStateOf(false) }
    var error by remember(route) { mutableStateOf<String?>(null) }
    var reload by remember(route) { mutableIntStateOf(0) }
    var createDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    val bottom = LocalMusicBottomInset.current

    LaunchedEffect(route, reload, kugouAccount.revision) {
        loading = true
        error = null
        when (val result = appResult { loadKugouPage(container, route, 0) }) {
            is AppResult.Success -> page = result.value
            is AppResult.Failure -> {
                if (result.kind == ErrorKind.Unauthorized) page = KugouListPage("酷狗音乐", needsLogin = true)
                else error = result.message
            }
        }
        loading = false
    }

    fun more() {
        if (loadingMore || loading) return
        loadingMore = true
        scope.launch {
            val nextOffset = if (page.songs.isNotEmpty()) page.songs.size else page.entries.size
            when (val result = appResult { loadKugouPage(container, route, nextOffset) }) {
                is AppResult.Success -> page = page.copy(
                    songs = (page.songs + result.value.songs).distinctBy { it.id },
                    entries = (page.entries + result.value.entries).distinctBy { it.id },
                    hasMore = result.value.hasMore && (result.value.songs.isNotEmpty() || result.value.entries.isNotEmpty()),
                )
                is AppResult.Failure -> error = result.message
            }
            loadingMore = false
        }
    }

    SettingsUiScope {
        SettingsScrollLayout(
            Modifier.fillMaxSize(),
            glassOverContent = false,
            topBar = { SettingsTopBar(page.title.ifBlank { "酷狗音乐" }, onBack) },
        ) { topInset ->
            SpringLazyColumn(
                Modifier.fillMaxSize().testTag("kugou_page"),
                contentPadding = PaddingValues(start = 16.dp, top = topInset + 8.dp, end = 16.dp, bottom = 24.dp + bottom),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (page.subtitle.isNotBlank() || page.intro.isNotBlank()) item(key = "intro") {
                    Text(
                        listOf(page.subtitle, page.intro).filter(String::isNotBlank).joinToString("\n").take(240),
                        fontSize = 13.sp, color = COUITheme.colorScheme.labelPrimary.copy(alpha = .6f),
                    )
                }
                if (page.needsLogin) item(key = "login-required") {
                    MusicTextAction(
                        if (kugouAccount.loggedIn) "酷狗登录已失效，点击重新登录 ›" else "前往登录酷狗音乐 ›",
                        { navigate("kugou/account") },
                        Modifier.testTag("kugou_go_login"),
                    )
                }
                if (page.canCreatePlaylist) item(key = "create-playlist") {
                    MusicTextAction("+ 新建酷狗歌单", { newPlaylistName = ""; createDialog = true }, Modifier.testTag("kugou_create_playlist"))
                }
                if (loading && page.songs.isEmpty() && page.entries.isEmpty()) item(key = "loading") { LoadingSongList(6) }
                error?.let { message ->
                    item(key = "error") { MusicTextAction("$message，点击重试", { reload++ }) }
                }
                if (page.songs.isNotEmpty()) item(key = "play-all") {
                    MusicTextAction("播放全部（${page.songs.size}）", { play(page.songs, 0) }, Modifier.testTag("kugou_play_all"))
                }
                itemsIndexed(page.songs, key = { index, song -> "${song.id}-$index" }) { index, song ->
                    SongRow(
                        SongRowUi(song),
                        { play(page.songs, index) },
                        { container.playerController.add(song, true) },
                        { container.playerController.add(song) },
                        extraActions = libraryVm?.let { vm -> { LibrarySongActions(vm, song, navigate) } },
                    )
                }
                itemsIndexed(page.entries, key = { _, entry -> "${entry.type}-${entry.id}" }) { _, entry ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(role = Role.Button) {
                                if (entry.type == "likes-root") navigate("kugou/likes")
                                else navigate(kugouRoute(entry.type, entry.id))
                            }
                            .padding(vertical = 8.dp)
                            .testTag("kugou_entry_${entry.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MusicCover(entry.cover, Modifier.size(52.dp), 160, cornerRadius = if (entry.type == "artist") 26.dp else 12.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (entry.subtitle.isNotBlank()) {
                                Text(entry.subtitle, fontSize = 12.sp, color = COUITheme.colorScheme.labelPrimary.copy(alpha = .58f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Text("›", fontSize = 20.sp, color = COUITheme.colorScheme.labelPrimary.copy(alpha = .35f))
                    }
                }
                if (page.hasMore) item(key = "more") {
                    MusicTextAction(if (loadingMore) "正在加载…" else "加载更多", ::more, enabled = !loadingMore)
                }
            }
        }
        if (createDialog) {
            MusicDialog("新建酷狗歌单", { if (!creating) createDialog = false }) {
                TextField(newPlaylistName, { newPlaylistName = it }, singleLine = true,
                    label = "歌单名称", modifier = Modifier.fillMaxWidth())
                MusicDialogButton(if (creating) "创建中…" else "创建", onClick = {
                    val title = newPlaylistName.trim()
                    if (title.isBlank() || creating) return@MusicDialogButton
                    creating = true
                    scope.launch {
                        when (val result = appResult { container.kugou.createPlaylist(title) }) {
                            is AppResult.Success -> { createDialog = false; reload++ }
                            is AppResult.Failure -> container.errorNotices.show(result.message)
                        }
                        creating = false
                    }
                }, enabled = newPlaylistName.isNotBlank() && !creating)
            }
        }
    }
}

package io.github.currencortex.music.feature.qqmusic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.media.PlaybackMode
import io.github.currencortex.music.core.media.PlayerMode
import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.core.network.appResult
import io.github.currencortex.music.data.qqmusic.QqArtistDetail
import io.github.currencortex.music.data.qqmusic.QqCatalogEntry
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.library.LibraryViewModel
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class QqArtistUiState(
    val loading: Boolean = true,
    val detail: QqArtistDetail? = null,
    val error: String? = null,
    val songsLoading: Boolean = false,
    val songsError: String? = null,
    val albumsLoading: Boolean = false,
    val albumsError: String? = null,
)

class QqArtistViewModel(val container: AppContainer, val mid: String) : ViewModel() {
    val state = MutableStateFlow(QqArtistUiState())
    private var mainJob: Job? = null
    private var songsJob: Job? = null
    private var albumsJob: Job? = null

    init {
        viewModelScope.launch {
            container.ready.await()
            reload()
            combine(
                container.accountRepository.sessionRevision,
                container.musicSettings.state.map { it.server }.distinctUntilChanged(),
                container.qqMusic.state.map { it.revision }.distinctUntilChanged(),
            ) { _, _, _ -> Unit }.drop(1).collect { reload() }
        }
    }

    fun reload() {
        mainJob?.cancel()
        songsJob?.cancel()
        albumsJob?.cancel()
        state.value = QqArtistUiState()
        mainJob = viewModelScope.launch {
            when (val result = appResult { container.qqMusic.artistDetail(mid) }) {
                is AppResult.Success -> state.update {
                    it.copy(
                        loading = false,
                        detail = result.value,
                        error = null,
                        albumsError = if (result.value.albumsFailed) "专辑列表暂时加载失败" else null,
                    )
                }
                is AppResult.Failure -> state.update {
                    it.copy(loading = false, error = result.message)
                }
            }
        }
    }

    fun loadMoreSongs() {
        val detail = state.value.detail ?: return
        if (state.value.songsLoading || !detail.hasMoreSongs) return
        state.update { it.copy(songsLoading = true, songsError = null) }
        songsJob = viewModelScope.launch {
            try {
                when (val result = appResult { container.qqMusic.artistSongs(mid, detail.songs.size) }) {
                    is AppResult.Success -> state.update { current ->
                        val active = current.detail ?: return@update current
                        val merged = (active.songs + result.value.songs).distinctBy(Song::id)
                        current.copy(
                            detail = active.copy(
                                songs = merged,
                                hasMoreSongs = result.value.hasMore && merged.size > active.songs.size,
                            ),
                            songsLoading = false,
                            songsError = null,
                        )
                    }
                    is AppResult.Failure -> state.update {
                        it.copy(songsLoading = false, songsError = result.message)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }
    }

    fun loadMoreAlbums() {
        val detail = state.value.detail ?: return
        if (state.value.albumsLoading || !detail.hasMoreAlbums) return
        state.update { it.copy(albumsLoading = true, albumsError = null) }
        albumsJob = viewModelScope.launch {
            when (val result = appResult { container.qqMusic.artistAlbums(mid, detail.albums.size) }) {
                is AppResult.Success -> state.update { current ->
                    val active = current.detail ?: return@update current
                    val merged = (active.albums + result.value.entries)
                        .distinctBy(QqCatalogEntry::id)
                    current.copy(
                        detail = active.copy(
                            albums = merged,
                            totalAlbum = maxOf(active.totalAlbum, result.value.total),
                            hasMoreAlbums = result.value.hasMore && merged.size > active.albums.size,
                        ),
                        albumsLoading = false,
                        albumsError = null,
                    )
                }
                is AppResult.Failure -> state.update {
                    it.copy(albumsLoading = false, albumsError = result.message)
                }
            }
        }
    }

    override fun onCleared() {
        mainJob?.cancel()
        songsJob?.cancel()
        albumsJob?.cancel()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QqArtistScreen(
    vm: QqArtistViewModel,
    library: LibraryViewModel,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
    play: (List<Song>, Int) -> Unit,
    initialTab: String = "home",
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val playback by vm.container.playerController.state.collectAsStateWithLifecycle()
    val detail = state.detail
    val room = LocalRoomSongRequest.current
    val canPlay = room == null && playback.mode == PlayerMode.LOCAL
    var tab by rememberSaveable(vm.mid, initialTab) { mutableStateOf(initialTab) }
    var briefOpen by rememberSaveable(vm.mid) { mutableStateOf(false) }
    val homeScroll = rememberLazyListState()
    val songsScroll = rememberLazyListState()
    val albumsScroll = rememberLazyListState()
    val menuScope = rememberCoroutineScope()
    val scroll = when (tab) {
        "songs" -> songsScroll
        "albums" -> albumsScroll
        else -> homeScroll
    }
    val playingSongs = if (tab == "home") detail?.songs?.take(5).orEmpty() else detail?.songs.orEmpty()
    LaunchedEffect(detail?.songs) {
        detail?.songs?.takeIf { it.isNotEmpty() }?.let(library::refreshSearchLikes)
    }
    val covers = remember(tab, detail) {
        if (tab == "albums") detail?.albums.orEmpty().take(10).map(QqCatalogEntry::cover)
        else detail?.songs.orEmpty().take(12).map(Song::cover)
    }
    PreloadMusicCovers(covers)
    MusicSubpage(detail?.name?.ifBlank { "QQ 歌手" } ?: "QQ 歌手", "qq_artist_page", onBack,
        backTag = "qq_artist_back") { topInset ->
        MusicPullToRefresh(state.loading, vm::reload, Modifier.fillMaxSize().padding(top = topInset)) {
            LazyColumn(
                Modifier.fillMaxSize().testTag("qq_artist_home"),
                state = scroll,
                contentPadding = musicScrollPadding(PaddingValues(16.dp, 8.dp, 16.dp, 32.dp)),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item("hero") {
                    QqArtistHero(detail) { if (detail != null) briefOpen = true }
                }
                stickyHeader("tabs") {
                    MusicCategoryTabs(
                        listOf("home" to "音乐", "songs" to "歌曲", "albums" to "专辑"),
                        tab,
                        { tab = it },
                        "qq_artist_tab",
                        Modifier.background(MiuixTheme.colorScheme.background),
                    )
                }
                state.error?.let { message ->
                    item("main_error") {
                        QqArtistSectionError(message, "重试歌手主页", vm::reload, state.loading)
                    }
                }
                if (state.loading && detail == null) item("loading") { LoadingSongList(5) }
                if (detail != null && (tab == "home" || tab == "songs")) {
                    if (playingSongs.isNotEmpty()) item("play_header") {
                        QqArtistPlayHeader(
                            if (tab == "home") "播放热门歌曲" else "播放已加载歌曲",
                            playingSongs.size,
                            canPlay,
                        ) {
                            vm.container.playerController.setMode(PlaybackMode.LIST)
                            play(playingSongs, 0)
                        }
                    }
                    if (room != null) item("room_hint") {
                        Text(
                            "一起听房间内暂不支持直接播放 QQ 歌手歌曲",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    itemsIndexed(playingSongs, key = { _, song -> "${tab}_song_${song.id}" }) { index, song ->
                        SongRow(
                            SongRowUi(song),
                            onPlay = { if (canPlay) play(playingSongs, index) },
                            onNext = { vm.container.playerController.add(song, true) },
                            onQueue = { vm.container.playerController.add(song) },
                        ) {
                            QqSongMenuActions(vm.container, library, song, navigate, menuScope)
                        }
                    }
                    if (!state.loading && playingSongs.isEmpty() && state.error == null) {
                        item("songs_empty") { QqArtistEmpty("暂无歌曲") }
                    }
                    if (tab == "songs") {
                        if (state.songsLoading) item("songs_loading") { LoadingSongList(2) }
                        state.songsError?.let { message ->
                            item("songs_error") {
                                QqArtistSectionError(message, "重试歌曲", vm::loadMoreSongs, state.songsLoading)
                            }
                        }
                        if (detail.hasMoreSongs && state.songsError == null) item("songs_more") {
                            MusicTextAction(
                                "加载更多歌曲",
                                vm::loadMoreSongs,
                                Modifier.fillMaxWidth().testTag("qq_artist_more_songs"),
                                enabled = !state.songsLoading,
                            )
                        } else if (!state.songsLoading && detail.songs.isNotEmpty()) {
                            item("songs_end") {
                                QqArtistHint("已加载全部 ${detail.songs.size} 首歌曲")
                            }
                        }
                    } else {
                        if (detail.songs.isNotEmpty()) item("all_songs") {
                            MusicTextAction(
                                "全部 ${maxOf(detail.totalSong, detail.songs.size)} 首歌曲 ›",
                                { tab = "songs" },
                                Modifier.fillMaxWidth().testTag("qq_artist_all_songs"),
                            )
                        }
                        item("albums_preview") {
                            QqArtistSectionTitle("专辑", "查看全部") { tab = "albums" }
                            when {
                                state.albumsLoading && detail.albums.isEmpty() -> LoadingTextList(2)
                                detail.albums.isNotEmpty() -> LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(detail.albums.take(6), key = QqCatalogEntry::id) { album ->
                                        MusicArtworkCard(
                                            album.cover,
                                            readableText(album.name),
                                            readableText(album.subtitle),
                                            { navigate(qqRoute("album", album.id)) },
                                            Modifier.width(152.dp).testTag("qq_artist_album_${album.id}"),
                                            enabled = album.id.isNotBlank(),
                                            pixels = 480,
                                        )
                                    }
                                }
                                state.albumsError == null -> QqArtistEmpty("暂无专辑")
                            }
                            state.albumsError?.let { message ->
                                QqArtistSectionError(message, "重试专辑", vm::loadMoreAlbums, state.albumsLoading)
                            }
                        }
                        item("biography") {
                            QqArtistBiography(detail) { briefOpen = true }
                        }
                    }
                }
                if (detail != null && tab == "albums") {
                    item("album_summary") {
                        QqArtistHint(
                            if (detail.totalAlbum > 0) "共 ${detail.totalAlbum} 张专辑"
                            else "共 ${detail.albums.size} 张专辑",
                        )
                    }
                    items(detail.albums, key = { "album_row_${it.id}" }) { album ->
                        QqArtistAlbumRow(album) { navigate(qqRoute("album", album.id)) }
                    }
                    if (state.albumsLoading) item("albums_loading") { LoadingSongList(3) }
                    state.albumsError?.let { message ->
                        item("albums_error") {
                            QqArtistSectionError(message, "重试专辑", vm::loadMoreAlbums, state.albumsLoading)
                        }
                    }
                    if (!state.albumsLoading && detail.albums.isEmpty() && state.albumsError == null) {
                        item("albums_empty") { QqArtistEmpty("暂无专辑") }
                    }
                    if (detail.hasMoreAlbums && state.albumsError == null) item("albums_more") {
                        MusicTextAction(
                            "加载更多专辑",
                            vm::loadMoreAlbums,
                            Modifier.fillMaxWidth().testTag("qq_artist_more_albums"),
                            enabled = !state.albumsLoading,
                        )
                    }
                }
            }
        }
    }
    if (briefOpen) MusicDialog("歌手简介", { briefOpen = false }) {
        Text(detail?.name.orEmpty(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        detail?.alias?.takeIf(String::isNotBlank)?.let { Text(it, fontSize = 13.sp) }
        Text(
            detail?.brief?.takeIf(String::isNotBlank)?.let(::readableText) ?: "暂无简介",
            Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
            fontSize = 14.sp,
            lineHeight = 22.sp,
        )
        MusicDialogButton("关闭", onClick = { briefOpen = false })
    }
}

@Composable
private fun QqArtistHero(detail: QqArtistDetail?, onOpenBrief: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(288.dp).clip(RoundedCornerShape(28.dp)).testTag("qq_artist_hero"),
    ) {
        if (detail == null) {
            MusicPlaceholder(Modifier.matchParentSize())
            return@Box
        }
        MusicCover(detail.cover, Modifier.matchParentSize(), 900, cornerRadius = 28.dp)
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .88f))),
            ),
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                detail.name.ifBlank { "QQ 歌手" },
                Modifier.testTag("qq_artist_name"),
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            detail.alias.takeIf(String::isNotBlank)?.let {
                Text(it, fontSize = 13.sp, color = Color.White.copy(alpha = .8f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            detail.brief.takeIf(String::isNotBlank)?.let {
                Text(
                    readableText(it),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = Color.White.copy(alpha = .76f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${detail.totalSong} 首歌曲 · ${detail.totalAlbum} 张专辑 · ${detail.totalMv} 支 MV",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = .85f),
            )
            if (detail.fans > 0) Text(
                "${qqCount(detail.fans)} 粉丝",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = .85f),
            )
            if (detail.brief.isNotBlank()) {
                Text(
                    "查看简介 ›",
                    Modifier.clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = .16f))
                        .clickable(role = Role.Button, onClick = onOpenBrief)
                        .testTag("qq_artist_open_brief")
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun QqArtistPlayHeader(title: String, count: Int, enabled: Boolean, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("qq_artist_play_all"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MusicTransportButton("播放全部", onPlay, Modifier.size(48.dp), prominent = true, enabled = enabled)
        Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text("$count 首", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun QqArtistSectionTitle(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        action?.let { MusicTextAction("$it ›", onClick) }
    }
}

@Composable
private fun QqArtistAlbumRow(album: QqCatalogEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .clickable(enabled = album.id.isNotBlank(), role = Role.Button, onClick = onClick)
            .testTag("qq_artist_album_row_${album.id}")
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MusicCover(album.cover, Modifier.size(88.dp), 240, cornerRadius = 12.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                readableText(album.name),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            album.subtitle.takeIf(String::isNotBlank)?.let {
                Text(
                    readableText(it),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QqArtistBiography(detail: QqArtistDetail, onOpen: () -> Unit) {
    QqArtistSectionTitle("歌手简介") { if (detail.brief.isNotBlank()) onOpen() }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .clickable(enabled = detail.brief.isNotBlank(), role = Role.Button, onClick = onOpen)
            .testTag("qq_artist_brief")
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MusicCover(detail.cover, Modifier.size(48.dp), 160, cornerRadius = 24.dp)
            Text(detail.name.ifBlank { "QQ 歌手" }, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
        Text(
            detail.brief.takeIf(String::isNotBlank)?.let(::readableText) ?: "暂无简介",
            fontSize = 14.sp,
            lineHeight = 23.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun QqArtistEmpty(message: String) {
    Text(
        message,
        Modifier.padding(vertical = 12.dp),
        fontSize = 14.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
private fun QqArtistHint(message: String) {
    Text(
        message,
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        fontSize = 12.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
private fun QqArtistSectionError(message: String, retry: String, onRetry: () -> Unit, loading: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(message, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        MusicTextAction(retry, onRetry, enabled = !loading)
    }
}

private fun qqCount(value: Long): String = when {
    value >= 100_000_000 -> "%.1f亿".format(java.util.Locale.ROOT, value / 100_000_000.0)
    value >= 10_000 -> "%.1f万".format(java.util.Locale.ROOT, value / 10_000.0)
    else -> value.coerceAtLeast(0).toString()
}

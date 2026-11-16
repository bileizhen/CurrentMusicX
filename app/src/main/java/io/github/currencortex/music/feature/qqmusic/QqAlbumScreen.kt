package io.github.currencortex.music.feature.qqmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
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
import io.github.currencortex.music.data.qqmusic.QqAlbumDetail
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.library.LibraryViewModel
import io.github.currencortex.music.feature.library.PlaylistPageLayout
import io.github.currencortex.music.feature.library.PlaylistTrack
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
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

data class QqAlbumUiState(
    val loading: Boolean = true,
    val detail: QqAlbumDetail? = null,
    val error: String? = null,
    val subscribed: Boolean? = null,
    val subscriptionLoading: Boolean = false,
    val writeLoading: Boolean = false,
    val actionMessage: String? = null,
    val actionError: String? = null,
    val songsLoading: Boolean = false,
    val songsError: String? = null,
)

class QqAlbumViewModel(val container: AppContainer, val mid: String) : ViewModel() {
    val state = MutableStateFlow(QqAlbumUiState())
    private var detailJob: Job? = null
    private var subscriptionJob: Job? = null
    private var writeJob: Job? = null
    private var songsJob: Job? = null

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
        if (state.value.writeLoading) return
        detailJob?.cancel()
        subscriptionJob?.cancel()
        songsJob?.cancel()
        state.value = QqAlbumUiState()
        detailJob = viewModelScope.launch {
            when (val result = appResult { container.qqMusic.albumDetail(mid) }) {
                is AppResult.Success -> {
                    state.update {
                        it.copy(loading = false, detail = result.value, error = null)
                    }
                    reloadSubscription()
                }
                is AppResult.Failure -> state.update {
                    it.copy(loading = false, error = result.message)
                }
            }
        }
    }

    fun reloadSubscription(explicit: Boolean = false) {
        if (state.value.writeLoading) return
        subscriptionJob?.cancel()
        val loggedIn = container.qqMusic.state.value.loggedIn
        if (!loggedIn) {
            state.update {
                it.copy(subscribed = null, subscriptionLoading = false, actionError = null)
            }
            return
        }
        state.update {
            it.copy(subscriptionLoading = true, actionError = null,
                actionMessage = if (explicit) null else it.actionMessage)
        }
        subscriptionJob = viewModelScope.launch {
            when (val result = appResult { container.qqMusic.albumSubscription(mid) }) {
                is AppResult.Success -> state.update {
                    it.copy(
                        subscribed = result.value,
                        subscriptionLoading = false,
                        actionMessage = if (explicit && result.value == null)
                            "暂时无法确认专辑收藏状态，请稍后重试" else it.actionMessage,
                    )
                }
                is AppResult.Failure -> state.update {
                    it.copy(
                        subscribed = null,
                        subscriptionLoading = false,
                        actionError = result.message.takeIf { explicit },
                    )
                }
            }
        }
    }

    fun setSubscribed(subscribed: Boolean) {
        if (state.value.writeLoading) return
        if (!container.qqMusic.state.value.loggedIn) {
            state.update { it.copy(actionError = "请先扫码登录 QQ 音乐") }
            return
        }
        state.update {
            it.copy(writeLoading = true, actionMessage = null, actionError = null)
        }
        writeJob = viewModelScope.launch {
            when (val write = appResult { container.qqMusic.subscribeAlbum(mid, subscribed) }) {
                is AppResult.Success -> {
                    // The write API echoes the requested state. Confirm it with the
                    // read-only subscription query instead of trusting that echo.
                    when (val confirmed = appResult { container.qqMusic.albumSubscription(mid) }) {
                        is AppResult.Success -> state.update {
                            when (val value = confirmed.value) {
                                null -> it.copy(
                                    subscribed = null,
                                    writeLoading = false,
                                    actionMessage = "收藏请求已提交，状态暂无法确认",
                                )
                                subscribed -> it.copy(
                                    subscribed = value,
                                    writeLoading = false,
                                    actionMessage = if (value) "已收藏专辑" else "已取消收藏专辑",
                                )
                                else -> it.copy(
                                    subscribed = value,
                                    writeLoading = false,
                                    actionError = "QQ 音乐未确认专辑收藏变更，请刷新后重试",
                                )
                            }
                        }
                        is AppResult.Failure -> state.update {
                            it.copy(
                                subscribed = null,
                                writeLoading = false,
                                actionMessage = "收藏请求已提交，状态暂无法确认",
                            )
                        }
                    }
                }
                is AppResult.Failure -> state.update {
                    it.copy(writeLoading = false, actionError = write.message)
                }
            }
        }
    }

    fun loadMoreSongs() {
        val detail = state.value.detail ?: return
        if (state.value.songsLoading || !detail.hasMoreSongs) return
        state.update { it.copy(songsLoading = true, songsError = null) }
        songsJob = viewModelScope.launch {
            when (val result = appResult { container.qqMusic.albumSongs(mid, detail.songs.size, 300) }) {
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
        }
    }

    override fun onCleared() {
        detailJob?.cancel()
        subscriptionJob?.cancel()
        writeJob?.cancel()
        songsJob?.cancel()
    }
}

@Composable
fun QqAlbumScreen(
    vm: QqAlbumViewModel,
    library: LibraryViewModel,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
    play: (List<Song>, Int) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val account by vm.container.qqMusic.state.collectAsStateWithLifecycle()
    val qqLikes by vm.container.qqMusic.likedState.collectAsStateWithLifecycle()
    val playback by vm.container.playerController.state.collectAsStateWithLifecycle()
    val queue by vm.container.playbackQueue.state.collectAsStateWithLifecycle()
    val bottom = LocalMusicBottomInset.current
    val detail = state.detail
    val list = rememberLazyListState()
    val menuScope = rememberCoroutineScope()
    var heroHeightPx by remember { mutableIntStateOf(0) }
    var searching by rememberSaveable(vm.mid) { mutableStateOf(false) }
    var query by rememberSaveable(vm.mid) { mutableStateOf("") }
    var menuOpen by rememberSaveable(vm.mid) { mutableStateOf(false) }
    var infoOpen by rememberSaveable(vm.mid) { mutableStateOf(false) }
    var accountRevision by rememberSaveable(vm.mid) { mutableLongStateOf(account.revision) }
    val cover = detail?.cover.orEmpty()
        .ifBlank { detail?.songs?.firstOrNull { it.cover.isNotBlank() }?.cover.orEmpty() }
    val songs = remember(detail?.songs, query) {
        val source = detail?.songs.orEmpty()
        if (query.isBlank()) source else {
            val needle = MusicSearchQuery(query)
            source.filter { needle.matches(*it.displayNames.toTypedArray(), it.artists, it.album) }
        }
    }
    val canPlay = playback.mode == PlayerMode.LOCAL
    val current = queue.current
    LaunchedEffect(detail?.songs) {
        detail?.songs?.takeIf { it.isNotEmpty() }?.let(library::refreshSearchLikes)
    }
    LaunchedEffect(account.revision, detail != null) {
        if (accountRevision != account.revision) {
            accountRevision = account.revision
            if (detail != null) vm.reloadSubscription()
        }
    }
    val songCovers = remember(songs) { songs.take(12).map(Song::cover) }
    PreloadMusicCovers(songCovers)
    fun start(shuffle: Boolean) {
        if (!canPlay || songs.isEmpty()) return
        vm.container.playerController.setMode(if (shuffle) PlaybackMode.SHUFFLE else PlaybackMode.LIST)
        play(songs, if (shuffle) songs.indices.random() else 0)
    }
    fun subscriptionAction() {
        if (!account.loggedIn) {
            navigate("qq/account")
            return
        }
        when (state.subscribed) {
            null -> vm.reloadSubscription(explicit = true)
            true -> vm.setSubscribed(false)
            false -> vm.setSubscribed(true)
        }
    }
    val subscriptionLabel = when {
        !account.loggedIn -> "登录后收藏"
        state.subscriptionLoading || state.writeLoading -> "处理中…"
        state.subscribed == null -> "检查收藏状态"
        state.subscribed == true -> "已收藏"
        else -> "收藏专辑"
    }
    PlaylistPageLayout(cover, list, heroHeightPx, "qq_album_page", toolbar = { progress ->
        GlassSearchToolbar(
            detail?.title?.ifBlank { "QQ 专辑" } ?: "QQ 专辑",
            ((progress - .35f) / .65f).coerceIn(0f, 1f),
            onBack,
            searching,
            {
                searching = it
                if (!it) query = ""
            },
            query,
            { query = it },
            GlassToolbarAction(Icons.Default.Refresh, "刷新专辑", vm::reload, "qq_album_refresh"),
            GlassToolbarAction(Icons.Default.MoreVert, "专辑操作", { menuOpen = true }, "qq_album_more"),
            Modifier.testTag("qq_album_toolbar"),
            inputTag = "qq_album_query",
            searchTag = "qq_album_search",
            placeholder = "搜索专辑歌曲",
        )
    }) { topInset ->
        MusicPullToRefresh(state.loading, vm::reload, Modifier.fillMaxSize(), showIndicator = false) {
            LazyColumn(
                Modifier.fillMaxSize().testTag("qq_album_tracks"),
                state = list,
                contentPadding = musicScrollPadding(
                    PaddingValues(top = topInset + 8.dp, bottom = 32.dp + bottom),
                ),
            ) {
                item("hero") {
                    QqAlbumHero(
                        detail = detail,
                        accountLoggedIn = account.loggedIn,
                        subscriptionLabel = subscriptionLabel,
                        subscriptionEnabled = !state.subscriptionLoading && !state.writeLoading && !state.loading,
                        canPlay = canPlay,
                        songs = songs,
                        onSubscription = ::subscriptionAction,
                        onShuffle = { start(true) },
                        onInfo = { infoOpen = true },
                        onArtist = { mid ->
                            if (mid.isNotBlank()) navigate(qqRoute("artist", mid))
                        },
                        onDescription = { if (detail?.description?.isNotBlank() == true) infoOpen = true },
                        onHeight = { heroHeightPx = it },
                    )
                }
                item("playback") {
                    Row(
                        Modifier.fillMaxWidth().background(MiuixTheme.colorScheme.background)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("qq_album_playback_panel"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MusicTransportButton(
                            "播放全部",
                            { start(false) },
                            Modifier.size(48.dp).testTag("qq_album_play_all"),
                            prominent = true,
                            enabled = canPlay && songs.isNotEmpty(),
                        )
                        Column(
                            Modifier.weight(1f).clickable(
                                enabled = canPlay && songs.isNotEmpty(),
                                role = Role.Button,
                            ) { start(false) }.padding(horizontal = 14.dp),
                        ) {
                            Text("播放全部", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    query.isNotBlank() -> "找到 ${songs.size} 首 / 已加载 ${detail?.songs?.size ?: 0} 首"
                                    detail == null -> "正在加载专辑"
                                    else -> "${detail.songs.size} 首 · ${qqAlbumDurationLabel(detail.songs)}"
                                },
                                Modifier.padding(top = 3.dp),
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f),
                            )
                        }
                    }
                }
                if (state.loading && detail == null) item("loading") {
                    Box(Modifier.fillMaxWidth().padding(20.dp)) { LoadingSongList(5) }
                }
                state.error?.let { message ->
                    item("error") {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(message)
                            MusicTextAction("重试专辑", vm::reload, enabled = !state.loading)
                        }
                    }
                }
                state.actionMessage?.let { message ->
                    item("action_message") { SettingsHint(message) }
                }
                state.actionError?.let { message ->
                    item("action_error") { SettingsHint(message, error = true) }
                }
                if (detail != null && !state.loading && songs.isEmpty() && state.error == null) item("empty") {
                    Text(
                        if (query.isNotBlank()) "没有找到匹配的歌曲" else "这张专辑还没有可播放歌曲",
                        Modifier.fillMaxWidth().padding(24.dp),
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = .55f),
                    )
                }
                itemsIndexed(songs, key = { _, song -> "qq_album_song_${song.id}" }) { index, song ->
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        PlaylistTrack(
                            song = song,
                            selected = current?.let {
                                it.id == song.id && it.musicSource == MusicSource.QQ_MUSIC
                            } == true,
                            liked = song.id in qqLikes,
                            play = { play(songs, index) },
                            next = { vm.container.playerController.add(song, true) },
                            enqueue = { vm.container.playerController.add(song) },
                        ) {
                            QqSongMenuActions(vm.container, library, song, navigate, menuScope)
                        }
                    }
                }
                if (state.songsLoading) item("songs_loading") { LoadingSongList(3) }
                state.songsError?.let { message ->
                    item("songs_error") {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(message, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            MusicTextAction("重试加载更多", vm::loadMoreSongs, enabled = !state.songsLoading)
                        }
                    }
                }
                if (detail?.hasMoreSongs == true && query.isBlank() &&
                    state.songsError == null && !state.songsLoading) item("songs_more") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        if (detail.songs.size >= 300) {
                            Text(
                                "QQ 音乐专辑接口每批最多读取 300 首",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        MusicTextAction(
                            "加载更多歌曲",
                            vm::loadMoreSongs,
                            Modifier.fillMaxWidth().testTag("qq_album_more_songs"),
                            enabled = !state.songsLoading,
                        )
                    }
                } else if (detail != null && query.isBlank() &&
                    detail.totalSongs > detail.songs.size && !state.songsLoading) {
                    item("songs_limit") {
                        SettingsHint("接口未返回更多歌曲，最多可读取 ${detail.songs.size} 首")
                    }
                }
            }
        }
    }
    if (menuOpen) MusicChoiceDialog("专辑操作", onDismiss = { menuOpen = false }) {
        MusicDestinationRow("刷新专辑", {
            menuOpen = false
            vm.reload()
        }, chevron = false)
        MusicDestinationRow("重新检查收藏状态", {
            menuOpen = false
            if (account.loggedIn) vm.reloadSubscription(explicit = true) else navigate("qq/account")
        }, enabled = account.loggedIn && !state.subscriptionLoading, chevron = false)
        if (account.loggedIn) {
            MusicDestinationRow(if (state.subscribed == true) "取消收藏专辑" else "收藏专辑", {
                menuOpen = false
                vm.setSubscribed(state.subscribed != true)
            }, enabled = !state.writeLoading && !state.subscriptionLoading, chevron = false)
        }
        MusicDestinationRow("专辑信息", {
            menuOpen = false
            infoOpen = true
        }, enabled = detail != null, chevron = false)
        detail?.singerMids?.firstOrNull()?.takeIf(String::isNotBlank)?.let { mid ->
            MusicDestinationRow("查看歌手", {
                menuOpen = false
                navigate(qqRoute("artist", mid))
            }, chevron = false)
        }
    }
    if (infoOpen) MusicDialog("专辑信息", { infoOpen = false }) {
        Text(detail?.title ?: "QQ 专辑", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        detail?.singerNames?.takeIf(String::isNotBlank)?.let { Text(it, fontSize = 13.sp) }
        qqAlbumMeta(detail).takeIf(String::isNotBlank)?.let { Text(it, fontSize = 13.sp) }
        Text(
            detail?.description?.takeIf(String::isNotBlank)?.let(::readableText) ?: "暂无专辑简介",
            Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
            fontSize = 14.sp,
            lineHeight = 22.sp,
        )
        MusicDialogButton("关闭", onClick = { infoOpen = false })
    }
}

@Composable
private fun QqAlbumHero(
    detail: QqAlbumDetail?,
    accountLoggedIn: Boolean,
    subscriptionLabel: String,
    subscriptionEnabled: Boolean,
    canPlay: Boolean,
    songs: List<Song>,
    onSubscription: () -> Unit,
    onShuffle: () -> Unit,
    onInfo: () -> Unit,
    onArtist: (String) -> Unit,
    onDescription: () -> Unit,
    onHeight: (Int) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().onSizeChanged { onHeight(it.height) }
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp)
            .testTag("qq_album_hero"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            MusicCover(
                detail?.cover.orEmpty(),
                Modifier.size(128.dp).clip(RoundedCornerShape(16.dp)).testTag("qq_album_cover"),
                420,
                cornerRadius = 16.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    detail?.title?.ifBlank { "QQ 专辑" } ?: "正在加载专辑",
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                detail?.singerNames?.takeIf(String::isNotBlank)?.let { names ->
                    val mid = detail.singerMids.firstOrNull().orEmpty()
                    Text(
                        names,
                        Modifier.clickable(enabled = mid.isNotBlank(), role = Role.Button) { onArtist(mid) },
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = .82f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                qqAlbumMeta(detail).takeIf(String::isNotBlank)?.let {
                    Text(it, fontSize = 12.sp, color = Color.White.copy(alpha = .68f),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                detail?.description?.takeIf(String::isNotBlank)?.let {
                    Text(
                        readableText(it),
                        Modifier.clickable(role = Role.Button, onClick = onDescription),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = .7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassActionButton(
                subscriptionLabel,
                onSubscription,
                Modifier.weight(1f).testTag("qq_album_subscribe"),
                enabled = subscriptionEnabled || !accountLoggedIn,
            )
            GlassActionButton(
                "随机播放",
                onShuffle,
                Modifier.weight(1f).testTag("qq_album_shuffle"),
                primary = true,
                enabled = canPlay && songs.isNotEmpty(),
            )
            GlassActionButton(
                "专辑信息",
                onInfo,
                Modifier.weight(1f).testTag("qq_album_info"),
                enabled = detail != null,
            )
        }
    }
}

private fun qqAlbumMeta(detail: QqAlbumDetail?): String = detail?.let {
    listOf(it.publishDate, it.company, it.language, it.albumType)
        .map(String::trim)
        .filter(String::isNotBlank)
        .joinToString(" · ")
}.orEmpty()

private fun qqAlbumDurationLabel(songs: List<Song>): String {
    if (songs.isEmpty() || songs.any { it.durationMs <= 0 }) return "专辑歌曲"
    val minutes = songs.sumOf(Song::durationMs) / 60_000
    return if (minutes >= 60) "${minutes / 60}小时${minutes % 60}分钟"
    else if (minutes > 0) "${minutes}分钟" else "不足1分钟"
}

// Navigation adapted from XBlocker MainActivity / SukiSU-Ultra v4.1.3 (0ca744a).
// SPDX-License-Identifier: GPL-3.0-only.
package io.github.currencortex.music.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import io.github.currencortex.music.core.media.PlayerMode
import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.ui.component.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.feature.about.AboutScreen
import io.github.currencortex.music.feature.about.LegalDocument
import io.github.currencortex.music.feature.about.LegalDocumentScreen
import io.github.currencortex.music.feature.about.MemberFocus
import io.github.currencortex.music.feature.about.MemberDetailDialog
import io.github.currencortex.music.feature.update.UpdateDialog
import io.github.currencortex.music.feature.home.HomeScreen
import io.github.currencortex.music.feature.home.MusicHomeScreen
import io.github.currencortex.music.feature.logs.LogExportDialog
import io.github.currencortex.music.feature.settings.AppearanceScreen
import io.github.currencortex.music.feature.settings.ScaleDialog
import io.github.currencortex.music.feature.settings.SettingsScreen
import io.github.currencortex.music.feature.settings.SettingsViewModel
import io.github.currencortex.music.feature.settings.UpdateSettingsViewModel
import io.github.currencortex.music.ui.component.PlainFloatingBar
import io.github.currencortex.music.ui.component.StandardNavigationBar
import io.github.currencortex.music.ui.component.HighApiFloatingNavigation
import io.github.currencortex.music.ui.theme.LeiTheme
import io.github.currencortex.music.ui.util.viewModelFactory
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import io.github.currencortex.music.feature.search.*
import io.github.currencortex.music.feature.auth.*
import io.github.currencortex.music.feature.player.*
import io.github.currencortex.music.feature.settings.MusicSettingsViewModel
import io.github.currencortex.music.feature.settings.MusicSettingsScreen
import io.github.currencortex.music.ui.component.MusicDialog
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.feature.library.*
import io.github.currencortex.music.data.song.Song
import java.net.URLDecoder
import io.github.currencortex.music.feature.profile.*
import io.github.currencortex.music.feature.binding.*
import io.github.currencortex.music.feature.room.*

private const val ROOT = 0
private const val APPEARANCE = 1
private const val ABOUT = 2
private const val SETTINGS = 20
private const val NETWORK = 21
private const val PLAYER = 22
private fun LegalDocument.route() = 3 + ordinal

@Composable
fun CurrentMusicApp(container: AppContainer) {
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val updateSettings by container.updateSettings.state.collectAsStateWithLifecycle()
    val settingsVm: SettingsViewModel = viewModel(factory = viewModelFactory { SettingsViewModel(container.settings) })
    val updateVm: UpdateSettingsViewModel = viewModel(factory = viewModelFactory { UpdateSettingsViewModel(container.updateSettings) })

    LaunchedEffect(updateSettings.autoCheckOnLaunch) {
        if (updateSettings.autoCheckOnLaunch) container.updates.checkOnLaunch()
    }

    val searchVm: SearchViewModel = viewModel(factory = viewModelFactory { SearchViewModel(container) })
    val authVm: AuthViewModel = viewModel(factory = viewModelFactory { AuthViewModel(container) })
    val musicSettingsVm: MusicSettingsViewModel = viewModel(factory = viewModelFactory { MusicSettingsViewModel(container) })
    val playerVm: PlayerViewModel = viewModel(factory = viewModelFactory { PlayerViewModel(container) })
    val libraryVm: LibraryViewModel = viewModel(factory = viewModelFactory { LibraryViewModel(container) })
    val profileVm: ProfileViewModel = viewModel(key = "my-profile", factory = viewModelFactory { ProfileViewModel(container) })
    val discoverVm: DiscoverViewModel = viewModel(factory = viewModelFactory { DiscoverViewModel(container) })
    val profileDialog by profileVm.dialog.collectAsStateWithLifecycle()
    val profileMessage by profileVm.message.collectAsStateWithLifecycle()
    val account by container.accountRepository.state.collectAsStateWithLifecycle()
    val sessionRevision by container.accountRepository.sessionRevision.collectAsStateWithLifecycle()
    LaunchedEffect(sessionRevision) { profileVm.dialog.value = null; profileVm.avatar.value = null; profileVm.message.value = null }
    val libraryMessage by libraryVm.message.collectAsStateWithLifecycle()
    val libraryDialogSong by libraryVm.selectedSong.collectAsStateWithLifecycle()
    // Position and loading updates belong to the player, not the entire navigation tree.
    val playerState by playerVm.navigation.collectAsStateWithLifecycle()
    val currentSong by playerVm.currentSong.collectAsStateWithLifecycle()
    val roomLive by container.roomSession.state.collectAsStateWithLifecycle()
    val tabsState = rememberSaveableStateHolder()
    LeiTheme(settings) {
        var selected by rememberSaveable { mutableIntStateOf(0) }
        var startupRouted by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(container) {
            if (!startupRouted) {
                container.sessionRestored.await()
                selected = if (container.accountRepository.state.value.account != null) 0 else 3
                startupRouted = true
            }
        }
        var backStack by rememberSaveable { mutableStateOf(listOf(ROOT.toString())) }
        var songMenu by remember { mutableStateOf<SongMenu?>(null) }
        var roomPending by remember { mutableStateOf(setOf<Long>()) }
        var miniHeight by remember { mutableStateOf(72.dp) }
        val density = LocalDensity.current
        val rootPage = backStack.last() == ROOT.toString()
        val keyboardOpen = WindowInsets.ime.getBottom(density) > 0
        val miniAvailable = !keyboardOpen && (currentSong != null || playerState.mode == PlayerMode.ROOM)
        val showMini = miniAvailable && backStack.last() !in setOf(PLAYER.toString(), "lib/video")
        PreloadMusicCovers(listOf(currentSong?.cover.orEmpty()), 800)
        LaunchedEffect(sessionRevision) { songMenu = null; roomPending = emptySet() }
        var roomDialogOpen by remember { mutableStateOf(false) }
        var playerDialogOpen by remember { mutableStateOf(false) }
        var miniQueueOpen by rememberSaveable { mutableStateOf(false) }
        var castDialogOpen by remember { mutableStateOf(false) }
        fun navigateBack() {
            if (roomDialogOpen || castDialogOpen || playerDialogOpen || miniQueueOpen || songMenu != null) return
            if (backStack.size > 1) {
                if (backStack.last() == "lib/video") container.playerController.closeVideo()
                backStack = backStack.dropLast(1)
            }
        }
        fun navigateTo(route: Int) { if (backStack.last() != route.toString()) backStack = backStack + route.toString() }
        fun navigateLibrary(route: String) { if (backStack.last() != route) backStack = backStack + route }
        var showLogs by rememberSaveable { mutableStateOf(false) }
        var showScale by rememberSaveable { mutableStateOf(false) }
        var memberFocus by remember { mutableStateOf<MemberFocus?>(null) }
        var shownMember by remember { mutableStateOf<MemberFocus?>(null) }
        LaunchedEffect(memberFocus) { memberFocus?.let { shownMember = it } }
        val updateDialogVisible by container.updates.dialogVisible.collectAsStateWithLifecycle()
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val context = androidx.compose.ui.platform.LocalContext.current
        val openUpdates: () -> Unit = { scope.launch { container.updates.present() } }
        var pendingPlay by remember { mutableStateOf<(() -> Unit)?>(null) }
        var explained by rememberSaveable { mutableStateOf(false) }
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            val action = pendingPlay
            pendingPlay = null
            action?.invoke()
        }
        fun playWithPermission(action: () -> Unit) {
            if (Build.VERSION.SDK_INT >= 33 && !explained &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                pendingPlay = action
            else action()
        }
        fun requestSong(song: Song) {
            if (!container.roomSession.active || song.id in roomPending) return
            val revision = container.accountRepository.sessionRevision.value
            val roomId = roomLive.detail?.room?.id
            roomPending = roomPending + song.id
            scope.launch {
                try {
                    val result = container.roomSession.submit(song)
                    if (revision == container.accountRepository.sessionRevision.value && roomId == container.roomSession.state.value.detail?.room?.id) {
                        libraryVm.message.value = when (result) {
                            is AppResult.Success -> "已提交点歌：${song.name}，可返回房间查看队列"
                            is AppResult.Failure -> "点歌失败：${result.kind.message}"
                        }
                    }
                } finally { if (revision == container.accountRepository.sessionRevision.value) roomPending = roomPending - song.id }
            }
        }
        LaunchedEffect(roomLive.detail?.room?.id, playerState.mode) {
            if (backStack.last() == "room/search" && !container.roomSession.active) navigateBack()
        }
        val roomRequest = if (container.roomSession.active && roomLive.detail != null)
            RoomSongRequest(::requestSong) { it in roomPending } else null
        val predictiveBack = settings.predictiveBack && Build.VERSION.SDK_INT >= 34
        val labels = listOf("首页", "发现", "搜索", "我的")
        val icons = listOf(Icons.Default.Home, Icons.Default.Star, Icons.Default.Search, Icons.Default.Person)

        top.yukonga.miuix.kmp.basic.Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MiuixTheme.colorScheme.background) {
        CompositionLocalProvider(LocalSongMenu provides { songMenu = it }, LocalRoomSongRequest provides roomRequest) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val wideLayout = maxWidth >= 700.dp
        val rootWide = rootPage && wideLayout
        // Reserve page space once. Animate only the overlay's GPU translation, never the page height.
        val navigationSpace = if (rootPage && !rootWide && !keyboardOpen) 92.dp else 0.dp
        val miniLift = animateDpAsState(navigationSpace, tween(180), label = "mini player lift")
        // A scene's insets must not depend on which route is currently on top. Predictive back
        // renders both scenes before committing; changing the shared viewport makes them jump.
        val rootFloating = settings.floatingBar && !wideLayout && !keyboardOpen
        val rootTabSpace = if (!wideLayout && !keyboardOpen && !settings.floatingBar) 92.dp else 0.dp
        val sceneInsets = remember(miniAvailable, miniHeight) {
            NavEntryDecorator<String> { entry ->
                val miniSpace = if (miniAvailable && entry.contentKey !in setOf(ROOT.toString(), PLAYER.toString(), "lib/video")) miniHeight else 0.dp
                // Each moving scene must cover the outgoing page and its dim scrim, including
                // its inset areas. A background on NavDisplay alone sits behind both scenes.
                Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)
                    .testTag("music_scene_${entry.contentKey}").padding(bottom = miniSpace)) { entry.Content() }
            }
        }
        val page: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.weight(1f).fillMaxSize().testTag("music_navigation").background(MiuixTheme.colorScheme.background),
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), sceneInsets),
            onBack = ::navigateBack,
            entryProvider = entryProvider {
                entry(ROOT.toString()) {
                    Box(Modifier.fillMaxSize().statusBarsPadding().padding(start = if (wideLayout) 104.dp else 0.dp,
                        bottom = if (rootFloating) 0.dp else rootTabSpace + if (miniAvailable) miniHeight else 0.dp)) {
                        CompositionLocalProvider(LocalMusicBottomInset provides if (rootFloating) 92.dp + if (miniAvailable) miniHeight else 0.dp else 0.dp) {
                        tabsState.SaveableStateProvider(selected) {
                            when (selected) {
                                0 -> MusicHomeScreen(libraryVm, onSearch = { selected = 2 }, onSettings = { navigateTo(SETTINGS) },
                                    navigate = ::navigateLibrary, play = { songs, index -> playWithPermission { container.playerController.playList(songs, index) } })
                                1 -> DiscoverScreen(discoverVm, ::navigateLibrary)
                                2 -> SearchScreen(searchVm, container.playerController, actions = { LibrarySongActions(libraryVm, it, ::navigateLibrary) },
                                    roomName = roomLive.detail?.room?.name, onBack = if (roomLive.detail != null) ({ navigateLibrary("room/list") }) else null) { songs, index ->
                                    playWithPermission { container.playerController.playList(songs, index) }
                                }
                                3 -> MeScreen(profileVm, authVm, ::navigateLibrary, { navigateTo(SETTINGS) },
                                    play = { songs, index -> playWithPermission { container.playerController.playList(songs, index) } })
                            }
                        }
                        }
                    }
                }
                entry(SETTINGS.toString()) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                        TextButton("返回", onClick = ::navigateBack)
                        Box(Modifier.weight(1f)) { SettingsScreen(updateViewModel = updateVm,
                            onAppearance = { navigateTo(APPEARANCE) }, onNetwork = { navigateTo(NETWORK) },
                            onLogs = { showLogs = true }, onAbout = { navigateTo(ABOUT) }, onUpdates = openUpdates) }
                    }
                }
                entry(NETWORK.toString()) { MusicSettingsScreen(musicSettingsVm, ::navigateBack) }
                entry(PLAYER.toString()) { PlayerScreen(playerVm, ::navigateBack, { playWithPermission { container.playerController.toggle() } },
                    actions = { song -> LibrarySongActions(libraryVm, song, ::navigateLibrary) },
                    onCast = { navigateLibrary("cast/devices") }, onRoom = { navigateLibrary("room/list") }, onDialogActive = { playerDialogOpen = it }) }
                entry("cast/devices") {
                    val vm: io.github.currencortex.music.feature.cast.CastViewModel = viewModel(factory = viewModelFactory { io.github.currencortex.music.feature.cast.CastViewModel(container) })
                    io.github.currencortex.music.feature.cast.CastScreen(vm, ::navigateBack, { castDialogOpen = it })
                }
                entry("room/search") {
                    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                        SearchScreen(searchVm, container.playerController, actions = { LibrarySongActions(libraryVm, it, ::navigateLibrary) },
                            roomName = roomLive.detail?.room?.name.orEmpty(), onBack = ::navigateBack) { songs, index -> requestSong(songs[index]) }
                    }
                }
                entry("room/list") {
                    val vm: RoomViewModel = viewModel(factory = viewModelFactory { RoomViewModel(container) })
                    RoomScreen(vm, ::navigateBack, { navigateLibrary("room/search") }, { navigateTo(PLAYER) }, { roomDialogOpen = it })
                }
                entry(APPEARANCE.toString()) {
                    Box(Modifier.fillMaxSize().navigationBarsPadding()) {
                        AppearanceScreen(settingsVm, onBack = ::navigateBack, onOpenScale = { showScale = true })
                    }
                }
                entry(ABOUT.toString()) {
                    AboutScreen(onBack = ::navigateBack, enableBlur = settings.blur,
                        onOpenDocument = { navigateTo(it.route()) },
                        onOpenMember = { member, group -> memberFocus = MemberFocus(member, group) })
                }
                LegalDocument.entries.forEach { document ->
                    entry(document.route().toString()) {
                        Box(Modifier.fillMaxSize().navigationBarsPadding()) {
                            LegalDocumentScreen(document, onBack = ::navigateBack,
                                onOpenDocument = { navigateTo(it.route()) })
                        }
                    }
                }
                backStack.filter { it.startsWith("user/") }.distinct().forEach { route ->
                    entry(route) {
                        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                            when {
                                route == "user/security" -> AccountSecurityScreen(profileVm, ::navigateBack)
                                route == "user/accounts" -> AccountScreen(container, authVm, ::navigateBack) {
                                    authVm.begin(); navigateLibrary("user/login")
                                }
                                route == "user/login" -> Column {
                                    TextButton("返回", onClick = ::navigateBack)
                                    LoginScreen(authVm, addingAccount = true, onDone = ::navigateBack)
                                }
                                route == "user/decorations" -> {
                                    val vm: DecorationViewModel = viewModel(key = route, factory = viewModelFactory { DecorationViewModel(container) })
                                    DecorationScreen(vm, ::navigateBack)
                                }
                                route == "user/binding" -> {
                                    val vm: BindingViewModel = viewModel(key = route, factory = viewModelFactory { BindingViewModel(container) })
                                    BindingScreen(vm, ::navigateBack)
                                }
                                route.startsWith("user/profile/") -> {
                                    val id = route.substringAfterLast('/').toLongOrNull() ?: 0L
                                    val vm: ProfileViewModel = if (id == account.account?.id) profileVm else viewModel(key = route,
                                        factory = viewModelFactory { ProfileViewModel(container, id) })
                                    ProfileScreen(vm, ::navigateLibrary, { songs, index -> playWithPermission { container.playerController.playList(songs, index) } }, onBack = ::navigateBack)
                                }
                            }
                        }
                    }
                }
                backStack.filter { it.startsWith("lib/") }.distinct().forEach { route ->
                    entry(route) {
                        when {
                            route == "lib/playlists" -> PlaylistIndexScreen(libraryVm, ::navigateBack, ::navigateLibrary)
                            route == "lib/video" -> if (currentSong?.video == true) io.github.currencortex.music.feature.mv.MvPlayerScreen(container, ::navigateBack)
                                else PlayerScreen(playerVm, ::navigateBack, { playWithPermission { container.playerController.toggle() } })
                            route.startsWith("lib/browse/") -> {
                                val parts = route.split('/')
                                val catalogVm: CatalogViewModel = viewModel(key = route, factory = viewModelFactory {
                                    CatalogViewModel(container, parts[2] == "album", URLDecoder.decode(parts.getOrElse(3) { "" }, "UTF-8"))
                                })
                                CatalogScreen(catalogVm, ::navigateBack, ::navigateLibrary)
                            }
                            else -> {
                                val detailVm: LibraryDetailViewModel = viewModel(key = route, factory = viewModelFactory { LibraryDetailViewModel(container, route) })
                                LibraryDetailScreen(detailVm, libraryVm, ::navigateBack, ::navigateLibrary,
                                    play = { songs, index -> playWithPermission { container.playerController.playList(songs, index) } },
                                    playMv = { mv -> playWithPermission {
                                        container.playerController.playVideo(Song(-mv.id, mv.name, mv.artistName, cover = mv.cover,
                                            durationMs = mv.duration, mv = mv.id, video = true))
                                        navigateLibrary("lib/video")
                                    } }, deleted = ::navigateBack)
                            }
                        }
                    }
                }
            },
        )
        }
        }
        }
        val miniOverlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {
        if (showMini) MiniPlayer(playerVm, { if (currentSong?.video == true) navigateLibrary("lib/video") else navigateTo(PLAYER) },
            { playWithPermission { container.playerController.toggle() } },
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().graphicsLayer { translationY = -miniLift.value.toPx() }
                .padding(start = if (rootWide) 104.dp else 0.dp)
                .onSizeChanged { miniHeight = with(density) { it.height.toDp() } }.padding(horizontal = 12.dp, vertical = 8.dp),
            onNext = { container.playerController.next(container.playerController.state.value.showPause) },
            onPrevious = { container.playerController.previous(container.playerController.state.value.showPause) },
            onQueue = { if (playerState.mode == PlayerMode.ROOM) navigateLibrary("room/list") else miniQueueOpen = true })
        if (rootPage && !rootWide && !keyboardOpen && !settings.floatingBar) Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().widthIn(max = 480.dp)) {
            StandardNavigationBar(selected, labels, icons) { selected = it }
        }
        }
        if (settings.blur && Build.VERSION.SDK_INT >= 33 && LocalView.current.isHardwareAccelerated) {
            HighApiFloatingNavigation(selected, labels, icons, { selected = it }, settings.blur, settings.liquidGlass,
                visible = settings.floatingBar && rootPage && !rootWide && !keyboardOpen, content = page, overlay = miniOverlay)
        } else {
            Box(Modifier.fillMaxSize()) {
                page()
                miniOverlay()
                if (rootPage && !rootWide && !keyboardOpen && settings.floatingBar) Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(horizontal = if (settings.floatingBar) 26.dp else 0.dp, vertical = if (settings.floatingBar) 12.dp else 0.dp).widthIn(max = 480.dp)) {
                    if (settings.floatingBar) PlainFloatingBar(selected, labels, icons) { selected = it }
                    else StandardNavigationBar(selected, labels, icons) { selected = it }
                }
            }
        }
        if (rootWide) Column(Modifier.width(100.dp).fillMaxHeight().statusBarsPadding().navigationBarsPadding().padding(8.dp)
            .testTag("wide_navigation"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            labels.forEachIndexed { index, label -> MusicTextAction(if (selected == index) "● $label" else label, { selected = index }) }
        }
        }
        }
        songMenu?.let { SongActionsSheet(it) { songMenu = null } }
        if (miniQueueOpen) io.github.currencortex.music.feature.player.PlaybackQueueSheet(playerVm) { miniQueueOpen = false }
        // XBlocker pattern: intercept completion when prediction is disabled. MIUIX
        // owns seeking, cancellation and settling otherwise. Popups are hosted after
        // navigation, once, and take precedence over returning to the parent page.
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            isBackEnabled = backStack.size > 1 && !predictiveBack && !showLogs &&
                pendingPlay == null && playerState.warning == null && !updateDialogVisible && !showScale && memberFocus == null && libraryDialogSong == null && profileDialog == null && profileMessage == null && !roomDialogOpen && !castDialogOpen && songMenu == null && !playerDialogOpen && !miniQueueOpen,
            onBackCompleted = ::navigateBack,
        )
        ScaleDialog(showScale, settingsVm) { showScale = false }
        MemberDetailDialog(show = memberFocus != null, focus = shownMember, onDismiss = { memberFocus = null })
        LogExportDialog(showLogs, container.logger) { showLogs = false }
        UpdateDialog(container.updates, container.updateTransfer)
        LibraryDialogs(libraryVm)
        ProfileDialogs(profileVm, authVm)
        if (profileMessage != null) MusicDialog("账号操作", onDismiss = { profileVm.message.value = null }) {
            Text(profileMessage.orEmpty()); TextButton("关闭", onClick = { profileVm.message.value = null })
        }
        val playbackMessage = libraryMessage ?: playerState.error
        if (playbackMessage != null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            androidx.compose.foundation.layout.Row(Modifier.imePadding().navigationBarsPadding().padding(12.dp)
                .padding(bottom = (if (rootPage && !keyboardOpen) 92.dp else 0.dp) + if (showMini) miniHeight else 0.dp)
                .background(MiuixTheme.colorScheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(playbackMessage.orEmpty(), Modifier.weight(1f), fontSize = 13.sp); MusicTextAction("关闭", { libraryVm.message.value = null; container.playerController.dismissError() })
            }
        }
        if (pendingPlay != null) MusicDialog("后台播放通知", onDismiss = {
            pendingPlay = null
        }) {
            Text("允许通知后，可从通知栏控制播放；锁屏和蓝牙媒体控制也由播放服务提供。")
            TextButton("允许通知并播放", onClick = {
                explained = true
                if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            })
            TextButton("继续播放", onClick = { explained = true; val action = pendingPlay; pendingPlay = null; action?.invoke() })
        }
        if (playerState.warning != null) MusicDialog("高规格音频", onDismiss = {
            container.playerController.pause()
            container.playerController.state.value = container.playerController.state.value.copy(warning = null)
        }) {
            Text("当前音频为高规格音频，部分设备可能出现断音、爆音或兼容问题。")
            TextButton("继续播放", onClick = { container.playerController.acceptHighSpec() })
            TextButton("切换无损", onClick = { playerVm.quality(AudioQuality.LOSSLESS) })
            TextButton("以后不提示", onClick = { playerVm.suppressWarning() })
        }
        }
    }
}

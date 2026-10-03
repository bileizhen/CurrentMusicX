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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
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
    val playerState by playerVm.state.collectAsStateWithLifecycle()
    val queue by playerVm.queue.collectAsStateWithLifecycle()
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
        var backStack by rememberSaveable { mutableStateOf(listOf(ROOT)) }
        fun navigateBack() { if (backStack.size > 1) backStack = backStack.dropLast(1) }
        fun navigateTo(route: Int) { if (backStack.last() != route) backStack = backStack + route }
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
        val predictiveBack = settings.predictiveBack && Build.VERSION.SDK_INT >= 34
        val labels = listOf("首页", "发现", "搜索", "我的")
        val icons = listOf(Icons.Default.Home, Icons.Default.Star, Icons.Default.Search, Icons.Default.Person)

        top.yukonga.miuix.kmp.basic.Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MiuixTheme.colorScheme.background) {
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background),
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            onBack = ::navigateBack,
            entryProvider = entryProvider {
                entry(ROOT) {
                    Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
                        val page: @Composable () -> Unit = {
                            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 92.dp).statusBarsPadding()) {
                                Box(Modifier.weight(1f)) {
                                    tabsState.SaveableStateProvider(selected) {
                                        when (selected) {
                                            0 -> MusicHomeScreen(container, onSearch = { selected = 2 }, onSettings = { navigateTo(SETTINGS) })
                                            1 -> Column(Modifier.padding(24.dp)) { Text("发现"); Text("当前版本支持搜索与音乐播放"); TextButton("搜索音乐", onClick = { selected = 2 }) }
                                            2 -> SearchScreen(searchVm, container.playerController) { songs, index ->
                                                playWithPermission { container.playerController.playList(songs, index) }
                                            }
                                            3 -> Column {
                                                TextButton("设置", onClick = { navigateTo(SETTINGS) })
                                                LoginScreen(authVm)
                                            }
                                        }
                                    }
                                }
                                MiniPlayer(playerVm, { navigateTo(PLAYER) }, { playWithPermission { container.playerController.toggle() } },
                                    Modifier.padding(horizontal = 12.dp))
                            }
                        }
                        if (settings.floatingBar && settings.blur && Build.VERSION.SDK_INT >= 33 && LocalView.current.isHardwareAccelerated) {
                            HighApiFloatingNavigation(
                                selectedIndex = selected, labels = labels, icons = icons, onSelect = { selected = it },
                                blur = settings.blur, glass = settings.liquidGlass, visible = true,
                                content = page,
                            )
                        } else {
                            page()
                            if (settings.floatingBar) {
                                Box(
                                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                                        .padding(horizontal = 26.dp, vertical = 12.dp).widthIn(max = 480.dp),
                                ) {
                                    PlainFloatingBar(selected, labels, icons) { selected = it }
                                }
                            } else {
                                Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
                                    StandardNavigationBar(selected, labels, icons) { selected = it }
                                }
                            }
                        }
                    }
                }
                entry(SETTINGS) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                        TextButton("返回", onClick = ::navigateBack, modifier = Modifier.then(androidx.compose.ui.Modifier))
                        TextButton("网络与播放", onClick = { navigateTo(NETWORK) })
                        Box(Modifier.weight(1f)) { SettingsScreen(updateViewModel = updateVm,
                            onAppearance = { navigateTo(APPEARANCE) }, onLogs = { showLogs = true },
                            onAbout = { navigateTo(ABOUT) }, onUpdates = openUpdates) }
                    }
                }
                entry(NETWORK) { MusicSettingsScreen(musicSettingsVm, ::navigateBack) }
                entry(PLAYER) { PlayerScreen(playerVm, ::navigateBack, { playWithPermission { container.playerController.toggle() } }) }
                entry(APPEARANCE) {
                    Box(Modifier.fillMaxSize().navigationBarsPadding()) {
                        AppearanceScreen(settingsVm, onBack = ::navigateBack, onOpenScale = { showScale = true })
                    }
                }
                entry(ABOUT) {
                    AboutScreen(onBack = ::navigateBack, enableBlur = settings.blur,
                        onOpenDocument = { navigateTo(it.route()) },
                        onOpenMember = { member, group -> memberFocus = MemberFocus(member, group) })
                }
                LegalDocument.entries.forEach { document ->
                    entry(document.route()) {
                        Box(Modifier.fillMaxSize().navigationBarsPadding()) {
                            LegalDocumentScreen(document, onBack = ::navigateBack,
                                onOpenDocument = { navigateTo(it.route()) })
                        }
                    }
                }
            },
        )
        // XBlocker pattern: intercept completion when prediction is disabled. MIUIX
        // owns seeking, cancellation and settling otherwise. Popups are hosted after
        // navigation, once, and take precedence over returning to the parent page.
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            isBackEnabled = backStack.size > 1 && !predictiveBack && !showLogs &&
                pendingPlay == null && playerState.warning == null && !updateDialogVisible && !showScale && memberFocus == null,
            onBackCompleted = ::navigateBack,
        )
        ScaleDialog(showScale, settingsVm) { showScale = false }
        MemberDetailDialog(show = memberFocus != null, focus = shownMember, onDismiss = { memberFocus = null })
        LogExportDialog(showLogs, container.logger) { showLogs = false }
        UpdateDialog(container.updates, container.updateTransfer)
        if (pendingPlay != null) MusicDialog("后台播放通知", onDismiss = {
            explained = true; val action = pendingPlay; pendingPlay = null; action?.invoke()
        }) {
            Text("允许通知后，可从通知栏控制播放；锁屏和蓝牙媒体控制也由播放服务提供。")
            TextButton("允许通知并播放", onClick = {
                explained = true
                if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            })
            TextButton("继续播放", onClick = { explained = true; val action = pendingPlay; pendingPlay = null; action?.invoke() })
        }
        if (playerState.warning != null) MusicDialog("高规格音频", onDismiss = { container.playerController.state.value = playerState.copy(warning = null) }) {
            Text("当前音频为高规格音频，部分设备可能出现断音、爆音或兼容问题。")
            TextButton("继续播放", onClick = { container.playerController.acceptHighSpec() })
            TextButton("切换无损", onClick = { playerVm.quality(AudioQuality.LOSSLESS) })
            TextButton("以后不提示", onClick = { playerVm.suppressWarning() })
        }
        }
    }
}

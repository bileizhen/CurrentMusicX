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

private const val ROOT = 0
private const val APPEARANCE = 1
private const val ABOUT = 2
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

    LeiTheme(settings) {
        var selected by rememberSaveable { mutableIntStateOf(0) }
        var backStack by rememberSaveable { mutableStateOf(listOf(ROOT)) }
        fun navigateBack() { if (backStack.size > 1) backStack = backStack.dropLast(1) }
        fun navigateTo(route: Int) { if (backStack.last() != route) backStack = backStack + route }
        var showLogs by rememberSaveable { mutableStateOf(false) }
        var previewUpdates by rememberSaveable { mutableStateOf(false) }
        var showScale by rememberSaveable { mutableStateOf(false) }
        var memberFocus by remember { mutableStateOf<MemberFocus?>(null) }
        var shownMember by remember { mutableStateOf<MemberFocus?>(null) }
        LaunchedEffect(memberFocus) { memberFocus?.let { shownMember = it } }
        val updateDialogVisible by container.updates.dialogVisible.collectAsStateWithLifecycle()
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val context = androidx.compose.ui.platform.LocalContext.current
        val openUpdates: () -> Unit = {
            if (io.github.currencortex.music.BuildConfig.UPDATE_DIALOG_PREVIEW) previewUpdates = true
            else scope.launch { container.updates.present() }
        }
        val predictiveBack = settings.predictiveBack && Build.VERSION.SDK_INT >= 34
        val labels = listOf("首页", "设置")
        val icons = listOf(Icons.Default.Home, Icons.Default.Settings)

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
                            Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 92.dp).statusBarsPadding()) {
                                when (selected) {
                                    0 -> HomeScreen()
                                    1 -> SettingsScreen(
                                        updateViewModel = updateVm,
                                        onAppearance = { navigateTo(APPEARANCE) },
                                        onLogs = { showLogs = true },
                                        onAbout = { navigateTo(ABOUT) }, onUpdates = openUpdates,
                                    )
                                }
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
                !previewUpdates && !updateDialogVisible && !showScale && memberFocus == null,
            onBackCompleted = ::navigateBack,
        )
        ScaleDialog(showScale, settingsVm) { showScale = false }
        MemberDetailDialog(show = memberFocus != null, focus = shownMember, onDismiss = { memberFocus = null })
        LogExportDialog(showLogs, container.logger) { showLogs = false }
        UpdateDialog(container.updates, container.updateTransfer)
        if (previewUpdates) {
            val prerelease = updateSettings.channel == io.github.currencortex.music.core.update.UpdateChannel.PRERELEASE
            val release = io.github.currencortex.music.core.update.AppRelease(
                if (prerelease) "0.2.0-rc.1" else "0.2.0",
                "本次更新改善日常使用体验。\n\n外观与交互\n\n• 新增主题预览，支持浅色、深色和跟随系统。\n\n• 改善悬浮底栏的模糊、液态玻璃效果与返回动画。\n\n通用功能\n\n• 支持正式版与预发布版更新渠道。\n\n• 支持保存和分享脱敏日志，便于排查问题。\n\n• 可离线查看开源许可和隐私说明。",
                io.github.currencortex.music.core.config.AppMetadata.RELEASES_URL,
                null, "preview.apk", prerelease, size = 13L * 1024 * 1024)
            io.github.currencortex.music.feature.update.UpdateDialogContent(
                io.github.currencortex.music.core.update.UpdateState.Available(release),
                onDismiss = { previewUpdates = false }, onRetry = {}, onIgnore = { previewUpdates = false },
                onOpenRelease = { io.github.currencortex.music.ui.util.openExternalLink(context, it) }, preview = true)
        }
        }
    }
}

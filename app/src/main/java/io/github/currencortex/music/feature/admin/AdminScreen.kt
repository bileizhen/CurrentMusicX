package io.github.currencortex.music.feature.admin

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.currencortex.music.data.admin.*
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import kotlinx.coroutines.*
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal sealed interface AdminDestination {
    data class User(val user: AdminUser) : AdminDestination
    data class EditUser(val user: AdminUser, val action: AdminUserAction) : AdminDestination
    data class Announcement(val item: AdminAnnouncement?) : AdminDestination
}
internal sealed interface AdminConfirmation {
    data class User(val user: AdminUser, val action: AdminUserAction) : AdminConfirmation
    data class Announcement(val item: AdminAnnouncement, val action: String, val label: String) : AdminConfirmation
    data class Kick(val session: AdminSession) : AdminConfirmation
    data object Cache : AdminConfirmation
}

@Composable fun AdminScreen(vm: AdminViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    val revision by vm.container.accountRepository.sessionRevision.collectAsStateWithLifecycle()
    val settings by vm.container.musicSettings.state.collectAsStateWithLifecycle()
    val identity = AdminIdentity(vm.container.accountRepository.server, account.account?.id, revision)
    val visible = state.identity == identity && identity.server == settings.server && state.authorized && !account.loading && !account.offline &&
        account.account?.let { it.isAdmin || it.isSuper || it.isOwner } == true
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.refresh(); awaitCancellation() }
    }
    key(identity, settings.server, visible) { AdminPanel(state, visible, vm, onBack) }
}

@Composable internal fun AdminPanel(state: AdminState, visible: Boolean, actions: AdminActions, onBack: () -> Unit) {
    var destination by remember { mutableStateOf<AdminDestination?>(null) }
    var confirmation by remember { mutableStateOf<AdminConfirmation?>(null) }
    val pager = rememberPagerState(initialPage = state.tab.ordinal) { AdminTab.entries.size }
    val tabs = rememberLazyListState()
    val lists = AdminTab.entries.map { rememberLazyListState() }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun open(value: AdminDestination) { actions.clearOperationError(); destination = value; focus.clearFocus(); keyboard?.hide() }
    fun confirm(value: AdminConfirmation) { actions.clearOperationError(); confirmation = value; focus.clearFocus(); keyboard?.hide() }
    LaunchedEffect(pager.settledPage) { actions.select(AdminTab.entries[pager.settledPage]); focus.clearFocus(); keyboard?.hide() }
    LaunchedEffect(pager.targetPage) { tabs.animateScrollToItem((pager.targetPage - 1).coerceAtLeast(0)) }
    LaunchedEffect(state.query, state.sort, state.offset) { lists[AdminTab.USERS.ordinal].scrollToItem(0) }
    LaunchedEffect(state.message) { if (state.message != null) { delay(4500); actions.dismissMessage() } }
    BackHandler(state.busy || destination != null) {
        if (!state.busy) destination = (destination as? AdminDestination.EditUser)?.let { AdminDestination.User(it.user) }
    }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(destination, transitionSpec = {
            val forward = targetState != null && (initialState == null || targetState is AdminDestination.EditUser)
            (fadeIn(tween(180)) + slideInHorizontally(tween(240)) { if (forward) it / 8 else -it / 8 }) togetherWith
                fadeOut(tween(120))
        }, label = "admin_detail_navigation") { currentDestination ->
        if (currentDestination == null) MusicSubpage("管理员面板", "admin_screen", onBack = { if (!state.busy) onBack() }, actions = {
            GlassIconButton(actions::refresh, if (state.loading) "正在刷新" else "刷新", enabled = !state.loading && !state.busy,
                modifier = Modifier.testTag("admin_refresh")) { Icon(MusicIcons.Update, null) }
        }) { topInset ->
            Column(Modifier.fillMaxSize().padding(top = topInset)) {
                if (!visible) {
                    if (state.loading) AdminLoading() else AdminEmpty("无法进入管理面板", state.error ?: "请使用有管理权限的账号登录", "重新验证", actions::refresh)
                } else {
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    val tabWidth = ((maxWidth / AdminTab.entries.size).value).coerceAtLeast(50f * LocalDensity.current.fontScale).dp
                    LazyRow(state = tabs, modifier = Modifier.fillMaxWidth().semantics { selectableGroup() }.testTag("admin_tabs")) {
                        items(AdminTab.entries) { tab ->
                            val selected = pager.targetPage == tab.ordinal
                            Column(Modifier.width(tabWidth).height(48.dp).clip(RoundedCornerShape(12.dp))
                                .selectable(selected, enabled = !state.busy, role = Role.Tab) {
                                    focus.clearFocus(); keyboard?.hide(); scope.launch { pager.animateScrollToPage(tab.ordinal) }
                                }.testTag("admin_tab_${tab.name.lowercase()}"), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Text(tab.label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                }
                                Box(Modifier.width(22.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                                    .background(if (selected) MiuixTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent))
                            }
                        }
                    }
                    }
                    AnimatedVisibility(state.message != null) { AdminNotice(state.message.orEmpty(), onDismiss = actions::dismissMessage) }
                    HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth().clipToBounds().testTag("admin_pager"), userScrollEnabled = !state.busy) { index ->
                        AdminSection(AdminTab.entries[index], state, lists[index], actions, ::open, ::confirm)
                    }
                }
            }
        }
        if (visible && currentDestination != null) {
            AdminDetail(currentDestination, state, actions, ::open, ::confirm, onBack = {
                if (!state.busy) { actions.clearOperationError(); destination = null }
            })
        }
        }
        if (visible) confirmation?.let { current ->
            AdminConfirmDialog(current, state, actions, close = { if (!state.busy) { confirmation = null; actions.clearOperationError() } },
                done = { confirmation = null; destination = null })
        }
    }
}

@Composable private fun AdminSection(tab: AdminTab, state: AdminState, list: LazyListState, actions: AdminActions,
    open: (AdminDestination) -> Unit, confirm: (AdminConfirmation) -> Unit) {
    val active = state.tab == tab
    val loading = active && state.loading
    val loaded = when (tab) {
        AdminTab.OVERVIEW, AdminTab.SYSTEM -> state.overview != null
        AdminTab.USERS -> state.users != null
        AdminTab.ANNOUNCEMENTS -> state.announcements != null
        AdminTab.DEVICES -> state.sessions != null
        AdminTab.CLIENTS -> state.clients != null
    }
    val error = state.error.takeIf { active }
    Column(Modifier.fillMaxSize()) {
        if (tab == AdminTab.USERS) AdminSearch(state, actions)
        if (tab == AdminTab.ANNOUNCEMENTS) AdminSectionHeading("公告管理", "${state.announcements?.count { !it.removed } ?: 0} 条生效", "发布公告",
            enabled = !loading && !state.busy) { open(AdminDestination.Announcement(null)) }
        if (error != null && loaded) AdminNotice(error, error = true, retry = actions::refresh)
        MusicPullToRefresh(loading, actions::refresh, Modifier.weight(1f).fillMaxWidth(), enabled = active && !state.busy) {
            LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("admin_list_${tab.name.lowercase()}"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = LocalMusicBottomInset.current + 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    !loaded && error != null -> item { AdminEmpty("加载失败", error, "重试", actions::refresh) }
                    !loaded -> item { AdminLoading() }
                    else -> when (tab) {
                        AdminTab.OVERVIEW -> adminOverview(state)
                        AdminTab.USERS -> adminUsers(state, actions, open)
                        AdminTab.ANNOUNCEMENTS -> adminAnnouncements(state, open)
                        AdminTab.DEVICES -> adminDevices(state, confirm)
                        AdminTab.CLIENTS -> adminClients(state)
                        AdminTab.SYSTEM -> adminSystem(state, confirm)
                    }
                }
            }
        }
    }
}

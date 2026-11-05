package io.github.currencortex.music.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.util.collectAsPageState
import io.github.currencortex.music.ui.util.viewModelFactory
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*

@Composable fun DiscoverScreen(vm: DiscoverViewModel, navigate: (String) -> Unit) {
    DiscoverScreen(vm, rememberPagerState { 4 }, navigate)
}

@Composable fun DiscoverScreen(vm: DiscoverViewModel,
    pager: androidx.compose.foundation.pager.PagerState, navigate: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val posts: PostsViewModel = viewModel(factory = viewModelFactory { PostsViewModel(vm.container) })
    val chat: ChatViewModel = viewModel(factory = viewModelFactory { ChatViewModel(vm.container) })
    LaunchedEffect(pager.settledPage) { if (pager.settledPage == 2) posts.ensureLoaded() }
    SettingsScrollLayout(Modifier.fillMaxSize().testTag("discover_screen"),
        topBar = { SettingsRootTopBar("发现", Modifier.testTag("discover_root_toolbar")) }) { topInset ->
        // Tabs own a fixed row. In particular, Chat's reversed message list must not
        // spend scroll distance collapsing a parent header or resizing its viewport.
        Column(Modifier.fillMaxSize().padding(top = topInset)) {
            SettingsSegmentedControl(listOf("音乐", "用户", "帖子", "Chat"), pager.targetPage,
                { index -> scope.launch { pager.animateScrollToPage(index) } },
                Modifier.elasticRootComponent().padding(horizontal = 16.dp).padding(bottom = 12.dp), tag = "discover_tabs", role = Role.Tab)
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth().clipToBounds()
                .testTag("discover_pager")) { page ->
                when (page) {
                    0 -> DiscoverMusicPage(vm, navigate)
                    1 -> DiscoverUsersPage(vm, navigate, pager.settledPage == 1)
                    2 -> PostsScreen(posts, navigate)
                    3 -> ChatScreen(chat, navigate, pager.settledPage == 3 && LocalMusicPageActive.current)
                }
            }
        }
    }
}

@Composable private fun DiscoverMusicPage(vm: DiscoverViewModel, navigate: (String) -> Unit) {
    val styles by vm.styles.collectAsPageState()
    val bottom = LocalMusicBottomInset.current
    LazyColumn(Modifier.fillMaxSize().testTag("discover_music"),
        contentPadding = PaddingValues(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 20.dp + bottom),
        verticalArrangement = Arrangement.spacedBy(0.dp)) {
        rootMotionItems(skipKeys = setOf("styles")) {
        item { SettingsGroup {
            ProfileDestination("每日推荐", "今天的专属歌单", MusicIcons.Play, "discover_daily") { navigate("lib/daily") }
            SettingsDivider(52.dp)
            ProfileDestination("猜你喜欢", "沿着喜欢的旋律继续探索", MusicIcons.Heart, "discover_foryou") { navigate("lib/foryou") }
        } }
        item(key = "styles") { io.github.currencortex.music.feature.style.MusicStyleCategories(styles, vm::loadStyles, navigate, vm::loadStyleCover, listHeader = true) }
    }
    }
}

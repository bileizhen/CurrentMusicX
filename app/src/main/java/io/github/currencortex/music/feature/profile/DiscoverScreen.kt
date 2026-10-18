package io.github.currencortex.music.feature.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.util.collectAsPageState
import io.github.currencortex.music.ui.util.viewModelFactory
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun DiscoverScreen(vm: DiscoverViewModel, navigate: (String) -> Unit) {
    val pager = rememberPagerState { 4 }
    val scope = rememberCoroutineScope()
    val posts: PostsViewModel = viewModel(factory = viewModelFactory { PostsViewModel(vm.container) })
    val chat: ChatViewModel = viewModel(factory = viewModelFactory { ChatViewModel(vm.container) })
    LaunchedEffect(pager.settledPage) { if (pager.settledPage == 2) posts.ensureLoaded() }
    Column(Modifier.fillMaxSize().testTag("discover_screen")) {
        Text("发现", Modifier.fillMaxWidth().then(LocalRootTabSwipe.current).padding(start = 20.dp, top = 20.dp, bottom = 14.dp), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        DiscoverTabs(pager.targetPage,
            onTabSelected = { index -> scope.launch { pager.animateScrollToPage(index) } },
            modifier = Modifier.padding(horizontal = 20.dp).testTag("discover_tabs"))
        Spacer(Modifier.height(12.dp))
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth().testTag("discover_pager")) { page ->
            when (page) {
                0 -> DiscoverMusicPage(vm, navigate)
                1 -> DiscoverUsersPage(vm, navigate, pager.settledPage == 1)
                2 -> PostsScreen(posts, navigate)
                3 -> ChatScreen(chat, navigate, pager.settledPage == 3 && LocalMusicPageActive.current)
            }
        }
    }
}

@Composable private fun DiscoverTabs(selected: Int, onTabSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    Row(modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp))
        .background(colors.surfaceContainer).padding(3.dp).selectableGroup()) {
        listOf("音乐", "用户", "帖子", "Chat").forEachIndexed { index, title ->
            val active = index == selected
            val background by animateColorAsState(if (active) colors.primary else Color.Transparent,
                tween(180), label = "discover tab background")
            val ink by animateColorAsState(if (active) colors.onPrimary else colors.onSurface.copy(alpha = .6f),
                tween(180), label = "discover tab text")
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(11.dp))
                .background(background).selectable(active, role = Role.Tab, onClick = { onTabSelected(index) }),
                contentAlignment = Alignment.Center) {
                Text(title, fontSize = 14.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable private fun DiscoverMusicPage(vm: DiscoverViewModel, navigate: (String) -> Unit) {
    val styles by vm.styles.collectAsPageState()
    val bottom = LocalMusicBottomInset.current
    LazyColumn(Modifier.fillMaxSize().testTag("discover_music"),
        contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 20.dp + bottom),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Card(Modifier.fillMaxWidth()) {
            MusicDestinationRow("每日推荐", { navigate("lib/daily") }, summary = "今天的专属歌单")
            MusicDestinationRow("猜你喜欢", { navigate("lib/foryou") }, summary = "沿着喜欢的旋律继续探索")
        } }
        item { io.github.currencortex.music.feature.style.MusicStyleCategories(styles, vm::loadStyles, navigate, vm::loadStyleCover) }
    }
}

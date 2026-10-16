package io.github.currencortex.music.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.util.collectAsPageState
import io.github.currencortex.music.ui.util.viewModelFactory
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*

@Composable fun DiscoverScreen(vm: DiscoverViewModel, navigate: (String) -> Unit) {
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val posts: PostsViewModel = viewModel(factory = viewModelFactory { PostsViewModel(vm.container) })
    LaunchedEffect(pager.settledPage) { if (pager.settledPage == 2) posts.ensureLoaded() }
    Column(Modifier.fillMaxSize().testTag("discover_screen")) {
        Text("发现", Modifier.padding(start = 20.dp, top = 20.dp, bottom = 14.dp), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        TabRow(listOf("音乐", "用户", "帖子"), pager.targetPage,
            onTabSelected = { index -> scope.launch { pager.animateScrollToPage(index) } },
            modifier = Modifier.padding(horizontal = 20.dp).testTag("discover_tabs"), height = 44.dp)
        Spacer(Modifier.height(12.dp))
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth().testTag("discover_pager")) { page ->
            when (page) {
                0 -> DiscoverMusicPage(vm, navigate)
                1 -> DiscoverUsersPage(vm, navigate, pager.settledPage == 1)
                2 -> PostsScreen(posts, navigate)
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

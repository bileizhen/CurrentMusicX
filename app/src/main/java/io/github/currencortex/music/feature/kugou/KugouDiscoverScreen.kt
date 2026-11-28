package io.github.currencortex.music.feature.kugou

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.core.network.appResult
import io.github.currencortex.music.data.style.MusicStyle
import io.github.currencortex.music.feature.profile.ProfileDestination
import io.github.currencortex.music.feature.style.MusicStyleCategories
import io.github.currencortex.music.feature.style.StyleCatalogState
import io.github.currencortex.music.ui.component.LocalMusicBottomInset
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.SpringLazyColumn
import io.github.currencortex.music.ui.component.nativeui.SettingsDivider
import io.github.currencortex.music.ui.component.nativeui.SettingsGroup
import io.github.currencortex.music.ui.component.rootMotionItems
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * KuGou supplies the music catalog while the standard Discover shell continues to own
 * its tabs, swipe pager, community pages, and bottom insets.
 */
@Composable
fun KugouDiscoverMusicPage(container: AppContainer, navigate: (String) -> Unit) {
    val repository = container.kugou
    val account by repository.state.collectAsStateWithLifecycle()
    val bottom = LocalMusicBottomInset.current
    val scope = rememberCoroutineScope()
    val coverPermits = remember { Semaphore(3) }
    val coverJobs = remember { mutableMapOf<Long, Job>() }
    var retry by remember { mutableIntStateOf(0) }
    var catalog by remember(account.revision) { mutableStateOf(StyleCatalogState()) }

    LaunchedEffect(account.revision, retry) {
        coverJobs.values.forEach(Job::cancel)
        coverJobs.clear()
        catalog = StyleCatalogState()
        when (val result = appResult { repository.categories() }) {
            is AppResult.Success -> catalog = StyleCatalogState(
                loading = false,
                styles = result.value.mapNotNull { category ->
                    category.id.toLongOrNull()?.let { id ->
                        MusicStyle(id, category.name, category.group)
                    }
                }.distinctBy(MusicStyle::tagId),
            )
            is AppResult.Failure -> catalog = StyleCatalogState(loading = false, error = result.message)
        }
    }

    fun loadCover(tagId: Long) {
        if (catalog.covers.containsKey(tagId) || coverJobs[tagId]?.isActive == true) return
        val rev = account.revision
        coverJobs[tagId] = scope.launch {
            val result = coverPermits.withPermit {
                appResult { repository.categoryPlaylists(tagId.toString(), limit = 1) }
            }
            if (rev != container.kugou.state.value.revision) return@launch
            val cover = (result as? AppResult.Success)?.value?.playlists?.firstOrNull()?.cover.orEmpty()
            catalog = catalog.copy(covers = catalog.covers + (tagId to cover))
        }
    }

    SpringLazyColumn(
        Modifier.fillMaxSize().testTag("kugou_discover_music"),
        contentPadding = PaddingValues(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 20.dp + bottom),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        rootMotionItems(skipKeys = setOf("kugou-styles")) {
            item {
                SettingsGroup {
                    ProfileDestination("每日推荐", "今天的专属酷狗推荐", MusicIcons.Play, "kugou_discover_daily") {
                        navigate("kugou/daily")
                    }
                    SettingsDivider(52.dp)
                    ProfileDestination("新歌速递", "第一时间发现热门新歌", MusicIcons.Heart, "kugou_discover_new") {
                        navigate("kugou/new")
                    }
                    SettingsDivider(52.dp)
                    ProfileDestination("排行榜", "查看酷狗音乐热门榜单", MusicIcons.List, "kugou_discover_ranks") {
                        navigate("kugou/ranks")
                    }
                }
            }
            item(key = "kugou-styles") {
                MusicStyleCategories(
                    state = catalog,
                    retry = { retry++ },
                    navigate = navigate,
                    loadCover = ::loadCover,
                    listHeader = true,
                    routeFor = { style -> "kugou/genre/${style.tagId}" },
                )
            }
        }
    }
}

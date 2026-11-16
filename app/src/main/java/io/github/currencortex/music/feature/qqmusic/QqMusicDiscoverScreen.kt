package io.github.currencortex.music.feature.qqmusic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
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
import io.github.currencortex.music.ui.component.nativeui.SettingsDivider
import io.github.currencortex.music.ui.component.nativeui.SettingsGroup
import io.github.currencortex.music.ui.component.rootMotionItems
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * QQ supplies the music catalog while the standard Discover shell continues to own
 * its tabs, swipe pager, community pages, and bottom insets.
 */
@Composable
fun QqMusicDiscoverMusicPage(container: AppContainer, navigate: (String) -> Unit) {
    val repository = container.qqMusic
    val settings by container.musicSettings.state.collectAsStateWithLifecycle()
    val bottom = LocalMusicBottomInset.current
    val scope = rememberCoroutineScope()
    val coverPermits = remember { Semaphore(3) }
    val coverJobs = remember { mutableMapOf<Long, Job>() }
    var retry by remember { mutableIntStateOf(0) }
    var catalog by remember(settings.server) { mutableStateOf(StyleCatalogState()) }

    LaunchedEffect(settings.server, retry) {
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
        val server = settings.server
        coverJobs[tagId] = scope.launch {
            val result = coverPermits.withPermit {
                appResult { repository.categoryPlaylists(tagId.toString(), limit = 1, sortId = 3) }
            }
            if (server != container.musicSettings.state.value.server) return@launch
            val cover = (result as? AppResult.Success)?.value?.playlists?.firstOrNull()?.cover.orEmpty()
            catalog = catalog.copy(covers = catalog.covers + (tagId to cover))
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().testTag("qq_discover_music"),
        contentPadding = PaddingValues(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 20.dp + bottom),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        rootMotionItems(skipKeys = setOf("qq-styles")) {
            item {
                SettingsGroup {
                    ProfileDestination("每日推荐", "今天的专属歌单", MusicIcons.Play, "qq_discover_daily") {
                        navigate("qq/daily")
                    }
                    SettingsDivider(52.dp)
                    ProfileDestination("猜你喜欢", "沿着喜欢的旋律继续探索", MusicIcons.Heart, "qq_discover_foryou") {
                        navigate("qq/foryou")
                    }
                    SettingsDivider(52.dp)
                    ProfileDestination("排行榜", "查看 QQ 音乐热门榜单", MusicIcons.List, "qq_discover_charts") {
                        navigate("qq/charts")
                    }
                }
            }
            item(key = "qq-styles") {
                MusicStyleCategories(
                    state = catalog,
                    retry = { retry++ },
                    navigate = navigate,
                    loadCover = ::loadCover,
                    listHeader = true,
                    routeFor = { style -> "qq/genre/${style.tagId}" },
                )
            }
        }
    }
}

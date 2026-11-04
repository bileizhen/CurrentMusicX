package io.github.currencortex.music.ui.component

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.song.*
import io.github.currencortex.music.ui.component.nativeui.GlassMenuItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

internal fun qualityFileSummary(br: Long, size: Long, sr: Long): String = buildList {
    if (br > 0) add(String.format(Locale.ROOT, "%.0f kbps", br / 1000.0))
    if (sr > 0) add(String.format(Locale.ROOT, "%.1f kHz", sr / 1000.0))
    if (size > 0) add(String.format(Locale.ROOT, "%.1f MiB", size / 1048576.0))
}.joinToString(" · ").ifEmpty { "暂无文件参数" }

/** Fetch only after entering a branch; leaving it cancels the request and permits a clean retry. */
@Composable private fun <T> rememberInsightBranch(id: String, title: String, owner: Any,
    load: suspend () -> List<T>, rows: (List<T>) -> List<GlassMenuItem>): GlassMenuItem {
    val scope = rememberCoroutineScope()
    var result by remember(owner) { mutableStateOf<AppResult<List<T>>?>(null) }
    var job by remember(owner) { mutableStateOf<Job?>(null) }
    var generation by remember(owner) { mutableIntStateOf(0) }
    val currentLoad by rememberUpdatedState(load)
    fun stop() { generation++; job?.cancel(); job = null }
    fun start() {
        if (job?.isActive == true || result is AppResult.Success) return
        result = null
        val expected = ++generation
        job = scope.launch {
            val response = appResult { currentLoad() }
            if (generation == expected) result = response
        }
    }
    DisposableEffect(owner) { onDispose { stop() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { stop() }
    val children = when (val value = result) {
        null -> listOf(GlassMenuItem("${id}_loading", "正在加载…", enabled = false))
        is AppResult.Failure -> listOf(GlassMenuItem("${id}_retry", "加载失败，点击重试",
            subtitle = if (value.status == 404) "当前服务器暂不支持" else value.message,
            keepOpen = true, onClick = { start() }))
        is AppResult.Success -> if (value.value.isEmpty()) listOf(GlassMenuItem("${id}_empty", "暂无资料", enabled = false))
            else rows(value.value)
    }
    return GlassMenuItem(id, title, children = children, subMenuMinWidth = 260.dp,
        onEnter = { start() }, onExit = { stop() })
}

@Composable internal fun songInsightsMenuItems(repository: SongInsightsRepository, song: Song,
    onPlay: (Song) -> Unit): List<GlassMenuItem> {
    if (NeteaseSongActionsRepository.songId(song) == null) return emptyList()
    val owner = song to repository.session()
    val play by rememberUpdatedState(onPlay)
    val qualities = rememberInsightBranch("song_file_info", "音质文件资料", owner,
        load = { repository.qualities(song) }) { files ->
        buildList {
            add(GlassMenuItem("song_file_note", "歌曲文件资料", subtitle = "实际音质以播放结果为准", enabled = false))
            files.forEach { file ->
                add(GlassMenuItem("song_file_${file.level}", file.label.ifBlank { file.level },
                    subtitle = qualityFileSummary(file.br, file.size, file.sr), enabled = false))
                file.variants.forEachIndexed { index, variant ->
                    add(GlassMenuItem("song_file_${file.level}_$index", variant.immerseType.ifBlank { "音质变体 ${index + 1}" },
                        subtitle = qualityFileSummary(variant.br, variant.size, variant.sr), enabled = false))
                }
            }
        }
    }
    val versions = rememberInsightBranch("song_versions", "其他版本", owner,
        load = { repository.versions(song) }) { songs ->
        songs.map { version -> GlassMenuItem("song_version_${version.id}", version.name,
            subtitle = listOf(version.artists, version.album).filter(String::isNotBlank).joinToString(" · "),
            onClick = { play(version) }) }
    }
    return listOf(qualities, versions)
}

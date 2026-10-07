package io.github.currencortex.music.feature.settings

import io.github.currencortex.music.ui.component.musicScrollPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.ui.component.MusicTextAction
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.ROOT, "%.2f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.ROOT, "%.1f MB", bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> String.format(Locale.ROOT, "%.0f KB", bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}

@Composable fun StorageScreen(vm: StorageViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val usage = state.usage
    LaunchedEffect(vm) { vm.refresh() }
    val colors = MiuixTheme.colorScheme
    val hint = colors.onSurfaceVariantSummary
    // One hue stepped down: the bar reads as a single system instead of borrowing another app's palette.
    val ramp = listOf(colors.primary, colors.primary.copy(alpha = .5f), colors.primary.copy(alpha = .28f),
        colors.onSurface.copy(alpha = .14f))
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().testTag("storage_screen"),
        contentPadding = musicScrollPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("存储空间", fontSize = 28.sp) }
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("CurrentMusic 占用", fontSize = 13.sp, color = hint)
            Text(formatSize(usage.total), fontSize = 32.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("storage_total"))
            StorageBar(listOf(usage.audio, usage.data, usage.downloads, usage.essential), ramp)
            Row(Modifier.fillMaxWidth()) {
                listOf("音乐缓存" to usage.audio, "数据缓存" to usage.data,
                    "下载" to usage.downloads, "必要文件" to usage.essential).forEachIndexed { index, (label, _) ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(ramp[index]))
                        Text(label, fontSize = 11.sp, color = hint, maxLines = 1)
                    }
                }
            }
        } } }

        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("音乐缓存")
                    Text(if (usage.audioTracks > 0) "${formatSize(usage.audio)} · ${usage.audioTracks} 首"
                        else formatSize(usage.audio), fontSize = 20.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("storage_audio_size"))
                }
                MusicTextAction(if (state.busy) "正在清理…" else "清理", { vm.clearAudio() },
                    Modifier.testTag("clear_audio_storage"), enabled = !state.busy && usage.audio > 0, destructive = true)
            }
            Text("听歌时缓存的音频，断网也能重播；清理后需要重新加载。", fontSize = 12.sp, color = hint)
            Text("不设上限：缓存会一直保留，需要时在这里清理；系统空间紧张时也可能被回收。",
                fontSize = 12.sp, color = hint)
        } } }

        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("数据缓存")
                    Text(formatSize(usage.data), fontSize = 20.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("storage_data_size"))
                }
                MusicTextAction(if (state.busy) "正在清理…" else "清理", { vm.clearData() },
                    Modifier.testTag("clear_data_storage"), enabled = !state.busy && usage.data > 0, destructive = true)
            }
            Text("封面、歌词与页面加载产生的临时数据；清理后不影响账号与已下载的音乐。",
                fontSize = 12.sp, color = hint)
        } } }

        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("下载的资源")
            Text(if (usage.downloadCount > 0) "${formatSize(usage.downloads)} · ${usage.downloadCount} 个"
                else formatSize(usage.downloads), fontSize = 20.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("storage_download_size"))
            Text("统计系统「下载/CurrentMusic」目录；可在歌曲操作的下载窗口或文件管理器中删除。",
                fontSize = 12.sp, color = hint)
        } } }

        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("必要文件")
            Text(formatSize(usage.essential), fontSize = 20.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("storage_essential_size"))
            Text("应用本体、运行库与本地数据（含登录凭据）。清理会退出登录并需要重新下载应用。",
                fontSize = 12.sp, color = hint)
        } } }

        state.message?.let { item { Text(it, fontSize = 13.sp, color = hint, modifier = Modifier.testTag("storage_message")) } }
    }
}

/** A single track that splits proportionally; zero-sized parts simply do not occupy a segment. */
@Composable private fun StorageBar(segments: List<Long>, colors: List<Color>) {
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
        .background(MiuixTheme.colorScheme.onSurface.copy(alpha = .08f))) {
        Row(Modifier.fillMaxSize()) {
            segments.forEachIndexed { index, value ->
                if (value > 0L) Box(Modifier.weight(value.toFloat()).fillMaxHeight().background(colors[index]))
            }
        }
    }
}

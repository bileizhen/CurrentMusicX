package io.github.currencortex.music.feature.settings

import io.github.currencortex.music.ui.component.nativeui.SettingsPage
import io.github.currencortex.music.ui.component.nativeui.SettingsUiScope
import io.github.currencortex.music.ui.component.nativeui.SettingsGroup as Card

import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.storage.DataCacheCategory
import io.github.currencortex.music.core.storage.StorageUsage
import io.github.currencortex.music.data.settings.AudioCacheLimit
import io.github.currencortex.music.ui.component.MusicDialog
import io.github.currencortex.music.ui.component.MusicDialogActions
import io.github.currencortex.music.ui.component.MusicDialogButton
import io.github.currencortex.music.ui.component.nativeui.GlassIconButton
import io.github.currencortex.music.ui.component.nativeui.SettingsDivider
import io.github.currencortex.music.ui.component.nativeui.SettingsHint
import io.github.currencortex.music.ui.component.nativeui.SettingsOption
import io.github.currencortex.music.ui.component.nativeui.SettingsOptionsRow
import io.github.currencortex.music.ui.component.nativeui.SettingsPreference
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.ArrowRight
import io.github.suqi8.coui.kmp.icon.extended.Delete
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.preference.PreferenceCategoryMarginStartType
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.util.Locale

internal fun formatStorageSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.ROOT, "%.2f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.ROOT, "%.1f MB", bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> String.format(Locale.ROOT, "%.0f KB", bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}

@Composable fun StorageScreen(vm: StorageViewModel) {
    SettingsUiScope {
    val state by vm.state.collectAsStateWithLifecycle()
    val cacheLimit by vm.cacheLimit.collectAsStateWithLifecycle()
    val usage = state.usage
    LaunchedEffect(vm) { vm.refresh() }
    val context = LocalContext.current
    // Free space on the volume that holds the caches, refreshed together with the measurement.
    val freeBytes = remember(usage) {
        runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(0L)
    }
    var pending by remember { mutableStateOf<ClearTarget?>(null) }
    val rows = remember(usage) { storageRows(usage) }
    SettingsPage("存储空间", "storage_screen") {
        item("usage") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatStorageSize(usage.total), fontSize = 34.sp, lineHeight = 40.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("storage_total"))
                    Text("| ${formatStorageSize(freeBytes)} 可用",
                        Modifier.padding(start = 8.dp, bottom = 5.dp), fontSize = 20.sp,
                        color = COUITheme.colorScheme.labelSecondaryVariant)
                }
                StorageBar(storageBarSegments(usage), storageBarColors, height = 14.dp)
            }
        }

        item("details") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PreferenceCategory(title = "存储明细", modifier = Modifier.weight(1f),
                        marginStartType = PreferenceCategoryMarginStartType.Small)
                    // The one-tap cleanup for both caches, on the section header line.
                    GlassIconButton({ pending = ClearTarget.All }, "清理缓存",
                        Modifier.testTag("clear_all_storage"), size = 44.dp,
                        enabled = !state.busy && usage.audio + usage.data > 0) {
                        Icon(COUIIcons.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
                Card {
                    rows.forEachIndexed { index, row ->
                        if (index > 0) SettingsDivider()
                        StoragePreferenceRow(row, state.busy) { pending = row.target }
                    }
                }
                SettingsHint("必要文件包含应用本体与登录凭据，不提供清理；下载的文件请在「下载/CurrentMusic」目录或文件管理器中删除。")
            }
        }

        item("limit") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PreferenceCategory(title = "缓存设置", marginStartType = PreferenceCategoryMarginStartType.Small)
                Card {
                    SettingsOptionsRow("缓存上限", cacheLimit?.label ?: "读取中…", AudioCacheLimit.entries.map { option ->
                        SettingsOption("audio_cache_limit_${option.name.lowercase()}", option.label, cacheLimit == option,
                            onSelect = { vm.setCacheLimit(option) })
                    }, Modifier.testTag("audio_cache_limit"), summary = "超过上限后，最久没听的歌会被先删掉。",
                        enabled = cacheLimit != null && !state.busy)
                }
            }
        }

        state.message?.let { message ->
            item("message") {
                Text(message, fontSize = 13.sp, color = COUITheme.colorScheme.labelSecondaryVariant,
                    modifier = Modifier.testTag("storage_message"))
            }
        }
    }

    ClearCacheDialog(pending, usage, state.busy, onDismiss = { pending = null }) { target ->
        pending = null
        when (target) {
            ClearTarget.Audio -> vm.clearAudio()
            ClearTarget.All -> vm.clearAll()
            is ClearTarget.Category -> vm.clearDataCategory(target.category)
        }
    }
    }
}

/** One row of 存储明细: colour dot, name, size and — when it can be cleared — a chevron. */
@Composable private fun StoragePreferenceRow(row: StorageRow, busy: Boolean, onOpen: () -> Unit) {
    val clickable = row.target != null && row.bytes > 0 && !busy
    SettingsPreference(
        title = row.label,
        modifier = Modifier.testTag("storage_row_${row.tag}"),
        enabled = row.target == null || row.bytes > 0,
        onClick = if (clickable) onOpen else null,
        leading = { Box(Modifier.size(10.dp).clip(CircleShape).background(row.color)) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(row.text, fontSize = 14.sp, color = COUITheme.colorScheme.labelTertiary)
                if (clickable) Icon(COUIIcons.Basic.ArrowRight, contentDescription = null,
                    modifier = Modifier.size(16.dp), tint = COUITheme.colorScheme.labelTertiary)
            }
        },
    )
}

private data class StorageRow(val label: String, val text: String, val bytes: Long, val color: Color,
    val tag: String, val target: ClearTarget?)

private fun storageRows(usage: StorageUsage) = listOf(
    StorageRow("音乐缓存", withCount(formatStorageSize(usage.audio), usage.audioTracks, "首"),
        usage.audio, storageBarColors[0], "audio", ClearTarget.Audio),
    StorageRow("封面缓存", formatStorageSize(usage.images), usage.images, storageBarColors[1], "images",
        ClearTarget.Category(DataCacheCategory.IMAGES)),
    StorageRow("歌词缓存", formatStorageSize(usage.lyrics), usage.lyrics, storageBarColors[2], "lyrics",
        ClearTarget.Category(DataCacheCategory.LYRICS)),
    StorageRow("分享图片", formatStorageSize(usage.shares), usage.shares, storageBarColors[3], "shares",
        ClearTarget.Category(DataCacheCategory.SHARES)),
    StorageRow("其他缓存", formatStorageSize(usage.other), usage.other, storageBarColors[4], "other",
        ClearTarget.Category(DataCacheCategory.OTHER)),
    StorageRow("下载的资源", withCount(formatStorageSize(usage.downloads), usage.downloadCount, "个"),
        usage.downloads, storageBarColors[5], "downloads", null),
    StorageRow("必要文件", formatStorageSize(usage.essential), usage.essential, storageBarColors[6], "essential", null),
)

private fun withCount(text: String, count: Int, unit: String) = if (count > 0) "$text · $count $unit" else text

private fun StorageUsage.bytesOf(category: DataCacheCategory): Long = when (category) {
    DataCacheCategory.IMAGES -> images
    DataCacheCategory.LYRICS -> lyrics
    DataCacheCategory.SHARES -> shares
    DataCacheCategory.OTHER -> other
}

/** What the 清理 dialog is about to remove; the copy states the size before anything is deleted. */
private sealed interface ClearTarget {
    val title: String
    fun description(usage: StorageUsage): String

    data object Audio : ClearTarget {
        override val title = "清理音乐缓存"
        override fun description(usage: StorageUsage) =
            "将删除 ${formatStorageSize(usage.audio)} 的离线音频缓存，需要时会重新加载；不影响已下载的文件。"
    }

    data class Category(val category: DataCacheCategory) : ClearTarget {
        override val title get() = "清理${category.label}"
        override fun description(usage: StorageUsage) =
            "将删除 ${formatStorageSize(usage.bytesOf(category))} 缓存文件，不影响账号登录和已下载的音乐。"
    }

    data object All : ClearTarget {
        override val title = "清理缓存"
        override fun description(usage: StorageUsage): String {
            val parts = buildList {
                if (usage.audio > 0) add("音乐缓存 ${formatStorageSize(usage.audio)}")
                if (usage.data > 0) add("数据缓存 ${formatStorageSize(usage.data)}")
            }
            return "将清理${parts.joinToString(" 和 ")}，不影响账号、下载的文件和设置。"
        }
    }
}

@Composable
private fun ClearCacheDialog(target: ClearTarget?, usage: StorageUsage, busy: Boolean,
    onDismiss: () -> Unit, onConfirm: (ClearTarget) -> Unit) {
    val pending = target ?: return
    MusicDialog(pending.title, onDismiss) {
        Text(pending.description(usage))
        MusicDialogActions {
            MusicDialogButton("取消", onDismiss, Modifier.weight(1f), enabled = !busy)
            MusicDialogButton(if (busy) "清理中…" else "清理", { onConfirm(pending) },
                Modifier.weight(1f).testTag("confirm_clear_cache"), enabled = !busy, destructive = true)
        }
    }
}

/** Segments in bar order; the four data-cache parts sit between audio and downloads. */
internal fun storageBarSegments(usage: StorageUsage): List<Long> = listOf(
    usage.audio, usage.images, usage.lyrics, usage.shares, usage.other, usage.downloads, usage.essential,
)

/**
 * Fixed categorical hues in the same order as the rows, following the system storage page rather
 * than a single tint, so every segment can be matched to one line of 存储明细.
 */
internal val storageBarColors = listOf(
    Color(0xFF3B6EF6), // 音乐缓存
    Color(0xFF12A5C6), // 封面缓存
    Color(0xFF8B5CF6), // 歌词缓存
    Color(0xFFEC5FA8), // 分享图片
    Color(0xFF7C8AA5), // 其他缓存
    Color(0xFF22A06B), // 下载的资源
    Color(0xFF44506B), // 必要文件
)

/** A single track that splits proportionally; zero-sized parts simply do not occupy a segment. */
@Composable internal fun StorageBar(segments: List<Long>, colors: List<Color>, height: Dp = 10.dp) {
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2))
        .background(COUITheme.colorScheme.labelPrimary.copy(alpha = .08f))) {
        Row(Modifier.fillMaxSize()) {
            segments.forEachIndexed { index, value ->
                if (value > 0L) Box(Modifier.weight(value.toFloat()).fillMaxHeight().background(colors[index]))
            }
        }
    }
}
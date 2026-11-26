package io.github.currencortex.music.feature.room

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.currencortex.music.data.netease.listen.ListenTogetherFateReport
import io.github.currencortex.music.ui.component.nativeui.SettingsDialog
import io.github.currencortex.music.ui.component.nativeui.SettingsToggle

@Composable internal fun ListenTogetherFateDeleteDialog(show: Boolean, report: ListenTogetherFateReport?,
    onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    var deleteRecords by remember(report) { mutableStateOf(false) }
    SettingsDialog(
        show = show,
        title = "删除缘分报告？",
        onDismissRequest = onDismiss,
        summary = report?.let { current ->
            buildString {
                append("将删除与${current.peer.nickname.ifBlank { "这位听友" }}的缘分报告及排行数据，删除后无法恢复。")
                if (current.openActivity) append("\n关联的一起听徽章及其进度也会被回收。")
            }
        },
        positiveText = "确认删除",
        onPositive = { onConfirm(deleteRecords) },
        negativeText = "取消",
        onNegative = onDismiss,
        destructive = true,
    ) {
        SettingsToggle("同时删除一起听历史", deleteRecords, { deleteRecords = it },
            Modifier.testTag("listen_fate_delete_records"), summary = "默认保留与 TA 的历史记录")
    }
}

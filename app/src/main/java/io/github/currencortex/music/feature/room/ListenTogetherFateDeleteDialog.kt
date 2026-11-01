package io.github.currencortex.music.feature.room

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.netease.listen.ListenTogetherFateReport
import io.github.currencortex.music.ui.component.MusicDialog
import io.github.currencortex.music.ui.component.MusicTextAction
import io.github.currencortex.music.ui.component.MusicDialogActions
import io.github.currencortex.music.ui.component.MusicDialogButton
import io.github.currencortex.music.ui.component.nativeui.SettingsToggle
import top.yukonga.miuix.kmp.basic.Text

@Composable internal fun ListenTogetherFateDeleteDialog(report: ListenTogetherFateReport,
    onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    var deleteRecords by remember(report) { mutableStateOf(false) }
    MusicDialog("删除缘分报告？", onDismiss) {
        Text("将删除与${report.peer.nickname.ifBlank { "这位听友" }}的缘分报告及排行数据，删除后无法恢复。", fontSize = 14.sp)
        if (report.openActivity) Text("关联的一起听徽章及其进度也会被回收。", fontSize = 14.sp)
        SettingsToggle("同时删除一起听历史", deleteRecords, { deleteRecords = it },
            Modifier.testTag("listen_fate_delete_records"), summary = "默认保留与 TA 的历史记录")
        MusicDialogActions {
            MusicDialogButton("取消", onDismiss, Modifier.weight(1f).testTag("listen_fate_delete_cancel"))
            MusicDialogButton("确认删除", { onConfirm(deleteRecords) }, Modifier.weight(1f).testTag("listen_fate_delete_confirm"), destructive = true)
        }
    }
}

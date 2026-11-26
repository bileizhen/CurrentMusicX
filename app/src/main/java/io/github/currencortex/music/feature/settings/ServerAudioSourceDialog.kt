package io.github.currencortex.music.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.currencortex.music.data.song.ServerAudioSource
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.ListDialogItem
import io.github.suqi8.coui.kmp.overlay.OverlaySingleChoiceDialog

@Composable
internal fun ServerAudioSourceDialog(show: Boolean, state: ServerAudioCatalogState?, onDismiss: () -> Unit,
    onRetry: () -> Unit, onImport: (ServerAudioSource) -> Unit, onLogin: () -> Unit) {
    val sources = state?.sources.orEmpty()
    var selection by remember(sources) { mutableStateOf<ServerAudioSource?>(null) }
    val summary = buildList {
        state?.let {
            add("提供方 · ${it.server}")
            if (it.busy) add("正在加载音源…")
            it.message?.let(::add)
        }
    }.joinToString("\n")
    OverlaySingleChoiceDialog(
        show = show,
        items = sources.map { source ->
            ListDialogItem(text = source.name, summary = "支持网易云曲库")
        },
        selectedIndex = sources.indexOf(selection),
        onSelectedIndexChange = { index -> selection = sources.getOrNull(index) },
        title = "从服务器导入",
        summary = summary,
        negative = DialogButtonBarAction("取消", onClick = onDismiss),
        positive = when {
            state == null -> null
            state.needsLogin -> DialogButtonBarAction("登录", onClick = onLogin)
            sources.isNotEmpty() -> DialogButtonBarAction(
                "导入并使用", enabled = selection != null, onClick = { selection?.let(onImport) })
            !state.busy -> DialogButtonBarAction("重新加载", onClick = onRetry)
            else -> null
        },
        onDismissRequest = onDismiss,
    )
}

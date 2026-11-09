package io.github.currencortex.music.feature.library

import android.content.Intent
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.core.network.appResult
import androidx.compose.ui.geometry.Rect
import io.github.currencortex.music.data.library.Playlist
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.MusicDialog
import io.github.currencortex.music.ui.component.MusicDialogButton
import io.github.currencortex.music.ui.component.nativeui.SettingsUiScope
import io.github.currencortex.music.ui.component.nativeui.GlassMenuItem
import io.github.currencortex.music.ui.component.nativeui.MusicArtworkPreview
import top.yukonga.miuix.kmp.basic.Text

@Composable internal fun PlaylistArtworkPreview(
    vm: LibraryViewModel, playlist: Playlist, anchor: Rect, canPlay: Boolean, onPlay: (shuffle: Boolean) -> Unit,
    onOpen: () -> Unit, onDismiss: () -> Unit,
) {
    val binding by vm.container.bindingRepository.state.collectAsStateWithLifecycle()
    val session by vm.container.neteaseSessions.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var info by remember(playlist.id, playlist.source, session.revision) { mutableStateOf<Playlist?>(null) }
    val native = playlist.source == "netease"
    LaunchedEffect(playlist.id, playlist.source, session.revision) {
        if (native) when (val result = appResult { vm.container.neteaseLibrary.playlistInfo(playlist.id) }) {
            is AppResult.Success -> info = result.value
            is AppResult.Failure -> vm.container.errorNotices.show(result.message)
        }
    }
    val target = info
    val canSubscribe = native && !playlist.nativeOwned && !playlist.nativeLiked &&
        target?.nativeOwned != true && target?.nativeLiked != true
    val subscribed = target?.nativeSubscribed ?: playlist.nativeSubscribed
    val display = target ?: playlist
    val context = LocalContext.current
    var infoOpen by remember(playlist.id, playlist.source) { mutableStateOf(false) }
    fun share() {
        val text = buildString {
            appendLine("《${display.name}》 · ${display.count} 首歌曲")
            if (display.description.isNotBlank()) appendLine(display.description)
            if (native) appendLine("https://music.163.com/#/playlist?id=${playlist.id}")
            append("来自 CurrentMusic")
        }
        runCatching {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text), "分享歌单"))
        }.onFailure { vm.container.errorNotices.show("暂时无法打开分享") }
    }
    if (infoOpen) SettingsUiScope {
        MusicDialog("歌单信息", onDismiss = onDismiss) {
            Text(display.name, Modifier.testTag("playlist_preview_info_name"), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(if (display.source in setOf("netease", "ncm")) "网易云音乐" else "CurrentMusic", fontSize = 13.sp)
            Text("${display.count} 首歌曲", fontSize = 13.sp)
            Text(display.description.ifBlank { "暂无歌单介绍" },
                Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()), fontSize = 14.sp)
            MusicDialogButton("关闭", onClick = onDismiss)
        }
    } else MusicArtworkPreview(
        cover = playlist.cover.ifBlank { playlist.songs.firstOrNull()?.cover.orEmpty() },
        title = playlist.name, anchor = anchor,
        actions = buildList {
            add(GlassMenuItem("play", "播放全部", MusicIcons.Play, enabled = canPlay, onClick = { onPlay(false) }))
            if (canSubscribe) add(GlassMenuItem("subscribe", if (subscribed) "已收藏" else "收藏",
                if (subscribed) Icons.Default.Star else MusicIcons.Star,
                enabled = target != null && !busy && binding?.bound == true && binding?.stale == false,
                keepOpen = true, onClick = {
                    vm.action(if (subscribed) "已取消收藏歌单" else "已收藏歌单") {
                        if (subscribed) vm.container.neteaseLibrary.unsubscribe(playlist.id)
                        else vm.container.neteaseLibrary.subscribe(playlist.id)
                        info = target?.copy(nativeSubscribed = !subscribed)
                    }
                }))
            add(GlassMenuItem("shuffle", "随机播放", MusicIcons.Shuffle, enabled = canPlay, onClick = { onPlay(true) }))
        },
        destinations = listOf(
            GlassMenuItem("open_playlist", "打开歌单", onClick = onOpen),
            GlassMenuItem("playlist_info", "歌单信息", keepOpen = true, onClick = { infoOpen = true }),
            GlassMenuItem("share_playlist", "分享", onClick = ::share),
        ),
        onDismiss = onDismiss, previewKind = "歌单",
    )
}

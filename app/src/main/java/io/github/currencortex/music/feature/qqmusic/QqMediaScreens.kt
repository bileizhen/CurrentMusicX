package io.github.currencortex.music.feature.qqmusic

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.core.network.appResult
import io.github.currencortex.music.data.qqmusic.QqComment
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.suqi8.coui.kmp.basic.Text

@Composable internal fun QqCommentsScreen(container: AppContainer, mid: String, onBack: () -> Unit) {
    val revision by container.accountRepository.sessionRevision.collectAsStateWithLifecycle()
    val qqAccount by container.qqMusic.state.collectAsStateWithLifecycle()
    val settings by container.musicSettings.state.collectAsStateWithLifecycle()
    val identity = "$revision|${qqAccount.revision}|${settings.server}"
    var comments by remember(mid, identity) { mutableStateOf<List<QqComment>>(emptyList()) }
    var page by remember(mid, identity) { mutableIntStateOf(0) }
    var seq by remember(mid, identity) { mutableStateOf("") }
    var total by remember(mid, identity) { mutableIntStateOf(0) }
    var more by remember(mid, identity) { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(mid, identity, page, refresh) {
        loading = true
        error = null
        when (val result = appResult { container.qqMusic.comments(container.qqMusic.detail(mid),
            page = page + 1, seq = seq, limit = 25) }) {
            is AppResult.Success -> {
                comments = (if (page == 0) result.value.comments else comments + result.value.comments).distinctBy { it.id }
                total = result.value.total.toInt()
                more = result.value.hasMore
                seq = result.value.nextSeq
            }
            is AppResult.Failure -> error = result.message
        }
        loading = false
    }
    MusicSubpage("QQ 音乐评论 · $total", "qq_comments", onBack) { inset ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = musicScrollPadding(PaddingValues(16.dp, inset + 8.dp, 16.dp, 32.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            error?.let { item { SettingsHint(it); MusicTextAction("重试", { refresh++ }) } }
            items(comments, key = { it.id }) { comment ->
                SettingsGroup {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(comment.nickname, fontSize = 14.sp)
                        Text(comment.text, fontSize = 15.sp)
                        Text("${comment.likes} 赞", fontSize = 12.sp)
                    }
                }
            }
            if (loading) item { LoadingTextList(4) }
            if (more) item { MusicTextAction("加载更多", { page += 1 }, enabled = !loading) }
            if (!loading && error == null && comments.isEmpty()) item { SettingsHint("暂无评论") }
        }
    }
}

@Composable internal fun QqMvScreen(container: AppContainer, id: String, onBack: () -> Unit) {
    val revision by container.accountRepository.sessionRevision.collectAsStateWithLifecycle()
    val qqAccount by container.qqMusic.state.collectAsStateWithLifecycle()
    val settings by container.musicSettings.state.collectAsStateWithLifecycle()
    val identity = "$revision|${qqAccount.revision}|${settings.server}"
    var source by remember(id, identity) { mutableStateOf("") }
    var error by remember(id, identity) { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var video by remember { mutableStateOf<VideoView?>(null) }
    var position by rememberSaveable(id) { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current
    val pageActive = LocalMusicPageActive.current
    LaunchedEffect(id, identity, refresh) {
        video?.stopPlayback()
        source = ""
        when (val result = appResult { container.qqMusic.mvUrl(id) }) {
            is AppResult.Success -> { container.playerController.pause(); source = result.value }
            is AppResult.Failure -> error = result.message
        }
    }
    DisposableEffect(lifecycle, pageActive) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) video?.let { position = it.currentPosition; it.pause() }
        }
        lifecycle.lifecycle.addObserver(observer)
        if (!pageActive) video?.let { position = it.currentPosition; it.pause() }
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            video?.let { position = it.currentPosition; it.pause() }
        }
    }
    DisposableEffect(id) { onDispose { video?.stopPlayback(); video = null } }
    MusicSubpage("QQ 音乐 MV", "qq_mv", onBack) { inset ->
        Column(Modifier.fillMaxSize().padding(top = inset, bottom = LocalMusicBottomInset.current)) {
            error?.let { SettingsHint(it); MusicTextAction("重试", { error = null; refresh++ }) }
            if (source.isNotBlank()) key(id, source) {
                AndroidView(factory = { context ->
                    VideoView(context).apply {
                        video = this
                        setMediaController(MediaController(context).also { it.setAnchorView(this) })
                        setOnPreparedListener {
                            if (position > 0) seekTo(position)
                            if (pageActive && lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
                        }
                        setOnErrorListener { _, _, _ -> error = "MV 播放失败，请重试"; true }
                        setVideoURI(Uri.parse(source))
                    }
                }, onRelease = { it.stopPlayback(); if (video === it) video = null }, modifier = Modifier.fillMaxWidth().weight(1f))
            } else if (error == null) LoadingTextList(3)
        }
    }
}

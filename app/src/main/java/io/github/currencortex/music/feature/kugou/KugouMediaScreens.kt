package io.github.currencortex.music.feature.kugou

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import io.github.currencortex.music.data.kugou.KugouComment
import io.github.currencortex.music.ui.component.LoadingTextList
import io.github.currencortex.music.ui.component.LocalMusicBottomInset
import io.github.currencortex.music.ui.component.LocalMusicPageActive
import io.github.currencortex.music.ui.component.MusicTextAction
import io.github.currencortex.music.ui.component.SpringLazyColumn
import io.github.currencortex.music.ui.component.musicScrollPadding
import io.github.currencortex.music.ui.component.nativeui.MusicSubpage
import io.github.currencortex.music.ui.component.nativeui.SettingsGroup
import io.github.currencortex.music.ui.component.nativeui.SettingsHint
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun KugouCommentsScreen(container: AppContainer, albumAudioId: Long, onBack: () -> Unit) {
    val kugouAccount by container.kugou.state.collectAsStateWithLifecycle()
    val identity = "${albumAudioId}|${kugouAccount.revision}"
    var sort by remember(identity) { mutableStateOf("hot") }
    var comments by remember(identity, sort) { mutableStateOf<List<KugouComment>>(emptyList()) }
    var page by remember(identity, sort) { mutableIntStateOf(1) }
    var total by remember(identity) { mutableStateOf(0L) }
    var more by remember(identity, sort) { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(identity, sort, page, refresh) {
        loading = true
        error = null
        when (val result = appResult {
            val song = container.kugou.detailById(albumAudioId)
            container.kugou.comments(song, sort = sort, page = page, limit = 25)
        }) {
            is AppResult.Success -> {
                comments = (if (page == 1) result.value.comments else comments + result.value.comments).distinctBy { it.id }
                total = result.value.total
                more = result.value.hasMore
            }
            is AppResult.Failure -> error = result.message
        }
        loading = false
    }

    MusicSubpage("酷狗评论 · $total", "kugou_comments", onBack) { inset ->
        SpringLazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = musicScrollPadding(PaddingValues(16.dp, inset + 8.dp, 16.dp, 32.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "sort") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MusicTextAction(if (sort == "hot") "· 热门评论" else "热门评论", { sort = "hot" }, enabled = !loading)
                    MusicTextAction(if (sort == "latest") "· 最新评论" else "最新评论", { sort = "latest" }, enabled = !loading)
                }
            }
            error?.let { item { SettingsHint(it); MusicTextAction("重试", { refresh++ }) } }
            items(comments, key = { it.id }) { comment ->
                SettingsGroup {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(comment.nickname, fontSize = 13.sp, color = COUITheme.colorScheme.labelSecondaryVariant)
                        Text(comment.text, fontSize = 15.sp)
                        Text("${comment.likes} 赞" + if (comment.replyCount > 0) " · ${comment.replyCount} 条回复" else "",
                            fontSize = 12.sp, color = COUITheme.colorScheme.labelSecondaryVariant)
                    }
                }
            }
            if (loading) item { LoadingTextList(4) }
            if (more) item { MusicTextAction("加载更多", { page += 1 }, enabled = !loading) }
            if (!loading && error == null && comments.isEmpty()) item { SettingsHint("暂无评论") }
        }
    }
}

@Composable
internal fun KugouMvScreen(container: AppContainer, mvHash: String, onBack: () -> Unit) {
    val kugouAccount by container.kugou.state.collectAsStateWithLifecycle()
    val identity = "$mvHash|${kugouAccount.revision}"
    var source by remember(identity) { mutableStateOf("") }
    var error by remember(identity) { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var video by remember { mutableStateOf<VideoView?>(null) }
    var position by rememberSaveable(mvHash) { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current
    val pageActive = LocalMusicPageActive.current

    LaunchedEffect(identity, refresh) {
        video?.stopPlayback()
        source = ""
        error = null
        when (val result = appResult { container.kugou.mvUrl(mvHash) }) {
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

    DisposableEffect(mvHash) { onDispose { video?.stopPlayback(); video = null } }

    MusicSubpage("酷狗 MV", "kugou_mv", onBack) { inset ->
        Column(Modifier.fillMaxSize().padding(top = inset, bottom = LocalMusicBottomInset.current)) {
            error?.let { SettingsHint(it); MusicTextAction("重试", { error = null; refresh++ }) }
            if (source.isNotBlank()) key(mvHash, source) {
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
                }, onRelease = { it.stopPlayback(); if (video === it) video = null },
                    modifier = Modifier.fillMaxWidth().weight(1f))
            } else if (error == null) LoadingTextList(3)
        }
    }
}

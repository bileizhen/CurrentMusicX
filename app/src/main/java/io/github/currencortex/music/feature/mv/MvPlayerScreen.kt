package io.github.currencortex.music.feature.mv

import android.content.*
import android.os.IBinder
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.media.MusicService
import top.yukonga.miuix.kmp.basic.*

/** The view borrows a video surface; the playback service retains sole player ownership. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable fun MvPlayerScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by container.playerController.state.collectAsStateWithLifecycle()
    var binder by remember { mutableStateOf<MusicService.VideoBinder?>(null) }
    var surface by remember { mutableStateOf<PlayerView?>(null) }
    LaunchedEffect(Unit) { container.playerController.connect() }
    DisposableEffect(context) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) { binder = service as? MusicService.VideoBinder }
            override fun onServiceDisconnected(name: ComponentName) { binder = null }
        }
        val bound = context.bindService(Intent(context, MusicService::class.java).setAction(MusicService.VIDEO_SURFACE), connection, Context.BIND_AUTO_CREATE)
        onDispose { surface?.let { binder?.detach(it) }; if (bound) context.unbindService(connection) }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp).testTag("mv_player")) {
        TextButton("返回", onClick = { container.playerController.pause(); onBack() })
        Text(state.song?.name ?: "MV")
        AndroidView(factory = { PlayerView(it).apply { useController = false; surface = this } },
            update = { binder?.attach(it) }, onRelease = { binder?.detach(it) },
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("mv_surface"))
        if (state.loading) Text("正在加载 MV…")
        state.error?.let { Text(it); TextButton("重试", onClick = { container.playerController.load() }) }
        val duration = state.durationMs.coerceAtLeast(1)
        var drag by remember { mutableStateOf<Float?>(null) }
        Slider(drag ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f), onValueChange = { drag = it },
            onValueChangeFinished = { drag?.let { container.playerController.seek((it * duration).toLong()) }; drag = null }, modifier = Modifier.testTag("mv_seek"))
        TextButton(if (state.playing) "暂停" else "播放", onClick = { container.playerController.toggle() }, modifier = Modifier.testTag("mv_toggle"))
    }
}

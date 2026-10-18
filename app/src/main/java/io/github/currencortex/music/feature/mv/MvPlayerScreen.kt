package io.github.currencortex.music.feature.mv

import android.content.*
import android.content.pm.ActivityInfo
import android.os.IBinder
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.media.MusicService
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.feature.player.PlayerTransportButton
import io.github.currencortex.music.feature.player.PlayerSeekBar
import io.github.currencortex.music.ui.component.MusicIcons
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The service owns playback. Fullscreen only changes presentation of the borrowed surface. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable fun MvPlayerScreen(container: AppContainer, onBack: () -> Unit, onDialogActive: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val view = LocalView.current
    val state by container.playerController.state.collectAsStateWithLifecycle()
    val song = state.song ?: return
    var binder by remember { mutableStateOf<MusicService.VideoBinder?>(null) }
    var surface by remember { mutableStateOf<PlayerView?>(null) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val originalOrientation = rememberSaveable { activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    var download by rememberSaveable { mutableStateOf(false) }
    var quality by rememberSaveable { mutableStateOf(false) }
    val items by remember(song.mv) { container.mvDownloads.observe(song) }.collectAsStateWithLifecycle(emptyList())
    val info = items.lastOrNull()
    val controller = container.playerController
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentDialogCallback by rememberUpdatedState(onDialogActive)
    SideEffect { currentDialogCallback(fullscreen || download || quality) }
    fun toggleFullscreen() {
        fullscreen = !fullscreen
        activity?.requestedOrientation = if (fullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else originalOrientation
    }
    BackHandler(fullscreen && !download && !quality) { toggleFullscreen() }
    LaunchedEffect(Unit) { controller.connect() }
    DisposableEffect(context) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) { binder = service as? MusicService.VideoBinder }
            override fun onServiceDisconnected(name: ComponentName) { binder = null }
        }
        val bound = context.bindService(Intent(context, MusicService::class.java).setAction(MusicService.VIDEO_SURFACE), connection, Context.BIND_AUTO_CREATE)
        onDispose { surface?.let { binder?.detach(it) }; if (bound) context.unbindService(connection) }
    }
    DisposableEffect(lifecycle, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && activity?.isChangingConfigurations != true && controller.state.value.song?.video == true)
                controller.pause()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            currentDialogCallback(false)
            if (activity?.isChangingConfigurations != true) activity?.requestedOrientation = originalOrientation
        }
    }
    DisposableEffect(view, state.showPause) {
        val previous = view.keepScreenOn
        view.keepScreenOn = state.showPause
        onDispose { view.keepScreenOn = previous }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val immersive = fullscreen || maxWidth > maxHeight
        MvSystemBars(immersive)
        MvPlayerContent(state, immersive, fullscreen, info.mvLabel(),
            onBack = { if (fullscreen) toggleFullscreen() else onBack() }, onToggle = { controller.toggle() },
            onSeek = controller::seek, onFullscreen = ::toggleFullscreen, onDownload = { download = true },
            onQuality = { quality = true }, onRetry = { controller.load(true, state.positionMs) }) { modifier ->
            AndroidView(factory = { PlayerView(it).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setKeepContentOnPlayerReset(true)
                surface = this
            } }, update = { binder?.attach(it) }, onRelease = { binder?.detach(it) }, modifier = modifier.testTag("mv_surface"))
        }
    }
    if (download) MvDownloadDialog(song, container) { download = false }
    if (quality) MvQualityDialog(song.videoQuality, controller::videoQuality) { quality = false }
}

@Composable internal fun MvPlayerContent(state: PlayerState, immersive: Boolean, fullscreen: Boolean, downloadLabel: String,
    onBack: () -> Unit, onToggle: () -> Unit, onSeek: (Long) -> Unit, onFullscreen: () -> Unit,
    onDownload: () -> Unit, onQuality: () -> Unit, onRetry: () -> Unit,
    surface: @Composable (Modifier) -> Unit) {
    var controls by rememberSaveable(state.song?.id) { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var drag by remember(state.song?.id) { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(0)
    val canSeek = duration > 0 && !state.resolving && state.error == null
    val currentToggle by rememberUpdatedState(onToggle)
    val visible = controls || !state.playing || state.loading || state.error != null || drag != null
    LaunchedEffect(controls, state.playing, state.loading, state.error, drag, interaction) {
        if (controls && state.playing && !state.loading && state.error == null && drag == null) { delay(3500); controls = false }
    }
    fun interact(action: () -> Unit) { interaction++; controls = true; action() }
    Column(Modifier.fillMaxSize().background(if (immersive) Color(0xff08090b) else MiuixTheme.colorScheme.background)
        .then(if (immersive) Modifier else Modifier.statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()))
        .testTag("mv_player"), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!immersive) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).testTag("mv_back"), contentAlignment = Alignment.Center) {
                Icon(MusicIcons.Back, "返回", modifier = Modifier.size(24.dp), tint = MiuixTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.weight(1f))
            Text("音乐视频", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f), modifier = Modifier.padding(end = 16.dp))
        }
        Box((if (immersive) Modifier.fillMaxWidth().weight(1f) else Modifier.padding(horizontal = 16.dp).widthIn(max = 840.dp)
            .fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp)))
            .background(Color(0xff08090b)).testTag("mv_stage")) {
            surface(Modifier.fillMaxSize())
            Box(Modifier.matchParentSize().pointerInput(Unit) {
                detectTapGestures(onTap = { controls = !controls; interaction++ }, onDoubleTap = { currentToggle(); controls = true; interaction++ })
            }.semantics { contentDescription = "MV 画面"; onClick("显示播放控制") { controls = true; interaction++; true } }.testTag("mv_gestures"))
            androidx.compose.animation.AnimatedVisibility(visible, enter = fadeIn(tween(180)), exit = fadeOut(tween(220)), modifier = Modifier.matchParentSize()) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .45f), Color.Transparent, Color.Black.copy(alpha = .8f))))
                    .then(if (immersive) Modifier.windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.systemBars).only(WindowInsetsSides.Horizontal)) else Modifier)) {
                    if (immersive) Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        MvTextAction("‹", if (fullscreen) "退出全屏" else "返回", "mv_back", onBack)
                        Text(state.song?.name.orEmpty(), Modifier.weight(1f), color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        MvTextAction(downloadLabel, "下载 MV", "mv_download", onDownload)
                    }
                    if (state.error == null) Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        MvTextAction("−10s", "后退 10 秒", "mv_back_10", { interact { onSeek((state.positionMs - 10000).coerceAtLeast(0)) } }, canSeek)
                        PlayerTransportButton(if (state.showPause) "暂停" else "播放", { interact(onToggle) },
                            Modifier.background(Color.Black.copy(alpha = .2f), CircleShape).testTag("mv_toggle"), playing = state.showPause, compact = true)
                        MvTextAction("+10s", "前进 10 秒", "mv_forward_10", { interact { onSeek((state.positionMs + 10000).coerceAtMost(duration)) } }, canSeek)
                    }
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                        PlayerSeekBar(drag ?: if (duration > 0) (state.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                            canSeek, onPreview = { drag = it; interaction++ },
                            onSeek = { if (it.isFinite()) onSeek((it * duration).toLong()); drag = null; interaction++ },
                            onCancel = { drag = null; interaction++ }, bufferedValue = state.bufferedProgress())
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${mvTime(drag?.let { (it * duration).toLong() } ?: state.positionMs)} / ${mvTime(duration)}",
                                color = Color.White.copy(alpha = .8f), fontSize = 11.sp, modifier = Modifier.weight(1f).testTag("mv_time"))
                            MvTextAction("${state.song?.videoQuality ?: 1080}P", "选择清晰度", "mv_quality", { interact(onQuality) })
                            Box(Modifier.size(44.dp).clickable(role = Role.Button, onClick = onFullscreen).testTag("mv_fullscreen"), contentAlignment = Alignment.Center) {
                                Icon(MusicIcons.Scale, if (fullscreen) "退出全屏" else "全屏", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
            if (state.loading && state.error == null) Text("正在缓冲…", Modifier.align(Alignment.TopCenter).padding(top = if (immersive) 60.dp else 12.dp),
                color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
            if (state.error != null) Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton("重新加载", { interact(onRetry) }, Modifier.testTag("mv_retry"))
            }
        }
        if (!immersive) Column(Modifier.widthIn(max = 840.dp).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(state.song?.name ?: "MV", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(state.song?.artists.orEmpty(), fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(downloadLabel, onDownload, Modifier.weight(1f).testTag("mv_download"),
                    colors = ButtonDefaults.textButtonColors(color = MiuixTheme.colorScheme.primary, textColor = MiuixTheme.colorScheme.onPrimary))
                TextButton("全屏观看", onFullscreen, Modifier.weight(1f))
            }
            Text("点按画面显示控制 · 双击播放或暂停", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .4f))
        }
    }
}

@Composable private fun MvTextAction(text: String, label: String, tag: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(Modifier.heightIn(min = 44.dp).widthIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
        .background(Color.Black.copy(alpha = .22f))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 10.dp)
        .semantics { contentDescription = label }.testTag(tag), contentAlignment = Alignment.Center) {
        if (text == "‹") Icon(MusicIcons.Back, null, tint = Color.White, modifier = Modifier.size(22.dp))
        else Text(text, color = Color.White.copy(alpha = if (enabled) 1f else .35f), fontSize = 12.sp)
    }
}

private fun mvTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%02d:%02d".format(seconds / 60, seconds % 60)
}

@Composable private fun MvSystemBars(immersive: Boolean) {
    val window = LocalActivity.current?.window
    val view = LocalView.current
    DisposableEffect(window, view, immersive) {
        if (window == null || !immersive) return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        val previous = controller.systemBarsBehavior
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.systemBarsBehavior = previous; controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

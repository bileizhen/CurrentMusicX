package io.github.currencortex.music.feature.recognition

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Build
import android.media.projection.MediaProjectionManager
import android.media.projection.MediaProjectionConfig
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun MusicRecognitionScreen(onBack: () -> Unit, play: (List<Song>, Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current
    val controller = remember {
        val encoder = NeteaseFingerprint(context)
        val repository = NeteaseRecognitionRepository()
        RecognitionController(scope, MicrophoneRecorder(), encoder::encode, repository::recognize)
    }
    val localState by controller.state.collectAsStateWithLifecycle()
    val desktopState by RecognitionDesktopSession.state.collectAsStateWithLifecycle()
    val desktopAttached by RecognitionDesktopSession.attached.collectAsStateWithLifecycle()
    val serviceRunning by RecognitionDesktopSession.running.collectAsStateWithLifecycle()
    val state = if (serviceRunning || (localState.phase == RecognitionPhase.IDLE && desktopState.phase != RecognitionPhase.IDLE)) desktopState else localState
    val historyStore = remember { RecognitionHistory(context) }
    var history by remember { mutableStateOf(historyStore.read()) }
    var showingHistory by remember { mutableStateOf(false) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var pendingRecord by remember { mutableStateOf(false) }
    var pendingDesktop by remember { mutableStateOf(false) }
    var desktopStarting by remember { mutableStateOf(false) }
    var source by rememberSaveable { mutableStateOf(if (Build.VERSION.SDK_INT >= 29) RecognitionAudioSource.SYSTEM else RecognitionAudioSource.MICROPHONE) }
    var pendingProjection by remember { mutableStateOf<Intent?>(null) }
    fun launchService(consent: Intent?, desktop: Boolean) {
        desktopStarting = desktop
        scope.launch {
            try {
                // Finish the previous source before starting a new foreground-service type.
                RecognitionDesktopService.stop(context)
                check(withTimeoutOrNull(2_000) { RecognitionDesktopSession.running.first { !it } } != null)
                if (!lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
                controller.reset()
                RecognitionDesktopSession.clearResult()
                if (consent != null) RecognitionDesktopService.startSystem(context, consent, desktop)
                else RecognitionDesktopService.start(context)
                if (desktop) {
                    val ready = withTimeoutOrNull(4_000) { RecognitionDesktopSession.attached.first { it } } == true
                    if (ready && lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    else if (!ready) permissionMessage = "桌面识曲启动失败，请检查权限后重试"
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { permissionMessage = "暂时无法开始识曲，请重新授权后重试" }
            finally { desktopStarting = false }
        }
    }
    fun startSystem() {
        if (!lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val consent = pendingProjection ?: return
        pendingProjection = null
        pendingRecord = false
        val desktop = pendingDesktop
        pendingDesktop = false
        launchService(consent, desktop)
    }
    val projectionConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            pendingProjection = result.data
            startSystem()
        } else {
            pendingDesktop = false
            permissionMessage = "已取消系统音频授权，可重新开始或切换麦克风"
        }
    }
    fun startChosen() {
        pendingRecord = false
        permissionMessage = null
        if (source == RecognitionAudioSource.SYSTEM) {
            RecognitionDesktopService.stop(context)
            val manager = context.getSystemService(MediaProjectionManager::class.java)
            val consent = if (Build.VERSION.SDK_INT >= 34) manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
                else manager.createScreenCaptureIntent()
            projectionConsent.launch(consent)
            return
        }
        if (pendingDesktop) {
            pendingDesktop = false
            if (!lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
            launchService(null, desktop = true)
        } else {
            RecognitionDesktopService.stop(context)
            RecognitionDesktopSession.clearResult()
            controller.start()
        }
    }
    BackHandler(showingHistory) { showingHistory = false }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingRecord = granted && lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (granted && lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            startChosen()
        } else if (!granted) permissionMessage = "需要录音权限才能识曲，可在系统设置中开启"
    }
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                controller.cancel()
                if (RecognitionDesktopSession.running.value && !RecognitionDesktopSession.attached.value) RecognitionDesktopService.stop(context)
            }
            if (event == Lifecycle.Event.ON_RESUME && pendingRecord) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startChosen()
            }
            if (event == Lifecycle.Event.ON_RESUME) history = historyStore.read()
            if (event == Lifecycle.Event.ON_RESUME && pendingProjection != null) startSystem()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer); pendingRecord = false; pendingProjection = null; controller.cancel()
            if (RecognitionDesktopSession.running.value && !RecognitionDesktopSession.attached.value) RecognitionDesktopService.stop(context)
        }
    }
    LaunchedEffect(state.phase, state.songs) {
        if (localState.phase == RecognitionPhase.MATCHED && localState.songs.isNotEmpty())
            history = withContext(Dispatchers.IO) { historyStore.add(localState.songs.first()) }
    }
    fun begin(desktop: Boolean = false) {
        if (desktopStarting || state.busy) return
        permissionMessage = null
        pendingDesktop = desktop
        if (desktop && !Settings.canDrawOverlays(context)) {
            pendingDesktop = false
            permissionMessage = "开启悬浮窗权限后，点击桌面灵动岛识曲"
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startChosen()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    val colors = MiuixTheme.colorScheme
    val bottom = LocalMusicBottomInset.current
    Column(Modifier.fillMaxSize().background(colors.background)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        .testTag("music_recognition")) {
        Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 20.dp).testTag("recognition_header")) {
            Box(Modifier.align(Alignment.CenterStart)) {
                RecognitionIconButton("返回", MusicIcons.Back, "recognition_back", { if (showingHistory) showingHistory = false else { controller.cancel(); onBack() } })
            }
            Text(if (showingHistory) "识别记录" else "听歌识曲", Modifier.align(Alignment.Center).padding(horizontal = 52.dp),
                fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Box(Modifier.align(Alignment.CenterEnd)) {
                if (showingHistory) {
                    Text("清空", Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) {
                        historyStore.clear(); history = emptyList()
                    }.padding(10.dp).testTag("recognition_clear_history"), color = colors.primary, fontSize = 14.sp)
                } else RecognitionIconButton("识别记录", MusicIcons.Update, "recognition_history", { controller.cancel(); showingHistory = true })
            }
        }
        if (showingHistory) {
            LazyColumn(Modifier.fillMaxSize().testTag("recognition_history_list"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = bottom + 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (history.isEmpty()) item { Text("还没有识别记录，听见喜欢的音乐就来试试", Modifier.padding(vertical = 30.dp), color = colors.onSurfaceVariantSummary, fontSize = 14.sp) }
                itemsIndexed(history, key = { _, item -> item.song.id }) { index, entry ->
                    RecognitionSongCard(entry.song, DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(entry.recognizedAt)),
                        "recognition_history_song_$index") { play(history.map { it.song }, index) }
                }
            }
        } else {
            MusicRecognitionContent(state, permissionMessage, bottom,
                onAction = { when (state.phase) {
                    RecognitionPhase.RECORDING -> if (serviceRunning) RecognitionDesktopService.finish(context) else controller.finishRecording()
                    RecognitionPhase.RECOGNIZING -> if (serviceRunning) RecognitionDesktopService.stop(context) else controller.cancel()
                    else -> begin()
                } }, onSettings = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }, onPlay = play, onDesktop = { begin(desktop = true) }, desktopStarting = desktopStarting,
                source = source, onSource = { if (!state.busy) source = it })
        }
    }
}

@Composable internal fun MusicRecognitionContent(state: RecognitionState, permissionMessage: String?, bottom: androidx.compose.ui.unit.Dp,
    onAction: () -> Unit, onSettings: () -> Unit, onPlay: (List<Song>, Int) -> Unit,
    onDesktop: (() -> Unit)? = null, desktopStarting: Boolean = false,
    source: RecognitionAudioSource = RecognitionAudioSource.MICROPHONE, onSource: ((RecognitionAudioSource) -> Unit)? = null) {
    val colors = MiuixTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Center the listening group in the usable area above the floating player, rather than
        // anchoring a fixed-height stack to the top of a full-screen scene.
        val usableHeight = (maxHeight - bottom - 24.dp).coerceAtLeast(0.dp)
        val orbSize = minOf(maxWidth - 44.dp,
            if (state.songs.isEmpty()) (usableHeight * .48f).coerceIn(190.dp, 300.dp)
            else (usableHeight * .34f).coerceIn(160.dp, 220.dp))
        val topSpace = if (state.songs.isEmpty() && permissionMessage == null)
            ((usableHeight - orbSize - 238.dp) / 2).coerceIn(12.dp, 64.dp) else 12.dp
        LazyColumn(Modifier.fillMaxSize().padding(bottom = bottom + 12.dp).testTag("recognition_content"), horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = topSpace, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("让好音乐，有名有姓", fontSize = 25.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                    Text(if (source == RecognitionAudioSource.SYSTEM) "识别手机里正在播放的音乐" else "靠近音源，听一小段就好", fontSize = 14.sp, color = colors.onSurfaceVariantSummary)
                    if (onSource != null) Row(Modifier.clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainer).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (if (Build.VERSION.SDK_INT >= 29) RecognitionAudioSource.entries else listOf(RecognitionAudioSource.MICROPHONE)).forEach { option ->
                            Text(if (option == RecognitionAudioSource.SYSTEM) "系统音频" else "麦克风",
                                Modifier.clip(RoundedCornerShape(16.dp)).background(if (source == option) colors.primary.copy(alpha = .12f) else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable(enabled = !state.busy, role = Role.Button) { onSource(option) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp).testTag("recognition_source_${option.name}"),
                                color = if (source == option) colors.primary else colors.onSurfaceVariantSummary, fontSize = 13.sp)
                        }
                    }
                }
            }
            item {
                RecognitionOrb(state, orbSize, onAction)
            }
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(when (state.phase) {
                        RecognitionPhase.RECORDING -> "正在聆听 · ${state.seconds} 秒"
                        RecognitionPhase.RECOGNIZING -> "正在识别音乐…"
                        RecognitionPhase.MATCHED -> "找到这首歌了"
                        RecognitionPhase.NO_MATCH -> "再听一次试试"
                        RecognitionPhase.ERROR -> "这次没能完成识别"
                        else -> "点击开始听歌识曲"
                    }, Modifier.testTag("recognition_status"), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(when (state.phase) {
                        RecognitionPhase.RECORDING -> if (state.seconds < 3) "至少聆听 3 秒，最长 10 秒" else "点击中央按钮，立即开始识别"
                        RecognitionPhase.RECOGNIZING -> "点击中央按钮可取消"
                        RecognitionPhase.MATCHED -> "点击歌曲即可播放，也可继续识别"
                        else -> state.message ?: "支持识别正在播放的歌曲原声"
                    }, fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                    permissionMessage?.let { Text(it, Modifier.testTag("recognition_permission_message"), color = colors.error, fontSize = 13.sp)
                        Text("去设置", Modifier.clickable(role = Role.Button, onClick = onSettings).padding(10.dp), color = colors.primary, fontSize = 14.sp) }
                }
            }
            itemsIndexed(state.songs, key = { _, song -> song.id }) { index, song ->
                RecognitionSongCard(song, if (index == 0) "网易云识别结果" else "你也许在听", "recognition_result_$index") { onPlay(state.songs, index) }
            }
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (onDesktop != null) Text(if (desktopStarting) "正在开启桌面识曲…" else "桌面灵动岛识曲  ↗",
                    Modifier.clip(RoundedCornerShape(20.dp)).background(colors.primary.copy(alpha = .08f))
                        .clickable(enabled = !state.busy && !desktopStarting, role = Role.Button, onClick = onDesktop)
                        .padding(horizontal = 18.dp, vertical = 12.dp).testTag("recognition_desktop"),
                    color = colors.primary.copy(alpha = if (state.busy) .45f else 1f), fontSize = 14.sp)
                Text((if (source == RecognitionAudioSource.SYSTEM) "需系统录制/投射授权，仅采集声音，不录制画面\n部分应用不允许采集，可切换麦克风\n" else "识别时使用麦克风\n") +
                    "音频指纹发送至网易云，仅保存歌曲记录\n桌面模式关闭灵动岛或锁屏即停，普通模式离页即停",
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp).testTag("recognition_disclosure"), fontSize = 11.sp,
                    lineHeight = 18.sp, color = colors.onSurfaceVariantSummary.copy(alpha = .75f), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable private fun RecognitionOrb(state: RecognitionState, diameter: androidx.compose.ui.unit.Dp, onAction: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val active = state.busy
    val pulse = if (active) {
        val transition = rememberInfiniteTransition(label = "recognition")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "ripple").value
    } else 0f
    Box(Modifier.fillMaxWidth().height(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val radius = size.minDimension / 2
            drawCircle(colors.primary.copy(alpha = .035f), radius)
            drawCircle(colors.primary.copy(alpha = .055f), radius * .79f)
            drawCircle(colors.primary.copy(alpha = .09f), radius * (.57f + state.level * .05f))
            if (active) drawCircle(colors.primary.copy(alpha = (1 - pulse) * .12f), radius * (.54f + pulse * .46f))
        }
        Box(Modifier.size(diameter * .486f).clip(CircleShape).background(colors.primary)
            .clickable(role = Role.Button, onClick = onAction).testTag("recognition_action"), contentAlignment = Alignment.Center) {
            Icon(if (state.phase == RecognitionPhase.RECORDING) MusicIcons.Stop else if (state.phase == RecognitionPhase.RECOGNIZING) MusicIcons.Close else MusicIcons.Microphone,
                when (state.phase) { RecognitionPhase.RECORDING -> "停止录音并识别"; RecognitionPhase.RECOGNIZING -> "取消识别"; else -> "开始听歌识曲" },
                Modifier.size(diameter * .2f), tint = colors.onPrimary)
        }
    }
}

@Composable private fun RecognitionSongCard(song: Song, subtitle: String, tag: String, onPlay: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surfaceContainer)
        .clickable(role = Role.Button, onClick = onPlay).testTag(tag).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MusicCover(song.cover, Modifier.size(62.dp), cornerRadius = 14.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(subtitle, fontSize = 11.sp, color = colors.primary)
            Text(song.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artists, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurfaceVariantSummary)
        }
        Icon(MusicIcons.Play, "播放", Modifier.size(24.dp), tint = colors.onSurface)
    }
}

@Composable private fun RecognitionIconButton(description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tag: String, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(CircleShape).background(MiuixTheme.colorScheme.surfaceContainer)
        .clickable(role = Role.Button, onClick = onClick).testTag(tag), contentAlignment = Alignment.Center) {
        Icon(icon, description, Modifier.size(22.dp))
    }
}

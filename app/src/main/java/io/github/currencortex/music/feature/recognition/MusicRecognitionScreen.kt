package io.github.currencortex.music.feature.recognition

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
    val state by controller.state.collectAsStateWithLifecycle()
    val historyStore = remember { RecognitionHistory(context) }
    var history by remember { mutableStateOf(historyStore.read()) }
    var showingHistory by remember { mutableStateOf(false) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var pendingRecord by remember { mutableStateOf(false) }
    BackHandler(showingHistory) { showingHistory = false }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingRecord = granted && lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (granted && lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            pendingRecord = false; permissionMessage = null; controller.start()
        } else if (!granted) permissionMessage = "需要麦克风权限才能识曲，可在系统设置中开启"
    }
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) controller.cancel()
            if (event == Lifecycle.Event.ON_RESUME && pendingRecord) {
                pendingRecord = false; permissionMessage = null
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) controller.start()
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); pendingRecord = false; controller.cancel() }
    }
    LaunchedEffect(state.phase, state.songs) {
        if (state.phase == RecognitionPhase.MATCHED && state.songs.isNotEmpty())
            history = withContext(Dispatchers.IO) { historyStore.add(state.songs.first()) }
    }
    fun begin() {
        permissionMessage = null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) controller.start()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    val colors = MiuixTheme.colorScheme
    val bottom = LocalMusicBottomInset.current
    Column(Modifier.fillMaxSize().background(colors.background).testTag("music_recognition")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RecognitionIconButton("返回", MusicIcons.Back, "recognition_back", { if (showingHistory) showingHistory = false else { controller.cancel(); onBack() } })
            Text(if (showingHistory) "识别记录" else "听歌识曲", Modifier.weight(1f), fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
            if (showingHistory) {
                Text("清空", Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) {
                    historyStore.clear(); history = emptyList()
                }.padding(10.dp).testTag("recognition_clear_history"), color = colors.primary, fontSize = 14.sp)
            } else RecognitionIconButton("识别记录", MusicIcons.Update, "recognition_history", { controller.cancel(); showingHistory = true })
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
                    RecognitionPhase.RECORDING -> controller.finishRecording()
                    RecognitionPhase.RECOGNIZING -> controller.cancel()
                    else -> begin()
                } }, onSettings = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }, onPlay = play)
        }
    }
}

@Composable internal fun MusicRecognitionContent(state: RecognitionState, permissionMessage: String?, bottom: androidx.compose.ui.unit.Dp,
    onAction: () -> Unit, onSettings: () -> Unit, onPlay: (List<Song>, Int) -> Unit) {
    val colors = MiuixTheme.colorScheme
    LazyColumn(Modifier.fillMaxSize().testTag("recognition_content"), horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = bottom + 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("让好音乐，有名有姓", fontSize = 25.sp, fontWeight = FontWeight.Medium)
                Text("靠近音源，听一小段就好", fontSize = 14.sp, color = colors.onSurfaceVariantSummary)
            }
        }
        item {
            RecognitionOrb(state, onAction)
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
            Text("识别时使用麦克风，并将音频指纹发送至网易云\n离开页面即停止录音，仅保存识别到的歌曲记录",
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), fontSize = 11.sp,
                lineHeight = 18.sp, color = colors.onSurfaceVariantSummary.copy(alpha = .75f))
        }
    }
}

@Composable private fun RecognitionOrb(state: RecognitionState, onAction: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val active = state.busy
    val pulse = if (active) {
        val transition = rememberInfiniteTransition(label = "recognition")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "ripple").value
    } else 0f
    Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(280.dp)) {
            val radius = size.minDimension / 2
            drawCircle(colors.primary.copy(alpha = .035f), radius)
            drawCircle(colors.primary.copy(alpha = .055f), radius * .79f)
            drawCircle(colors.primary.copy(alpha = .09f), radius * (.57f + state.level * .05f))
            if (active) drawCircle(colors.primary.copy(alpha = (1 - pulse) * .12f), radius * (.54f + pulse * .46f))
        }
        Box(Modifier.size(136.dp).clip(CircleShape).background(colors.primary)
            .clickable(role = Role.Button, onClick = onAction).testTag("recognition_action"), contentAlignment = Alignment.Center) {
            Icon(if (state.phase == RecognitionPhase.RECORDING) MusicIcons.Stop else if (state.phase == RecognitionPhase.RECOGNIZING) MusicIcons.Close else MusicIcons.Microphone,
                when (state.phase) { RecognitionPhase.RECORDING -> "停止录音并识别"; RecognitionPhase.RECOGNIZING -> "取消识别"; else -> "开始听歌识曲" },
                Modifier.size(56.dp), tint = colors.onPrimary)
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

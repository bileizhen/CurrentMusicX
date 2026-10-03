package io.github.currencortex.music.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.ui.component.*
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun PlayerScreen(vm: PlayerViewModel, onBack: () -> Unit, onToggle: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var showQuality by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp).testTag("player_screen")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton("返回", onClick = onBack, modifier = Modifier.testTag("navigate_back"))
            Text("正在播放", Modifier.weight(1f))
            TextButton(settings.quality.label, onClick = { showQuality = !showQuality })
        }
        if (showQuality) LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(AudioQuality.entries) { quality -> TextButton(quality.label, onClick = { vm.quality(quality); showQuality = false }) }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            if (maxWidth >= 600.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                PlayerControls(vm, onToggle, Modifier.weight(1f))
                LyricsPanel(vm, Modifier.weight(1f))
            } else if (showLyrics) LyricsPanel(vm, Modifier.fillMaxSize())
            else PlayerControls(vm, onToggle, Modifier.fillMaxSize())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(queue.mode.label, onClick = { vm.player.setMode(PlaybackMode.entries[(queue.mode.ordinal + 1) % PlaybackMode.entries.size]) })
            TextButton(if (showLyrics) "封面" else "歌词", onClick = { showLyrics = !showLyrics }, modifier = Modifier.testTag("open_lyrics"))
            TextButton("队列 ${queue.songs.size}", onClick = { showQueue = !showQueue })
        }
        if (showQueue) MusicDialog("播放队列", onDismiss = { showQueue = false }) {
            TextButton("清空队列", onClick = { vm.player.clear(); showQueue = false })
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                itemsIndexed(queue.songs) { index, song ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton((if (index == queue.index) "▶ " else "") + song.name, onClick = { vm.player.select(index) }, modifier = Modifier.weight(1f))
                        TextButton("移除", onClick = { vm.player.remove(index) })
                    }
                }
            }
        }
        state.error?.let { Text(it) }
    }
}
@Composable private fun PlayerControls(vm: PlayerViewModel, onToggle: () -> Unit, modifier: Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val song = queue.current
    var drag by remember { mutableStateOf<Float?>(null) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        MusicCover(song?.cover.orEmpty(), Modifier.widthIn(max = 340.dp).fillMaxWidth(.85f).aspectRatio(1f), pixels = 800)
        Text(song?.name ?: "还没有选择歌曲", fontSize = 24.sp)
        Text(song?.artists.orEmpty())
        if (state.loading) Text("正在加载音源…")
        val duration = state.durationMs.coerceAtLeast(1)
        Slider(value = drag ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = { drag = it }, onValueChangeFinished = { drag?.let { vm.player.seek((it * duration).toLong()) }; drag = null },
            valueRange = 0f..1f, modifier = Modifier.fillMaxWidth().testTag("player_seek").semantics {
                setProgress { progress -> vm.player.seek((progress.coerceIn(0f, 1f) * duration).toLong()); true }
            })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(state.positionMs)); Text(formatTime(state.durationMs))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton("上一首", onClick = { vm.player.previous() }, enabled = song != null)
            TextButton(if (state.playing) "暂停" else "播放", onClick = onToggle, enabled = song != null, modifier = Modifier.testTag("player_toggle"))
            TextButton("下一首", onClick = { vm.player.next() }, enabled = song != null)
        }
    }
}
@Composable private fun LyricsPanel(vm: PlayerViewModel, modifier: Modifier) {
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val player by vm.state.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    var pausedUntil by remember { mutableLongStateOf(0L) }
    var following by remember { mutableStateOf(true) }
    val active = lyrics.lines.indexOfLast { it.t <= player.positionMs }.coerceAtLeast(0)
    LaunchedEffect(list) {
        list.interactionSource.interactions.collect { if (it is DragInteraction.Start) {
            pausedUntil = android.os.SystemClock.elapsedRealtime() + 4000; following = false
        } }
    }
    LaunchedEffect(active, pausedUntil, following) {
        val wait = pausedUntil - android.os.SystemClock.elapsedRealtime()
        if (wait > 0) delay(wait)
        following = true
        if (lyrics.lines.isNotEmpty()) list.animateScrollToItem(active)
    }
    Column(modifier.testTag("lyrics_panel")) {
        if (lyrics.loading) Text("正在加载歌词…")
        lyrics.error?.let { Text(it) }
        if (!lyrics.loading && lyrics.lines.isEmpty()) Text("暂无歌词")
        if (!following) TextButton("回到当前歌词", onClick = { pausedUntil = 0; following = true })
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(vertical = 80.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            itemsIndexed(lyrics.lines) { index, line ->
                Column(Modifier.fillMaxWidth().clickable { vm.player.seek(line.t) }.padding(8.dp)) {
                    Text(line.txt, fontSize = if (index == active) 24.sp else 19.sp,
                        color = if (index == active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
                    if (line.trans.isNotBlank()) Text(line.trans)
                }
            }
        }
    }
}
private fun formatTime(value: Long): String { val seconds = value.coerceAtLeast(0) / 1000; return "%d:%02d".format(seconds / 60, seconds % 60) }

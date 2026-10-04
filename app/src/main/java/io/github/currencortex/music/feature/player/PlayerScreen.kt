package io.github.currencortex.music.feature.player

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.feature.lyrics.ui.*
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

private enum class PlayerContent { COVER, LYRICS }
private enum class PlayerOverlay { NONE, QUEUE, OPTIONS, QUALITY, ACTIONS, MODE }

@Composable fun PlayerScreen(vm: PlayerViewModel, onBack: () -> Unit, onToggle: () -> Unit,
    actions: (@Composable (io.github.currencortex.music.data.song.Song) -> Unit)? = null,
    onCast: (() -> Unit)? = null, onRoom: (() -> Unit)? = null, onDialogActive: (Boolean) -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var content by rememberSaveable { mutableStateOf(PlayerContent.COVER) }
    var overlay by rememberSaveable { mutableStateOf(PlayerOverlay.NONE) }
    var translation by rememberSaveable { mutableStateOf(true) }
    var romanization by rememberSaveable { mutableStateOf(false) }
    var wordAnimation by rememberSaveable { mutableStateOf(true) }
    var effects by rememberSaveable { mutableStateOf(true) }
    val menuHost = LocalSongMenu.current
    val dismiss = { overlay = PlayerOverlay.NONE }
    LaunchedEffect(overlay) { onDialogActive(overlay != PlayerOverlay.NONE) }
    DisposableEffect(Unit) { onDispose { onDialogActive(false) } }
    val indication = LocalIndication.current
    val colors = remember { darkColorScheme(primary = Color.White, onPrimary = Color(0xFF282629),
        background = Color(0xFF262428), surface = Color(0xFF262428)) }
    // The controller overload also supplies the white default content color for overlay titles.
    MiuixTheme(controller = remember { ThemeController(colorSchemeMode = ColorSchemeMode.Dark, isDark = true, darkColors = colors) }) {
    CompositionLocalProvider(LocalIndication provides indication) {
    Box(Modifier.fillMaxSize().testTag("player_screen")) {
        PlayerBackdrop(queue.current?.cover.orEmpty(), Modifier.matchParentSize())
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 24.dp).testTag("player_safe_content")) {
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerIconButton(PlayerIcon.COLLAPSE, "收起播放器", onBack, Modifier.testTag("navigate_back"))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(settings.quality.label, Modifier.clickable(enabled = state.mode == PlayerMode.LOCAL, role = Role.Button) { overlay = PlayerOverlay.QUALITY }
                        .padding(horizontal = 16.dp, vertical = 14.dp), color = Color.White.copy(alpha = .55f), fontSize = 12.sp)
                }
                PlayerIconButton(PlayerIcon.MORE, "播放与歌词设置", { overlay = PlayerOverlay.OPTIONS }, Modifier.testTag("lyrics_options"))
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                if (maxWidth >= 600.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        CoverContent(vm, Modifier.weight(1f))
                        PlayerTransport(vm, onToggle)
                    }
                    LyricsPanel(vm, Modifier.weight(1.15f).fillMaxHeight(), translation, romanization, wordAnimation, effects)
                } else Column(Modifier.fillMaxSize()) {
                    AnimatedContent(content, Modifier.weight(1f).fillMaxWidth(), transitionSpec = {
                        fadeIn(tween(240)) togetherWith fadeOut(tween(160))
                    }, label = "cover and lyrics") { shown ->
                        if (shown == PlayerContent.LYRICS) Column(Modifier.fillMaxSize()) {
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                MusicCover(queue.current?.cover.orEmpty(), Modifier.size(46.dp), pixels = 160)
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(queue.current?.name ?: "还没有选择歌曲", color = Color.White,
                                        fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(queue.current?.artists.orEmpty(), fontSize = 13.sp, color = Color.White.copy(alpha = .55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            LyricsPanel(vm, Modifier.weight(1f).fillMaxWidth(), translation, romanization, wordAnimation, effects)
                        } else CoverContent(vm, Modifier.fillMaxSize())
                    }
                    PlayerTransport(vm, onToggle)
                }
            }
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                PlayerIconButton(PlayerIcon.LYRICS, if (content == PlayerContent.LYRICS) "显示封面" else "显示歌词", {
                    content = if (content == PlayerContent.LYRICS) PlayerContent.COVER else PlayerContent.LYRICS
                }, Modifier.testTag("open_lyrics"), selected = content == PlayerContent.LYRICS)
                if (onCast != null) PlayerIconButton(PlayerIcon.CAST, if (state.mode == PlayerMode.CAST) "投屏控制" else "投屏", onCast)
                PlayerIconButton(PlayerIcon.QUEUE, "播放队列，${queue.songs.size} 首", { overlay = PlayerOverlay.QUEUE }, Modifier.testTag("open_player_queue"))
            }
        }
        when (overlay) {
            PlayerOverlay.NONE -> Unit
            PlayerOverlay.QUEUE -> MusicDialog("播放队列 · ${queue.songs.size}", dismiss) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(queue.mode.label, onClick = { overlay = PlayerOverlay.MODE }, enabled = state.mode == PlayerMode.LOCAL, modifier = Modifier.weight(1f))
                    TextButton("清空", onClick = { vm.player.clear(); dismiss() }, enabled = state.mode == PlayerMode.LOCAL)
                }
                LazyColumn(Modifier.heightIn(max = 360.dp).testTag("player_queue_sheet")) {
                    itemsIndexed(queue.songs, key = { index, song -> "${song.id}-$index" }) { index, song ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            MusicDestinationRow((if (index == queue.index) "▶ " else "") + song.name,
                                { vm.player.select(index); dismiss() }, Modifier.weight(1f), summary = song.artists,
                                enabled = state.mode == PlayerMode.LOCAL, chevron = false)
                            TextButton("移除", onClick = { vm.player.remove(index) }, enabled = state.mode == PlayerMode.LOCAL)
                        }
                    }
                }
            }
            PlayerOverlay.MODE -> MusicDialog("播放模式", dismiss) {
                PlaybackMode.entries.forEach { mode -> TextButton((if (mode == queue.mode) "✓ " else "") + mode.label,
                    onClick = { vm.player.setMode(mode); overlay = PlayerOverlay.QUEUE }, enabled = state.mode == PlayerMode.LOCAL) }
            }
            PlayerOverlay.QUALITY -> MusicDialog("播放音质", dismiss) {
                AudioQuality.entries.forEach { quality -> TextButton((if (quality == settings.quality) "✓ " else "") + quality.label,
                    onClick = { vm.quality(quality); dismiss() }, enabled = state.mode == PlayerMode.LOCAL) }
            }
            PlayerOverlay.ACTIONS -> if (queue.current != null) MusicDialog("歌曲操作", dismiss) { actions?.invoke(queue.current!!) }
            PlayerOverlay.OPTIONS -> MusicDialog("播放与歌词", dismiss) {
                if (actions != null && queue.current != null) TextButton("歌曲操作", onClick = {
                    val song = queue.current ?: return@TextButton
                    if (menuHost == null) overlay = PlayerOverlay.ACTIONS
                    else { dismiss(); menuHost(SongMenu(song, {}, {}, {}, extra = { actions(song) }, transport = false)) }
                })
                TextButton("播放音质 · ${settings.quality.label}", onClick = { overlay = PlayerOverlay.QUALITY }, enabled = state.mode == PlayerMode.LOCAL)
                TextButton("播放模式 · ${queue.mode.label}", onClick = { overlay = PlayerOverlay.MODE }, enabled = state.mode == PlayerMode.LOCAL)
                if (onRoom != null) TextButton(if (state.mode == PlayerMode.ROOM) "房间控制" else "一起听", onClick = { dismiss(); onRoom() })
                SettingsSwitch("翻译歌词", translation, { translation = it })
                SettingsSwitch("罗马音", romanization, { romanization = it })
                SettingsSwitch("逐字动画", wordAnimation, { wordAnimation = it })
                SettingsSwitch("歌词景深", effects, { effects = it })
            }
        }
    }
    }
    }
}

@Composable private fun CoverContent(vm: PlayerViewModel, modifier: Modifier) {
    val queue by vm.queue.collectAsStateWithLifecycle()
    val song = queue.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val coverSize = minOf(maxWidth * .92f, (maxHeight - 112.dp).coerceAtLeast(72.dp), 360.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            MusicCover(song?.cover.orEmpty(), Modifier.size(coverSize).testTag("player_cover"), pixels = 800)
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(song?.name ?: "还没有选择歌曲", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(song?.artists.orEmpty(), fontSize = 18.sp, color = Color.White.copy(alpha = .55f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable private fun LyricsPanel(vm: PlayerViewModel, modifier: Modifier, translation: Boolean,
    romanization: Boolean, wordAnimation: Boolean, effects: Boolean) {
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val player by vm.state.collectAsStateWithLifecycle()
    val position = rememberLyricsPosition(player)
    if (lyrics.document.lines.isNotEmpty()) key(player.song?.id, lyrics.document) {
        AppleLyrics(lyrics.document, position, vm.player::seek, modifier, player.canControlPlayback,
            translation, romanization, wordAnimation, effects)
    } else Box(modifier.testTag("lyrics_panel"), contentAlignment = Alignment.Center) {
        if (lyrics.loading) Column(Modifier.fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            MusicPlaceholder(Modifier.fillMaxWidth(.8f).height(30.dp))
            MusicPlaceholder(Modifier.fillMaxWidth().height(30.dp))
            MusicPlaceholder(Modifier.fillMaxWidth(.6f).height(30.dp))
            Text("正在加载歌词", fontSize = 13.sp, color = Color.White.copy(alpha = .45f))
        } else Text(lyrics.error ?: "此刻，让音乐说话", Modifier.padding(28.dp), color = Color.White.copy(alpha = .5f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

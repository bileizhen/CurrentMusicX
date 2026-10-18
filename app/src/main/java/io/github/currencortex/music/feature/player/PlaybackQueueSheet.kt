package io.github.currencortex.music.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.data.song.NeteaseSongActionsRepository
import io.github.currencortex.music.ui.component.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.Icon

internal data class NativeQueueActions(val recommended: Boolean, val description: String,
    val onRecommendation: (Boolean) -> Unit, val onDownload: () -> Unit, val onCollect: () -> Unit,
    val onSongMenu: (Song) -> Unit)

internal class QueuePageMotion {
    val animation = Animatable(0f)
    var dragged by mutableStateOf<Float?>(null)
    val progress get() = dragged ?: animation.value
}
internal val QueuePageProgress = SemanticsPropertyKey<Float>("QueuePageProgress")

@Composable fun PlaybackQueueSheet(vm: PlayerViewModel, onDismiss: () -> Unit,
    actions: (@Composable (Song) -> Unit)? = null) {
    PlaybackQueuePage(vm, remember { QueuePageMotion() }, true, "mini_queue_sheet", onDismiss, actions)
}

@Composable internal fun PlaybackQueuePage(vm: PlayerViewModel, motion: QueuePageMotion,
    backdrop: Boolean, tag: String, onDismiss: () -> Unit, songActions: (@Composable (Song) -> Unit)? = null) {
    val queue by vm.queue.collectAsStateWithLifecycle()
    val player by vm.state.collectAsStateWithLifecycle()
    val loading by vm.heartLoading.collectAsStateWithLifecycle()
    val actions by vm.actions.collectAsStateWithLifecycle()
    val together by vm.together.state.collectAsStateWithLifecycle()
    val sharedQueue by vm.together.queue.collectAsStateWithLifecycle()
    val shared = together.active
    val displayed = if (shared) sharedQueue else queue
    val openMenu = LocalSongMenu.current
    var downloading by remember { mutableStateOf(false) }
    var collecting by remember { mutableStateOf(false) }
    var batch by remember { mutableStateOf<List<Song>>(emptyList()) }
    val nativeActions = if (shared) NativeQueueActions(together.room?.openHeartRcmd == true,
        together.room?.playlistCopywriting.orEmpty().ifBlank { "根据双方口味，心动推荐" }, vm.together::setRecommendation,
        { batch = displayed.songs.toList(); downloading = true },
        { batch = displayed.songs.toList(); collecting = true },
        { song -> openMenu?.invoke(SongMenu(song, { vm.together.selectSong(song.id) },
            { vm.player.add(song, true) }, { vm.player.add(song) }, extra = { songActions?.invoke(song) })) }) else null
    PlaybackQueueContent(displayed, if (shared) !together.busy else player.mode == PlayerMode.LOCAL,
        if (shared) together.queueLoading else loading,
        !shared && NeteaseSongActionsRepository.songId(queue.current) != null,
        if (shared) together.error ?: together.queueError else actions.error,
        motion, backdrop, tag,
        onSelect = { index -> if (shared) displayed.songs.getOrNull(index)?.let { vm.together.selectSong(it.id) } else vm.player.select(index) },
        onRemove = { index -> if (shared) displayed.songs.getOrNull(index)?.let { vm.together.removeSong(it.id) } else vm.player.remove(index) },
        onClear = { if (shared) vm.together.clearQueue() else vm.player.clear() },
        onMode = vm::setPlaybackMode, onDismiss = onDismiss, shared = shared, nativeActions = nativeActions)
    NativeQueueCollectionDialog(vm, batch, collecting) { collecting = false }
    io.github.currencortex.music.feature.download.QueueDownloadDialog(vm.container, batch, downloading) { downloading = false }
}

/** One viewport and queue for both entry points; no separate dialog queue or audio restart on opening. */
@Composable internal fun PlaybackQueueContent(queue: QueueSnapshot, editable: Boolean, loading: Boolean,
    heartAvailable: Boolean, error: String?, motion: QueuePageMotion, backdrop: Boolean, tag: String,
    onSelect: (Int) -> Unit, onRemove: (Int) -> Unit, onClear: () -> Unit,
    onMode: (PlaybackMode) -> Unit, onDismiss: () -> Unit, shared: Boolean = false,
    nativeActions: NativeQueueActions? = null) {
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    val list = rememberLazyListState((queue.index - 2).coerceAtLeast(0))
    val rowKeys = remember(queue.songs) {
        val occurrences = mutableMapOf<Long, Int>()
        queue.songs.map { song ->
            val occurrence = occurrences.getOrDefault(song.id, 0)
            occurrences[song.id] = occurrence + 1
            "${song.id}-$occurrence"
        }
    }
    var modeOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf<Job?>(null) }
    var closing by remember { mutableStateOf(false) }
    var height by remember { mutableFloatStateOf(1f) }
    val fling = with(LocalDensity.current) { 700.dp.toPx() }
    fun settle(close: Boolean) {
        if (closing) return
        settling?.cancel()
        closing = close
        val start = motion.progress
        settling = scope.launch {
            motion.animation.snapTo(start)
            motion.dragged = null
            motion.animation.animateTo(if (close) 0f else 1f,
                tween((280 * (if (close) start else 1 - start)).toInt().coerceAtLeast(120),
                    easing = CubicBezierEasing(.2f, 0f, .2f, 1f)))
            if (close) dismiss()
        }
    }
    fun drag(delta: Float): Float {
        if (closing || modeOpen || confirmClear) return 0f
        if (motion.dragged == null) {
            settling?.cancel()
            motion.dragged = motion.progress
            scope.launch { motion.animation.stop() }
        }
        val before = motion.progress
        motion.dragged = (before - delta / height).coerceIn(0f, 1f)
        return (before - motion.progress) * height
    }
    fun release(velocity: Float) {
        if (motion.dragged != null) settle(if (kotlin.math.abs(velocity) > fling) velocity > 0 else motion.progress < .82f)
    }
    LaunchedEffect(motion) {
        // Opening from the player is driven by its existing pointer stream until release.
        if (motion.dragged == null) settling = scope.launch {
            motion.animation.animateTo(1f, tween(300, easing = CubicBezierEasing(.2f, 0f, .2f, 1f)))
        }
    }
    LaunchedEffect(queue.current?.id) {
        if (queue.index >= 0) list.scrollToItem((queue.index - 2).coerceAtLeast(0))
    }
    DisposableEffect(motion) { onDispose { motion.dragged = null } }
    BackHandler { when {
        confirmClear -> confirmClear = false
        modeOpen -> modeOpen = false
        else -> settle(true)
    } }
    val nested = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput && (motion.dragged != null ||
                    (available.y > 0 && !list.canScrollBackward))) return Offset(0f, drag(available.y))
            return Offset.Zero
        }
        override suspend fun onPreFling(available: Velocity): Velocity {
            if (motion.dragged == null) return Velocity.Zero
            release(available.y)
            return available
        }
    }
    val dragHeader = Modifier.draggable(rememberDraggableState { drag(it) }, Orientation.Vertical,
        onDragStopped = { release(it) })
    val interactions = remember { MutableInteractionSource() }
    BoxWithConstraints(Modifier.fillMaxSize().testTag(tag)
        .semantics { this[QueuePageProgress] = motion.progress; isTraversalGroup = true }
        .pointerInput(Unit) { detectTapGestures {} }) {
        height = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        if (backdrop) PlayerBackdrop(queue.current?.cover.orEmpty(), Modifier.matchParentSize().graphicsLayer { alpha = motion.progress })
        Column(Modifier.fillMaxSize().graphicsLayer { translationY = size.height * (1 - motion.progress) }
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)
            .align(Alignment.TopCenter)) {
            Column(Modifier.fillMaxWidth().then(dragHeader).testTag("queue_drag_handle")) {
                // Leave the island's lane clear; the close hint belongs to the footer.
                Spacer(Modifier.height(48.dp))
                queue.current?.let { song ->
                    Row(Modifier.fillMaxWidth().testTag("queue_current_song")
                        .clickable(interactionSource = interactions, indication = null) { settle(true) }
                        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        MusicCover(song.cover, Modifier.size(48.dp), cornerRadius = 8.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(song.name, color = Color.White, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.queueSummary(), color = Color.White.copy(alpha = .5f), fontSize = 13.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(64.dp)) {
                    Text("${if (queue.index >= 0) queue.index + 1 else 0} / ${queue.songs.size}",
                        Modifier.align(Alignment.CenterStart).padding(start = 8.dp).testTag("queue_position"),
                        fontSize = 13.sp, color = Color.White.copy(alpha = .4f))
                    Text(if (shared) "一起听歌单" else "播放队列", Modifier.align(Alignment.Center), fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    QueueTextAction("清除", { confirmClear = true }, editable && queue.songs.isNotEmpty(),
                        Modifier.align(Alignment.CenterEnd).width(48.dp).testTag("queue_clear"))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .07f)))
                if (nativeActions != null) {
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).heightIn(min = 48.dp).testTag("queue_recommendation")
                            .toggleable(nativeActions.recommended, enabled = editable, role = Role.Checkbox,
                                onValueChange = nativeActions.onRecommendation), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                                if (nativeActions.recommended) Icon(MusicIcons.Check, null, Modifier.size(18.dp), tint = Color.White.copy(alpha = .8f))
                                else Box(Modifier.size(14.dp).border(1.dp, Color.White.copy(alpha = .5f), CircleShape))
                            }
                            Text(nativeActions.description, Modifier.padding(start = 8.dp, end = 8.dp), fontSize = 12.sp, lineHeight = 18.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color.White.copy(alpha = .6f))
                        }
                        QueueIconAction(MusicIcons.Download, "下载全部", editable && queue.songs.isNotEmpty(), nativeActions.onDownload, "queue_download_all")
                        QueueIconAction(MusicIcons.Folder, "收藏全部到歌单", editable && queue.songs.isNotEmpty(), nativeActions.onCollect, "queue_collect_all")
                    }
                }
            }
            if (queue.songs.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth().then(dragHeader), contentAlignment = Alignment.Center) {
                Text("播放队列为空", color = Color.White.copy(alpha = .5f), fontSize = 16.sp)
            } else LazyColumn(Modifier.weight(1f).fillMaxWidth().clipToBounds().testTag("queue_songs").nestedScroll(nested),
                state = list, contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)) {
                itemsIndexed(queue.songs, key = { index, _ -> rowKeys[index] }) { index, song ->
                    Row(Modifier.fillMaxWidth().background(if (index == queue.index) Color.White.copy(alpha = .11f) else Color.Transparent,
                        RoundedCornerShape(14.dp)).testTag("queue_row_$index").animateItem(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).combinedClickable(enabled = editable, interactionSource = remember { MutableInteractionSource() },
                            indication = null, onLongClick = nativeActions?.let { { it.onSongMenu(song) } },
                            onClick = { if (index != queue.index) onSelect(index) })
                            .semantics { selected = index == queue.index }.testTag("queue_song_$index")
                            .padding(horizontal = 8.dp, vertical = 10.dp)) {
                            Text(song.name, color = Color.White.copy(alpha = .9f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.queueSummary(), color = Color.White.copy(alpha = .45f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(Modifier.size(48.dp).testTag("queue_remove_$index").clickable(enabled = editable,
                            interactionSource = remember { MutableInteractionSource() }, indication = null) { onRemove(index) }
                            .semantics { contentDescription = "移除 ${song.name}" }, contentAlignment = Alignment.Center) {
                            Text("−", color = Color.White.copy(alpha = if (editable) .5f else .2f), fontSize = 21.sp)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().then(dragHeader).padding(bottom = 12.dp)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .07f)))
                error?.let { Text(it, color = Color.White.copy(alpha = .55f), fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 10.dp)) }
                if (!shared && !editable) Text("请先退出一起听或结束投屏，再编辑队列", color = Color.White.copy(alpha = .55f), fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 10.dp))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    QueueTextAction(queue.mode.label + if (loading) " · 加载中" else "", { modeOpen = true }, editable,
                        Modifier.testTag("open_player_mode"), pill = true)
                    Spacer(Modifier.width(12.dp))
                    Text(if (shared) { if (editable) "双方共享歌单" else "正在同步操作…" }
                        else if (backdrop) "向下轻扫关闭队列" else "向下轻扫返回播放界面",
                        Modifier.weight(1f).padding(end = 8.dp), color = Color.White.copy(alpha = .4f), fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
        }
        MusicOverlayDialog(show = modeOpen, title = "播放模式", onDismissRequest = { modeOpen = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PlaybackMode.entries.filter { !shared || it != PlaybackMode.HEART }.forEach { mode ->
                TextButton((if (mode == queue.mode) "✓ " else "") + mode.label,
                    { onMode(mode); modeOpen = false }, enabled = editable &&
                        (mode != PlaybackMode.HEART || (!loading && heartAvailable)),
                    modifier = Modifier.testTag("playback_mode_${mode.name}").semantics { selected = mode == queue.mode })
            }
            }
        }
        MusicOverlayDialog(show = confirmClear, title = "清除播放队列？", onDismissRequest = { confirmClear = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (shared) "会同步清空双方的歌单并暂停播放，一起听会话仍会保留。" else "清除后将停止当前播放。")
            TextButton("取消", { confirmClear = false }, modifier = Modifier.testTag("queue_clear_cancel"))
            TextButton("清除", { onClear(); confirmClear = false }, enabled = editable, modifier = Modifier.testTag("queue_clear_confirm"))
            }
        }
    }
}

private fun Song.queueSummary() = listOf(artists, album).filter { it.isNotBlank() }.joinToString(" · ")

@Composable private fun QueueIconAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String,
    enabled: Boolean, onClick: () -> Unit, tag: String) {
    Box(Modifier.size(48.dp).testTag(tag).clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, Modifier.size(22.dp), tint = Color.White.copy(alpha = if (enabled) .65f else .25f))
    }
}

@Composable private fun QueueTextAction(label: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier, pill: Boolean = false) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) .94f else 1f, tween(120), label = "queue button press")
    Box(modifier.heightIn(min = 48.dp).graphicsLayer { scaleX = scale.value; scaleY = scaleX }
        .clickable(enabled = enabled, role = Role.Button, interactionSource = interactions, indication = null, onClick = onClick)
        .padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(label, modifier = if (pill) Modifier.background(Color.White.copy(alpha = .1f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp) else Modifier,
            color = Color.White.copy(alpha = if (enabled) .65f else .25f), fontSize = 13.sp)
    }
}

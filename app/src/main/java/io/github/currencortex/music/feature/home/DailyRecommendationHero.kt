package io.github.currencortex.music.feature.home

import androidx.compose.animation.core.*
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.player.LocalPlayerArtworkRotation
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.exp
import kotlin.math.tanh
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** Browsing never changes the playback queue. Only the explicit play action does. */
@Composable internal fun DailyRecommendationHero(
    songs: List<Song>, loading: Boolean, error: String?, discovery: Boolean,
    playback: PlayerState, onPlay: (Int) -> Unit, onOpen: () -> Unit, onRetry: () -> Unit,
    onConnect: () -> Unit, modifier: Modifier = Modifier, dateLabel: String = "",
    room: RoomSongRequest? = null,
) {
    val colors = MiuixTheme.colorScheme
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedIndex = songs.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 } ?: 0
    val selected = songs.getOrNull(selectedIndex)
    // Keep the same song selected through a refresh or a reordered recommendation response.
    LaunchedEffect(selected?.id) { selectedId = selected?.id }
    val sameSong = selected != null && playback.song?.id == selected.id
    val requesting = selected?.let { room?.pending?.invoke(it.id) } == true
    val canPlay = selected != null && !requesting
    val primaryLabel = when {
        requesting -> "正在提交点歌"
        room != null -> "为房间点歌"
        sameSong && playback.showPause -> "暂停推荐歌曲"
        else -> "播放推荐歌曲"
    }
    Column(modifier.fillMaxWidth().testTag("home_daily_hero")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("每日推荐", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(listOfNotNull(dateLabel.takeIf(String::isNotBlank),
                    if (loading) "更新中" else if (songs.isNotEmpty()) "${songs.size} 首${if (discovery) "新歌推荐" else "为你精选"}" else null).joinToString(" · "),
                    fontSize = 11.sp, color = colors.onSurface.copy(alpha = .48f))
            }
            MusicTextAction("全部 ›", onOpen, Modifier.testTag("open_daily"))
        }
        when {
            songs.isNotEmpty() -> {
                val playSelected = { if (canPlay) onPlay(selectedIndex) }
                key(songs.map { it.id }) {
                    RecommendationCoverFlow(songs, selectedIndex, { selectedId = songs[it].id },
                        sameSong && playback.showPause, playSelected, canPlay, primaryLabel)
                }
                Crossfade(selected!!, Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    animationSpec = tween(240, easing = FastOutSlowInEasing), label = "recommendation caption") { song ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(song.name, Modifier.testTag("home_daily_title"), fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artists.ifBlank { song.album }, Modifier.padding(top = 4.dp), fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurface.copy(alpha = .55f))
                    }
                }
                if (sameSong && playback.error != null && room == null) Text(playback.error,
                    Modifier.fillMaxWidth().padding(top = 8.dp), fontSize = 12.sp, textAlign = TextAlign.Center, color = colors.error)
                if (error != null) MusicTextAction("推荐更新失败，点击重试", onRetry, Modifier.testTag("home_daily_retry"), !loading)
            }
            loading -> Column(Modifier.fillMaxWidth().padding(vertical = 20.dp).testTag("home_loading"),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MusicPlaceholder(Modifier.size(176.dp).clip(RoundedCornerShape(18.dp)))
                MusicPlaceholder(Modifier.width(140.dp).height(18.dp))
                MusicPlaceholder(Modifier.width(90.dp).height(12.dp))
            }
            else -> Column(Modifier.fillMaxWidth().padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(MusicIcons.List, null, Modifier.size(34.dp), tint = colors.primary.copy(alpha = .55f))
                Text(if (error != null) "推荐暂时没加载出来" else "今日推荐还没有内容", fontSize = 16.sp)
                Text(error ?: if (discovery) "连接网易云，发现适合你的音乐" else "稍后刷新，再来听点新歌",
                    Modifier.padding(horizontal = 12.dp), fontSize = 12.sp, textAlign = TextAlign.Center,
                    color = colors.onSurface.copy(alpha = .55f))
                MusicTextAction(if (discovery && error == null) "连接网易云 ›" else "重新加载",
                    if (discovery && error == null) onConnect else onRetry, Modifier.testTag("home_daily_retry"))
            }
        }
        if (discovery && songs.isNotEmpty()) MusicTextAction("连接网易云，获取你的专属推荐 ›", onConnect,
            Modifier.align(Alignment.CenterHorizontally).testTag("home_connect_netease"))
    }
}

@Composable private fun RecommendationCoverFlow(
    songs: List<Song>, selected: Int, onSelect: (Int) -> Unit, showRecord: Boolean,
    onPlay: () -> Unit, canPlay: Boolean, playLabel: String,
) {
    val select by rememberUpdatedState(onSelect)
    val scope = rememberCoroutineScope()
    var targetPosition by remember { mutableFloatStateOf(selected.toFloat()) }
    val position = remember { Animatable(selected.toFloat()) }
    var release by remember { mutableIntStateOf(0) }
    var releaseVelocity by remember { mutableStateOf<Float?>(null) }
    var dragPosition by remember { mutableFloatStateOf(selected.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val renderedPosition = remember { derivedStateOf { if (dragging) dragPosition else position.value } }
    // Only crossing the midpoint changes composition/draw order. All per-frame movement is in graphicsLayer.
    val foreground = remember { derivedStateOf { renderedPosition.value.roundToInt() } }
    LaunchedEffect(selected, release) {
        val from = Math.floorMod(targetPosition.roundToInt(), songs.size)
        val difference = selected - from
        val delta = when {
            difference > songs.size / 2 -> difference - songs.size
            difference < -songs.size / 2 -> difference + songs.size
            else -> difference
        }
        targetPosition += delta
        if (!dragging) {
            val velocity = releaseVelocity ?: position.velocity
            releaseVelocity = null
            position.animateTo(targetPosition, spring(dampingRatio = .94f, stiffness = 135f, visibilityThreshold = .001f),
                initialVelocity = velocity)
        }
    }
    val recordReveal = animateFloatAsState(if (showRecord) 1f else 0f,
        spring(dampingRatio = .94f, stiffness = 115f, visibilityThreshold = .001f), label = "recommendation record")
    val rotation = LocalPlayerArtworkRotation.current
    val density = LocalDensity.current
    val accent = MiuixTheme.colorScheme.primary
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("home_daily_deck")
        .semantics {
            stateDescription = "第 ${selected + 1} 首，共 ${songs.size} 首"
            customActions = listOf(
                CustomAccessibilityAction("上一首推荐") { if (songs.size > 1) { onSelect(Math.floorMod(selected - 1, songs.size)); true } else false },
                CustomAccessibilityAction("下一首推荐") { if (songs.size > 1) { onSelect((selected + 1) % songs.size); true } else false })
        }, contentAlignment = Alignment.Center) {
        val coverSize = (maxWidth / 2.38f).coerceIn(128.dp, 184.dp)
        val gestureDistance = with(density) { (coverSize * .65f).toPx() }
        val stage = Modifier.fillMaxWidth().height(coverSize + 40.dp)
            .pointerInput(songs.map { it.id }, gestureDistance) {
                var start = 0f
                var pulled = 0f
                var nearest = selected
                val velocityTracker = VelocityTracker()
                fun finish(cancelled: Boolean) {
                    val released = dragPosition
                    val velocity = if (cancelled || songs.size <= 1) 0f else -velocityTracker.calculateVelocity().x / gestureDistance
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        position.snapTo(released)
                        targetPosition = released.roundToInt().toFloat()
                        if (!cancelled) {
                            // Preserve release speed, with at most one additional card of momentum.
                            targetPosition = (released + velocity * .12f).coerceIn(released - 1f, released + 1f).roundToInt().toFloat()
                            select(Math.floorMod(targetPosition.roundToInt(), songs.size))
                        }
                        releaseVelocity = velocity
                        dragging = false
                        release++
                    }
                }
                detectHorizontalDragGestures(
                    onDragStart = {
                        start = renderedPosition.value; pulled = 0f; dragPosition = start; dragging = true
                        nearest = start.roundToInt()
                        velocityTracker.resetTracking()
                        scope.launch(start = CoroutineStart.UNDISPATCHED) { position.stop() }
                    },
                    onDragCancel = { finish(true) }, onDragEnd = { finish(false) },
                ) { change, amount ->
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    pulled += amount
                    if (songs.size > 1) {
                        dragPosition = start - pulled / gestureDistance
                        val next = dragPosition.roundToInt()
                        if (next != nearest) {
                            nearest = next
                            select(Math.floorMod(next, songs.size))
                        }
                    }
                }
            }
        Box(stage, contentAlignment = Alignment.Center) {
            val visible = minOf(songs.size, 7)
            val centre = foreground.value
            for (slot in centre - (visible - 1) / 2..centre + visible / 2) key(slot) {
                val index = Math.floorMod(slot, songs.size)
                Box(Modifier.size(coverSize).zIndex(10f - abs(slot - centre)).graphicsLayer {
                    val relative = slot - renderedPosition.value
                    val depth = abs(relative)
                    val scale = exp(-depth * .20f)
                    translationX = tanh(relative * .70f) * coverSize.toPx() * .98f
                    translationY = (1f - scale) * coverSize.toPx() * .42f
                    scaleX = scale; scaleY = scale
                    rotationZ = tanh(relative * .55f) * 12f
                    alpha = (1f - (depth - 1f).coerceAtLeast(0f) * .14f).coerceIn(.35f, 1f)
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }) {
                    if (index == selected) VinylRecord(accent, songs[index].cover,
                        Modifier.size(coverSize * .9f).align(Alignment.Center).graphicsLayer {
                            translationX = coverSize.toPx() * .52f * recordReveal.value
                            rotationZ = rotation?.value ?: 0f
                        }.testTag("home_daily_record"))
                    val artwork = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                        .testTag("home_daily_cover_${songs[index].id}").semantics { contentDescription = songs[index].name }
                        .clickable(enabled = index != selected || canPlay, role = Role.Button,
                            onClickLabel = if (index == selected) playLabel else "浏览${songs[index].name}") {
                            if (index == selected) onPlay() else onSelect(index)
                        }
                    DeckArtwork(songs[index].cover, artwork)
                }
            }
        }
        PreloadMusicCovers((-4..4).map { songs[Math.floorMod(selected + it, songs.size)].cover }, 480)
    }
}

@Composable private fun DeckArtwork(cover: String, modifier: Modifier) {
    val context = LocalContext.current
    val model = remember(context, cover) { ImageRequest.Builder(context).data(coverRequestUrl(cover, 480)).size(480).build() }
    Box(modifier.background(MiuixTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
        Icon(MusicIcons.List, null, Modifier.size(28.dp), tint = MiuixTheme.colorScheme.onSurface.copy(alpha = .16f))
        if (cover.isNotBlank()) AsyncImage(model, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable private fun VinylRecord(accent: Color, cover: String, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxSize()) {
        val radius = size.minDimension / 2
        drawCircle(Brush.radialGradient(listOf(Color(0xFF34363A), Color(0xFF121315), Color(0xFF292B2F)), radius = radius))
        for (ring in 0..11) drawCircle(Color.White.copy(alpha = if (ring % 3 == 0) .12f else .045f),
            radius * (.44f + ring * .042f), style = Stroke(.7.dp.toPx()))
        drawArc(Color.White.copy(alpha = .13f), 210f, 48f, false,
            topLeft = Offset(radius * .16f, radius * .16f), size = size * .84f, style = Stroke(2.dp.toPx()))
        drawCircle(accent, radius * .26f)
        drawCircle(Color(0xFF101113), radius * .055f)
    }
    DeckArtwork(cover, Modifier.fillMaxSize(.26f).clip(CircleShape))
    Canvas(Modifier.fillMaxSize(.05f)) { drawCircle(Color(0xFF101113)) }
    }
}

package io.github.currencortex.music.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.MusicCover
import io.github.currencortex.music.ui.component.MusicDestinationRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.sign

internal val PlayerSideProgress = SemanticsPropertyKey<Float>("PlayerSideProgress")
private const val SIDE_TRAVEL = .92f

internal class PlayerSidePageState(private val scope: CoroutineScope) {
    private val animation = Animatable(0f)
    private var dragged by mutableStateOf<Float?>(null)
    private var settling: Job? = null
    private var targetOpen = false
    var direction by mutableFloatStateOf(1f)
        private set
    var pagerDragging by mutableStateOf(false)
        private set
    val progress get() = dragged ?: animation.value
    val active by derivedStateOf { dragged != null || progress > .0001f }

    fun begin(openingDirection: Float = direction, fromPager: Boolean = false) {
        val current = progress
        if (!active) direction = openingDirection
        pagerDragging = fromPager
        settling?.cancel()
        dragged = current
        scope.launch(start = CoroutineStart.UNDISPATCHED) { animation.stop() }
    }

    fun drag(delta: Float, travel: Float) {
        dragged?.let { dragged = (it + delta * direction / travel.coerceAtLeast(1f)).coerceIn(0f, 1f) }
    }

    fun end(velocity: Float, flingThreshold: Float, cancelled: Boolean = false) {
        if (dragged == null) return
        pagerDragging = false
        settle(if (cancelled) targetOpen else if (abs(velocity) > flingThreshold) velocity * direction > 0 else progress >= .4f)
    }

    fun close() = settle(false)

    fun returnToPager() {
        settling?.cancel()
        targetOpen = false
        pagerDragging = false
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            animation.snapTo(0f)
            dragged = null
        }
    }

    private fun settle(open: Boolean) {
        val current = progress
        settling?.cancel()
        pagerDragging = false
        targetOpen = open
        settling = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            animation.snapTo(current)
            dragged = null
            animation.animateTo(if (open) 1f else 0f,
                tween((320 * abs((if (open) 1f else 0f) - current)).toInt().coerceAtLeast(120),
                    easing = CubicBezierEasing(.2f, 0f, .2f, 1f)))
        }
    }
}

/** Both pages stay measured at full size. Dragging changes only their drawing layers. */
@Composable internal fun PlayerSidePageLayout(state: PlayerSidePageState, enabled: Boolean,
    openingDirection: () -> Float, pagerScrolling: () -> Boolean, excludedArea: () -> LayoutCoordinates?,
    side: @Composable () -> Unit, content: @Composable () -> Unit) {
    val gesturesEnabled by rememberUpdatedState(enabled)
    val allowedDirection by rememberUpdatedState(openingDirection)
    val scrolling by rememberUpdatedState(pagerScrolling)
    val excluded by rememberUpdatedState(excludedArea)
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val fling = with(LocalDensity.current) { 600.dp.toPx() }
    val flingThreshold by rememberUpdatedState(fling)
    val edgeScroll = remember(state) {
        object : NestedScrollConnection {
            // Let the pager consume movement until its endpoint, then keep the same
            // scroll gesture alive while the side page consumes the remaining distance.
            private fun drag(delta: Float): Offset {
                val travel = (coordinates?.size?.width ?: 1) * SIDE_TRAVEL
                val before = state.progress
                state.drag(delta, travel)
                val consumed = (state.progress - before) * travel * state.direction
                if (state.progress == 0f) state.returnToPager()
                return Offset(consumed, 0f)
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (source == NestedScrollSource.UserInput && state.pagerDragging) drag(available.x) else Offset.Zero

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || !gesturesEnabled ||
                    state.active && !state.pagerDragging) return Offset.Zero
                val direction = allowedDirection()
                if (available.x * direction <= 0f) return Offset.Zero
                if (!state.pagerDragging) state.begin(direction, fromPager = true)
                return drag(available.x)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!state.pagerDragging) return Velocity.Zero
                state.end(available.x, flingThreshold)
                return Velocity(available.x, 0f)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (state.pagerDragging) state.end(available.x, flingThreshold)
                return Velocity.Zero
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF19191B)).testTag("player_side_host")
        .semantics { this[PlayerSideProgress] = state.progress }
        .onGloballyPositioned { coordinates = it }
        .nestedScroll(edgeScroll)
        .pointerInput(state) {
            val velocity = VelocityTracker()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val gestureDirection = if (scrolling()) 0f else allowedDirection()
                if (!gesturesEnabled || (!state.active && gestureDirection == 0f)) return@awaitEachGesture
                val initiallyOpen = state.active
                val root = coordinates ?: return@awaitEachGesture
                if (!initiallyOpen && excluded()?.takeIf { it.isAttached }?.let { area ->
                    val point = area.localPositionOf(root, down.position)
                    point.x in 0f..area.size.width.toFloat() && point.y in 0f..area.size.height.toFloat()
                } == true) return@awaitEachGesture
                velocity.resetTracking()
                velocity.addPosition(down.uptimeMillis, down.position)
                var moved = Offset.Zero
                var started = false
                try {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                        velocity.addPosition(change.uptimeMillis, change.position)
                        if (change.isConsumed) break
                        if (!change.pressed) {
                            if (started) {
                                state.end(velocity.calculateVelocity().x, flingThreshold)
                                started = false
                                change.consume()
                            }
                            break
                        }
                        val delta = change.positionChange()
                        if (!started) {
                            moved += delta
                            if (abs(moved.y) > viewConfiguration.touchSlop && abs(moved.y) >= abs(moved.x)) break
                            if (abs(moved.x) <= viewConfiguration.touchSlop) continue
                            // Only an outward swipe at either end opens a side; inward swipes belong to the pager.
                            if (!initiallyOpen && (moved.x * gestureDirection <= 0f || scrolling() ||
                                allowedDirection() != gestureDirection)) break
                            state.begin(gestureDirection)
                            started = true
                            state.drag(moved.x - sign(moved.x) * viewConfiguration.touchSlop, size.width * SIDE_TRAVEL)
                        } else state.drag(delta.x, size.width * SIDE_TRAVEL)
                        change.consume()
                    }
                } finally {
                    if (started) state.end(0f, flingThreshold, cancelled = true)
                }
            }
        }) {
        if (state.active) Box(Modifier.fillMaxSize().graphicsLayer {
            val p = state.progress
            scaleX = .86f + .14f * p
            scaleY = scaleX
            translationX = -state.direction * size.width * .04f * (1f - p)
            alpha = .65f + .35f * p
        }.testTag("player_side_page")) { side() }
        Box(Modifier.fillMaxSize().graphicsLayer {
            val p = state.progress
            transformOrigin = TransformOrigin(if (state.direction > 0f) 0f else 1f, .5f)
            scaleX = 1f - .28f * p
            scaleY = scaleX
            translationX = state.direction * size.width * SIDE_TRAVEL * p
            shape = RoundedCornerShape((12f * p).dp)
            clip = p > 0f
            shadowElevation = 12.dp.toPx() * p
        }.testTag("player_side_foreground")) {
            Box(Modifier.fillMaxSize().then(if (state.active) Modifier.clearAndSetSemantics {} else Modifier)) { content() }
            if (state.active) Box(Modifier.matchParentSize().testTag("player_side_return")
                .clickable(enabled = enabled, role = Role.Button, onClick = state::close)
                .semantics { contentDescription = "返回播放页" })
        }
    }
}

@Composable internal fun PlayerSidePageContent(song: Song?, quality: String, qualityEnabled: Boolean,
    onQuality: () -> Unit, onEffects: (() -> Unit)?, onShare: () -> Unit, onComments: () -> Unit,
    onActions: (() -> Unit)?) {
    val colors = MiuixTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .padding(start = maxWidth * (1f - SIDE_TRAVEL)).testTag("player_side_scroll"),
            contentPadding = PaddingValues(start = 12.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item { Text("歌曲信息", fontSize = 24.sp, fontWeight = FontWeight.SemiBold) }
            item { Card { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MusicCover(song?.cover.orEmpty(), Modifier.size(64.dp), pixels = 200)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(song?.name ?: "还没有选择歌曲", fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(song?.artists.orEmpty(), color = colors.onSurfaceVariantSummary, fontSize = 13.sp)
                }
            } } }
            if (!song?.album.isNullOrBlank()) item { Card { Column(Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("专辑", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                Text(song!!.album, fontSize = 16.sp)
            } } }
            item { Card { Column {
                MusicDestinationRow("播放音质", onQuality, summary = quality, enabled = qualityEnabled,
                    modifier = Modifier.testTag("side_open_quality"))
                onEffects?.let { MusicDestinationRow("音效", it, modifier = Modifier.testTag("side_open_effects")) }
            } } }
            item { Card { Column {
                MusicDestinationRow("歌曲评论", onComments, enabled = song != null, modifier = Modifier.testTag("side_open_comments"))
                MusicDestinationRow("分享歌曲", onShare, enabled = song != null, modifier = Modifier.testTag("side_share"))
                onActions?.let { MusicDestinationRow("歌曲操作", it, enabled = song != null,
                    modifier = Modifier.testTag("side_song_actions")) }
            } } }
        }
    }
}

package io.github.currencortex.music.feature.library

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.util.LruCache
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.VelocityTracker
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.LocalMusicBottomInset
import io.github.currencortex.music.ui.component.LocalRefreshRatePreference
import io.github.currencortex.music.ui.component.applyViewRefreshRate
import io.github.currencortex.music.ui.component.coverRequestUrl
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import top.yukonga.miuix.kmp.basic.Text
import kotlin.math.*

internal val LocalRecordFlightTarget = staticCompositionLocalOf<() -> Rect?> { { null } }

/** Native Canvas owns movement, cover projection and hit testing in the same frame. */
@Composable internal fun PlaylistDepthRibbon(songs: List<Song>, canPlay: Boolean,
    onPlay: (Int) -> Unit, onPrepare: (Int) -> (() -> Unit), onClose: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var selected by remember(songs) { mutableStateOf<Int?>(null) }
    var paused by remember { mutableStateOf(!ValueAnimator.areAnimatorsEnabled()) }
    val renderer = remember(context) { DepthRibbonView(context) }
    val refreshRate = LocalRefreshRatePreference.current
    val landing by rememberUpdatedState(LocalRecordFlightTarget.current)
    SideEffect { renderer.recordTarget = { landing() } }
    val currentSelection by rememberUpdatedState<(Int?) -> Unit>({ selected = it })
    DisposableEffect(renderer, owner) {
        renderer.onFocus = { currentSelection(it) }
        val observer = LifecycleEventObserver { _, _ ->
            renderer.setRunning(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        owner.lifecycle.addObserver(observer)
        renderer.setRunning(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { owner.lifecycle.removeObserver(observer); renderer.dispose() }
    }
    BackHandler { if (selected != null) renderer.releaseFocus() else onClose() }
    Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0A)).testTag("playlist_depth_ribbon")) {
        AndroidView(factory = { renderer }, update = { it.configure(songs, paused); applyViewRefreshRate(it, refreshRate) },
            modifier = Modifier.fillMaxSize().testTag("depth_ribbon_canvas")
                .onGloballyPositioned { renderer.rootOffset = it.boundsInRoot().topLeft }.semantics {
                contentDescription = "歌曲立体缎带，左右滑动浏览，点击封面查看歌曲，长按查看全景"
                scrollBy { x, y -> renderer.travel(-(x + y) / 150f); true }
                scrollToIndex { index ->
                    if (index in songs.indices) { renderer.focusSong(index); true } else false
                }
                customActions = listOf(
                    CustomAccessibilityAction(if (paused) "继续漫游" else "暂停漫游") { paused = !paused; true },
                    CustomAccessibilityAction("查看当前歌曲") { renderer.focusNearest(); true },
                    CustomAccessibilityAction("返回列表") { onClose(); true },
                )
            })
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
            .padding(bottom = LocalMusicBottomInset.current + 16.dp).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            val song = selected?.let(songs::getOrNull)
            if (song != null) {
                Text(song.name, color = Color.White, fontSize = 21.sp, maxLines = 2)
                Text(listOf(song.artists, song.album).filter(String::isNotBlank).joinToString(" · "),
                    color = Color.White.copy(alpha = .65f), fontSize = 13.sp, maxLines = 2,
                    modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RibbonButton("继续浏览", onClick = renderer::releaseFocus, modifier = Modifier.testTag("depth_ribbon_release"))
                    if (canPlay) RibbonButton("播放歌曲", onClick = {
                        selected?.let { index -> renderer.ejectRecord(songs[index].id, { onPrepare(index) }) { onPlay(index) } }
                    }, accent = true, modifier = Modifier.testTag("depth_ribbon_play"))
                }
            }
        }
    }
}

@Composable private fun RibbonButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, accent: Boolean = false) {
    Box(modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
        .background(if (accent) Color(0xFFB19EEF) else Color(0xFF242227))
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 13.sp, color = if (accent) Color(0xFF17121F) else Color(0xFFDDD9E4))
    }
}

internal class DepthRibbonView(context: Context) : View(context) {
    var recordTarget: () -> Rect? = { null }
    var rootOffset = Offset.Zero
    private var recordFlight: RibbonRecordFlight? = null
    private var onRecordLanded: (() -> Unit)? = null
    private var cancelRecordPreparation: (() -> Unit)? = null
    internal val recordFlightProgress get() = recordFlight?.progress
    private val openedQuad = FloatArray(8)
    var onFocus: (Int?) -> Unit = {}
    private var songs = emptyList<Song>()
    private var path = DepthRibbonPath(1)
    private var position = 0f
    private var velocity = 0f
    private var paused = false
    private var running = false
    private var disposed = false
    private var lastFrame = 0L
    private var focused: Int? = null
    private var focusCard: Int? = null
    private var reveal = false
    private val overview = RibbonRevealTransition()
    internal val revealFraction get() = overview.value
    private var yaw = 0f
    private var touching = false
    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var tracker: VelocityTracker? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val up = RibbonVector(0f, 1f, 0f)
    private var eye = RibbonVector(0f, 0f, 0f)
    private var aim = RibbonVector(0f, 0f, 1f)
    private var initialized = false
    private var settling = true
    private var focusAmount = 0f
    internal var renderedFrames = 0L
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val permits = Semaphore(4)
    private val requests = mutableMapOf<String, Job>()
    private val failed = mutableSetOf<String>()
    private val cache = object : LruCache<String, BitmapShader>(24 * 1024 * 1024) {
        // Every texture is decoded to a bounded 256 px square (ARGB_8888).
        override fun sizeOf(key: String, value: BitmapShader) = 256 * 256 * 4
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()
    private val unitQuad = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
    private class Card {
        val quad = FloatArray(8)
        val backQuad = FloatArray(8)
        var logical = 0
        var depth = 0f
        var alpha = 0f
        var brightness = 1f
    }
    private val pool = Array(72) { Card() }
    private val visible = ArrayList<Card>(72)
    private val depthOrder = compareByDescending<Card> { it.depth }
    private var lastRequestWindow = Int.MIN_VALUE
    private val hold = Runnable {
        if (touching && !dragging && focused == null) setReveal(true)
    }

    init { isClickable = true; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun configure(value: List<Song>, pause: Boolean) {
        if (songs != value) {
            cancelRecord()
            songs = value
            path = DepthRibbonPath(value.size.coerceIn(1, 72))
            position = 0f; focused = null; velocity = 0f; initialized = false
            lastRequestWindow = Int.MIN_VALUE
            requests.values.toList().forEach(Job::cancel); requests.clear(); failed.clear()
            invalidate()
        }
        if (paused != pause) { paused = pause; velocity = 0f; lastFrame = 0L; invalidate() }
    }
    fun setRunning(value: Boolean) {
        if (running == value) return
        running = value; lastFrame = 0L
        if (!value) { cancelRecord(); touching = false; setReveal(false); velocity = 0f; removeCallbacks(hold) }
        else invalidate()
    }
    fun dispose() {
        disposed = true; running = false; cancelRecord(); removeCallbacks(hold)
        tracker?.recycle(); tracker = null; scope.cancel(); requests.clear(); cache.evictAll()
    }
    override fun onDetachedFromWindow() { lastFrame = 0L; removeCallbacks(hold); super.onDetachedFromWindow() }

    fun travel(delta: Float) {
        if (songs.isEmpty()) return
        cancelRecord()
        if (focused != null) { focused = null; onFocus(null) }
        position += delta.coerceIn(-72f, 72f)
        velocity = 0f; setReveal(false); settling = true; invalidate()
    }
    fun focusSong(index: Int) {
        if (index !in songs.indices) return
        val nearest = position.roundToInt()
        val forward = Math.floorMod(index - ribbonSongIndex(nearest, songs.size), songs.size)
        focusLogical(nearest + if (forward > songs.size / 2) forward - songs.size else forward)
    }
    fun focusNearest() { if (songs.isNotEmpty()) focusLogical(position.roundToInt()) }
    private fun focusLogical(logical: Int) {
        cancelRecord()
        position = logical.toFloat(); focused = logical; focusCard = logical; velocity = 0f; setReveal(false)
        settling = true; onFocus(ribbonSongIndex(logical, songs.size)); invalidate()
    }
    fun releaseFocus() { cancelRecord(); focused = null; settling = true; onFocus(null); invalidate() }
    private fun cancelRecord(cancelPreparation: Boolean = true) {
        recordFlight = null; onRecordLanded = null
        if (cancelPreparation) cancelRecordPreparation?.invoke()
        cancelRecordPreparation = null
    }
    fun ejectRecord(songId: Long, prepare: () -> (() -> Unit) = { {} }, onLanded: () -> Unit = {}) {
        if (recordFlight != null) return
        if (!running || !ValueAnimator.areAnimatorsEnabled()) { onLanded(); return }
        val logical = focused ?: return
        if (songs.getOrNull(ribbonSongIndex(logical, songs.size))?.id != songId) return
        val card = visible.firstOrNull { it.logical == logical } ?: run { onLanded(); return }
        val target = recordTarget()?.translate(-rootOffset) ?: run { onLanded(); return }
        val q = card.quad
        val bounds = Rect((0..3).minOf { q[it * 2] }, (0..3).minOf { q[it * 2 + 1] },
            (0..3).maxOf { q[it * 2] }, (0..3).maxOf { q[it * 2 + 1] })
        recordFlight = RibbonRecordFlight(bounds, target, cache.get(songs[ribbonSongIndex(logical, songs.size)].cover))
        onRecordLanded = onLanded
        cancelRecordPreparation = prepare()
        lastFrame = System.nanoTime(); invalidate()
    }
    private fun setReveal(value: Boolean) {
        if (reveal != value && !overview.moving) lastFrame = System.nanoTime()
        reveal = value; overview.reveal(value); settling = true; invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (songs.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                touching = true; dragging = false; velocity = 0f
                downX = event.x; downY = event.y; lastX = event.x
                tracker?.recycle(); tracker = VelocityTracker.obtain().also { it.addMovement(event) }
                postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(event)
                if (hypot(event.x - downX, event.y - downY) > slop) {
                    dragging = true; removeCallbacks(hold)
                    if (reveal) { yaw += (event.x - lastX) / width.coerceAtLeast(1) * 3f; settling = true }
                    else travel(-(event.x - lastX) / (width.coerceAtLeast(1) * .24f))
                    invalidate()
                }
                lastX = event.x
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(hold)
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    if (reveal) setReveal(false)
                    else if (dragging) {
                        tracker?.computeCurrentVelocity(1000)
                        velocity = (-(tracker?.xVelocity ?: 0f) / (width.coerceAtLeast(1) * .24f)).coerceIn(-12f, 12f)
                    } else {
                        performClick()
                        if (focused != null) releaseFocus()
                        else visible.asReversed().firstOrNull { contains(it.quad, event.x, event.y) }?.let { focusLogical(it.logical) }
                    }
                } else { setReveal(false); velocity = 0f }
                touching = false; tracker?.recycle(); tracker = null
                parent.requestDisallowInterceptTouchEvent(false); invalidate()
            }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }

    private fun contains(q: FloatArray, x: Float, y: Float): Boolean {
        var sign = 0f
        for (i in 0..3) {
            val j = (i + 1) % 4
            val cross = (q[j * 2] - q[i * 2]) * (y - q[i * 2 + 1]) - (q[j * 2 + 1] - q[i * 2 + 1]) * (x - q[i * 2])
            if (i == 0) sign = cross else if (sign * cross < 0f) return false
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (songs.isEmpty() || disposed || width == 0 || height == 0) return
        renderedFrames++
        val now = System.nanoTime()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1e9f).coerceIn(0f, .05f)
        lastFrame = now
        val motion = ValueAnimator.areAnimatorsEnabled()
        recordFlight?.let { flight ->
            flight.advance(dt)
            recordTarget()?.let { flight.target = it.translate(-rootOffset) }
            if (!motion || flight.progress >= 1f) {
                val complete = onRecordLanded
                cancelRecord(cancelPreparation = false)
                // Commit playback and mini artwork together, only after the record has landed.
                complete?.invoke()
            }
        }
        if (motion) overview.advance(dt) else overview.snap()
        if (running && !touching && focused == null && motion && overview.value < .001f) {
            position += ((if (paused) 0f else .3f) + velocity) * dt
            velocity *= exp(-4.5f * dt)
            if (abs(velocity) < .005f) velocity = 0f
        }
        val center = path.point(position)
        val direction = path.tangent(position)
        val right = up.cross(direction).unit()
        val focal = height / (2f * tan(Math.toRadians(55.0 / 2).toFloat()))
        var targetEye: RibbonVector
        var targetAim: RibbonVector
        when {
            focused != null -> {
                val distance = max(1.65f, .72f * focal / (width * .76f))
                targetEye = center - direction * distance + up * .12f
                targetAim = center + up * .12f
            }
            else -> { targetEye = path.point(position - 1.5f) + right * .8f + up * .65f; targetAim = path.point(position + 2f) }
        }
        val overviewDistance = path.radius * max(3.4f, 2.6f * height / width)
        targetEye = targetEye.mix(RibbonVector(sin(yaw) * overviewDistance, overviewDistance * .58f,
            cos(yaw) * overviewDistance), overview.value)
        targetAim = targetAim.mix(RibbonVector(0f, 0f, 0f), overview.value)
        val blend = if (!initialized || !motion) 1f else 1f - exp(-8f * dt)
        eye = eye.mix(targetEye, blend); aim = aim.mix(targetAim, blend); initialized = true
        val focusTarget = if (focused != null) 1f else 0f
        focusAmount += (focusTarget - focusAmount) * blend
        settling = overview.moving || (eye - targetEye).length() + (aim - targetAim).length() + abs(focusTarget - focusAmount) > .002f
        val forward = (aim - eye).unit()
        val cameraRight = up.cross(forward).unit()
        val cameraUp = forward.cross(cameraRight).unit()
        val start = floor(position).toInt() - min(8, (path.count - 1) / 2)
        visible.clear()
        for (slot in 0 until path.count) {
            val logical = start + slot
            val point = path.point(logical.toFloat())
            val normal = path.tangent(logical.toFloat())
            val horizontal = up.cross(normal).unit() * .36f
            val vertical = normal.cross(horizontal.unit()).unit() * .36f
            val card = pool[slot]
            card.logical = logical; card.depth = (point - eye).dot(forward)
            if (card.depth < .15f) continue
            val thickness = normal * (if (normal.dot(point - eye) >= 0f) .035f else -.035f)
            var valid = true
            for (corner in 0..3) {
                val world = point + horizontal * (if (corner == 0 || corner == 3) -1f else 1f) +
                    vertical * (if (corner < 2) 1f else -1f) - eye
                val z = world.dot(forward)
                if (z < .1f) { valid = false; break }
                card.quad[corner * 2] = width * .5f + world.dot(cameraRight) * focal / z
                card.quad[corner * 2 + 1] = height * .46f - world.dot(cameraUp) * focal / z
                val back = world + thickness
                val backZ = back.dot(forward).coerceAtLeast(.1f)
                card.backQuad[corner * 2] = width * .5f + back.dot(cameraRight) * focal / backZ
                card.backQuad[corner * 2 + 1] = height * .46f - back.dot(cameraUp) * focal / backZ
            }
            if (!valid) continue
            val q = card.quad
            if ((0..3).all { q[it * 2] < 0 } || (0..3).all { q[it * 2] > width } ||
                (0..3).all { q[it * 2 + 1] < 0 } || (0..3).all { q[it * 2 + 1] > height }) continue
            // Distance changes surface lighting, never its opacity or occlusion.
            val distanceLight = exp(-max(0f, card.depth - 3f) * .055f).coerceAtLeast(.3f)
            val facingLight = .82f + .18f * abs(normal.dot((eye - point).unit()))
            card.brightness = (distanceLight + (.9f - distanceLight) * overview.value) * facingLight
            card.alpha = if (logical != focusCard) 1f - focusAmount else 1f
            if (card.alpha > .025f) visible.add(card)
        }
        visible.sortWith(depthOrder)
        for (card in visible) {
            val song = songs[ribbonSongIndex(card.logical, songs.size)]
            val shader = cache.get(song.cover)
            // A second projected surface gives the sleeve a thin, perspective-correct edge.
            if (matrix.setPolyToPoly(unitQuad, 0, card.backQuad, 0, 4)) {
                val edgeSave = canvas.save()
                canvas.concat(matrix)
                paint.shader = null
                paint.color = android.graphics.Color.BLACK
                paint.alpha = (card.alpha * 65).roundToInt()
                canvas.drawRoundRect(-.018f, .014f, 1.024f, 1.058f, .078f, .078f, paint)
                paint.alpha = (card.alpha * 115).roundToInt()
                canvas.drawRoundRect(-.006f, .012f, 1.012f, 1.038f, .066f, .066f, paint)
                paint.color = 0xFF514D58.toInt()
                paint.alpha = (card.alpha * 255).roundToInt()
                paint.style = Paint.Style.FILL_AND_STROKE
                paint.strokeWidth = .012f
                canvas.drawRoundRect(0f, 0f, 1f, 1f, .06f, .06f, paint)
                paint.style = Paint.Style.FILL
                canvas.restoreToCount(edgeSave)
            }
            val flight = recordFlight?.takeIf { card.logical == focusCard }
            if (flight != null && flight.progress < RibbonRecordFlight.LaunchFraction) flight.draw(canvas, paint)
            val face = if (flight != null) {
                card.quad.copyInto(openedQuad)
                val opening = flight.opening
                // The left edge is the sleeve hinge; perspective shortens and lifts its free edge.
                for (rightCorner in 1..2) {
                    val leftCorner = if (rightCorner == 1) 0 else 3
                    openedQuad[rightCorner * 2] += (card.quad[leftCorner * 2] - card.quad[rightCorner * 2]) * opening * .42f
                    openedQuad[rightCorner * 2 + 1] += (if (rightCorner == 1) -.055f else .055f) * flight.source.height * opening
                }
                openedQuad
            } else card.quad
            if (!matrix.setPolyToPoly(unitQuad, 0, face, 0, 4)) continue
            val save = canvas.save()
            canvas.concat(matrix)
            drawDepthRibbonFace(canvas, paint, shader, card.brightness * (1f - (flight?.opening ?: 0f) * .18f), card.alpha)
            canvas.restoreToCount(save)
        }
        recordFlight?.takeIf { it.progress >= RibbonRecordFlight.LaunchFraction }?.draw(canvas, paint)
        // Texture work happens only at card boundaries, never once per animation frame.
        if (lastRequestWindow != start) { lastRequestWindow = start; requestTextures(start) }
        if (running && motion && ((!paused && focused == null && !touching) || velocity != 0f || settling || recordFlight != null)) postInvalidateOnAnimation()
    }

    private fun requestTextures(start: Int) {
        val wanted = (0 until path.count).map { songs[ribbonSongIndex(start + it, songs.size)].cover }.filter(String::isNotBlank).toSet()
        requests.keys.toList().filter { it !in wanted }.forEach { requests.remove(it)?.cancel() }
        for (url in wanted) if (cache.get(url) == null && url !in requests && url !in failed) {
            requests[url] = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    permits.withPermit {
                        val result = SingletonImageLoader.get(context).execute(ImageRequest.Builder(context)
                            .data(coverRequestUrl(url, 256)).size(256).allowHardware(false).build())
                        if (result is SuccessResult) {
                            val bitmap = withContext(Dispatchers.Default) { result.image.toBitmap() }
                            val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                            // Crop rectangular artwork without padding it with transparent pixels.
                            val scale = 1f / min(bitmap.width, bitmap.height)
                            shader.setLocalMatrix(Matrix().apply {
                                setScale(scale, scale)
                                postTranslate((1f - bitmap.width * scale) / 2f, (1f - bitmap.height * scale) / 2f)
                            })
                            cache.put(url, shader)
                            invalidate()
                        } else failed.add(url)
                    }
                } finally { requests.remove(url) }
            }.also { it.start() }
        }
    }
}

/** The opaque backing also prevents PNG transparency from exposing another album. */
internal fun drawDepthRibbonFace(canvas: Canvas, paint: Paint, shader: BitmapShader?, brightness: Float, visibility: Float = 1f) {
    val alpha = (visibility.coerceIn(0f, 1f) * 255).roundToInt()
    paint.shader = null
    paint.color = 0xFF343039.toInt()
    paint.alpha = alpha
    canvas.drawRoundRect(0f, 0f, 1f, 1f, .06f, .06f, paint)
    if (shader != null) {
        paint.shader = shader
        paint.color = android.graphics.Color.WHITE
        paint.alpha = alpha
        canvas.drawRoundRect(0f, 0f, 1f, 1f, .06f, .06f, paint)
        paint.shader = null
    }
    paint.color = android.graphics.Color.BLACK
    paint.alpha = ((1f - brightness.coerceIn(0f, 1f)) * alpha).roundToInt()
    canvas.drawRoundRect(0f, 0f, 1f, 1f, .06f, .06f, paint)
    paint.color = android.graphics.Color.WHITE
    paint.alpha = (alpha * .16f).roundToInt()
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = .004f
    canvas.drawRoundRect(.002f, .002f, .998f, .998f, .06f, .06f, paint)
    paint.style = Paint.Style.FILL
}

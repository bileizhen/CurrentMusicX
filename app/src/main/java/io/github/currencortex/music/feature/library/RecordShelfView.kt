package io.github.currencortex.music.feature.library

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.LruCache
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.coverRequestUrl
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.*

/** One Android frame clock owns the shelf, its hit regions and the index ruler. */
internal class RecordShelfView(context: Context) : View(context) {
    var onSelection: (Int) -> Unit = {}
    var onPreview: (Int?) -> Unit = {}
    private var previewIndex: Int? = null
    private fun preview(index: Int?) {
        if (previewIndex != index) { previewIndex = index; onPreview(index) }
    }
    var onActivate: (Int) -> Unit = {}
    var onPickingChanged: (Boolean) -> Unit = {}
    var onSongsChanged: (List<Song>) -> Unit = {}
    var onResultsChangingChanged: (Boolean) -> Unit = {}
    internal val displayedSongs get() = songs
    internal var replacingResults = false
        private set
    var topInset = 0f
    var bottomInset = 0f
    internal var position = 0f
        private set
    internal var renderedFrames = 0L
        private set
    private var songs = emptyList<Song>()
    private var configured = false
    private var pendingSongs: List<Song>? = null
    internal var resultsExitTime = -1f
        private set
    private var searchActive = false
    private var resultsPosition = 0f
    private data class ResultMotion(val song: Song, val from: FloatArray,
        val fromDistance: Float, val toDistance: Float?, val opacity: Float) {
        val sleeve = Sleeve().also { it.song = song; it.index = -1 }
    }
    private var resultsMotion = emptyList<ResultMotion>()
    internal fun recordQuad(song: Song): FloatArray? = visible.firstOrNull { sameSong(it.song, song) }?.quad?.copyOf()
    private var target = 0f
    private var extraction = 0f
    private var velocity = 0f
    internal var running = false
        private set
    private var disposed = false
    private var lastFrame = 0L
    private var selected = -1
    private var touching = false
    private var dragging = false
    private var scrubbing = false
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var tracker: VelocityTracker? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val permits = Semaphore(4)
    private val requests = mutableMapOf<String, Job>()
    private val failed = mutableSetOf<String>()
    private class Texture(val image: Bitmap)
    private val textures = object : LruCache<String, Texture>(20 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Texture) = value.image.allocationByteCount
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    fun setCustomTypeface(value: Typeface?) {
        val face = value ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)
        if (textPaint.typeface != face) { textPaint.typeface = face; invalidate() }
    }
    private val matrix = Matrix()
    private val unit = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
    private class Sleeve { var index = 0; lateinit var song: Song; var distance = 0f; var entryBlur = 0f; var entryOpacity = 1f; val quad = FloatArray(8) }
    private val sleeves = Array(29) { Sleeve() }
    private val visible = ArrayList<Sleeve>(29)
    private val depthOfField = if (Build.VERSION.SDK_INT >= 31) ShelfDepthOfField() else null
    private val lighting = if (Build.VERSION.SDK_INT >= 33) runCatching { RecordShelfLighting() }
        .onFailure { android.util.Log.w("RecordShelf", "Material shader unavailable", it) }.getOrNull() else null
    internal val hasLitMaterial get() = lighting != null
    private val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f,
            intArrayOf(0x52080D12, 0x30080D12, 0x0C080D12, 0x00080D12),
            floatArrayOf(0f, .3f, .65f, 1f), Shader.TileMode.CLAMP)
    }
    private val contactPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f,
            intArrayOf(0x9206090C.toInt(), 0x4706090C, 0x0006090C),
            floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP)
    }
    private val rulerPointGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f,
            intArrayOf(0x8CFFFFFF.toInt(), 0x3DFFFFFF, 0x10FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, .28f, .6f, 1f), Shader.TileMode.CLAMP)
    }
    private val maskPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private var requestedWindow = Int.MIN_VALUE
    internal var introTime = 0f
        private set
    private var pickTime = -1f
    private var pickStart = 0f
    private var pickTarget = 0f
    private val edgePath = Path()
    private fun ease(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
    private fun cancelPick() {
        if (pickTime >= 0f) { pickTime = -1f; onPickingChanged(false) }
    }
    fun pick(index: Int) {
        if (replacingResults || index !in songs.indices) return
        preview(null)
        if (!ValueAnimator.areAnimatorsEnabled()) { select(index, true); onPickingChanged(false); return }
        pickStart = position
        pickTarget = index + ((position - index) / songs.size).roundToInt() * songs.size.toFloat()
        pickTime = 0f; onPickingChanged(true); invalidate()
    }
    private val density get() = resources.displayMetrics.density
    private val rulerY get() = height - bottomInset - 10f * density

    init { isClickable = true; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun configure(value: List<Song>, searching: Boolean = false) {
        val clearingSearch = searchActive && !searching
        searchActive = searching
        if (clearingSearch) {
            preview(null); cancelPick(); applySongs(value)
            return
        }
        if (pendingSongs == value) return
        if (pendingSongs != null) {
            beginResultsMotion(value)
            return
        }
        if (songs == value) return
        val sameRecords = songs.size == value.size && songs.indices.all {
            songs[it].id == value[it].id && songs[it].musicSource == value[it].musicSource
        }
        if (sameRecords) {
            songs = value; onSongsChanged(value); invalidate()
            return
        }
        preview(null)
        cancelPick()
        if (songs.isNotEmpty() && running && isAttachedToWindow && ValueAnimator.areAnimatorsEnabled()) {
            beginResultsMotion(value)
            target = position; velocity = 0f; touching = false; dragging = false; scrubbing = false
            tracker?.recycle(); tracker = null
            setResultsChanging(true)
            invalidate()
        } else applySongs(value)
    }

    private fun sameSong(a: Song, b: Song) = a.id == b.id && a.musicSource == b.musicSource

    /** Snapshot the actual on-screen poses, so typing again never restarts from a stale rack. */
    private fun beginResultsMotion(value: List<Song>) {
        val identity = songs.getOrNull(selected)
        val retained = value.indexOfFirst { identity != null && sameSong(it, identity) }
        resultsPosition = (if (retained >= 0) retained.toFloat() else position)
            .coerceIn(0f, maxOf(0, value.lastIndex).toFloat())
        val destination = shelfWindow(resultsPosition, value.size).map { logical ->
            value[Math.floorMod(logical, value.size)] to logical - resultsPosition
        }
        val old = visible.filter { sleeve -> sleeve.entryOpacity > .02f && sleeve.quad[0] < width && sleeve.quad[2] > 0f }
        val motions = old.map { sleeve ->
            // A matching record remains on the shelf, even if its new slot is beyond the window.
            val targetDistance = destination.firstOrNull { sameSong(it.first, sleeve.song) }?.second
                ?: value.indexOfFirst { sameSong(it, sleeve.song) }.takeIf { it >= 0 }?.let {
                    val raw = it - resultsPosition
                    raw + ((sleeve.distance - raw) / value.size).roundToInt() * value.size
                }
            ResultMotion(sleeve.song, sleeve.quad.copyOf(), sleeve.distance, targetDistance, sleeve.entryOpacity)
        }.toMutableList()
        val stageTop = topInset + 76f * density
        val stageHeight = maxOf(100f * density, rulerY - stageTop - 24f * density)
        val stageWidth = minOf(width.toFloat(), stageHeight * 1.12f)
        val originX = (width - stageWidth) * .5f
        destination.filter { (song, _) -> old.none { sameSong(it.song, song) } }.forEach { (song, distance) ->
            val q = FloatArray(8)
            shelfQuad(distance, stageWidth, stageTop, stageHeight, 0f, q)
            val offset = (stageWidth * 1.01f - q[0]).coerceAtLeast(0f)
            for (corner in 0..3) {
                q[corner * 2] += originX + offset
                q[corner * 2 + 1] += shelfEntryOffsetY(offset, stageWidth)
            }
            motions += ResultMotion(song, q, distance, distance, .12f)
        }
        resultsMotion = motions
        pendingSongs = value; resultsExitTime = 0f
        requestTextureUrls(motions.sortedBy { abs(it.toDistance ?: it.fromDistance) }.map { it.song.cover })
        setResultsChanging(true); invalidate()
    }

    private fun setResultsChanging(value: Boolean) {
        if (replacingResults == value) return
        replacingResults = value; onResultsChangingChanged(value)
    }

    private fun applySongs(value: List<Song>, enter: Boolean = true) {
        val animateResults = configured && value.isNotEmpty() && ValueAnimator.areAnimatorsEnabled()
        configured = true
        val identity = songs.getOrNull(selected)
        songs = value
        pendingSongs = null; resultsExitTime = -1f; resultsMotion = emptyList()
        visible.clear()
        introTime = if (enter && ValueAnimator.areAnimatorsEnabled()) 0f else 1.85f
        extraction = 0f
        val retained = value.indexOfFirst { it.id == identity?.id && it.musicSource == identity.musicSource }
        position = (if (retained >= 0) retained.toFloat() else position).coerceIn(0f, maxOf(0, songs.lastIndex).toFloat())
        target = position; velocity = 0f; selected = -1; requestedWindow = Int.MIN_VALUE
        requests.values.toList().forEach(Job::cancel); requests.clear(); failed.clear()
        onSongsChanged(value)
        setResultsChanging(animateResults)
        notifySelection(); invalidate()
    }

    fun setRunning(value: Boolean) {
        if (running == value) return
        running = value; lastFrame = 0L
        if (!value) {
            preview(null); touching = false; velocity = 0f; tracker?.recycle(); tracker = null
            pendingSongs?.let { applySongs(it) }
        }
        else invalidate()
    }

    fun dispose() { preview(null); disposed = true; running = false; scope.cancel(); requests.clear(); textures.evictAll(); tracker?.recycle(); tracker = null }
    override fun onDetachedFromWindow() { lastFrame = 0L; super.onDetachedFromWindow() }

    fun select(index: Int, immediate: Boolean = false) {
        if (replacingResults || index !in songs.indices) return
        preview(null)
        cancelPick()
        velocity = 0f
        target = index + ((position - index) / songs.size).roundToInt() * songs.size.toFloat()
        if (immediate || !ValueAnimator.areAnimatorsEnabled()) { position = target; extraction = 1f; notifySelection() }
        invalidate()
    }

    fun travel(delta: Float) {
        if (replacingResults || songs.isEmpty()) return
        preview(null)
        cancelPick(); target += delta.roundToInt(); velocity = 0f
        if (!ValueAnimator.areAnimatorsEnabled()) { position = target; notifySelection() }
        invalidate()
    }

    private fun notifySelection() {
        if (songs.isEmpty()) return
        val index = Math.floorMod(position.roundToInt(), songs.size)
        if (index != selected) { selected = index; onSelection(index) }
    }

    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (replacingResults) return true
        if (songs.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelPick()
                parent?.requestDisallowInterceptTouchEvent(true)
                touching = true; dragging = false; velocity = 0f
                scrubbing = abs(event.y - rulerY) < 32f * density
                downX = event.x; downY = event.y; lastX = event.x; lastY = event.y
                tracker?.recycle(); tracker = VelocityTracker.obtain().also { it.addMovement(event) }
                if (scrubbing) scrub(event.x)
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(event)
                if (hypot(event.x - downX, event.y - downY) > slop) { dragging = true; preview(null) }
                if (scrubbing) scrub(event.x)
                else if (dragging) {
                    position += (-(event.x - lastX) + (event.y - lastY) * .55f) / (width * .13f)
                    target = position.roundToInt().toFloat(); notifySelection()
                }
                lastX = event.x; lastY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.actionMasked == MotionEvent.ACTION_UP && !scrubbing) {
                    if (dragging) {
                        tracker?.computeCurrentVelocity(1000)
                        velocity = ((-(tracker?.xVelocity ?: 0f) + (tracker?.yVelocity ?: 0f) * .55f) / (width * .13f)).coerceIn(-22f, 22f)
                        target = (position + velocity * .12f).roundToInt().toFloat()
                    } else {
                        performClick()
                        visible.asReversed().firstOrNull { contains(it.quad, event.x, event.y) }?.let { if (it.index == selected && extraction > .95f) onActivate(it.index) else select(it.index) }
                    }
                }
                touching = false; dragging = false; scrubbing = false
                tracker?.recycle(); tracker = null; parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        invalidate(); return true
    }

    private fun scrub(x: Float) {
        preview(null)
        position = ((x / width - .08f) / .84f).coerceIn(0f, 1f) * songs.lastIndex
        target = position.roundToInt().toFloat(); notifySelection()
    }

    private fun contains(q: FloatArray, x: Float, y: Float): Boolean {
        var sign = 0f
        for (i in 0..3) {
            val j = (i + 1) % 4
            val cross = (q[j * 2] - q[i * 2]) * (y - q[i * 2 + 1]) - (q[j * 2 + 1] - q[i * 2 + 1]) * (x - q[i * 2])
            if (i == 0) sign = cross else if (sign * cross < 0) return false
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (disposed || width == 0 || height == 0) return
        renderedFrames++
        val now = System.nanoTime()
        val dt = if (lastFrame == 0L) 1f / 60f else ((now - lastFrame) / 1e9f).coerceIn(0f, .05f)
        lastFrame = now
        val previousPosition = position
        val animate = ValueAnimator.areAnimatorsEnabled()
        if (pendingSongs != null) {
            resultsExitTime += dt
            if (!animate || resultsExitTime >= .52f) {
                position = resultsPosition
                applySongs(requireNotNull(pendingSongs), enter = false)
            }
        } else {
            introTime = if (animate) minOf(1.85f, introTime + dt) else 1.85f
            if (!animate || introTime >= 1.4f && extraction >= .98f) setResultsChanging(false)
        }
        if (pickTime >= 0f) {
            pickTime += dt
            if (pickTime >= .18f) {
                position = pickStart + (pickTarget - pickStart) * ease((pickTime - .18f) / .88f)
                target = position
            }
            if (pickTime >= 1.52f) { target = pickTarget; position = target; pickTime = -1f; onPickingChanged(false) }
        } else if (!touching) position = if (animate) shelfApproach(position, target, dt, 9f) else target
        notifySelection()
        val extracted = if (pendingSongs != null || introTime < 1.25f || touching && dragging ||
            abs(position - target) > .035f || pickTime in 0f..1.06f) 0f else 1f
        extraction = if (pendingSongs != null) 0f
            else if (animate) shelfApproach(extraction, extracted, dt, 10.5f) else extracted
        preview(selected.takeIf { running && !replacingResults && it in songs.indices && extracted == 1f && extraction > .12f && !scrubbing })
        if (songs.isEmpty()) return
        val window = shelfWindow(position, songs.size)
        visible.clear()
        val stageTop = topInset + 76f * density
        val stageHeight = maxOf(100f * density, rulerY - stageTop - 24f * density)
        val stageWidth = minOf(width.toFloat(), stageHeight * 1.12f)
        val originX = (width - stageWidth) * .5f
        if (pendingSongs != null) {
            val progress = ease(resultsExitTime / .52f)
            for (motion in resultsMotion) {
                val sleeve = motion.sleeve
                val destination = motion.toDistance
                sleeve.distance = motion.fromDistance + ((destination ?: motion.fromDistance) - motion.fromDistance) * progress
                if (destination != null) {
                    shelfQuad(destination, stageWidth, stageTop, stageHeight, 0f, sleeve.quad)
                    for (corner in 0..3) {
                        sleeve.quad[corner * 2] += originX
                        for (axis in 0..1) {
                            val i = corner * 2 + axis
                            sleeve.quad[i] = motion.from[i] + (sleeve.quad[i] - motion.from[i]) * progress
                        }
                    }
                    sleeve.entryOpacity = motion.opacity + (1f - motion.opacity) * progress
                } else {
                    val offset = shelfResultsExitOffsetX(resultsExitTime, motion.fromDistance,
                        stageWidth, motion.from[0] - originX, 0f)
                    for (corner in 0..3) {
                        sleeve.quad[corner * 2] = motion.from[corner * 2] + offset
                        sleeve.quad[corner * 2 + 1] = motion.from[corner * 2 + 1] + shelfEntryOffsetY(offset, stageWidth)
                    }
                    sleeve.entryOpacity = motion.opacity * (1f - progress)
                }
                visible.add(sleeve)
            }
            visible.sortByDescending { it.distance }
        } else for ((slot, logical) in window.reversed().withIndex()) {
            val index = Math.floorMod(logical, songs.size)
            val sleeve = sleeves[slot]; sleeve.index = index; sleeve.song = songs[index]; sleeve.distance = logical - position
            shelfQuad(sleeve.distance, stageWidth, stageTop, stageHeight,
                if (index == selected) extraction else 0f, sleeve.quad)
            fun offsetAt(time: Float): Float = shelfEntryOffsetX(time, sleeve.distance, stageWidth, sleeve.quad[0])
            val motionTime = introTime
            val entranceX = offsetAt(motionTime)
            val entranceY = shelfEntryOffsetY(entranceX, stageWidth)
            val previousX = offsetAt((motionTime - dt).coerceAtLeast(0f))
            sleeve.entryBlur = abs(previousX - entranceX) * .16f
            sleeve.entryOpacity = (1f - entranceX / stageWidth).coerceIn(.12f, 1f)
            for (corner in 0..3) {
                sleeve.quad[corner * 2] += originX + entranceX
                sleeve.quad[corner * 2 + 1] += entranceY
            }
            visible.add(sleeve)
        }
        // A case slides within its own plane. Keep the rack's back-to-front order:
        // nearer sleeves still cover its left edge until it clears their right edge.
        val entryMask = ease((introTime - .85f) / .35f)
        val maskBottom = (height - bottomInset * .2f) * (1f - entryMask) + rulerY * entryMask
        val shelfLayer = canvas.saveLayer(0f, stageTop - 20f * density, width.toFloat(), maskBottom, null)
        // Shadows lie on the shared supporting plane, below the standing case edges.
        // Drawing them before the cases makes the foreground correctly occlude distant shadows.
        for (sleeve in visible) {
            val q = sleeve.quad
            val pull = if (sleeve.index == selected) extraction else 0f
            val middleX = (q[4] + q[6]) * .5f
            val middleY = (q[5] + q[7]) * .5f
            val edgeAngle = Math.toDegrees(atan2((q[5] - q[7]).toDouble(), (q[4] - q[6]).toDouble())).toFloat()
            val save = canvas.save()
            canvas.translate(middleX + stageWidth * (.012f + pull * .009f),
                middleY + stageWidth * (.012f + pull * .010f))
            canvas.rotate(edgeAngle)
            canvas.scale(stageWidth * (.245f + pull * .018f), stageWidth * (.025f + pull * .016f))
            floorPaint.alpha = ((105f + pull * 105f) * sleeve.entryOpacity).roundToInt()
            canvas.drawCircle(0f, 0f, 1f, floorPaint)
            canvas.restoreToCount(save)
            val contact = canvas.save()
            canvas.translate(middleX + stageWidth * .004f, middleY + stageWidth * .004f)
            canvas.rotate(edgeAngle)
            canvas.scale(stageWidth * .233f, stageWidth * (.007f + pull * .002f))
            contactPaint.alpha = ((150f + pull * 50f) * sleeve.entryOpacity).roundToInt()
            canvas.drawCircle(0f, 0f, 1f, contactPaint)
            canvas.restoreToCount(contact)
        }
        for ((slot, sleeve) in visible.withIndex()) {
            if (!matrix.setPolyToPoly(unit, 0, sleeve.quad, 0, 4)) continue
            val song = sleeve.song
            val logicalDistance = abs(sleeve.distance)
            val near = sleeve.distance < 0f
            val outsideFocus = (logicalDistance - if (near) 2f else 3.5f).coerceAtLeast(0f)
            val defocus = if (sleeve.index == selected) 0f else
                (outsideFocus.pow(1.45f) * if (near) 1.3f else .9f).coerceAtMost(18f) * stageWidth / 724f
            val motionBlur = if (extraction < .1f) abs(position - previousPosition) * stageWidth * .012f else 0f
            val blur = maxOf(defocus, motionBlur.coerceAtMost(12f), sleeve.entryBlur).roundToInt().coerceAtMost(48)
            if (Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated && blur > 0) {
                val q = sleeve.quad
                val padding = ceil(stageWidth * .095f).toInt()
                val left = maxOf(-padding, floor(minOf(q[0], q[6]) - padding).toInt())
                val right = minOf(width + padding, ceil(maxOf(q[2], q[4]) + padding).toInt())
                val upper = maxOf(-padding, floor(minOf(q[1], q[3]) - padding).toInt())
                val lower = minOf(height + padding, ceil(maxOf(q[5], q[7]) + padding).toInt())
                if (right <= left || lower <= upper) continue
                depthOfField?.draw(canvas, slot, left, upper, right, lower, blur, matrix, sleeve.entryOpacity) { recording ->
                    drawSleeve(recording, song, textures.get(song.cover), false)
                }
            } else {
                val q = sleeve.quad
                val padding = stageWidth * .095f
                val save = if (sleeve.entryOpacity >= .999f) canvas.save() else canvas.saveLayerAlpha(
                    minOf(q[0], q[6]) - padding, minOf(q[1], q[3]) - padding,
                    maxOf(q[2], q[4]) + padding, maxOf(q[5], q[7]) + padding,
                    (sleeve.entryOpacity * 255).roundToInt())
                canvas.concat(matrix)
                drawSleeve(canvas, song, textures.get(song.cover), sleeve.index == selected)
                canvas.restoreToCount(save)
            }
        }
        // Depth fades into the ambient artwork before reaching the title and controls.
        maskPaint.shader = LinearGradient(0f, stageTop - 20f * density, 0f, maskBottom,
            intArrayOf(Color.TRANSPARENT, Color.BLACK, Color.BLACK, Color.TRANSPARENT),
            floatArrayOf(0f, .055f, .93f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPaint(maskPaint)
        canvas.restoreToCount(shelfLayer)
        drawRuler(canvas)
        if (pendingSongs == null && requestedWindow != window.first) { requestedWindow = window.first; requestTextures(window) }
        if (running && animate && (pendingSongs != null || introTime < 1.85f || pickTime >= 0f || position != target || extraction != extracted)) postInvalidateOnAnimation()
    }

    private fun drawSleeve(canvas: Canvas, song: Song, texture: Texture?, active: Boolean) {
        paint.shader = null; paint.style = Paint.Style.FILL
        val lit = if (Build.VERSION.SDK_INT >= 33 && canvas.isHardwareAccelerated) lighting else null
        if (Build.VERSION.SDK_INT >= 33 && lit != null) {
            lit.prepare(position * .07f + if (active) extraction * .6f else 0f)
        }
        // Back, top and hinge faces are separate planes, not a white outline around the cover.
        fun face(vararg points: Float) {
            edgePath.reset(); edgePath.moveTo(points[0], points[1])
            for (i in 2 until points.size step 2) edgePath.lineTo(points[i], points[i + 1])
            edgePath.close(); canvas.drawPath(edgePath, paint)
        }
        paint.color = 0xAA929CA8.toInt()
        canvas.drawRect(.045f, -.055f, 1.045f, .945f, paint)
        paint.shader = LinearGradient(0f, -.03f, 0f, .012f,
            intArrayOf(0xFF8D969E.toInt(), 0xFFF2F3F2.toInt(), 0xFFB8BDC1.toInt(), 0xFFE1E4E5.toInt()),
            floatArrayOf(0f, .35f, .75f, 1f), Shader.TileMode.CLAMP)
        if (Build.VERSION.SDK_INT >= 33 && lit != null) { paint.color = Color.WHITE; paint.shader = lit.top }
        face(0f, 0f, .045f, -.055f, 1.045f, -.055f, 1f, 0f)
        // Recessed tray channel between the two clear lid rails, visible across the top.
        paint.shader = null; paint.color = 0x66505C68
        face(.056f, -.043f, 1.029f, -.043f, 1.013f, -.022f, .040f, -.022f)
        paint.color = 0xDDEDF1F3.toInt()
        face(.046f, -.055f, 1.045f, -.055f, 1.041f, -.050f, .042f, -.050f)
        paint.color = 0xB8DCE2E7.toInt()
        face(.028f, -.034f, 1.027f, -.034f, 1.024f, -.029f, .024f, -.029f)
        paint.shader = LinearGradient(1f, 0f, 1.045f, 0f,
            intArrayOf(0xFF7A848D.toInt(), 0xFFD5D9DC.toInt(), 0xFF737D87.toInt(), 0xFFE0E3E5.toInt()), null, Shader.TileMode.CLAMP)
        if (Build.VERSION.SDK_INT >= 33 && lit != null) { paint.color = Color.WHITE; paint.shader = lit.side }
        face(1f, 0f, 1.045f, -.055f, 1.045f, .945f, 1f, 1f)
        paint.shader = null
        // Split line between the tray and lid, plus the bright rear moulding ridge.
        paint.color = 0xC5202A35.toInt()
        face(1.024f, -.029f, 1.031f, -.038f, 1.031f, .962f, 1.024f, .971f)
        paint.color = 0xDAF0F2F4.toInt()
        face(1.039f, -.048f, 1.044f, -.054f, 1.044f, .946f, 1.039f, .952f)
        paint.shader = null; paint.color = 0xC29AA5AD.toInt()
        canvas.drawRect(0f, 0f, 1f, 1f, paint)
        paint.color = 0xFF10151E.toInt(); canvas.drawRect(.041f, .032f, .977f, .975f, paint)
        texture?.let {
            // Center-crop the cover into the portrait insert without stretching faces or lettering.
            val save = canvas.save()
            canvas.clipRect(.333f, .034f, .976f, .973f)
            val artWidth = .643f; val artHeight = .939f
            val bitmapRatio = it.image.width.toFloat() / it.image.height
            val physicalRatio = .435f / .45f
            val drawWidth = maxOf(artWidth, artHeight * bitmapRatio / physicalRatio)
            val drawHeight = drawWidth * physicalRatio / bitmapRatio
            paint.color = Color.WHITE
            canvas.drawBitmap(it.image, null, RectF(.333f + (artWidth - drawWidth) / 2,
                .034f + (artHeight - drawHeight) / 2, .333f + (artWidth + drawWidth) / 2,
                .034f + (artHeight + drawHeight) / 2), paint)
            canvas.restoreToCount(save)
        } ?: run {
            paint.shader = LinearGradient(.33f, 0f, 1f, 1f, 0xFF3B5261.toInt(), 0xFF17212C.toInt(), Shader.TileMode.CLAMP)
            canvas.drawRect(.333f, .034f, .976f, .973f, paint); paint.shader = null
            textPaint.color = 0x88FFFFFF.toInt(); textPaint.textSize = .19f
            canvas.drawText("♪", .58f, .57f, textPaint)
        }
        // The printed insert is recessed under the cover: the lip occludes its upper/left edge.
        paint.shader = LinearGradient(0f, .029f, 0f, .066f, 0xA6000000.toInt(), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(.075f, .029f, .976f, .066f, paint)
        paint.shader = LinearGradient(.073f, 0f, .113f, 0f, 0x90000000.toInt(), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(.073f, .029f, .113f, .975f, paint); paint.shader = null
        val spine = canvas.save(); canvas.translate(.195f, .80f); canvas.rotate(-90f)
        // Android shapes glyphs at a real font size before the projected canvas scales them down.
        // Sub-pixel font sizes in unit coordinates quantize advances and collapse the lettering.
        canvas.scale(1f / 1024f, 1f / 1024f)
        textPaint.textSize = 53f; textPaint.color = 0xFFE8EBEE.toInt()
        fun fit(text: String, length: Float): String {
            val count = textPaint.breakText(text, true, length, null)
            return if (count < text.length) text.take(maxOf(0, count - 1)) + "…" else text
        }
        canvas.drawText(fit(song.name, 700f), 0f, 0f, textPaint)
        textPaint.textSize = 31f; textPaint.color = 0xFFA5AFB9.toInt()
        canvas.drawText(fit(song.artists, 700f), 0f, 68f, textPaint); canvas.restoreToCount(spine)
        val imprint = canvas.save(); canvas.scale(1f / 1024f, 1f / 1024f)
        textPaint.textSize = 25f; textPaint.color = 0xFFB3BBC3.toInt()
        canvas.drawText(fit(song.album, 200f), 128f, 958f, textPaint); canvas.restoreToCount(imprint)
        // Clear moulded hinge rail, with dark refraction flanked by narrow specular highlights.
        paint.shader = LinearGradient(0f, 0f, .075f, 0f,
            intArrayOf(0xCCEDF1F3.toInt(), 0x668E9AA5, 0xE1C5CBD0.toInt(), 0x994B5865.toInt(), 0xE9E1E6EA.toInt()),
            floatArrayOf(0f, .22f, .5f, .78f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(.006f, .012f, .075f, .988f, paint); paint.shader = null
        // The moulded lip has a lit crown and a refracting lower edge on each exposed side.
        // These broad transparent bands are separate from the fine seam around the two halves.
        val lipColors = intArrayOf(0xF2F2F3F3.toInt(), 0xE6D6DADC.toInt(), 0xB59AA4AC.toInt(), 0xEDF3F5F5.toInt(), 0xB65C6873.toInt())
        val lipStops = floatArrayOf(0f, .25f, .53f, .77f, 1f)
        paint.shader = LinearGradient(0f, 0f, 0f, .027f, lipColors, lipStops, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 1f, .027f, paint)
        paint.shader = LinearGradient(0f, .978f, 0f, 1f, lipColors, lipStops, Shader.TileMode.CLAMP)
        canvas.drawRect(.035f, .978f, 1f, 1f, paint)
        paint.shader = LinearGradient(.975f, 0f, 1f, 0f, lipColors, lipStops, Shader.TileMode.CLAMP)
        canvas.drawRect(.975f, .027f, 1f, .978f, paint)
        paint.shader = null
        paint.shader = LinearGradient(0f, 0f, .45f, 1f, intArrayOf(0x22FFFFFF, 0x00FFFFFF, 0x12D9E9FF), null, Shader.TileMode.CLAMP)
        canvas.drawRect(.075f, .028f, .983f, .978f, paint); paint.shader = null
        if (Build.VERSION.SDK_INT >= 33 && lit != null) {
            paint.color = Color.WHITE; paint.shader = lit.rim
            canvas.drawRect(0f, 0f, 1f, 1f, paint); paint.shader = null
        }
        paint.style = Paint.Style.STROKE
        paint.color = 0xC04E5B66.toInt(); paint.strokeWidth = .011f
        canvas.drawRect(.011f, .012f, .991f, .99f, paint)
        paint.color = if (active) 0xE6FFFFFF.toInt() else 0xBCD9E0E5.toInt(); paint.strokeWidth = .006f
        canvas.drawRect(0f, 0f, 1f, 1f, paint)
        paint.color = 0xB7E2E5E7.toInt(); paint.strokeWidth = .003f
        canvas.drawRect(.027f, .023f, .978f, .977f, paint)
        canvas.drawLine(1.045f, -.055f, 1.045f, .945f, paint)
        canvas.drawLine(.045f, -.055f, 1.045f, -.055f, paint)
        canvas.drawLine(1f, 1f, 1.045f, .945f, paint)
        paint.style = Paint.Style.FILL
        for (x in floatArrayOf(.12f, .33f, .59f, .83f)) {
            paint.color = 0xAA64717C.toInt(); canvas.drawRect(x, -.025f, x + .007f, .014f, paint)
            paint.color = 0xE3F5F7F8.toInt(); canvas.drawRect(x + .007f, -.022f, x + .047f, -.016f, paint)
            paint.color = 0xBCE6EBEF.toInt(); canvas.drawRect(x + .007f, -.002f, x + .047f, .009f, paint)
            paint.color = 0xAABCC5CB.toInt(); canvas.drawRect(x + .007f, .982f, x + .047f, .994f, paint)
        }
    }

    private fun drawRuler(canvas: Canvas) {
        val normalized = ((position % songs.size) + songs.size) % songs.size
        val fraction = if (songs.size <= 1) .5f else (normalized / songs.lastIndex).coerceIn(0f, 1f)
        val left = width * .08f; val span = width * .84f
        for (tick in 0..88) {
            val f = tick / 88f
            val emphasis = exp(-((f - fraction) / .095f).pow(2))
            val h = width * (.009f + emphasis * .054f + if (tick % 5 == 0) .006f else 0f)
            paint.color = Color.WHITE; paint.alpha = (65f + emphasis * 150f).roundToInt(); paint.strokeWidth = density * .7f
            canvas.drawLine(left + f * span, rulerY, left + f * span, rulerY - h, paint)
        }
        val x = left + fraction * span
        val tipY = rulerY - width * .113f
        val pointGlow = canvas.save()
        canvas.translate(x, tipY); canvas.scale(7f * density, 7f * density)
        canvas.drawCircle(0f, 0f, 1f, rulerPointGlow)
        canvas.restoreToCount(pointGlow)
        paint.alpha = 255; paint.strokeWidth = density
        canvas.drawLine(x, rulerY, x, tipY, paint)
        canvas.drawCircle(x, tipY, 2f * density, paint)
    }

    private fun requestTextures(window: IntRange) {
        requestTextureUrls(window.sortedBy { abs(it - position) }.map { songs[Math.floorMod(it, songs.size)].cover })
    }

    private fun requestTextureUrls(urls: List<String>) {
        val wanted = urls.filter(String::isNotBlank).toSet()
        requests.keys.toList().filter { it !in wanted }.forEach { requests.remove(it)?.cancel() }
        // Nearest sleeves load first, including when jumping across thousands of songs.
        for (url in wanted) {
            if (url.isBlank() || textures.get(url) != null || url in requests || url in failed) continue
            requests[url] = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    permits.withPermit {
                        val result = SingletonImageLoader.get(context).execute(ImageRequest.Builder(context)
                            .data(coverRequestUrl(url, 384)).size(384).allowHardware(false).build())
                        if (result is SuccessResult) {
                            val texture = withContext(Dispatchers.Default) {
                                val image = result.image.toBitmap()
                                Texture(image)
                            }
                            textures.put(url, texture); invalidate()
                        } else failed.add(url)
                    }
                } finally { requests.remove(url) }
            }.also { it.start() }
        }
    }
}

/** Kept behind an API guard so Android 8-11 never resolve RenderEffect or allocate RenderNode arrays. */
@androidx.annotation.RequiresApi(31)
private class ShelfDepthOfField {
    private val nodes = MutableList(29) { RenderNode("record-sleeve-$it") }
    private val effects = Array(49) { radius ->
        if (radius == 0) null else RenderEffect.createBlurEffect(radius.toFloat(), radius.toFloat(), Shader.TileMode.DECAL)
    }
    fun draw(canvas: Canvas, slot: Int, left: Int, top: Int, right: Int, bottom: Int,
        blur: Int, matrix: Matrix, opacity: Float, content: (Canvas) -> Unit) {
        while (nodes.size <= slot) nodes.add(RenderNode("record-sleeve-${nodes.size}"))
        val node = nodes[slot]
        node.setPosition(left, top, right, bottom); node.setRenderEffect(effects[blur]); node.alpha = opacity
        val recording = node.beginRecording(right - left, bottom - top)
        recording.translate(-left.toFloat(), -top.toFloat()); recording.concat(matrix)
        content(recording); node.endRecording(); canvas.drawRenderNode(node)
    }
}

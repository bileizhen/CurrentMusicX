package io.github.currencortex.music.ui.component

import android.content.res.Resources
import android.graphics.*
import androidx.core.graphics.PathParser
import io.github.currencortex.music.R
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*
import kotlin.random.Random

/**
 * Motion sampled from the supplied 30 fps reference, evaluated continuously at the display rate.
 * The original logo/video is never played: CurrentMusic and the account name are independent layers.
 * All blur masks, glyphs, gradients, paths and paints are allocated before the first animated frame.
 */
internal class HaloLaunchRenderer(resources: Resources, username: String, customTypeface: Typeface? = null) {
    private companion object {
        const val FLIP_FRAME_RATE = 30f
        const val FLIP_CACHE_SCALE = .4f
        const val FLIP_CACHE_TOP = 180f
        const val FIRST_FLIP_START = 866.6667f
        const val SECOND_FLIP_START = 1633.3333f
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val ringPath = Path()
    private val projectedRingPath = Path()
    private val projectedInner = FloatArray(194)
    private val projectionMatrix = Matrix()
    private val projectionValues = FloatArray(9)
    private val additive = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val glowSector = Path()
    private val glowSectorBounds = RectF(-1200f, -1200f, 1200f, 1200f)
    private val scratchPath = Path()
    private val bounds = RectF()
    private val source = Rect()
    private val ring = FloatArray(5)
    private val white = Color.WHITE
    private val cyan = Color.rgb(0, 225, 239)
    private val random = Random(41021)
    private data class Glyph(val x: Float, val image: Bitmap)
    private data class Particle(val x: Float, val y: Float, val radius: Float, val speed: Float, val alpha: Float)
    private val particles = List(86) {
        Particle(random.nextFloat(), random.nextFloat(), 3f + random.nextFloat().pow(2) * 100f,
            7f + random.nextFloat() * 24f, .09f + random.nextFloat() * .28f)
    }
    private val halo = RadialGradient(0f, 0f, 1f,
        intArrayOf(white, 0x99FFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, .23f, 1f), Shader.TileMode.CLAMP)
    private val atmosphere = RadialGradient(0f, 0f, 1f,
        intArrayOf(0xFF123C53.toInt(), 0xFF061827.toInt(), 0xFF01040B.toInt()),
        floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
    private val pink = RadialGradient(0f, 0f, 1f,
        intArrayOf(white, 0xFFFFA6F6.toInt(), 0x00B147E3), floatArrayOf(0f, .3f, 1f), Shader.TileMode.CLAMP)
    private val whiteTint = PorterDuffColorFilter(white, PorterDuff.Mode.SRC_IN)
    private val cyanTint = PorterDuffColorFilter(cyan, PorterDuff.Mode.SRC_IN)
    private val blueTint = PorterDuffColorFilter(0xFF52B9EE.toInt(), PorterDuff.Mode.SRC_IN)
    private val redTint = PorterDuffColorFilter(0xFFFF457E.toInt(), PorterDuff.Mode.SRC_IN)
    private val ringGlow = Bitmap.createBitmap(1280, 1280, Bitmap.Config.ARGB_8888).also {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = white; style = Paint.Style.STROKE
        }
        val c = Canvas(it)
        // Broad visible bloom plus a hot inner halo. The core itself remains a separate vector.
        for ((radius, width, alpha) in listOf(Triple(70f, 70f, 200), Triple(30f, 50f, 230), Triple(10f, 28f, 245))) {
            p.strokeWidth = width; p.alpha = alpha
            p.maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
            c.drawCircle(640f, 640f, 400f, p)
        }
        it.prepareToDraw()
    }
    private val ringMotion = resources.openRawResource(R.raw.halo_ring_motion).bufferedReader().use {
        val json = JSONArray(it.readText())
        Array(json.length()) { i -> json.getJSONArray(i).let { row -> FloatArray(6) { j -> row.getDouble(j).toFloat() } } }
    }
    private val glyphs = resources.openRawResource(R.raw.current_music_wordmark).bufferedReader().use {
        val json = JSONObject(it.readText()).getJSONArray("glyphs")
        List(json.length()) { i ->
            val row = json.getJSONObject(i)
            val path = PathParser.createPathFromPathData(row.getString("path"))
            Glyph(row.getDouble("x").toFloat(), sprite(path, 150, 135, 22f, 22f))
        }
    }
    private val nameImage: Bitmap
    private val nameWidth: Float
    private data class FlipFrame(val image: Bitmap, val bounds: RectF)
    private val firstFlipFrames: Array<FlipFrame>
    private val secondFlipFrames: Array<FlipFrame>

    init {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = white; textSize = 54f; typeface = customTypeface ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        // Render the entire Unicode string with Android shaping, then fit it under the second word.
        val displayName = android.text.TextUtils.ellipsize(username, android.text.TextPaint(textPaint),
            1200f, android.text.TextUtils.TruncateAt.END).toString()
        val width = ceil(textPaint.measureText(displayName)).toInt().coerceAtLeast(1)
        nameImage = Bitmap.createBitmap(width + 40, 100, Bitmap.Config.ARGB_8888)
        val c = Canvas(nameImage)
        textPaint.maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
        textPaint.alpha = 100; c.drawText(displayName, 20f, 60f, textPaint)
        textPaint.maskFilter = null; textPaint.alpha = 255; c.drawText(displayName, 20f, 60f, textPaint)
        nameWidth = minOf(470f, width.toFloat())
        // Prepared on HaloLaunchFrame's background worker. Tight crops keep the two short
        // shutters bounded in memory; playback only blends two already-uploaded textures.
        val working = Bitmap.createBitmap(768, 576, Bitmap.Config.ARGB_8888)
        val frameCanvas = Canvas(working).apply {
            scale(FLIP_CACHE_SCALE, FLIP_CACHE_SCALE); translate(0f, FLIP_CACHE_TOP)
        }
        val pixels = IntArray(working.width * working.height)
        firstFlipFrames = prepareFlipFrames(FIRST_FLIP_START, 9, working, frameCanvas, pixels)
        secondFlipFrames = prepareFlipFrames(SECOND_FLIP_START, 11, working, frameCanvas, pixels)
        working.recycle()
        sampleRing(0f)
    }

    private fun sprite(path: Path, width: Int, height: Int, x: Float, y: Float): Bitmap {
        val image = Bitmap.createBitmap(width * 2, height * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image); canvas.scale(2f, 2f); canvas.translate(x, y)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white }
        p.maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
        p.alpha = 110; canvas.drawPath(path, p)
        p.maskFilter = null; p.alpha = 255; canvas.drawPath(path, p)
        return image
    }

    fun draw(canvas: Canvas, width: Float, height: Float, time: Float) {
        val t = time.coerceIn(0f, 8500f)
        val fade = 1f - smooth(phase(t, 7767f, 8333f))
        if (fade <= 0f) return
        val environment = smooth(phase(t, 3067f, 3567f))
        paint.colorFilter = null; paint.shader = null; paint.style = Paint.Style.FILL
        if (environment > 0f) {
            canvas.save(); canvas.translate(width * .18f, height * .35f)
            canvas.scale(max(width, height) * .95f, max(width, height) * .95f)
            paint.shader = atmosphere; paint.alpha = (255 * environment * fade).toInt()
            canvas.drawCircle(0f, 0f, 1f, paint); canvas.restore(); paint.shader = null
            drawParticles(canvas, width, height, t, environment * fade)
        }
        canvas.save()
        val portrait = height > width
        // A landscape video's height is its ring's limiting dimension. On a phone use the
        // available width instead, then land the crest independently above the larger wordmark.
        val fit = minOf(width / if (portrait) 1580f else 1920f, height / 1200f)
        canvas.translate(width / 2, height * .48f)
        canvas.scale(fit, fit)
        // The final logo continuously recedes; do not stop abruptly when the title arrives.
        val zoom = 1f - (if (portrait) .12f else .16f) * phase(t, 3600f, 7767f)
        canvas.scale(zoom, zoom); canvas.translate(-960f, -540f)
        sampleRing(t)
        canvas.save()
        val introScale = if (portrait) 1f + .65f * (1f - smooth(phase(t, 1467f, 2200f))) else 1f
        canvas.translate(960f, 540f); canvas.scale(introScale, introScale); canvas.translate(-960f, -540f)
        drawRing(canvas, t, fade)
        canvas.restore()
        if (t >= 2233f) drawStar(canvas, t, fade, environment)
        if (t >= 2767f) drawTitle(canvas, t, fade, environment)
        if (t >= 4033f) drawName(canvas, t, fade)
        canvas.restore()
        // Original light wipe spans the whole frame, with a pink leading edge and white plateau.
        if (t in 3167f..3633f) {
            val bloom = smooth(phase(t, 3167f, 3367f)) * (1f - smooth(phase(t, 3467f, 3633f)))
            canvas.save(); canvas.translate(width * phase(t, 3280f, 3610f), height * .85f)
            val radius = max(width, height) * (1f + bloom)
            canvas.scale(radius, radius); paint.shader = pink; paint.alpha = (255 * bloom).toInt()
            canvas.drawCircle(0f, 0f, 1f, paint); canvas.restore(); paint.shader = null
            val flash = smooth(phase(t, 3310f, 3367f)) * (1f - smooth(phase(t, 3467f, 3567f)))
            paint.color = white; paint.alpha = (255 * flash).toInt()
            canvas.drawRect(0f, 0f, width, height, paint)
        }
        paint.alpha = 255; paint.colorFilter = null
    }

    private fun sampleRing(t: Float) {
        if (t < 800f) {
            val p = phase(t, 400f, 800f)
            ring[0] = 960f; ring[1] = 540f
            ring[2] = 35f + 395f * smooth(p); ring[3] = 20f + 255f * smooth(p); ring[4] = 0f
        } else if (t <= 2200f) {
            val index = ((t - 800f) * .03f).toInt().coerceIn(0, ringMotion.lastIndex - 1)
            val a = ringMotion[index]; val b = ringMotion[index + 1]
            val p = phase(t, a[0], b[0])
            for (i in 0..4) ring[i] = a[i + 1] + (b[i + 1] - a[i + 1]) * p
        } else {
            val p = smooth(phase(t, 2200f, 2700f))
            ring[0] = 937.7f; ring[1] = 449.3f
            ring[2] = 228.4f - 8.4f * p; ring[3] = 88.4f - 17.4f * p; ring[4] = 23.5f + 2.5f * p
        }
        // Move the crest to the Current / Music junction while the longer replacement title enters.
        ring[0] += 66f * smooth(phase(t, 2767f, 3567f))
    }

    private fun drawRing(c: Canvas, t: Float, opacity: Float) {
        val strength = HaloRingProjection.strength(t)
        if (strength < .005f) {
            drawSharpRing(c, t, opacity)
            return
        }
        val frames = if (t < 1400f) firstFlipFrames else secondFlipFrames
        val start = if (t < 1400f) FIRST_FLIP_START else SECOND_FLIP_START
        val frame = ((t - start) * (FLIP_FRAME_RATE / 1000f)).coerceIn(0f, frames.lastIndex.toFloat())
        val index = frame.toInt().coerceAtMost(frames.lastIndex - 1)
        val progress = frame - index
        paint.colorFilter = null; paint.shader = null; paint.style = Paint.Style.FILL
        paint.xfermode = additive
        paint.alpha = (255 * opacity * (1f - progress)).toInt()
        c.drawBitmap(frames[index].image, null, frames[index].bounds, paint)
        paint.alpha = (255 * opacity * progress).toInt()
        c.drawBitmap(frames[index + 1].image, null, frames[index + 1].bounds, paint)
        paint.xfermode = null
    }

    private fun prepareFlipFrames(start: Float, count: Int, working: Bitmap,
        canvas: Canvas, pixels: IntArray): Array<FlipFrame> {
        val blurCanvas = Canvas(working)
        val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val whiteMask = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val blurOffset = IntArray(2)
        return Array(count) { index ->
            working.eraseColor(Color.TRANSPARENT)
            val time = start + index * (1000f / FLIP_FRAME_RATE)
            sampleRing(time)
            drawRingExposure(canvas, time, 1f)
            val strength = HaloRingProjection.strength(time)
            if (strength >= .005f) {
                // Integrate adjacent exposure lines into a soft shutter trail. The ring is
                // monochrome here, so blurring its alpha preserves the emitted white light.
                blurPaint.maskFilter = BlurMaskFilter(4f * strength, BlurMaskFilter.Blur.NORMAL)
                val alpha = working.extractAlpha(blurPaint, blurOffset)
                working.eraseColor(Color.TRANSPARENT)
                blurCanvas.drawBitmap(alpha, blurOffset[0].toFloat(), blurOffset[1].toFloat(), whiteMask)
                alpha.recycle()
            }
            working.getPixels(pixels, 0, working.width, 0, 0, working.width, working.height)
            var left = working.width; var top = working.height; var right = 0; var bottom = 0
            for (y in 0 until working.height) for (x in 0 until working.width) {
                if (pixels[y * working.width + x] ushr 24 == 0) continue
                left = minOf(left, x); right = maxOf(right, x)
                top = minOf(top, y); bottom = maxOf(bottom, y)
            }
            val image = Bitmap.createBitmap(working, left, top, right - left + 1, bottom - top + 1)
            image.prepareToDraw()
            FlipFrame(image, RectF(left / FLIP_CACHE_SCALE, top / FLIP_CACHE_SCALE - FLIP_CACHE_TOP,
                (right + 1) / FLIP_CACHE_SCALE, (bottom + 1) / FLIP_CACHE_SCALE - FLIP_CACHE_TOP))
        }
    }

    private fun drawRingExposure(c: Canvas, t: Float, opacity: Float) {
        val strength = HaloRingProjection.strength(t)
        if (strength < .005f) {
            drawSharpRing(c, t, opacity)
            return
        }
        // Evaluate the real trajectory over a 1/30 s shutter. This is deterministic when
        // seeking/previewing; the cached exposure never depends on past display frames.
        paint.xfermode = additive
        for (sample in -4..4) {
            val time = t + sample * (1000f / 240f) * strength
            val weight = (5 - abs(sample)) / 25f
            sampleRing(time)
            drawProjectedRing(c, time, opacity * weight)
        }
        paint.xfermode = null
        sampleRing(t)
    }

    private fun drawProjectedRing(c: Canvas, t: Float, opacity: Float) {
        val mix = HaloRingProjection.strength(t)
        val squeeze = HaloRingProjection.heightScale(t)
        val depth = HaloRingProjection.depth(t, ring[3] / ring[2]) * if (squeeze < 0f) -1f else 1f
        val rx = ring[2]
        val ry = ring[3]
        val height = ry * abs(squeeze)
        val rotation = ring[4] * PI.toFloat() / 180f
        val cr = cos(rotation)
        val sr = sin(rotation)
        val sweep = if (t < 933f) 3f + 320f * phase(t, 400f, 933f).pow(2.1f)
            else 323f + 37f * smooth(phase(t, 933f, 1200f))
        val start = if (t < 1000f) 90f else 270f
        val thickness = 27f - 8f * smooth(phase(t, 1100f, 2200f))
        val radialWidth = thickness / (2f * rx)
        projectedRingPath.rewind()
        ringPath.rewind()
        for (i in 0..96) {
            val theta = (start + i * sweep / 96f) * PI.toFloat() / 180f
            val ct = cos(theta)
            val st = sin(theta)
            val nx = ct / rx
            val ny = st / ry
            val normal = sqrt(nx * nx + ny * ny)
            val normalX = nx / normal * thickness / 2f
            val normalY = ny / normal * thickness / 2f
            val outerX = rx * ct + normalX
            val outerY = ry * st + normalY
            val innerX = rx * ct - normalX
            val innerY = ry * st - normalY
            val x = outerX + (HaloRingProjection.x(rx, ct, st, 1f + radialWidth, depth) - outerX) * mix
            val y = outerY + (HaloRingProjection.y(height, st, 1f + radialWidth, depth) - outerY) * mix
            val ix = innerX + (HaloRingProjection.x(rx, ct, st, 1f - radialWidth, depth) - innerX) * mix
            val iy = innerY + (HaloRingProjection.y(height, st, 1f - radialWidth, depth) - innerY) * mix
            val px = ring[0] + x * cr - y * sr
            val py = ring[1] + x * sr + y * cr
            if (i == 0) projectedRingPath.moveTo(px, py) else projectedRingPath.lineTo(px, py)
            val cx = rx * ct + (HaloRingProjection.x(rx, ct, st, 1f, depth) - rx * ct) * mix
            val cy = ry * st + (HaloRingProjection.y(height, st, 1f, depth) - ry * st) * mix
            val centerX = ring[0] + cx * cr - cy * sr
            val centerY = ring[1] + cx * sr + cy * cr
            if (i == 0) ringPath.moveTo(centerX, centerY) else ringPath.lineTo(centerX, centerY)
            projectedInner[i * 2] = ring[0] + ix * cr - iy * sr
            projectedInner[i * 2 + 1] = ring[1] + ix * sr + iy * cr
        }
        for (i in 96 downTo 0) projectedRingPath.lineTo(projectedInner[i * 2], projectedInner[i * 2 + 1])
        projectedRingPath.close()
        if (sweep >= 359.9f) ringPath.close()

        // Project the cached bloom through the same plane as the luminous ribbon.
        c.save(); c.translate(ring[0], ring[1]); c.rotate(ring[4])
        projectionValues[0] = rx * sqrt(1f - depth * depth) / 400f
        projectionValues[1] = 0f; projectionValues[2] = 0f
        projectionValues[3] = 0f; projectionValues[4] = height / 400f; projectionValues[5] = height * depth
        projectionValues[6] = 0f; projectionValues[7] = depth / 400f; projectionValues[8] = 1f
        projectionMatrix.setValues(projectionValues)
        c.concat(projectionMatrix)
        if (sweep < 359.9f) {
            glowSector.rewind(); glowSector.moveTo(0f, 0f)
            glowSector.arcTo(glowSectorBounds, start, sweep, false); glowSector.close()
            c.clipPath(glowSector)
        }
        paint.colorFilter = null; paint.shader = null; paint.style = Paint.Style.FILL
        paint.alpha = (255 * opacity).toInt()
        c.drawBitmap(ringGlow, -640f, -640f, paint)
        c.restore()
        paint.color = white; paint.alpha = (255 * opacity).toInt()
        c.drawPath(projectedRingPath, paint)
        // A luminous tube still radiates when its plane is edge-on. Screen-space bloom
        // conserves that light and joins adjacent shutter samples into a continuous smear.
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND; paint.strokeCap = Paint.Cap.ROUND
        for (i in 3 downTo 1) {
            paint.strokeWidth = thickness + i * 16f
            paint.alpha = (255 * opacity * mix * when (i) { 3 -> .13f; 2 -> .25f; else -> .45f }).toInt()
            c.drawPath(ringPath, paint)
        }
        paint.strokeWidth = thickness * .55f
        paint.alpha = (255 * opacity * mix * 2.7f).toInt().coerceAtMost(255)
        c.drawPath(ringPath, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawSharpRing(c: Canvas, t: Float, opacity: Float) {
        if (t < 367f) return
        val sweep = if (t < 933f) 3f + 320f * phase(t, 400f, 933f).pow(2.1f)
            else 323f + 37f * smooth(phase(t, 933f, 1200f))
        val start = if (t < 1000f) 90f else 270f
        val r = ring[4] * PI.toFloat() / 180f
        val cs = cos(r); val sn = sin(r)
        ringPath.rewind()
        for (i in 0..96) {
            val theta = (start + i * sweep / 96f) * PI.toFloat() / 180f
            val x = cos(theta) * ring[2]; val y = sin(theta) * ring[3]
            val px = ring[0] + x * cs - y * sn; val py = ring[1] + x * sn + y * cs
            if (i == 0) ringPath.moveTo(px, py) else ringPath.lineTo(px, py)
        }
        if (sweep >= 359.9f) ringPath.close()
        val thickness = 27f - 8f * smooth(phase(t, 1100f, 2200f)) +
            50f * exp(-((t - 1000f) / 36f).pow(2)) + 18f * exp(-((t - 1800f) / 32f).pow(2))
        paint.colorFilter = null; paint.color = white; paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND; paint.strokeCap = Paint.Cap.ROUND
        // Every stage glows, including the open arc. Sector clipping avoids drawing a ghost full ring.
        c.save(); c.translate(ring[0], ring[1]); c.rotate(ring[4])
        c.scale(ring[2] / 400f, ring[3] / 400f)
        if (sweep < 359.9f) {
            glowSector.rewind(); glowSector.moveTo(0f, 0f)
            glowSector.arcTo(glowSectorBounds, start, sweep, false); glowSector.close()
            c.clipPath(glowSector)
        }
        paint.alpha = (opacity * 255 * phase(t, 367f, 433f)).toInt()
        c.drawBitmap(ringGlow, -640f, -640f, paint); c.restore()
        // Perspective must flatten the ring, not crush its bloom into an unlit white edge.
        // These screen-space strokes preserve the soft halo through both edge-on flips.
        val edgeBloom = (1f - ring[3] / ring[2].coerceAtLeast(1f)).coerceIn(0f, 1f)
        for (i in 8 downTo 1) {
            paint.strokeWidth = thickness + i * 12f
            paint.alpha = (opacity * edgeBloom * 255 * .045f * phase(t, 367f, 433f)).toInt()
            c.drawPath(ringPath, paint)
        }
        paint.strokeWidth = thickness; paint.alpha = (255 * opacity * phase(t, 367f, 433f)).toInt()
        c.drawPath(ringPath, paint); paint.style = Paint.Style.FILL
    }

    private fun drawStar(c: Canvas, t: Float, opacity: Float, colorPhase: Float) {
        val shift = ring[0] - 937.7f
        // Two independently arriving pointed arms, with a small damped overshoot on landing.
        for (arm in HaloStarArm.entries) {
            val p = arm.progress(t)
            if (p <= 0f) continue
            val settle = 1f + 1.1f * (p - 1).pow(3) + .1f * (p - 1).pow(2)
            c.save(); c.translate(shift + (1f - settle) * arm.travelX, (1f - settle) * arm.travelY)
            scratchPath.rewind()
            scratchPath.moveTo(arm.points[0], arm.points[1])
            for (i in 2 until arm.points.size step 2) scratchPath.lineTo(arm.points[i], arm.points[i + 1])
            scratchPath.close()
            paint.color = blend(white, cyan, colorPhase); paint.alpha = (255 * opacity).toInt()
            c.drawPath(scratchPath, paint); c.restore()
        }
    }

    private fun drawTitle(c: Canvas, t: Float, opacity: Float, colorPhase: Float) {
        val p = phase(t, 2767f, 3633f)
        val spacing = 1f + 5.8f * (1f - p).pow(2.25f)
        val fontScale = 1.78f
        val center = 401f
        paint.alpha = (255 * opacity * phase(t, 2767f, 2900f)).toInt()
        for ((i, glyph) in glyphs.withIndex()) {
            val x = 960f + (glyph.x - center) * fontScale * spacing
            // Fixed source bitmaps retain glow; hardware translates/scales them at native refresh rate.
            paint.colorFilter = if (i < 7 && colorPhase >= .7f) cyanTint else whiteTint
            bounds.set(x - 22 * fontScale, 535f - 22 * fontScale,
                x + 128 * fontScale, 535f + 113 * fontScale)
            c.drawBitmap(glyph.image, null, bounds, paint)
        }
        paint.colorFilter = null
    }

    private fun drawName(c: Canvas, t: Float, opacity: Float) {
        val appear = phase(t, 4033f, 4600f)
        val glitch = if (t < 4600f) 1f - appear else if (t in 5367f..6067f)
            sin(phase(t, 5367f, 6067f) * PI.toFloat()).coerceAtLeast(0f) * .5f else 0f
        val height = nameWidth / nameImage.width * nameImage.height
        val right = 1650f; val top = 705f
        paint.alpha = (255 * opacity * minOf(1f, appear * 3f)).toInt()
        val slices = 5
        for (i in 0 until slices) {
            source.set(0, i * nameImage.height / slices, nameImage.width, (i + 1) * nameImage.height / slices)
            val offset = sin(t * .081f + i * 2.7f) * 52f * glitch
            bounds.set(right - nameWidth + offset, top + i * height / slices,
                right + offset, top + (i + 1) * height / slices)
            if (glitch > .05f) {
                bounds.offset(7f * glitch, 0f); paint.colorFilter = redTint; c.drawBitmap(nameImage, source, bounds, paint)
                bounds.offset(-14f * glitch, 0f); paint.colorFilter = cyanTint; c.drawBitmap(nameImage, source, bounds, paint)
                bounds.offset(7f * glitch, 0f)
            }
            paint.colorFilter = null; c.drawBitmap(nameImage, source, bounds, paint)
        }
    }

    private fun drawParticles(c: Canvas, width: Float, height: Float, t: Float, opacity: Float) {
        val scale = min(width, height) / 1080f
        for ((i, p) in particles.withIndex()) {
            val x = ((p.x + t * p.speed / 1900000f) % 1.2f - .1f) * width
            val y = ((p.y - t * p.speed / 2600000f + 2.4f) % 1.2f - .1f) * height
            val radius = p.radius * scale
            paint.color = 0xFF5EBFEA.toInt(); paint.alpha = (255 * p.alpha * opacity).toInt()
            if (i % 3 == 0) {
                paint.strokeWidth = max(.8f, scale * 2f)
                c.drawLine(x, y, x + 3 * scale, y - 8 * scale, paint)
            } else if (i % 3 == 1) {
                paint.alpha = (255 * p.alpha * opacity * .25f).toInt()
                c.drawCircle(x, y, radius, paint)
            } else {
                c.save(); c.translate(x, y); c.scale(radius * 2, radius * 2)
                paint.shader = halo; paint.colorFilter = blueTint
                c.drawCircle(0f, 0f, 1f, paint)
                c.restore(); paint.shader = null; paint.colorFilter = null
            }
        }
    }

    private fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt())
    private fun phase(t: Float, start: Float, end: Float) = ((t - start) / (end - start)).coerceIn(0f, 1f)
    private fun smooth(t: Float) = t * t * (3 - 2 * t)
}

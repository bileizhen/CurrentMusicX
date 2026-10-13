package io.github.currencortex.music.feature.library

import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.min

/** One short-lived record, using the gallery's existing texture and frame clock. */
internal class RibbonRecordFlight(val source: Rect, var target: Rect, private val artwork: BitmapShader?) {
    var progress = 0f
        private set
    companion object { const val LaunchFraction = .32f }
    private fun smooth(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
    private val extraction get() = smooth(progress / LaunchFraction)
    private val travel get() = smooth((progress - LaunchFraction) / (1f - LaunchFraction))
    val opening get() = if (progress < LaunchFraction) extraction else 1f - smooth((progress - LaunchFraction) / .25f)
    private val launch get() = source.center + Offset(source.width * .18f, -source.height * .58f)
    val center: Offset get() {
        if (progress < LaunchFraction) return source.center + (launch - source.center) * extraction
        val t = travel
        // A quadratic arc continues upwards before descending into the actual mini-cover slot.
        val control = Offset((launch.x + target.center.x) / 2f, min(launch.y, target.center.y) - source.height * .5f)
        val q = 1f - t
        return launch * (q * q) + control * (2f * q * t) + target.center * (t * t)
    }
    val diameter get() = source.width * .86f * (1f - travel) + target.width * travel
    fun advance(seconds: Float) { progress = (progress + seconds.coerceIn(0f, .05f) / 1.5f).coerceAtMost(1f) }

    fun draw(canvas: Canvas, paint: Paint) {
        val point = center
        val diameter = diameter
        val save = canvas.save()
        canvas.translate(point.x - diameter / 2f, point.y - diameter / 2f)
        canvas.scale(diameter, diameter)
        paint.shader = null; paint.style = Paint.Style.FILL
        paint.color = Color.BLACK; paint.alpha = 75
        canvas.drawCircle(.51f, .52f, .5f, paint)
        paint.color = 0xFF17171C.toInt()
        canvas.drawCircle(.5f, .5f, .5f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = .002f
        for (ring in 0..10) {
            paint.color = if (ring % 2 == 0) 0xFF36363F.toInt() else 0xFF23232B.toInt()
            canvas.drawCircle(.5f, .5f, .23f + ring * .023f, paint)
        }
        paint.strokeWidth = .008f; paint.color = 0xFF777783.toInt(); paint.alpha = 100
        canvas.drawArc(.014f, .014f, .986f, .986f, 205f, 80f, false, paint)
        canvas.drawArc(.014f, .014f, .986f, .986f, 25f, 60f, false, paint)
        paint.style = Paint.Style.FILL; paint.color = Color.WHITE; paint.alpha = 255
        // The label expands on arrival to merge into the mini player's circular artwork.
        val merge = smooth((travel - .78f) / .22f)
        val labelRadius = .21f + .28f * merge
        val label = canvas.save()
        canvas.rotate(210f * smooth(progress) * (1f - merge), .5f, .5f)
        canvas.translate(.5f - labelRadius, .5f - labelRadius)
        canvas.scale(labelRadius * 2f, labelRadius * 2f)
        paint.shader = artwork
        if (artwork == null) paint.color = 0xFFB19EEF.toInt()
        canvas.drawCircle(.5f, .5f, .5f, paint)
        paint.shader = null
        canvas.restoreToCount(label)
        paint.color = Color.BLACK; paint.alpha = ((1f - merge) * 255).toInt()
        canvas.drawCircle(.5f, .5f, .025f, paint)
        paint.alpha = 255
        canvas.restoreToCount(save)
    }
}

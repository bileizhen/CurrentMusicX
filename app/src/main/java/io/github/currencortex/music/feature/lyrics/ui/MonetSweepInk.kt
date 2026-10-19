// SPDX-License-Identifier: AGPL-3.0-only
// Android batch adapter for Folia MonetRailTokens.tsx's feathered mask (26c57f9b).
package io.github.currencortex.music.feature.lyrics.ui

import android.graphics.BitmapShader
import android.graphics.Canvas.VertexMode
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

/** All glyph sweeps share one paragraph texture and one native draw, including feathered edges. */
internal class MonetSweepInk(ink: ImageBitmap, color: Color) {
    private val mesh = MonetInkMesh(ink, color)
    fun begin() = mesh.begin()
    fun opaque(box: Rect) = mesh.quad(box, box, 1f, 1f)
    fun add(box: Rect, edgeEnd: Float, softness: Float, rtl: Boolean) {
        val left = if (rtl) box.right - edgeEnd else box.left + edgeEnd - softness
        val right = left + softness
        val middle = left + softness * if (rtl) .55f else .45f
        fun alpha(x: Float): Float {
            val p = ((x - left) / softness).coerceIn(0f, 1f).let { if (rtl) 1f - it else it }
            return if (p <= .45f) 1f - p / .45f * .08f else .92f * (1f - (p - .45f) / .55f)
        }
        var from = box.left
        fun piece(edge: Float) {
            val to = edge.coerceIn(box.left, box.right)
            if (to > from) {
                val rect = Rect(from, box.top, to, box.bottom)
                mesh.quad(rect, rect, alpha(from), alpha(to)); from = to
            }
        }
        piece(left); piece(middle); piece(right); piece(box.right)
    }
    fun draw(scope: DrawScope) = mesh.draw(scope)
}

/** Native vertices batch the immutable alpha texture; only vertex alpha/positions change per frame. */
internal class MonetInkMesh(ink: ImageBitmap, color: Color, capacity: Int = 1536) {
    private var vertices = FloatArray(capacity)
    private var texture = FloatArray(capacity)
    private var colors = IntArray(capacity / 2)
    private var count = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        shader = BitmapShader(ink.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        colorFilter = PorterDuffColorFilter(color.toArgb(), PorterDuff.Mode.SRC_IN)
    }
    fun begin() { count = 0 }
    fun quad(box: Rect, uv: Rect, leftAlpha: Float, rightAlpha: Float) {
        if (leftAlpha <= 0f && rightAlpha <= 0f) return
        if (count + 12 > vertices.size) {
            vertices = vertices.copyOf(vertices.size * 2)
            texture = texture.copyOf(vertices.size)
            colors = colors.copyOf(vertices.size / 2)
        }
        fun vertex(x: Float, y: Float, u: Float, v: Float, alpha: Float) {
            vertices[count] = x; texture[count++] = u
            vertices[count] = y; texture[count++] = v
            colors[count / 2 - 1] = ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24) or 0x00FFFFFF
        }
        vertex(box.left, box.top, uv.left, uv.top, leftAlpha)
        vertex(box.right, box.top, uv.right, uv.top, rightAlpha)
        vertex(box.left, box.bottom, uv.left, uv.bottom, leftAlpha)
        vertex(box.left, box.bottom, uv.left, uv.bottom, leftAlpha)
        vertex(box.right, box.top, uv.right, uv.top, rightAlpha)
        vertex(box.right, box.bottom, uv.right, uv.bottom, rightAlpha)
    }
    fun draw(scope: DrawScope) {
        if (count > 0) scope.drawContext.canvas.nativeCanvas.drawVertices(VertexMode.TRIANGLES, count,
            vertices, 0, texture, 0, colors, 0, null, 0, 0, paint)
    }
}

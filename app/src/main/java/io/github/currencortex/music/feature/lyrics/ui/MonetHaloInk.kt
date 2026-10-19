// SPDX-License-Identifier: AGPL-3.0-only
// Android atlas adapter for Folia's two absolute-time text shadows. See THIRD_PARTY_NOTICES.md.
package io.github.currencortex.music.feature.lyrics.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope

internal class MonetHaloInk(halos: List<Pair<Int, Pair<ImageBitmap, Offset>>>, color: Color) {
    private data class Patch(val word: Int, val box: Rect, val uv: Rect)
    private val patches: List<Patch>
    private val mesh: MonetInkMesh
    init {
        // Pack once per cached paragraph/weight, rather than switching a texture for each shadow.
        val width = maxOf(1024, halos.maxOfOrNull { it.second.first.width } ?: 1)
        var x = 0; var y = 0; var row = 0
        val origins = halos.map { (_, halo) ->
            val bitmap = halo.first
            if (x + bitmap.width > width) { x = 0; y += row + 2; row = 0 }
            val origin = x to y
            x += bitmap.width + 2; row = maxOf(row, bitmap.height)
            origin
        }
        val atlas = android.graphics.Bitmap.createBitmap(width, (y + row).coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(atlas)
        patches = halos.mapIndexed { index, (word, halo) ->
            val bitmap = halo.first
            val (left, top) = origins[index]
            canvas.drawBitmap(bitmap.asAndroidBitmap(), left.toFloat(), top.toFloat(), null)
            Patch(word, Rect(halo.second.x, halo.second.y, halo.second.x + bitmap.width, halo.second.y + bitmap.height),
                Rect(left.toFloat(), top.toFloat(), (left + bitmap.width).toFloat(), (top + bitmap.height).toFloat()))
        }
        atlas.prepareToDraw()
        mesh = MonetInkMesh(atlas.asImageBitmap(), color, maxOf(12, patches.size * 12))
    }
    fun draw(scope: DrawScope, strengths: FloatArray) {
        mesh.begin()
        patches.forEach { patch ->
            val alpha = (strengths[patch.word] * .88f).coerceIn(0f, 1f)
            if (alpha > .001f) mesh.quad(patch.box, patch.uv, alpha, alpha)
        }
        mesh.draw(scope)
    }
}

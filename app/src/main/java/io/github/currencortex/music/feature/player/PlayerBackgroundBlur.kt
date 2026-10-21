package io.github.currencortex.music.feature.player

/** Separable box passes for Android 8–11, where Compose's RenderEffect blur is unavailable. */
internal fun blurBackgroundPixels(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
    require(width > 0 && height > 0 && pixels.size == width * height)
    if (radius <= 0) return pixels.copyOf()
    val r = radius.coerceAtMost(48)
    val window = 2 * r + 1
    var source = pixels.copyOf()
    var target = IntArray(pixels.size)
    repeat(3) {
        repeat(2) { axis ->
            val length = if (axis == 0) width else height
            val lines = if (axis == 0) height else width
            val stride = if (axis == 0) 1 else width
            for (line in 0 until lines) {
                val start = if (axis == 0) line * width else line
                var a = 0; var red = 0; var green = 0; var blue = 0
                fun add(index: Int, sign: Int) {
                    val color = source[start + index.coerceIn(0, length - 1) * stride]
                    a += (color ushr 24) * sign; red += (color ushr 16 and 255) * sign
                    green += (color ushr 8 and 255) * sign; blue += (color and 255) * sign
                }
                for (index in -r..r) add(index, 1)
                for (index in 0 until length) {
                    target[start + index * stride] = ((a / window) shl 24) or ((red / window) shl 16) or
                        ((green / window) shl 8) or (blue / window)
                    add(index - r, -1); add(index + r + 1, 1)
                }
            }
            val previous = source; source = target; target = previous
        }
    }
    return source
}

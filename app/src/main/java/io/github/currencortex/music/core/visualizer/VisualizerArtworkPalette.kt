package io.github.currencortex.music.core.visualizer

import kotlin.math.*

/** Extracted once per artwork load. Dark borders do not drown out its chromatic subject. */
data class VisualizerArtworkPalette(val primary: Int, val accent: Int) {
    companion object {
        fun fromPixels(pixels: IntArray): VisualizerArtworkPalette? {
            val weights = FloatArray(16)
            val hueX = FloatArray(16); val hueY = FloatArray(16)
            val saturation = FloatArray(16)
            var opaque = 0
            var neutral = 0f
            for (pixel in pixels) {
                if ((pixel ushr 24) < 128) continue
                opaque++
                val r = (pixel ushr 16 and 255) / 255f
                val g = (pixel ushr 8 and 255) / 255f
                val b = (pixel and 255) / 255f
                val high = maxOf(r, g, b); val low = minOf(r, g, b)
                neutral += (r + g + b) / 3f
                val chroma = high - low
                val sat = if (high == 0f) 0f else chroma / high
                if (high < .12f || sat < .18f) continue
                val sector = when (high) {
                    r -> (g - b) / chroma
                    g -> (b - r) / chroma + 2f
                    else -> (r - g) / chroma + 4f
                }
                val hue = ((sector / 6f) % 1f + 1f) % 1f
                val bin = (hue * 16 + .5f).toInt() % 16
                val weight = sat * sqrt(high)
                weights[bin] += weight
                hueX[bin] += cos(hue * 2f * PI.toFloat()) * weight
                hueY[bin] += sin(hue * 2f * PI.toFloat()) * weight
                saturation[bin] += sat * weight
            }
            if (opaque == 0) return null
            val first = weights.indices.maxBy { weights[it] }
            if (weights[first] == 0f) {
                // A grayscale cover stays neutral; missing/transparent covers use preset defaults.
                val value = (.55f + neutral / opaque * .35f).coerceIn(.55f, .9f)
                return VisualizerArtworkPalette(rgb(0f, 0f, value), rgb(0f, 0f, (value + .12f).coerceAtMost(.95f)))
            }
            fun hue(bin: Int): Float = ((atan2(hueY[bin], hueX[bin]) / (2f * PI.toFloat())) + 1f) % 1f
            val h = hue(first)
            val second = weights.indices.filter { minOf(abs(it - first), 16 - abs(it - first)) >= 2 }
                .maxByOrNull { weights[it] }
            val accentHue = if (second != null && weights[second] > weights[first] * .25f) hue(second) else (h + .085f) % 1f
            val sat = (saturation[first] / weights[first]).coerceIn(.45f, .82f)
            return VisualizerArtworkPalette(rgb(h, sat, .92f), rgb(accentHue, sat * .75f, .96f))
        }

        private fun rgb(h: Float, s: Float, v: Float): Int {
            val x = h * 6f
            val i = x.toInt(); val f = x - i
            val p = v * (1f - s); val q = v * (1f - f * s); val t = v * (1f - (1f - f) * s)
            val (r, g, b) = when (i % 6) {
                0 -> Triple(v, t, p); 1 -> Triple(q, v, p); 2 -> Triple(p, v, t)
                3 -> Triple(p, q, v); 4 -> Triple(t, p, v); else -> Triple(v, p, q)
            }
            return (0xFF shl 24) or ((r * 255).roundToInt() shl 16) or ((g * 255).roundToInt() shl 8) or (b * 255).roundToInt()
        }
    }
}

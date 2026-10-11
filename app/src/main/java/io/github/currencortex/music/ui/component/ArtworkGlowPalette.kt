package io.github.currencortex.music.ui.component

import kotlin.math.abs

internal data class ArtworkGlowPalette(val primary: Int, val secondary: Int) {
    companion object {
        val Neutral = ArtworkGlowPalette(0xff9da9b6.toInt(), 0xffb3a7b6.toInt())
        private data class CoverColour(val argb: Int, val population: Int)

        private fun chroma(color: Int): Int {
            val channels = listOf(16, 8, 0).map { color ushr it and 255 }
            return channels.max() - channels.min()
        }

        /** Small quantized histogram: prefer real cover colours over white lettering or black borders. */
        fun fromPixels(pixels: IntArray): ArtworkGlowPalette {
            val counts = IntArray(4096)
            val red = LongArray(4096); val green = LongArray(4096); val blue = LongArray(4096)
            pixels.forEach { color ->
                if ((color ushr 24) < 128) return@forEach
                val r = color ushr 16 and 255; val g = color ushr 8 and 255; val b = color and 255
                val max = maxOf(r, g, b); val min = minOf(r, g, b)
                if (max < 28 || min > 225) return@forEach
                val bucket = (r / 16 shl 8) or (g / 16 shl 4) or (b / 16)
                counts[bucket]++; red[bucket] += r.toLong(); green[bucket] += g.toLong(); blue[bucket] += b.toLong()
            }
            val candidates = counts.indices.filter { counts[it] > 0 }.sortedByDescending { i ->
                val chroma = (maxOf(red[i], green[i], blue[i]) - minOf(red[i], green[i], blue[i])).toFloat() / (counts[i] * 255)
                counts[i] * (.25f + chroma)
            }.map { i ->
                val r = (red[i] / counts[i]).toInt(); val g = (green[i] / counts[i]).toInt(); val b = (blue[i] / counts[i]).toInt()
                val max = maxOf(r, g, b).toFloat()
                val spread = max - minOf(r, g, b)
                // Keep the cover's character; light diffusion supplies brightness, not extra saturation.
                val saturationBoost = if (spread < 12f) 1f else 1.12f
                val brighten = maxOf(1f, 205f / max)
                fun channel(value: Int) = ((max - (max - value) * saturationBoost) * brighten).toInt().coerceIn(0, 255)
                CoverColour((255 shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b), counts[i])
            }
            val leading = candidates.firstOrNull() ?: return Neutral
            val minimumAccentPopulation = maxOf(2, leading.population / 10)
            val dominant = if (chroma(leading.argb) >= 32) leading else candidates.firstOrNull {
                it.population >= minimumAccentPopulation && chroma(it.argb) >= 32
            } ?: leading
            val first = dominant.argb
            // Grey lettering and tiny accents must not wash out one half of a colourful cover.
            val minimumPopulation = maxOf(2, dominant.population / 10)
            val minimumChroma = if (chroma(first) < 32) 0 else maxOf(32, chroma(first) / 2)
            val second = candidates.firstOrNull { candidate ->
                val color = candidate.argb
                candidate.population >= minimumPopulation &&
                    chroma(color) >= minimumChroma &&
                    listOf(16, 8, 0).sumOf { shift -> abs((color ushr shift and 255) - (first ushr shift and 255)) } > 110
            }?.argb ?: first
            return ArtworkGlowPalette(first, second)
        }
    }
}

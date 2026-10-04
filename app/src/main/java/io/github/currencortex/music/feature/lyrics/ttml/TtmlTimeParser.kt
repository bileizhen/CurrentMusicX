package io.github.currencortex.music.feature.lyrics.ttml

import io.github.currencortex.music.feature.lyrics.model.*
import java.math.BigDecimal
import java.math.RoundingMode

/** AMLL second/minute/hour clocks and TTML offset units, without floating-point rounding. */
object TtmlTimeParser {
    fun parse(value: String): Long {
        val source = value.trim()
        try {
            val unit = Regex("^(\\d+(?:\\.\\d+)?)(ms|s|m|h)$").matchEntire(source)
            val seconds = if (unit != null) {
                BigDecimal(unit.groupValues[1]).multiply(when (unit.groupValues[2]) {
                    "ms" -> BigDecimal("0.001"); "m" -> BigDecimal(60); "h" -> BigDecimal(3600); else -> BigDecimal.ONE
                })
            } else {
                val parts = source.split(':')
                if (parts.size !in 1..3 || parts.any { !it.matches(Regex("\\d+(?:\\.\\d+)?")) } ||
                    parts.dropLast(1).any { '.' in it }) throw NumberFormatException("Invalid clock")
                if (parts.size > 1 && BigDecimal(parts.last()) >= BigDecimal(60)) throw NumberFormatException("Invalid seconds")
                if (parts.size == 3 && BigDecimal(parts[1]) >= BigDecimal(60)) throw NumberFormatException("Invalid minutes")
                parts.fold(BigDecimal.ZERO) { total, part -> total.multiply(BigDecimal(60)).add(BigDecimal(part)) }
            }
            return seconds.multiply(BigDecimal(1000)).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (e: NumberFormatException) {
            throw LyricsException(LyricsErrorCode.INVALID_TIMELINE, "Invalid TTML time: $source", e)
        } catch (e: ArithmeticException) {
            throw LyricsException(LyricsErrorCode.INVALID_TIMELINE, "TTML time exceeds millisecond range", e)
        }
    }
}

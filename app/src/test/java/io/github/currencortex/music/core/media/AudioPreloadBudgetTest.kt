package io.github.currencortex.music.core.media

import org.junit.Assert.*
import org.junit.Test

class AudioPreloadBudgetTest {
    @Test fun foregroundGetsHeadroomAndWriterDoesNotThrashNearTheStartThreshold() {
        val budget = AudioPreloadBudget()
        assertFalse(budget.allow("track", 0, 3_000, 180_000))
        assertTrue(budget.allow("track", 0, 21_000, 180_000))
        assertTrue(budget.allow("track", 2_000, 21_000, 180_000))
        assertFalse(budget.allow("track", 15_000, 21_000, 180_000))
        assertFalse(budget.allow("track", 16_000, 35_000, 180_000))
        assertTrue(budget.allow("track", 16_000, 37_000, 180_000))
    }
    @Test fun aShortFullyBufferedSongCanPreloadButSwitchingTracksResetsItsBudget() {
        val budget = AudioPreloadBudget()
        assertTrue(budget.allow("one", 3_000, 12_000, 12_000))
        assertFalse(budget.allow("two", 0, 3_000, 180_000))
        assertFalse(budget.allow("two", 0, 30_000, 0))
        assertFalse(budget.allow("two", 180_000, 180_000, 180_000))
        assertTrue(budget.allow("two", 0, 30_000, 180_000))
        budget.reset()
        assertFalse(budget.allow("two", 0, 10_000, 180_000))
    }
}

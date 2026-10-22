package io.github.currencortex.music.core.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateSourceOrderTest {
    private val sources = listOf(UpdateSource.GITHUB, UpdateSource.TBAP, UpdateSource.CURRENTMUSIC,
        UpdateSource.GH_PROXY, UpdateSource.GEEKERTAO, UpdateSource.DPIK)

    @Test fun dedicatedSourceIsFirstEvenWhenUnavailableOrSlower() {
        for (status in UpdateSourceStatus.entries) {
            val health = sources.associateWith { UpdateSourceHealth(UpdateSourceStatus.AVAILABLE, 10) } +
                (UpdateSource.CURRENTMUSIC to UpdateSourceHealth(status, 9000))
            assertEquals(UpdateSource.CURRENTMUSIC, sources.sortedByHealth(health).first())
        }
    }

    @Test fun availableSourcesAreOrderedByLatencyBeforeFailedAndPendingSources() {
        val health = mapOf(
            UpdateSource.GITHUB to UpdateSourceHealth(UpdateSourceStatus.TIMEOUT, 5000),
            UpdateSource.TBAP to UpdateSourceHealth(UpdateSourceStatus.AVAILABLE, 200),
            UpdateSource.GH_PROXY to UpdateSourceHealth(UpdateSourceStatus.AVAILABLE, 50),
            UpdateSource.GEEKERTAO to UpdateSourceHealth(UpdateSourceStatus.LIMITED, 5),
        )
        assertEquals(listOf(UpdateSource.CURRENTMUSIC, UpdateSource.GH_PROXY, UpdateSource.TBAP,
            UpdateSource.GITHUB, UpdateSource.GEEKERTAO, UpdateSource.DPIK), sources.sortedByHealth(health))
    }

    @Test fun equalLatencyAndMissingResultsKeepTheOriginalOrder() {
        val health = sources.associateWith { UpdateSourceHealth(UpdateSourceStatus.AVAILABLE, 100) }
        val pinnedOrder = listOf(UpdateSource.CURRENTMUSIC) + sources.filter { it != UpdateSource.CURRENTMUSIC }
        assertEquals(pinnedOrder, sources.sortedByHealth(health))
        assertEquals(pinnedOrder, sources.sortedByHealth(emptyMap()))
    }
}

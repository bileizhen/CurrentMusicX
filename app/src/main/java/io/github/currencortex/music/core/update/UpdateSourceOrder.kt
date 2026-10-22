package io.github.currencortex.music.core.update

/** Pin the dedicated source; rank working alternatives by response time, leaving other rows stable. */
internal fun List<UpdateSource>.sortedByHealth(health: Map<UpdateSource, UpdateSourceHealth>): List<UpdateSource> =
    withIndex().sortedWith(compareBy<IndexedValue<UpdateSource>> {
        when {
            it.value == UpdateSource.CURRENTMUSIC -> 0
            health[it.value]?.status == UpdateSourceStatus.AVAILABLE -> 1
            else -> 2
        }
    }.thenBy {
        if (health[it.value]?.status == UpdateSourceStatus.AVAILABLE) health[it.value]?.latencyMs ?: Long.MAX_VALUE
        else Long.MAX_VALUE
    }.thenBy { it.index }).map { it.value }

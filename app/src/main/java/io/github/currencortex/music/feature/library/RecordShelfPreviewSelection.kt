package io.github.currencortex.music.feature.library

import io.github.currencortex.music.data.song.Song
import kotlin.random.Random

/** Both automatic modes skip videos; shuffle avoids the current record when another is available. */
internal fun nextRecordPreviewIndex(songs: List<Song>, selection: Int, shuffle: Boolean,
    random: Random = Random.Default): Int? {
    if (songs.isEmpty()) return null
    val candidates = (1..songs.size).map { Math.floorMod(selection + it, songs.size) }
        .filter { !songs[it].video }
    if (!shuffle) return candidates.firstOrNull()
    val alternatives = candidates.filter { it != selection }
    return (alternatives.ifEmpty { candidates }).randomOrNull(random)
}

package io.github.currencortex.music.data.roaming

import io.github.currencortex.music.data.kugou.KugouMusicRepository
import io.github.currencortex.music.data.kugou.KugouParsing
import io.github.currencortex.music.data.song.Song

/** KuGou "猜你喜欢" (Personal FM) backed by `pfm_scene_list_new` and `persnfm.service/personal_recommend`. */
class KugouRoamingRepository(private val repository: KugouMusicRepository) :
    io.github.currencortex.music.feature.roaming.RoamingRepository {
    private val songHashes = object : LinkedHashMap<Long, String>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, String>?): Boolean = size > 300
    }

    override suspend fun catalog(): RoamingCatalog =
        runCatching { repository.personalFmCatalog() }.getOrElse { KugouParsing.defaultRoamingCatalog() }

    override suspend fun songs(choice: RoamingChoice, excluded: List<Long>): List<Song> {
        val hashes = synchronized(songHashes) { excluded.mapNotNull(songHashes::get) }
        val poolId = choice.poolId.toIntOrNull() ?: choice.code.toIntOrNull() ?: 0
        val list = repository.personalFmSongs(songPoolId = poolId, excludedHashes = hashes, excludedIds = excluded)
        synchronized(songHashes) {
            for (song in list) {
                val hash = song.kugou?.hash?.takeIf(String::isNotBlank) ?: continue
                songHashes[song.id] = hash
            }
        }
        return list.filterNot { it.id in excluded }.distinctBy { it.id }
    }
}

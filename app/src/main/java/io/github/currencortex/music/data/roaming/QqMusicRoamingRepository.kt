package io.github.currencortex.music.data.roaming

import io.github.currencortex.music.data.qqmusic.QqMusicRepository
import io.github.currencortex.music.data.song.Song

/** QQ Music serves its real mode catalog and scene IDs; the shared UI only mirrors them. */
class QqMusicRoamingRepository(private val repository: QqMusicRepository) :
    io.github.currencortex.music.feature.roaming.RoamingRepository {
    override suspend fun catalog(): RoamingCatalog {
        val scenes = repository.radioScenes()
        return RoamingCatalog(
            modes = scenes.filter { it.id in MODE_IDS }.map { it.choice() },
            scenes = scenes.filter { it.id !in MODE_IDS }.map { it.choice() },
        )
    }

    override suspend fun songs(choice: RoamingChoice, excluded: List<Long>): List<Song> {
        val songs = repository.radio(poolId = choice.poolId, sceneId = choice.code)
        return songs.filter { it.id !in excluded }.distinctBy { it.id }
    }

    private fun io.github.currencortex.music.data.qqmusic.QqRadioScene.choice() =
        RoamingChoice(id, name, icon = cover, scene = id !in MODE_IDS, poolId = poolId)

    private companion object {
        val MODE_IDS = setOf("17", "18", "19")
    }
}

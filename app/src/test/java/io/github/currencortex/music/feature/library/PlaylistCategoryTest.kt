package io.github.currencortex.music.feature.library

import io.github.currencortex.music.data.library.Playlist
import org.junit.Assert.*
import org.junit.Test

class PlaylistCategoryTest {
    private fun playlist(source: String, owner: Long, nativeOwned: Boolean = false) =
        Playlist(1, "Playlist", "", "", source, owner, 0, emptyList(), nativeOwned = nativeOwned)

    @Test fun nativeCategoriesUseNeteaseOwnershipRatherThanCurrentMusicUserId() {
        val own = playlist("netease", 42, true)
        val saved = playlist("netease", 99)
        assertTrue(PlaylistCategory.CREATED.includes(own, 7))
        assertFalse(PlaylistCategory.COLLECTED.includes(own, 7))
        assertTrue(PlaylistCategory.COLLECTED.includes(saved, 7))
        assertFalse(PlaylistCategory.CREATED.includes(saved, 7))
    }

    @Test fun currentMusicOwnershipAndImportedSnapshotsStayDistinct() {
        val own = playlist("", 7)
        val imported = playlist("ncm", 7)
        assertTrue(PlaylistCategory.CREATED.includes(own, 7))
        assertFalse(PlaylistCategory.CREATED.includes(own, 8))
        assertTrue(PlaylistCategory.ALL.includes(imported, 7))
        assertFalse(PlaylistCategory.CREATED.includes(imported, 7))
        assertFalse(PlaylistCategory.COLLECTED.includes(imported, 7))
    }
}

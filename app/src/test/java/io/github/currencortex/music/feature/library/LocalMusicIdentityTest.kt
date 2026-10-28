package io.github.currencortex.music.feature.library

import org.junit.Assert.*
import org.junit.Test

class LocalMusicIdentityTest {
    @Test fun volumeAndMediaIdFormStableDistinctLocalIdentities() {
        val primary = "content://media/external_primary/audio/media/42"
        val sd = "content://media/1234-5678/audio/media/42"
        assertEquals(localSongId(primary), localSongId(primary))
        assertNotEquals(localSongId(primary), localSongId(sd))
        assertTrue(localSongId(primary) < 0)
        assertTrue(localSongId(sd) < 0)
    }
}

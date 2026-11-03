package io.github.currencortex.music.feature.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LocalFolderScannerTest {
    @Test fun onlySelectedTreeIsScannedIncludingNestedAudioWithoutDuplicates() = runBlocking {
        val dir = "vnd.android.document/directory"
        val tree = mapOf(
            "chosen" to listOf(LocalDocument("one", "One.mp3", "audio/mpeg"),
                LocalDocument("nested", "Albums", dir), LocalDocument("cover", "cover.jpg", "image/jpeg")),
            "nested" to listOf(LocalDocument("two", "Two.FLAC", "application/octet-stream"),
                LocalDocument("one", "One.mp3", "audio/mpeg"), LocalDocument("chosen", "Loop", dir)),
            "outside" to listOf(LocalDocument("three", "Three.mp3", "audio/mpeg")))
        val visited = mutableListOf<String>()
        val found = mutableListOf<String>()
        walkLocalAudio("chosen", { visited += it; tree.getValue(it) }, { found += it.id })
        assertEquals(listOf("chosen", "nested"), visited)
        assertEquals(listOf("one", "two"), found)
    }

    @Test fun revokedFolderGrantDoesNotFallBackToDeviceScan() = runBlocking {
        var calls = 0
        try {
            walkLocalAudio("chosen", { calls++; throw SecurityException("revoked") }, { fail("No files") })
            fail("Must report lost access")
        } catch (_: SecurityException) { assertEquals(1, calls) }
    }

    @Test fun cancellationStopsTraversal() = runBlocking {
        var calls = 0
        try {
            walkLocalAudio("chosen", { calls++; throw CancellationException() }, { fail("No files") })
            fail("Must cancel")
        } catch (_: CancellationException) { assertEquals(1, calls) }
    }
}

package io.github.currencortex.music

import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.AudioCache
import io.github.currencortex.music.core.storage.StorageStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class StorageStoreTest {
    @Test fun clearingPageCacheKeepsAudioAndInProgressTransfers(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val root = File(app.cacheDir, "storage-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getCacheDir() = root }
        val audio = AudioCache(context, File(root, "audio"))
        try {
            audio.refreshUsage()
            fun temp(path: String) = File(root, path).apply { parentFile!!.mkdirs(); writeText("fixture") }
            val audioMarker = temp("audio/keep")
            val otherAudio = temp("audio.isolated/keep")
            val download = temp("song-downloads/task/audio.download")
            val update = temp("updates/update.apk.part")
            val lyric = temp("lyrics/clear")
            val image = temp("image_cache/clear")
            val store = StorageStore(context, audio) { File(root, "image_cache").deleteRecursively() }
            assertTrue(store.measure().data > 0)
            assertEquals(0L, store.clearDataCache())
            listOf(audioMarker, otherAudio, download, update).forEach { assertTrue("Keep active resource: $it", it.exists()) }
            assertFalse(lyric.exists()); assertFalse(image.exists())
        } finally { audio.close(); root.deleteRecursively() }
    }
}

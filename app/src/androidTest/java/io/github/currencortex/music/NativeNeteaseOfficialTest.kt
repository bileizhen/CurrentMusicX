package io.github.currencortex.music

import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.core.network.RequestSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Explicit live public endpoint smoke test; no login or user-library writes. */
class NativeNeteaseOfficialTest {
    @Test fun officialSearchLyricsGenresArtistAndQrWorkOnDevice(): Unit = runBlocking {
        AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(), "official-${UUID.randomUUID()}", useNativeNetease = true).use { container ->
            container.ready.await(); container.sessionRestored.await()
            val result = container.musicRepository.search("Die For You") as AppResult.Success
            assertTrue(result.value.songs.any { it.name.contains("Die For You", true) && it.artists.contains("The Weeknd") })
            val key = container.bindingRepository.qrKey(RequestSession(container.accountRepository.server, null))
            assertTrue(key.isNotBlank())
            assertEquals(801, container.bindingRepository.qrStatus(key, RequestSession(container.accountRepository.server, null)).code)
            assertTrue(container.musicStyles.list().isNotEmpty())
            assertTrue(container.libraryRepository.artist(9621).songs.isNotEmpty())
            val song = container.musicRepository.detail(5271858)
            assertEquals(5271858L, song.id); assertTrue(song.name.isNotBlank())
            val lyrics = container.musicRepository.lyricsDocument(5271858) as AppResult.Success
            assertTrue(lyrics.value.lines.isNotEmpty()); assertEquals("网易云", lyrics.value.metadata.source)
            android.util.Log.i("NativeNetEaseVerified", "Official search, song detail, lyrics, genres, artist and QR passed; audio remains CurrentMusic")
        }
    }
}

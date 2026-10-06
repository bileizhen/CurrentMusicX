package io.github.currencortex.music.data.library

import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class SearchArtistRepositoryTest {
    private fun repository(server: MockWebServer) = LibraryRepository(
        ApiClient({ server.url("/configured/").toString() }, { "private-project-token" }, {}), { 7L },
        { RequestSession(server.url("/configured/").toString(), "private-project-token") })

    @Test fun artistAndAlbumSearchUseNormalizedPublicGatewayAndRealCounts() = runBlocking {
        MockWebServer().use { server ->
            val repository = repository(server)
            server.enqueue(MockResponse().setBody("""{"artists":[{"id":31,"name":"作者","pic":"http://example.com/artist","alias":"别名"}],"totals":{"artist":23},"hasMore":{"artist":true}}"""))
            val artists = repository.catalog("歌名", albums = false, offset = 30)
            assertEquals(23, artists.total); assertTrue(artists.more)
            assertEquals("https://example.com/artist", artists.entries.single().cover)
            val artistRequest = server.takeRequest()
            assertEquals("/configured/ncm/search", artistRequest.requestUrl!!.encodedPath)
            assertEquals("artist", artistRequest.requestUrl!!.queryParameter("type"))
            assertEquals("30", artistRequest.requestUrl!!.queryParameter("offset"))
            assertEquals("歌名", artistRequest.requestUrl!!.queryParameter("keywords"))
            assertNull(artistRequest.getHeader("Authorization"))
            server.enqueue(MockResponse().setBody("""{"albums":[{"id":41,"name":"专辑","artist":"作者","pic":"http://example.com/album"}],"totals":{"album":12},"hasMore":{"album":false}}"""))
            val albums = repository.catalog("歌名", albums = true)
            assertEquals(12, albums.total); assertFalse(albums.more)
            assertEquals("作者", albums.entries.single().subtitle)
            val albumRequest = server.takeRequest()
            assertEquals("album", albumRequest.requestUrl!!.queryParameter("type"))
            assertNull(albumRequest.getHeader("Authorization"))
        }
    }
    @Test fun artistHomepageKeepsSongAndAlbumTotalsAndReleaseMetadata() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"id":31,"name":"作者","pic":"http://example.com/artist","total":448,"more":true,"songs":[{"ncm_id":11,"name":"歌曲"}],"albumTotal":167,"albums":[{"id":41,"name":"专辑","pic":"http://example.com/album","publishTime":1790870400000,"size":6}]}"""))
            val artist = repository(server).artist(31, 100)
            assertEquals(448, artist.songTotal); assertEquals(167, artist.albumTotal)
            assertEquals(1790870400000, artist.albums.single().publishTime)
            assertEquals(6, artist.albums.single().size)
            val request = server.takeRequest()
            assertEquals("/configured/ncm/artist", request.requestUrl!!.encodedPath)
            assertEquals("100", request.requestUrl!!.queryParameter("offset"))
            assertNull(request.getHeader("Authorization"))
        }
    }
    @Test fun biographyParsesSectionsCachesSuccessAndRejectsServerErrors() = runBlocking {
        MockWebServer().use { server ->
            val repository = repository(server)
            server.enqueue(MockResponse().setBody("""{"code":200,"briefDesc":"艺人简介","introduction":[{"ti":"经历","txt":"详细经历"}]}"""))
            val biography = repository.artistBiography(31)
            assertEquals("艺人简介", biography.briefDesc)
            assertEquals("经历", biography.introduction.single().title)
            assertEquals("详细经历", biography.introduction.single().text)
            assertEquals(biography, repository.artistBiography(31))
            assertEquals(1, server.requestCount)
            val request = server.takeRequest()
            assertEquals("/configured/ncm/artist/desc", request.requestUrl!!.encodedPath)
            assertNull(request.getHeader("Authorization"))
            server.enqueue(MockResponse().setBody("""{"code":500,"briefDesc":"rejected"}"""))
            val result = appResult { repository.artistBiography(31, fresh = true) }
            assertEquals(ErrorKind.Server, (result as AppResult.Failure).kind)
        }
    }
}

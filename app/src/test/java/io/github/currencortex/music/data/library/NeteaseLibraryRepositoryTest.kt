package io.github.currencortex.music.data.library

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.binding.BindingRepository
import io.github.currencortex.music.data.song.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class NeteaseLibraryRepositoryTest {
    private fun json(body: String) = MockResponse().setBody(body)
    private fun lists(uid: Long = 7) = """{"code":200,"playlist":[
        {"id":9918106457,"name":"Native likes","specialType":5,"trackCount":1,"creator":{"userId":$uid}},
        {"id":88,"name":"My native playlist","creator":{"userId":$uid}},
        {"id":89,"name":"Someone else's playlist","creator":{"userId":9}}]}"""
    private fun fixture(server: MockWebServer): Triple<BindingRepository, NeteaseSongActionsRepository, NeteaseLibraryRepository> {
        val session = RequestSession(server.url("/cm/").toString(), "isolated")
        val api = ApiClient({ session.server }, { session.token }, {})
        val binding = BindingRepository(api, { session }) {}
        val actions = NeteaseSongActionsRepository(api) { session }
        return Triple(binding, actions, NeteaseLibraryRepository(api, binding, actions) { session })
    }
    private fun dispatch(server: MockWebServer, override: (RecordedRequest) -> MockResponse? = { null }) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                assertEquals("Bearer isolated", request.getHeader("Authorization"))
                override(request)?.let { return it }
                return when (request.requestUrl!!.encodedPath) {
                    "/cm/ncmbind" -> json("""{"bound":true,"profile":{"uid":7},"ncmLikedPlId":42}""")
                    "/cm/ncmbind/likelist" -> json("""{"ids":[11]}""")
                    "/cm/ncm/user/playlist" -> json(lists())
                    "/cm/ncm/playlist/detail" -> json("""{"code":200,"playlist":{"id":9918106457,"tracks":[]}}""")
                    "/cm/ncm/song/detail" -> {
                        val ids = request.requestUrl!!.queryParameter("ids")!!.split(',')
                        json("""{"code":200,"songs":[${ids.joinToString(",") { """{"id":$it,"name":"Track $it","ar":[{"id":2,"name":"Artist"}],"al":{"name":"Album"},"dt":180000}""" }}]}""")
                    }
                    else -> error("Unexpected endpoint: ${request.requestUrl!!.encodedPath}")
                }
            }
        }
    }
    @Test fun likedLibraryUsesRealUpstreamMembershipAndNeverTheImportedPlaylistId() = runBlocking {
        MockWebServer().use { server ->
            val paths = CopyOnWriteArrayList<String>()
            dispatch(server) { paths += it.requestUrl!!.encodedPath; null }
            val (_, _, native) = fixture(server)
            val playlist = native.likedPlaylist()
            assertEquals(9918106457L, playlist.id)
            assertEquals("netease", playlist.source)
            assertTrue(playlist.nativeOwned)
            assertEquals(listOf(11L), playlist.songs.map { it.id })
            assertEquals("Artist", playlist.songs.single().artists)
            assertFalse(paths.any { it == "/cm/likes/mine" || it.startsWith("/cm/playlists/") })
            assertFalse(playlist.editable(7))
        }
    }
    @Test fun nativeSongDetailsLoadEveryBatchInPlaylistOrder() = runBlocking {
        MockWebServer().use { server ->
            val batches = CopyOnWriteArrayList<Int>()
            val ids = (1..401).toList().reversed()
            dispatch(server) { request -> when (request.requestUrl!!.encodedPath) {
                "/cm/ncm/playlist/detail" -> json("""{"code":200,"playlist":{"id":88,"name":"Large","creator":{"userId":7},"trackCount":401,"trackIds":[${ids.joinToString(",") { """{"id":$it}""" }}]}}""")
                "/cm/ncm/song/detail" -> {
                    val size = request.requestUrl!!.queryParameter("ids")!!.split(',').size
                    batches += size
                    if (size > 100) json("""{"code":400}""") else null
                }
                else -> null
            } }
            val (_, _, native) = fixture(server)
            val playlist = native.playlist(88)
            assertEquals(ids.map(Int::toLong), playlist.songs.map { it.id })
            assertEquals(listOf(100, 100, 100, 100, 1), batches.toList())
        }
    }
    @Test fun songDetailsAcceptCurrentMusicGatewayMetadataWithoutDroppingTracks() = runBlocking {
        MockWebServer().use { server ->
            dispatch(server) { request -> when (request.requestUrl!!.encodedPath) {
                "/cm/ncm/song/detail" -> json("""{"songs":[{"ncm_id":11,"name":"Gateway track",
                    "artists":"Artist A / Artist B","artist_ids":[2,3],"album":"Gateway album",
                    "pic":"http://example.test/cover.jpg","duration":180000,"mv":9}]}""")
                "/cm/ncm/playlist/detail" -> if (request.requestUrl!!.queryParameter("id") == "88")
                    json("""{"code":200,"playlist":{"id":88,"name":"My native playlist",
                    "creator":{"userId":7},"trackCount":1,"trackIds":[{"id":11}]}}""") else null
                else -> null
            } }
            val (_, _, native) = fixture(server)
            for (playlist in listOf(native.likedPlaylist(), native.playlist(88))) {
                val song = playlist.songs.single()
                assertEquals(11L, song.id)
                assertEquals("Gateway track", song.name)
                assertEquals("Artist A / Artist B", song.artists)
                assertEquals("Gateway album", song.album)
                assertEquals("https://example.test/cover.jpg", song.cover)
                assertEquals(180000L, song.durationMs)
                assertEquals(listOf(2L, 3L), song.artistIds)
                assertEquals(9L, song.mv)
            }
        }
    }
    @Test fun playlistUsesEmbeddedTracksAndOnlyRequestsMissingMetadata() = runBlocking {
        MockWebServer().use { server ->
            val detailIds = CopyOnWriteArrayList<String>()
            dispatch(server) { request -> when (request.requestUrl!!.encodedPath) {
                "/cm/ncm/playlist/detail" -> json("""{"code":200,"playlist":{"id":88,"name":"Mixed",
                    "creator":{"userId":7},"trackCount":2,"trackIds":[{"id":12},{"id":11}],
                    "tracks":[{"id":11,"name":"Embedded","ar":[{"id":2,"name":"Artist"}],
                    "al":{"name":"Album","picUrl":"http://example.test/cover.jpg"},"dt":180000}]}}""")
                "/cm/ncm/song/detail" -> { detailIds += request.requestUrl!!.queryParameter("ids")!!; null }
                else -> null
            } }
            val (_, _, native) = fixture(server)
            val result = native.playlist(88)
            assertEquals(listOf(12L, 11L), result.songs.map { it.id })
            assertEquals("Embedded", result.songs[1].name)
            assertEquals(listOf("12"), detailIds.toList())
        }
    }
    @Test fun likedPlaylistKeepsConfirmedMembershipWhileReusingAndCachingEmbeddedTracks() = runBlocking {
        MockWebServer().use { server ->
            var details = 0
            var songRequests = 0
            dispatch(server) { request -> when (request.requestUrl!!.encodedPath) {
                "/cm/ncm/playlist/detail" -> {
                    details++
                    json("""{"code":200,"playlist":{"id":9918106457,"tracks":[
                        {"id":11,"name":"Embedded liked track"},{"id":12,"name":"Stale track"}]}}""")
                }
                "/cm/ncm/song/detail" -> { songRequests++; null }
                "/cm/ncmbind/like/11" -> json("""{"code":200,"like":false}""")
                else -> null
            } }
            val (_, actions, native) = fixture(server)
            assertEquals(listOf(11L), native.likedPlaylist().songs.map { it.id })
            assertEquals(listOf(11L), native.likedPlaylist().songs.map { it.id })
            assertEquals(1, details)
            assertEquals(0, songRequests)
            actions.setLiked(11, false)
            assertTrue(native.likedPlaylist().songs.isEmpty())
            assertEquals(2, details)
        }
    }
    @Test fun addingToOwnedPlaylistUsesConfirmedNativeWriteAndRejectsOtherOwners() = runBlocking {
        MockWebServer().use { server ->
            val writes = CopyOnWriteArrayList<RecordedRequest>()
            dispatch(server) { request -> if (request.requestUrl!!.encodedPath == "/cm/ncm/playlist/tracks") {
                writes += request; json("""{"code":200}""")
            } else null }
            val (_, _, native) = fixture(server)
            native.add(88, Song(11, "Track"))
            assertEquals("1", writes.single().requestUrl!!.queryParameter("confirm"))
            assertEquals("add", writes.single().requestUrl!!.queryParameter("op"))
            assertEquals("88", writes.single().requestUrl!!.queryParameter("pid"))
            assertEquals("11", writes.single().requestUrl!!.queryParameter("tracks"))
            assertEquals(ErrorKind.Forbidden, (appResult { native.add(89, Song(11, "Track")) } as AppResult.Failure).kind)
            assertEquals(1, writes.size)
        }
    }
    @Test fun likingAndUnlikingRemainConsistentWhileUpstreamListLags() = runBlocking {
        MockWebServer().use { server ->
            val intents = CopyOnWriteArrayList<Boolean>()
            dispatch(server) { request -> if (request.requestUrl!!.encodedPath == "/cm/ncmbind/like/11") {
                val liked = ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject["like"]!!.jsonPrimitive.boolean
                intents += liked; json("""{"code":200,"like":$liked}""")
            } else null }
            val (_, actions, native) = fixture(server)
            actions.setLiked(11, false)
            assertTrue(native.likedPlaylist(fresh = true).songs.isEmpty())
            native.add(9918106457L, Song(11, "Track"))
            assertEquals(listOf(11L), native.likedPlaylist(fresh = true).songs.map { it.id })
            assertEquals(listOf(false, true), intents.toList())
        }
    }
    @Test fun staleBindingAndBusinessFailureNeverReportSuccessfulCollection() = runBlocking {
        MockWebServer().use { server ->
            var stale = false
            dispatch(server) { request -> when (request.requestUrl!!.encodedPath) {
                "/cm/ncmbind" -> json("""{"bound":true,"stale":$stale,"profile":{"uid":7}}""")
                "/cm/ncm/playlist/tracks" -> json("""{"code":502}""")
                else -> null
            } }
            val (binding, _, native) = fixture(server)
            assertEquals(ErrorKind.Server, (appResult { native.add(88, Song(11, "Track")) } as AppResult.Failure).kind)
            stale = true; binding.status()
            assertEquals(ErrorKind.NeteaseBindingRequired, (appResult { native.likedPlaylist() } as AppResult.Failure).kind)
        }
    }
    @Test fun playlistListingLoadsFurtherPages() = runBlocking {
        MockWebServer().use { server ->
            val offsets = CopyOnWriteArrayList<String>()
            dispatch(server) { request -> if (request.requestUrl!!.encodedPath == "/cm/ncm/user/playlist") {
                val offset = request.requestUrl!!.queryParameter("offset")!!
                offsets += offset
                json(if (offset == "0") """{"code":200,"more":true,"playlist":[{"id":88,"creator":{"userId":7}}]}"""
                    else """{"code":200,"more":false,"playlist":[{"id":90,"creator":{"userId":7}}]}""")
            } else null }
            val (_, _, native) = fixture(server)
            assertEquals(listOf(88L, 90L), native.playlists().map { it.id })
            assertEquals(listOf("0", "1"), offsets.toList())
        }
    }
    @Test fun accountChangeWhileReadingBindingRejectsOldLibraryData() = runBlocking {
        MockWebServer().use { server ->
            val token = java.util.concurrent.atomic.AtomicReference("first")
            val requests = CopyOnWriteArrayList<String>()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request.requestUrl!!.encodedPath
                    token.set("second")
                    return json("""{"bound":true,"profile":{"uid":7}}""")
                }
            }
            val base = server.url("/cm/").toString()
            val api = ApiClient({ base }, { token.get() }, {})
            val session = { RequestSession(base, token.get()) }
            val binding = BindingRepository(api, session) {}
            val native = NeteaseLibraryRepository(api, binding, NeteaseSongActionsRepository(api, session = session), session)
            assertEquals(ErrorKind.Unauthorized, (appResult { native.likedPlaylist() } as AppResult.Failure).kind)
            assertEquals(listOf("/cm/ncmbind"), requests.toList())
            assertNull(binding.state.value)
        }
    }
    @Test fun changingTheBoundNeteaseUserDoesNotReuseThePreviousUsersLikedSnapshot() = runBlocking {
        MockWebServer().use { server ->
            var uid = 7L
            dispatch(server) { request -> when (request.requestUrl!!.encodedPath) {
                "/cm/ncmbind" -> json("""{"bound":true,"profile":{"uid":$uid}}""")
                "/cm/ncmbind/likelist" -> json("""{"ids":[${if (uid == 7L) 11 else 12}]}""")
                "/cm/ncm/user/playlist" -> json(lists(uid))
                else -> null
            } }
            val (binding, _, native) = fixture(server)
            assertEquals(listOf(11L), native.likedPlaylist().songs.map { it.id })
            uid = 8L; binding.status()
            val next = native.likedPlaylist()
            assertEquals(8L, next.ownerId)
            assertEquals(listOf(12L), next.songs.map { it.id })
        }
    }
}

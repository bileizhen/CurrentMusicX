package io.github.currencortex.music.data.song

import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class NeteaseSongActionsRepositoryTest {
    @Test fun delayedListsCannotUndoConfirmedRemovalAndOverridesAreScopedAndExpire()=runBlocking {
        MockWebServer().use { server ->
            var uid=7L; var clock=0L
            server.dispatcher=object:Dispatcher(){
                override fun dispatch(request:RecordedRequest)=MockResponse().setBody(when(request.requestUrl!!.encodedPath){
                    "/cm/ncmbind" -> """{"bound":true,"profile":{"uid":$uid}}"""
                    "/cm/ncmbind/likelist" -> {
                        assertNotNull(request.requestUrl!!.queryParameter("timestamp")); """{"ids":[11]}"""
                    }
                    "/cm/ncmbind/like/11" -> {
                        assertFalse(ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject["like"]!!.jsonPrimitive.boolean)
                        """{"code":200,"like":false}"""
                    }
                    else -> error("Unexpected endpoint")
                })
            }
            val s=RequestSession(server.url("/cm/").toString(),"personal")
            val repo=NeteaseSongActionsRepository(ApiClient({s.server},{s.token},{}),now={clock}){s}
            assertTrue(repo.isLiked(11))
            repo.setLiked(11,false,s,Song(11,"Track"))
            assertFalse(repo.isLiked(11,fresh=true))
            uid=8L;assertTrue(repo.isLiked(11,fresh=true))
            uid=7L;clock=121_000_000_000L;assertTrue(repo.isLiked(11,fresh=true))
        }
    }
    @Test fun failedOrContradictoryWriteDoesNotCreateAConfirmedMembership()=runBlocking {
        MockWebServer().use { server ->
            server.dispatcher=object:Dispatcher(){
                override fun dispatch(request:RecordedRequest)=MockResponse().setBody(when(request.requestUrl!!.encodedPath){
                    "/cm/ncmbind" -> """{"bound":true,"profile":{"uid":7}}"""
                    "/cm/ncmbind/likelist" -> """{"ids":[]}"""
                    "/cm/ncmbind/like/11" -> """{"code":200,"like":false}"""
                    else -> error("Unexpected endpoint")
                })
            }
            val s=RequestSession(server.url("/cm/").toString(),"personal")
            val repo=NeteaseSongActionsRepository(ApiClient({s.server},{s.token},{})){s}
            assertEquals(ErrorKind.Server,(appResult{repo.setLiked(11,true,s,Song(11,"Track"))} as AppResult.Failure).kind)
            assertFalse(repo.isLiked(11,fresh=true));assertEquals(0L,repo.revision.value)
        }
    }
    @Test fun olderServersFallBackOnlyWhenBindingLikeEndpointsAreMissing() = runBlocking {
        MockWebServer().use { server ->
            val paths = mutableListOf<String>()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path=request.requestUrl!!.encodedPath; paths += path
                    return MockResponse().setBody(when(path) {
                        "/cm/ncmbind" -> """{"bound":true,"profile":{"uid":7}}"""
                        "/cm/ncmbind/likelist", "/cm/ncmbind/like/11" -> return MockResponse().setResponseCode(404)
                        "/cm/ncm/likelist" -> """{"ids":[]}"""
                        "/cm/ncm/like" -> { assertEquals("true",request.requestUrl!!.queryParameter("like")); """{"code":200}""" }
                        else -> error(path)
                    })
                }
            }
            val s=RequestSession(server.url("/cm/").toString(),"personal")
            val repo=NeteaseSongActionsRepository(ApiClient({s.server},{s.token},{})){s}
            assertTrue(repo.toggleLiked(11,Song(11,"Track")))
            assertTrue(paths.containsAll(listOf("/cm/ncmbind/likelist","/cm/ncm/likelist","/cm/ncmbind/like/11","/cm/ncm/like")))
        }
    }
    @Test fun countsUseNeteaseTotalsAndPersonalLikesCanToggleWithoutCurrentMusicLikes() = runBlocking {
        val server = MockWebServer()
        var liked = true
        val paths = mutableListOf<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                paths += path
                assertEquals("Bearer personal", request.getHeader("Authorization"))
                return MockResponse().setBody(when (path) {
                    "/cm/ncmbind" -> """{"bound":true,"profile":{"uid":7},"ncmLikedPlId":42}"""
                    "/cm/ncmbind/likelist" -> """{"code":200,"ids":${if (liked) "[11]" else "[]"}}"""
                    "/cm/ncmbind/like/11" -> {
                        assertEquals("POST", request.method)
                        liked = io.github.currencortex.music.core.network.ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject["like"]!!.jsonPrimitive.boolean
                        """{"code":200}"""
                    }
                    "/cm/ncm/song/red/count" -> """{"code":200,"data":{"count":6804490,"countDesc":"100w+"},"pop":100}"""
                    "/cm/ncm/comment/music" -> """{"code":200,"total":1970250,"comments":[],"more":false}"""
                    else -> error("Unexpected endpoint $path")
                })
            }
        }
        server.start()
        try {
            val session = RequestSession(server.url("/cm/").toString(), "personal")
            val repo = NeteaseSongActionsRepository(ApiClient({ session.server }, { session.token }, {})) { session }
            assertEquals(6804490L, repo.likeCount(11))
            assertEquals(1970250L, repo.comments(11, limit = 1).total)
            assertTrue(repo.isLiked(11))
            assertFalse(repo.toggleLiked(11))
            assertFalse(repo.isLiked(11))
            assertFalse(paths.any { it.contains("likes/") || it.contains("songs/status") })
        } finally { server.shutdown() }
    }

    @Test fun missingBindingBlocksMutationsAndUnknownCountIsNeverZero() = runBlocking {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody(when (request.requestUrl!!.encodedPath) {
                "/cm/ncmbind" -> """{"bound":false}"""
                "/cm/ncm/song/red/count" -> """{"code":200,"data":{},"pop":999}"""
                else -> error("Unbound accounts cannot mutate or load recommendations")
            })
        }
        server.start()
        try {
            val s = RequestSession(server.url("/cm/").toString(), "personal")
            val repo = NeteaseSongActionsRepository(ApiClient({ s.server }, { s.token }, {})) { s }
            assertNull(repo.likeCount(11))
            assertEquals(ErrorKind.NeteaseBindingRequired, (appResult { repo.setLiked(11, true) } as AppResult.Failure).kind)
            assertEquals(ErrorKind.NeteaseBindingRequired, (appResult { repo.heartList(11) } as AppResult.Failure).kind)
        } finally { server.shutdown() }
    }

    @Test fun commentsPageAndHeartListDecodeActualNativeShapes() = runBlocking {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setBody(when (request.requestUrl!!.encodedPath) {
                "/cm/ncmbind" -> """{"bound":true,"profile":{"uid":7},"ncmLikedPlId":42}"""
                "/cm/ncm/user/playlist" -> """{"code":200,"playlist":[{"id":1234,"specialType":5,"creator":{"userId":8}},{"id":9918106457,"specialType":5,"creator":{"userId":7}}]}"""
                "/cm/ncm/comment/music" -> {
                    assertEquals("20", request.requestUrl!!.queryParameter("offset"))
                    """{"code":200,"total":22,"more":false,"comments":[{"commentId":2,"content":"来自网易云的评论","likedCount":19,"user":{"nickname":"听友"}}]}"""
                }
                "/cm/ncm/playmode/intelligence/list" -> {
                    assertEquals("9918106457", request.requestUrl!!.queryParameter("pid")); assertEquals("11", request.requestUrl!!.queryParameter("sid"))
                    assertEquals("20", request.requestUrl!!.queryParameter("count"))
                    """{"code":200,"data":[{"songInfo":{"id":11,"name":"当前曲"}},{"songInfo":{"id":12,"name":"推荐曲","ar":[{"id":3,"name":"歌手"}],"al":{"name":"专辑","picUrl":"http://example.com/cover"},"dt":30000}},{"songInfo":{"id":12,"name":"重复推荐"}}]}"""
                }
                else -> error("Unexpected endpoint")
            })
        }
        server.start()
        try {
            val s = RequestSession(server.url("/cm/").toString(), "personal")
            val repo = NeteaseSongActionsRepository(ApiClient({ s.server }, { s.token }, {})) { s }
            val comments = repo.comments(11, offset = 20)
            assertEquals("来自网易云的评论", comments.comments.single().content)
            assertEquals("听友", comments.comments.single().user.nickname)
            val heart = repo.heartList(11)
            assertEquals(9918106457L, heart.playlistId)
            val song = heart.songs.single()
            assertEquals(12L, song.id); assertEquals("歌手", song.artists); assertEquals("专辑", song.album)
            assertEquals("https://example.com/cover", song.cover); assertEquals(30000L, song.durationMs)
            assertEquals(ErrorKind.NeteaseBindingRequired, (appResult { repo.heartList(11, playlistId = 999) } as AppResult.Failure).kind)
        } finally { server.shutdown() }
    }

    @Test fun accountChangeRejectsAnOldCountResponse() = runBlocking {
        val server = MockWebServer()
        val token = java.util.concurrent.atomic.AtomicReference("first")
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                token.set("second")
                return MockResponse().setBody("""{"code":200,"data":{"count":123}}""")
            }
        }
        server.start()
        try {
            val base = server.url("/cm/").toString()
            val repo = NeteaseSongActionsRepository(ApiClient({ base }, { token.get() }, {})) { RequestSession(base, token.get()) }
            assertEquals(ErrorKind.Unauthorized, (appResult { repo.likeCount(11) } as AppResult.Failure).kind)
        } finally { server.shutdown() }
    }

    @Test fun missingUpstreamLikedPlaylistDoesNotUseImportedLocalId(): Unit = runBlocking {
        MockWebServer().use { server ->
            val s = RequestSession(server.url("/cm/").toString(), "personal")
            val repo = NeteaseSongActionsRepository(ApiClient({ s.server }, { s.token }, {})) { s }
            server.enqueue(MockResponse().setBody("""{"bound":true,"profile":{"uid":7},"ncmLikedPlId":273}"""))
            server.enqueue(MockResponse().setBody("""{"code":200,"playlist":[{"id":991,"specialType":0,"creator":{"userId":7}}]}"""))
            val result = appResult { repo.heartList(11) } as AppResult.Failure
            assertEquals(ErrorKind.NeteaseLikedPlaylistUnavailable, result.kind)
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun heartErrorsDistinguishMissingPlaylistAndNoRecommendations(): Unit = runBlocking {
        MockWebServer().use { server ->
            val s = RequestSession(server.url("/cm/").toString(), "personal")
            val repo = NeteaseSongActionsRepository(ApiClient({ s.server }, { s.token }, {})) { s }
            server.enqueue(MockResponse().setBody("""{"bound":true,"profile":{"uid":7},"ncmLikedPlId":273}"""))
            server.enqueue(MockResponse().setBody("""{"code":200,"playlist":[{"id":991,"specialType":5,"creator":{"userId":7}}]}"""))
            server.enqueue(MockResponse().setBody("""{"code":400,"message":"歌单不存在","data":null}"""))
            assertEquals(ErrorKind.NeteaseLikedPlaylistUnavailable, (appResult { repo.heartList(11) } as AppResult.Failure).kind)
            server.enqueue(MockResponse().setBody("""{"bound":true,"profile":{"uid":7},"ncmLikedPlId":273}"""))
            server.enqueue(MockResponse().setBody("""{"code":200,"data":[]}"""))
            assertEquals(ErrorKind.NeteaseHeartNoRecommendations, (appResult { repo.heartList(11) } as AppResult.Failure).kind)
            assertEquals(5, server.requestCount) // The upstream playlist ID is reused within this session.
        }
    }

    @Test fun badgeFormattingAndSourceMatchingNeverInventNeteaseIdentity() {
        assertEquals("9999", formatNeteaseCount(9999)); assertEquals("1w+", formatNeteaseCount(10000))
        assertEquals("680w+", formatNeteaseCount(6804490)); assertEquals("1亿+", formatNeteaseCount(100000000))
        assertNull(NeteaseSongActionsRepository.songId(Song(1, "QQ", musicSource = MusicSource.QQ_MUSIC)))
        assertNull(NeteaseSongActionsRepository.songId(Song(1, "MV", video = true)))
        assertEquals(2L, NeteaseSongActionsRepository.songId(Song(1, "NCM", externalIds = SongExternalIds(neteaseId = "2"))))
    }
}

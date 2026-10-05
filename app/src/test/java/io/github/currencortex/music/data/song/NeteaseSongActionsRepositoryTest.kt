package io.github.currencortex.music.data.song

import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class NeteaseSongActionsRepositoryTest {
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
                    "/cm/ncm/likelist" -> """{"code":200,"ids":${if (liked) "[11]" else "[]"}}"""
                    "/cm/ncm/like" -> { liked = request.requestUrl!!.queryParameter("like") == "true"; """{"code":200}""" }
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
                "/cm/ncm/comment/music" -> {
                    assertEquals("20", request.requestUrl!!.queryParameter("offset"))
                    """{"code":200,"total":22,"more":false,"comments":[{"commentId":2,"content":"来自网易云的评论","likedCount":19,"user":{"nickname":"听友"}}]}"""
                }
                "/cm/ncm/playmode/intelligence/list" -> {
                    assertEquals("42", request.requestUrl!!.queryParameter("pid")); assertEquals("11", request.requestUrl!!.queryParameter("sid"))
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
            assertEquals(42L, heart.playlistId)
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

    @Test fun badgeFormattingAndSourceMatchingNeverInventNeteaseIdentity() {
        assertEquals("9999", formatNeteaseCount(9999)); assertEquals("1w+", formatNeteaseCount(10000))
        assertEquals("680w+", formatNeteaseCount(6804490)); assertEquals("1亿+", formatNeteaseCount(100000000))
        assertNull(NeteaseSongActionsRepository.songId(Song(1, "QQ", musicSource = MusicSource.QQ_MUSIC)))
        assertNull(NeteaseSongActionsRepository.songId(Song(1, "MV", video = true)))
        assertEquals(2L, NeteaseSongActionsRepository.songId(Song(1, "NCM", externalIds = SongExternalIds(neteaseId = "2"))))
    }
}

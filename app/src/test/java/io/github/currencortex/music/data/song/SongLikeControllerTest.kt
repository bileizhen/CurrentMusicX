package io.github.currencortex.music.data.song

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.library.LibraryRepository
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class SongLikeControllerTest {
    private suspend fun ready(controller: SongLikeController) = withTimeout(5000) {
        while (controller.state.value.currentMusic.busy || controller.state.value.netease.busy) delay(5)
    }
    @Test fun destinationsStayIndependentAndFailuresPreserveMembership(): Unit = runBlocking {
        MockWebServer().use { server ->
            val cm = AtomicBoolean(false); val ncm = AtomicBoolean(false); val failCm = AtomicBoolean(false)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    return MockResponse().setBody(when (path) {
                        "/cm/songs/status" -> """{"liked":${if (cm.get()) "[11]" else "[]"}}"""
                        "/cm/ncmbind" -> """{"bound":true,"profile":{"uid":7}}"""
                        "/cm/ncmbind/likelist" -> """{"ids":${if (ncm.get()) "[11]" else "[]"}}"""
                        "/cm/likes/11" -> {
                            if (failCm.get()) return MockResponse().setResponseCode(500)
                            cm.set(!cm.get()); """{"on":${cm.get()}}"""
                        }
                        "/cm/ncmbind/like/11" -> {
                            assertEquals("POST", request.method)
                            val body = ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject
                            assertEquals("Track", body["name"]!!.jsonPrimitive.content)
                            ncm.set(body["like"]!!.jsonPrimitive.boolean); """{"code":200}"""
                        }
                        else -> return MockResponse().setResponseCode(404)
                    })
                }
            }
            val expected = RequestSession(server.url("/cm/").toString(), "personal")
            val api = ApiClient({expected.server},{expected.token},{})
            val library = LibraryRepository(api,{7},{expected})
            val ncmRepo = NeteaseSongActionsRepository(api){expected}
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val controller = SongLikeController(library,ncmRepo,scope){expected}
            try {
                controller.open(Song(11,"Track")); ready(controller)
                assertEquals(false, controller.state.value.currentMusic.liked)
                assertEquals(false, controller.state.value.netease.liked)
                controller.toggle(LikeDestination.CURRENT_MUSIC); ready(controller)
                assertTrue(cm.get()); assertFalse(ncm.get())
                assertEquals(1L, library.revision.value)
                controller.toggle(LikeDestination.NETEASE); ready(controller)
                assertTrue(cm.get()); assertTrue(ncm.get()); assertEquals(1L,ncmRepo.revision.value)
                assertEquals("Imported NetEase playlists must also refresh",2L,library.revision.value)
                failCm.set(true); controller.toggle(LikeDestination.CURRENT_MUSIC); ready(controller)
                assertTrue(cm.get()); assertEquals(true,controller.state.value.currentMusic.liked)
                assertNotNull(controller.state.value.currentMusic.error); assertTrue(ncm.get())
                failCm.set(false); controller.toggle(LikeDestination.CURRENT_MUSIC); ready(controller)
                assertFalse(cm.get()); assertTrue(ncm.get())
                controller.toggle(LikeDestination.NETEASE); ready(controller)
                assertFalse(ncm.get()); assertFalse(cm.get())
                controller.dismiss(); assertNull(controller.state.value.song)
            } finally { scope.cancel() }
        }
    }
    @Test fun unavailableNeteaseBindingDoesNotBlockCurrentMusic(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().setBody(when(request.requestUrl!!.encodedPath) {
                    "/cm/ncmbind" -> """{"bound":false}"""
                    "/cm/songs/status" -> """{"liked":[]}"""
                    "/cm/likes/11" -> """{"on":true}"""
                    else -> error("Unbound accounts must never send upstream like mutations")
                })
            }
            val expected=RequestSession(server.url("/cm/").toString(),"personal")
            val api=ApiClient({expected.server},{expected.token},{})
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
            val controller=SongLikeController(LibraryRepository(api,{7},{expected}),NeteaseSongActionsRepository(api){expected},scope){expected}
            try {
                controller.open(Song(11,"Track")); ready(controller)
                assertNull(controller.state.value.netease.liked); assertNotNull(controller.state.value.netease.error)
                controller.toggle(LikeDestination.CURRENT_MUSIC); ready(controller)
                assertEquals(true,controller.state.value.currentMusic.liked)
                controller.toggle(LikeDestination.NETEASE); ready(controller)
                assertNull(controller.state.value.netease.liked)
            } finally { scope.cancel() }
        }
    }
}

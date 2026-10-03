package io.github.currencortex.music.data.profile

import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class ProfileRepositoryTest {
    @Test fun editingWithoutKnownVisibilityDoesNotChangePrivacyAndPasswordUsesDedicatedRoute() = runBlocking {
        MockWebServer().use { s ->
            val base = s.url("/cm/").toString()
            val repo = ProfileRepository(ApiClient({ base }, { "credential" }, {})) { RequestSession(base, "credential") }
            s.enqueue(MockResponse().setBody("{}")); s.enqueue(MockResponse().setBody("""{"id":7,"nickname":"New"}"""))
            repo.update("New", "Bio", null)
            assertFalse(ApiJson.parseToJsonElement(s.takeRequest().body.readUtf8()).jsonObject.containsKey("publicSquare")); s.takeRequest()
            s.enqueue(MockResponse().setBody("{}")); repo.password("old-value", "new-value")
            val password = s.takeRequest(); assertEquals("/cm/profile/password", password.path); assertEquals("PUT", password.method)
            assertEquals(setOf("oldPassword", "newPassword"), ApiJson.parseToJsonElement(password.body.readUtf8()).jsonObject.keys)
        }
    }
    @Test fun publicProfileAndSquareKeepServerStatsAndSendNoCredentials() = runBlocking {
        MockWebServer().use { s ->
            val base = s.url("/cm/").toString()
            val repo = ProfileRepository(ApiClient({ base }, { "credential" }, {})) { RequestSession(base, "credential") }
            s.enqueue(MockResponse().setBody("""{"user":{"id":7,"nickname":"User","avatarDecoration":"frame"},"stat":{"likes":3,"favs":2,"playDays":8,"listenMs":90000},"recent":[{"ncm_id":1,"name":"Song"}],"playlists":[{"id":2,"name":"List"}]}"""))
            val profile = repo.profile(7); assertEquals(90000L, profile.stat.listenMs); assertEquals("frame", profile.user.decoration)
            assertNull(s.takeRequest().getHeader("Authorization"))
            s.enqueue(MockResponse().setBody("""{"total":4,"users":[{"id":7,"avatar_decoration":"frame","days":8}],"stats":{"users":4,"listening":1}}"""))
            val square = repo.square("query", "listen", 30, true)
            assertEquals("frame", square.users.first().profileUser().decoration)
            val request = s.takeRequest(); assertNull(request.getHeader("Authorization")); assertEquals("1", request.requestUrl!!.queryParameter("listening")); assertEquals("30", request.requestUrl!!.queryParameter("offset"))
        }
    }
    @Test fun profileAndAvatarWritesRequireSameSessionForFollowupReads() = runBlocking {
        MockWebServer().use { s ->
            val base = s.url("/cm/").toString()
            val repo = ProfileRepository(ApiClient({ base }, { "credential" }, {})) { RequestSession(base, "credential") }
            s.enqueue(MockResponse().setBody("{}")); s.enqueue(MockResponse().setBody("""{"id":7,"nickname":"New","publicSquare":true}"""))
            assertEquals("New", repo.update(" New ", "Bio", true).nickname)
            val edit = s.takeRequest(); assertEquals("PUT", edit.method)
            assertTrue(ApiJson.parseToJsonElement(edit.body.readUtf8()).jsonObject["publicSquare"]!!.jsonPrimitive.boolean); s.takeRequest()
            s.enqueue(MockResponse().setBody("{}")); s.enqueue(MockResponse().setBody("""{"id":7,"avatar":"7.jpg"}"""))
            repo.avatar(byteArrayOf(1, 2, 3)); val upload = s.takeRequest()
            assertEquals("AQID", ApiJson.parseToJsonElement(upload.body.readUtf8()).jsonObject["data"]!!.jsonPrimitive.content)
            assertEquals("Bearer credential", upload.getHeader("Authorization"))
        }
    }
    @Test fun changedSessionAfterWriteCannotReadNewAccountsPrivateProfile() = runBlocking {
        MockWebServer().use { s ->
            val base = s.url("/cm/").toString(); var token = "old"
            val repo = ProfileRepository(ApiClient({ base }, { token }, {})) { RequestSession(base, token) }
            s.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest): MockResponse {
                token = "new"; return MockResponse().setBody("{}")
            } }
            assertTrue(appResult { repo.update("Name", "Bio", false) } is AppResult.Failure)
            assertEquals("Bearer old", s.takeRequest().getHeader("Authorization"))
            assertNull(s.takeRequest(100, TimeUnit.MILLISECONDS))
        }
    }
    @Test fun decorationUnlockRulesRemainServerDefinedAndAssetPathsRejectTraversal() = runBlocking {
        MockWebServer().use { s ->
            val base = s.url("/cm/").toString()
            val repo = ProfileRepository(ApiClient({ base }, { "credential" }, {})) { RequestSession(base, "credential") }
            s.enqueue(MockResponse().setBody("""{"unlocked":false,"listenMs":10,"minListenMs":999,"decorations":[{"id":"frame","name":"Frame"}],"scales":{"frame":1.5}}"""))
            val catalog = repo.decorations(); assertFalse(catalog.unlocked); assertEquals(999L, catalog.minListenMs); assertEquals(1.5, repo.scales.value["frame"]!!, 0.01)
            assertNull(repo.avatarUrl(base, "../token")); assertNull(repo.decorationUrl(base, "../../token"))
            assertTrue(repo.avatarUrl(base, "7.jpg")!!.endsWith("avatar/7.jpg"))
            val chinese = repo.decorationUrl(base, "key-霸王色")!!.toHttpUrl()
            assertEquals("key-霸王色.gif", chinese.pathSegments.last())
            assertEquals("decor", chinese.pathSegments[chinese.pathSegments.lastIndex - 1])
            val reserved = repo.decorationUrl(base, "挂件?#%")!!.toHttpUrl()
            assertEquals("挂件?#%.gif", reserved.pathSegments.last()); assertNull(reserved.query); assertNull(reserved.fragment)
        }
    }
}

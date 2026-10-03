package io.github.currencortex.music.core.network

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ApiClientTest {
    @Serializable data class Value(val ok: Boolean)
    @Test fun serverFailuresRetainHttpStatusWithoutExposingResponseBodyOrExpiringSession() = runBlocking {
        MockWebServer().use { server ->
            var expired = false
            val api = ApiClient({ server.url("/cm/").toString() }, { "test-secret" }, { expired = true })
            server.enqueue(MockResponse().setResponseCode(502).setBody("""{"error":"upstream failure","captcha":"private-code","token":"private-token"}"""))
            val failure = appResult { api.request("POST", "ncmbind/phone/login", authenticated = true) } as AppResult.Failure
            assertEquals(ErrorKind.Server, failure.kind); assertEquals(502, failure.status)
            assertFalse(failure.toString().contains("private-")); assertFalse(expired)
        }
    }
    @Test fun verbsBearerEncodingAndServerPrefix() = runBlocking {
        MockWebServer().use { server ->
            val api = ApiClient({ server.url("/cm/").toString() }, { "test-secret" }, {})
            for (method in listOf("GET", "POST", "PUT", "DELETE")) {
                server.enqueue(MockResponse().setBody("""{"ok":true}"""))
                api.request(method, "songs", mapOf("keywords" to "中文 &/?"), buildJsonObject { put("value", "safe") }, true)
                val request = server.takeRequest()
                assertEquals(method, request.method)
                assertEquals("Bearer test-secret", request.getHeader("Authorization"))
                assertEquals("/cm/songs", request.requestUrl!!.encodedPath)
                assertEquals("中文 &/?", request.requestUrl!!.queryParameter("keywords"))
            }
        }
    }
    @Test fun unauthorizedCentralizesInvalidationButBindingDoesNot() = runBlocking {
        MockWebServer().use { server ->
            var expired = 0
            val api = ApiClient({ server.url("/").toString() }, { "test-secret" }, { expired++ })
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"登录已过期"}"""))
            assertEquals(ErrorKind.Unauthorized, (appResult { api.get<Value>("auth/me", authenticated = true) } as AppResult.Failure).kind)
            assertEquals(1, expired)
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"尚未绑定网易云"}"""))
            assertEquals(ErrorKind.Forbidden, (appResult { api.get<Value>("ncm/test", authenticated = true) } as AppResult.Failure).kind)
            assertEquals(1, expired)
            server.enqueue(MockResponse().setBody("invalid"))
            assertEquals(ErrorKind.Parse, (appResult { api.get<Value>("parse") } as AppResult.Failure).kind)
        }
    }
    @Test fun publicRequestsNeverSendToken() = runBlocking {
        MockWebServer().use { server ->
            val api = ApiClient({ server.url("/").toString() }, { "secret" }, {})
            server.enqueue(MockResponse().setBody("""{"ok":true}"""))
            assertTrue(api.get<Value>("public").ok)
            assertNull(server.takeRequest().getHeader("Authorization"))
        }
    }
    @Test fun callerCancellationStopsPendingNetworkCall() = runBlocking {
        MockWebServer().use { server ->
            val api = ApiClient({ server.url("/").toString() }, { null }, {})
            server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
            val job = launch(start = CoroutineStart.UNDISPATCHED) { api.get<Value>("slow") }
            assertNotNull(server.takeRequest(3, java.util.concurrent.TimeUnit.SECONDS))
            withTimeout(3000) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
        }
    }
}

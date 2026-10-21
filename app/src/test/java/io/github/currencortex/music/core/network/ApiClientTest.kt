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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ApiClientTest {
    @Test fun registrationFailuresPreserveOnlyTheUpstreamErrorField() = runBlocking {
        MockWebServer().use { server ->
            val api = ApiClient({ server.url("/cm/").toString() }, { "stored-session" }, {})
            val cases = listOf(
                Triple("auth/register", 400, "用户名需 2~24 位字母/数字/下划线/中文"),
                Triple("auth/register", 409, "用户名已被占用"),
                Triple("auth/email/code", 429, "请稍后再获取验证码"),
                Triple("auth/phone/code", 400, "手机号格式不正确"))
            for ((path, status, message) in cases) {
                server.enqueue(MockResponse().setResponseCode(status)
                    .setBody("""{"error":"$message","token":"private-token","code":"private-code"}"""))
                val failure = appResult { api.request("POST", path) } as AppResult.Failure
                assertEquals(message, failure.message)
                assertEquals(status, failure.status)
                assertFalse(failure.toString().contains("private-"))
                assertNull(server.takeRequest().getHeader("Authorization"))
            }
        }
    }

    @Test fun malformedRegistrationErrorsAndUnrelatedRoutesKeepGenericMessages() = runBlocking {
        MockWebServer().use { server ->
            val api = ApiClient({ server.url("/cm/").toString() }, { null }, {})
            for (body in listOf("<html>proxy error</html>", """{"error":{"debug":"details"}}""", """{"error":" "}""", """{"error":123}""")) {
                server.enqueue(MockResponse().setResponseCode(400).setBody(body))
                val failure = appResult { api.request("POST", "auth/register") } as AppResult.Failure
                assertEquals(ErrorKind.Unknown.message, failure.message)
            }
            server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"internal traceback"}"""))
            assertEquals(ErrorKind.Server.message, (appResult { api.request("POST", "auth/register") } as AppResult.Failure).message)
            server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"internal details"}"""))
            assertEquals(ErrorKind.Unknown.message, (appResult { api.request("POST", "other") } as AppResult.Failure).message)
        }
    }

    @Serializable(with = SlowValueSerializer::class) data class SlowValue(val uiResponded: Boolean)
    object SlowValueSerializer : KSerializer<SlowValue> {
        val decoding = AtomicBoolean(false)
        var heartbeat = CountDownLatch(1)
        override val descriptor = PrimitiveSerialDescriptor("SlowValue", PrimitiveKind.STRING)
        override fun serialize(encoder: Encoder, value: SlowValue) = encoder.encodeString("ok")
        override fun deserialize(decoder: Decoder): SlowValue {
            decoder.decodeString()
            decoding.set(true)
            return try { SlowValue(heartbeat.await(2, TimeUnit.SECONDS)) } finally { decoding.set(false) }
        }
    }
    @Test fun uiCanProcessEventsWhileResponseIsBeingDeserialized() {
        Executors.newSingleThreadExecutor { Thread(it, "fixture-ui") }.asCoroutineDispatcher().use { ui -> runBlocking(ui) {
            MockWebServer().use { server ->
                SlowValueSerializer.heartbeat = CountDownLatch(1)
                server.enqueue(MockResponse().setBody("\"ok\""))
                val api = ApiClient({ server.url("/cm/").toString() }, { null }, {})
                val tick = launch { while (isActive) {
                    if (SlowValueSerializer.decoding.get()) SlowValueSerializer.heartbeat.countDown()
                    delay(1)
                } }
                try { assertTrue("UI event loop must stay responsive during DTO decoding", api.get<SlowValue>("large").uiResponded) }
                finally { tick.cancelAndJoin() }
            }
        } }
    }
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
            val failure = appResult { api.get<Value>("auth/me", authenticated = true) } as AppResult.Failure
            assertEquals(ErrorKind.Unauthorized, failure.kind)
            assertEquals("CurrentMusic 登录已过期，请重新登录", failure.message)
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

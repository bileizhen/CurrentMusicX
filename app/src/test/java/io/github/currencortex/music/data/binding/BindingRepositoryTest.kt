package io.github.currencortex.music.data.binding

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.feature.binding.qrPixels
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class BindingRepositoryTest {
    @Test fun phoneBindingAndSyncUseDedicatedAuthenticatedRoutes() = runBlocking {
        MockWebServer().use { s ->
            val base = s.url("/cm/").toString(); var invalidations = 0
            val repo = BindingRepository(ApiClient({ base }, { "credential" }, {}), { RequestSession(base, "credential") }) { invalidations++ }
            s.enqueue(MockResponse().setBody("{}")); repo.sendCode("123456789", "86")
            val sms = s.takeRequest(); assertEquals("/cm/ncmbind/phone/code", sms.path); assertEquals("Bearer credential", sms.getHeader("Authorization"))
            s.enqueue(MockResponse().setBody("{}")); repo.bindPhone("123456789", "1234", "86")
            val login = s.takeRequest(); assertEquals("1234", ApiJson.parseToJsonElement(login.body.readUtf8()).jsonObject["captcha"]!!.jsonPrimitive.content)
            s.enqueue(MockResponse().setBody("""{"imported":2,"tracks":5,"pending":1,"failed":1}"""))
            val result = repo.sync(); assertEquals(1, result.pending); assertTrue(result.message().contains("待续传")); assertEquals(1, invalidations)
        }
    }
    @Test fun qrKeysAndChecksStayInSessionAndQueryValuesAreEncoded() = runBlocking {
        MockWebServer().use { s ->
            val base = s.url("/cm/").toString(); var token = "old"
            val repo = BindingRepository(ApiClient({ base }, { token }, {}), { RequestSession(base, token) }) {}
            val expected = RequestSession(base, token)
            s.enqueue(MockResponse().setBody("""{"key":"key & value"}""")); val key = repo.qrKey(expected); s.takeRequest()
            s.enqueue(MockResponse().setBody("""{"code":802}""")); assertEquals(802, repo.qrStatus(key, expected).code)
            assertEquals(key, s.takeRequest().requestUrl!!.queryParameter("key"))
            token = "new"; assertTrue(appResult { repo.qrStatus(key, expected) } is AppResult.Failure)
            assertNull(s.takeRequest(100, TimeUnit.MILLISECONDS))
        }
    }
    @Test fun nativeQrRoundTripsCanonicalLoginUrlWithQuietZone() {
        val base = "https://example.com/cm/"
        val repo = BindingRepository(ApiClient({ base }, { null }, {}), { RequestSession(base, null) }) {}
        val url = repo.qrUrl("key-value")
        assertEquals("https://music.163.com/login?codekey=key-value", url)
        val pixels = qrPixels(url)
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(512, 512, pixels)))
        assertEquals(url, QRCodeReader().decode(bitmap).text)
        assertEquals(-1, pixels.first())
    }
}

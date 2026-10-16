package io.github.currencortex.music.data.effects

import io.github.currencortex.music.core.netease.*
import io.github.currencortex.music.core.security.TokenStore
import io.github.currencortex.music.data.binding.BoundProfile
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class OriginalAiMasterRepositoryTest {
    @Test fun actualRecipeMetadataAndVerifiedSongMaterialRemainOriginalAndAccountMatched() = runBlocking {
        val server = MockWebServer().apply { start() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val dir = Files.createTempDirectory("master-material").toFile()
        try {
            val vault = object : TokenStore {
                private var raw: String? = null
                override fun read() = raw
                override fun write(token: String) { raw = token }
                override fun clear() { raw = null }
            }
            val sessions = NeteaseSessionStore(vault, scope).also { it.ready.await() }
            sessions.login(mapOf("MUSIC_U" to "test-session"), BoundProfile(42), sessions.snapshot())
            val url = server.url("/").toString()
            val transport = NeteaseTransport(sessions, NeteaseEndpoints(url, url, url))
            for ((name, hash) in listOf("base" to "f146c66e5baa6a6020b423a08cb5f664",
                "diy" to "d79417b1e6a0c01144b874dfd87359fa", "song" to "426c7a27a928a2140b8b18428b7fe01c")) {
                File(dir, "$hash.ncae").writeBytes(javaClass.classLoader!!
                    .getResource("original-ai-master/$name.ncae")!!.readBytes())
            }
            server.enqueue(MockResponse().setBody("""{"code":200,"data":{"userId":42,"redplus":{"vipCode":300,"expireTime":${System.currentTimeMillis() + 300_000}}}}"""))
            server.enqueue(MockResponse().setBody("""{"code":200,"data":[{"id":186016,"baseKey":"key_warm","baseName":"温润","shortText":"声音细腻又温暖","longText":"原版接口完整说明","effect":{"url":"https://d1.music.126.net/test.ncae","md5":"426c7a27a928a2140b8b18428b7fe01c","size":471}}]}"""))
            val repo = OriginalAiMasterRepository(dir, transport, OriginalMoodModelRepository(File(dir, "unused"), transport))
            val value = repo.prepare(186016)
            assertEquals("key_warm", value.baseKey)
            assertEquals("温润", value.baseName)
            assertEquals("声音细腻又温暖", value.description)
            assertEquals("原版接口完整说明", value.longText)
            assertNotNull(value.song)
            assertEquals(32, value.base.size)
            assertEquals(5, value.diy.size)
            assertEquals(2, server.requestCount)
            assertEquals("/eapi/music-vip-membership/client/vip/info", server.takeRequest(2, TimeUnit.SECONDS)!!.path)
            assertEquals("/eapi/vip-rights/svip/sound/effect/get", server.takeRequest(2, TimeUnit.SECONDS)!!.path)
        } finally { scope.cancel(); server.shutdown(); dir.deleteRecursively() }
    }
}

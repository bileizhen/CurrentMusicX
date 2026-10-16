package io.github.currencortex.music.data.effects

import io.github.currencortex.music.core.netease.*
import io.github.currencortex.music.core.security.TokenStore
import io.github.currencortex.music.data.binding.BoundProfile
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class OriginalMoodEntitlementTest {
    private fun member(id: Long = 42, code: Int = 300, until: Long = 2000) = buildJsonObject {
        put("userId", id); put("redplus", buildJsonObject { put("vipCode", code); put("expireTime", until) })
    }

    @Test fun exactTypeElevenGateAcceptsActualFutureRedPlus300() {
        assertEquals(OriginalMoodEntitlement(42, true, 2000), originalMoodMembershipEntitlement(42, member(), 1000))
    }

    @Test fun genericBlackVipOrWrongCodeCannotSubstituteForOriginalSvip() {
        assertFalse(originalMoodMembershipEntitlement(42, member(code = 100), 1000).canUse)
        assertFalse(originalMoodMembershipEntitlement(42, member(code = 230), 1000).canUse)
        assertFalse(originalMoodMembershipEntitlement(42, member(until = 1000), 1000).canUse)
        assertFalse(originalMoodMembershipEntitlement(42, member(id = 43), 1000).canUse)
    }

    @Test fun validOriginalSvipIsNotVetoedByFalseGenericBenefitQuery() = runBlocking {
        val server = MockWebServer().apply { start() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val vault = object : TokenStore {
                var raw: String? = null
                override fun read() = raw
                override fun write(token: String) { raw = token }
                override fun clear() { raw = null }
            }
            val sessions = NeteaseSessionStore(vault, scope).also { it.ready.await() }
            sessions.login(mapOf("MUSIC_U" to "test-session"), BoundProfile(42), sessions.snapshot())
            val url = server.url("/").toString()
            val repo = OriginalMoodModelRepository(File("unused-mood-test"), NeteaseTransport(sessions, NeteaseEndpoints(url, url, url)))
            val deadline = System.currentTimeMillis() + 300_000
            server.enqueue(MockResponse().setBody(buildJsonObject { put("code", 200); put("data", member(until = deadline)) }.toString()))
            // The original playback path does not need this unrelated endpoint's approval.
            server.enqueue(MockResponse().setBody("""{"code":200,"data":[{"resourceId":106,"resourceType":116,"canUse":false}]}"""))
            assertEquals(OriginalMoodEntitlement(42, true, deadline), repo.entitlement())
            assertEquals(1, server.requestCount)
            assertEquals("/eapi/music-vip-membership/client/vip/info", server.takeRequest().path)
        } finally { scope.cancel(); server.shutdown() }
    }
}

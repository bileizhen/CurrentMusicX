package io.github.currencortex.music.data.netease.listen

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class ListenTogetherInvitationResolverTest {
    @Test fun fullShareTextAndPlainOfficialShortLinksAreAccepted() {
        assertTrue(ListenTogetherInvitation.isSupported("我的耳机分你一半 https://163cn.tv/fixtureInvite (@网易云音乐)"))
        assertTrue(ListenTogetherInvitation.isSupported("https://163cn.tv/fixtureInvite"))
        assertFalse(ListenTogetherInvitation.isSupported("https://163cn.tv.evil.invalid/a"))
        assertFalse(ListenTogetherInvitation.isSupported("https://name:password@163cn.tv/a"))
    }
    @Test fun resolvesOnlyOfficialRedirectAndDoesNotSendAnyAccountHeader() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Set-Cookie", "tracking=discarded")
                .setHeader("Location", "/next"))
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location",
                "https://st.music.163.com/listen-together/share/?roomId=room-100&inviterId=200&songId=7"))
            val resolver = ListenTogetherInvitationResolver(OkHttpClient(), server.url("/"))
            assertEquals(ListenTogetherInvitation("room-100", 200), resolver.resolve("分享 ${server.url("/short")} (@网易云音乐)"))
            repeat(2) { val request = server.takeRequest(); assertNull(request.getHeader("Cookie")); assertNull(request.getHeader("Authorization")) }
            assertEquals(2, server.requestCount)
        }
    }
    @Test fun hostileRedirectIsNotFetchedAndFullInvitationsNeverNeedNetwork() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://example.invalid/private"))
            val resolver = ListenTogetherInvitationResolver(OkHttpClient(), server.url("/"))
            assertTrue(runCatching { resolver.resolve(server.url("/short").toString()) }.isFailure)
            assertEquals(1, server.requestCount)
            assertEquals(ListenTogetherInvitation("room-100", 200), resolver.resolve(
                "https://st.music.163.com/listen-together/share/?roomId=room-100&inviterId=200"))
            assertEquals(1, server.requestCount)
        }
    }
}

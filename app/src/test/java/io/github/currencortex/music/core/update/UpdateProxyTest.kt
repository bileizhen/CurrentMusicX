package io.github.currencortex.music.core.update

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class UpdateProxyTest {
    private val proxy = UpdateProxy("https://updates.bileizhen.top", "update-v1", "fixture-secret-never-used-in-production-0123456789",
        { 1791288000L }, { "1234567890abcdef1234567890abcdef" })
    @Test fun requestSignatureMatchesWorkerVector() {
        val headers = proxy.headers("https://updates.bileizhen.top/download/v1.1.1/CurrentMusic-Android-v1.1.1.apk".toHttpUrl())
        assertEquals("00eba3480896867cd816287b9051077258364fd7c15b626a047c05e28a29ef70", headers["X-CurrentMusic-Signature"])
        assertEquals("com.bileizhen.currentmusic", headers["X-CurrentMusic-App"])
    }
    @Test fun credentialsNeverFollowPublicSourcesOrRedirects() {
        for (url in listOf("https://github.com/bileizhen/CurrentMusicX/releases/download/v1.1.1/app.apk",
            "https://release-assets.githubusercontent.com/github-production-release-asset/1/file", "https://ghfile.geekertao.top/download/file.apk",
            "http://updates.bileizhen.top/download/file.apk", "https://updates.bileizhen.top.evil.example/download/file.apk")) {
            assertTrue(proxy.headers(url.toHttpUrl()).isEmpty())
        }
    }
    @Test fun proxyCannotTurnIntoArbitraryGitHubDownloadGateway() {
        assertEquals("https://updates.bileizhen.top/download/v1.1.1/CurrentMusic-Android-v1.1.1.apk",
            proxy.assetUrl("https://github.com/bileizhen/CurrentMusicX/releases/download/v1.1.1/CurrentMusic-Android-v1.1.1.apk".toHttpUrl()))
        assertTrue(runCatching { proxy.assetUrl("https://github.com/other/repo/releases/download/v1/app.apk".toHttpUrl()) }.isFailure)
        assertFalse(UpdateProxy("https://example.com", "key", "a".repeat(64)).configured)
        assertFalse(UpdateProxy("https://updates.bileizhen.top", "key", "").configured)
    }
}

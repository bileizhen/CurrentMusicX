package io.github.currencortex.music.core.update

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class UpdateProxyTest {
    @Test fun dedicatedMirrorUsesTheExactPublicLatestEndpointWithoutBuildSecrets() {
        val proxy = UpdateProxy()
        assertTrue(proxy.configured)
        assertEquals("https://music.20110208.xyz/cm/x/download/latest", proxy.assetUrl(
            "https://github.com/bileizhen/CurrentMusicX/releases/download/v1.1.10/CurrentMusic-Android-v1.1.10.apk".toHttpUrl()))
        assertEquals(UpdateSource.CURRENTMUSIC, UpdateSource.default)
    }
    @Test fun mirrorRejectsOtherRepositoriesAndUntrustedEndpointOverrides() {
        val proxy = UpdateProxy()
        for (url in listOf("https://github.com/other/repo/releases/download/v1/app.apk",
            "http://github.com/bileizhen/CurrentMusicX/releases/download/v1/app.apk",
            "https://user@github.com/bileizhen/CurrentMusicX/releases/download/v1/app.apk")) {
            assertTrue(runCatching { proxy.assetUrl(url.toHttpUrl()) }.isFailure)
        }
        for (url in listOf("https://example.com", UpdateProxy.ENDPOINT + "?token=example",
            UpdateProxy.ENDPOINT.replace("https:", "http:"))) assertFalse(UpdateProxy(url).configured)
    }
}
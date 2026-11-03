package io.github.currencortex.music.core.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateSourceTest {
    private val release = AppRelease("1.0.0", "notes",
        "https://github.com/bileizhen/CurrentMusicX/releases/tag/v1.0.0",
        "https://github.com/bileizhen/CurrentMusicX/releases/download/v1.0.0/CurrentMusic-Android-v1.0.0.apk",
        "CurrentMusic-Android-v1.0.0.apk", prerelease = false)

    @Test fun officialSourceUsesTheOriginalUrl() {
        assertEquals(release.apkUrl, UpdateSource.GITHUB.url(release))
    }

    @Test fun ciHidesOnlyTheStableMirrorAndOtherSourcesKeepExactBuildUrls() {
        val ci = release.copy(version = "1.0.0-ci.1", prerelease = true,
            apkUrl = release.apkUrl!!.replace("v1.0.0", "v1.0.0-ci.1"))
        assertEquals(UpdateSource.available.filter { it != UpdateSource.CURRENTMUSIC }, UpdateSource.forChannel(UpdateChannel.CI))
        UpdateSource.forChannel(UpdateChannel.CI).forEach {
            assertEquals(it.prefix + ci.apkUrl, it.url(ci))
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { UpdateSource.CURRENTMUSIC.url(ci) }
        assertEquals(UpdateSource.available, UpdateSource.forChannel(UpdateChannel.STABLE))
    }

    @Test fun mirrorsPrefixTheOfficialUrlOverHttps() {
        assertEquals("https://github.tbap.top/" + release.apkUrl, UpdateSource.TBAP.url(release))
        assertEquals("https://gh-proxy.com/" + release.apkUrl, UpdateSource.GH_PROXY.url(release))
        assertEquals("https://ghfile.geekertao.top/" + release.apkUrl, UpdateSource.GEEKERTAO.url(release))
        assertEquals("https://github.dpik.top/" + release.apkUrl, UpdateSource.DPIK.url(release))
    }

    @Test fun defaultUsesConfiguredPrivateMirrorAndEachFallbackIsTriedOnlyOnce() {
        assertEquals(if (UpdateProxy.current.configured) UpdateSource.CURRENTMUSIC else UpdateSource.GITHUB, UpdateSource.default)
        for (source in UpdateSource.available) {
            assertEquals(source, source.fallbacks().first())
            assertEquals(UpdateSource.available.toSet(), source.fallbacks().toSet())
            assertEquals(source.fallbacks().size, source.fallbacks().distinct().size)
        }
        assertEquals(UpdateSource.GITHUB, UpdateSource.GEEKERTAO.fallbacks().last())
        assertEquals(UpdateSource.GITHUB, UpdateSource.DPIK.fallbacks().last())
        assertEquals(UpdateSource.GITHUB, UpdateSource.CURRENTMUSIC.fallbacks()[1])
    }
}

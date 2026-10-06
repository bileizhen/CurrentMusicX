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

    @Test fun mirrorsPrefixTheOfficialUrlOverHttps() {
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

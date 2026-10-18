package io.github.currencortex.music.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class MyMusicNavigationTest {
    @Test fun guestCollectionsAskForTheAvailableAccountWhileBrowsingStaysOpen() {
        assertEquals("user/binding", myMusicDestination("lib/likes", false, false, false, true, true))
        assertEquals("user/login", myMusicDestination("lib/playlists", false, false, false, true, false))
        assertEquals("lib/recent", myMusicDestination("lib/recent", false, false, false, true, true))
        assertEquals("room/list", myMusicDestination("room/list", false, false, false, true, true))
    }
    @Test fun eitherHealthyAccountCanOpenPersonalCollections() {
        assertEquals("lib/likes", myMusicDestination("lib/likes", false, true, false, true, true))
        assertEquals("lib/playlists", myMusicDestination("lib/playlists", true, false, false, true, true))
    }
    @Test fun expiredPrimaryNeteaseAccountNeedsReauthenticationWithoutLosingTheProjectAccount() {
        assertEquals("user/binding", myMusicDestination("lib/likes", true, true, true, true, true))
        assertEquals("lib/likes", myMusicDestination("lib/likes", true, true, true, false, true))
    }
}

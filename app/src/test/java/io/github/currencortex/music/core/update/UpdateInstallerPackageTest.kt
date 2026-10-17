package io.github.currencortex.music.core.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateInstallerPackageTest {
    @Test fun isolatedBuildStillValidatesTheOfficialPackage() {
        val official = "com.bileizhen.currentmusic"
        assertEquals(official, updateReleasePackage(official))
        assertEquals(official, updateReleasePackage("$official.debug"))
        assertEquals(official, updateReleasePackage("$official.verification"))
        assertEquals("$official.verification.other", updateReleasePackage("$official.verification.other"))
    }
}

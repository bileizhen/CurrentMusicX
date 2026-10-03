package io.github.currencortex.music.core.update

import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class VersionTest {
    @Test fun releaseOrdering() {
        assertTrue(Version.parse("1.2.4")!! > Version.parse("1.2.3")!!)
        assertTrue(Version.parse("1.2.3")!! > Version.parse("1.2.3-rc.1")!!)
        assertTrue(Version.parse("1.2.3-rc.2")!! > Version.parse("1.2.3-beta.9")!!)
        assertTrue(Version.parse("v2.0.0-alpha.1")!! > Version.parse("1.9.9")!!)
    }

    @Test fun invalidAndOverflowingVersionsAreIgnored() {
        listOf("", "1.2", "01.2.3", "1.2.3-nightly", "999999999999999999999.2.3",
            "1.2.3-rc.999999999999999999999").forEach { assertNull(Version.parse(it)) }
    }
}

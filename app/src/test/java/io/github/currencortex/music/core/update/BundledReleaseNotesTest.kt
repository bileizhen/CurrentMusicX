package io.github.currencortex.music.core.update

import org.junit.Assert.*
import org.junit.Test

class BundledReleaseNotesTest {
    @Test fun onlyExactInstalledBuildCanUseBundledNotes() {
        val json = """{"version":"1.3.3-ci.119","notes":"本次构建说明"}"""
        assertEquals("本次构建说明", parseBundledReleaseNotes(json, "1.3.3-ci.119")?.notes)
        assertNull(parseBundledReleaseNotes(json, "1.3.3"))
        assertNull(parseBundledReleaseNotes(json, "1.3.3-ci.120"))
        assertNull(parseBundledReleaseNotes("invalid", "1.3.3-ci.119"))
        assertNull(parseBundledReleaseNotes("""{"version":"1.3.3","notes":""}""", "1.3.3"))
    }
}

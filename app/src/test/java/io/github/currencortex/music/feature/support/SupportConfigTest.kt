package io.github.currencortex.music.feature.support

import org.junit.Assert.*
import org.junit.Test

class SupportConfigTest {
    @Test fun unopenedConfigurationCannotLaunch() {
        assertNull(supportHttpsUrl(""))
    }
    @Test fun onlyCompleteHttpsUrlsWithoutCredentialsAreAccepted() {
        listOf("", " ", "http://example.com/a", "javascript:alert(1)", "https:///path", "https://",
            "https://user:secret@example.com", "https://example.com/\n", "https://example.com/a b",
            "intent://example.com", "file:///tmp/code", "https://example.com:99999").forEach {
            assertNull(it, supportHttpsUrl(it))
        }
        listOf("https://example.com/support", "https://example.com/a?ref=music#support").forEach {
            assertEquals(it, supportHttpsUrl(it))
        }
    }
    @Test fun rapidClicksAreIgnoredAndNextDeliberateClickWorks() {
        var time = 0L
        val gate = SupportClickGate { time }
        assertTrue(gate.acquire())
        repeat(5) { time += 150; assertFalse(gate.acquire()) }
        time = 1000
        assertTrue(gate.acquire())
        assertFalse(gate.acquire())
        time = 3000
        assertTrue(gate.acquire())
    }
}

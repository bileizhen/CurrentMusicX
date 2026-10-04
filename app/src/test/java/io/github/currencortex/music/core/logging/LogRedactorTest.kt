package io.github.currencortex.music.core.logging

import org.junit.Assert.*
import org.junit.Test

class LogRedactorTest {
    @Test fun secretsAreRedactedInHeadersJsonAndUrls() {
        val secret = "test-secret-XYZ"
        listOf("Authorization: Bearer $secret", "Cookie: session=$secret; other=$secret",
            "https://example.com/?token=$secret&password=$secret",
            "{\"access_token\":\"$secret\",\"password\":\"$secret\"}",
            "request failed with Bearer $secret", "x-api-key: $secret",
            "https://example.com/api/netease?key=$secret&id=1", "{\"apiKey\":\"$secret\"}").forEach {
            val safe = LogRedactor.redact(it)
            assertFalse(safe, safe.contains(secret))
            assertTrue(safe, safe.contains("[REDACTED]"))
        }
    }

    @Test fun multilineErrorsKeepStackTracesWithoutCredentials() {
        val raw = "2026-10-03 12:00:01.000 E/Network: failed token=hidden\n" +
            "java.io.IOException: offline\n  at sample.request(Network.kt:10)\n"
        val safe = LogRedactor.redact(raw)
        assertFalse(safe.contains("hidden"))
        assertTrue(safe.contains("token=[REDACTED]"))
        assertTrue(safe.endsWith("java.io.IOException: offline\n  at sample.request(Network.kt:10)\n"))
    }
}

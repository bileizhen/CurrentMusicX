package io.github.currencortex.music.core.network
import org.junit.Assert.*
import org.junit.Test
class ServerUrlTest {
    @Test fun normalizesPrefixAndRejectsCredentialsAndUnsupportedProtocols() {
        assertEquals("https://example.com/cm/", ServerUrl.normalize(" https://example.com/cm/// "))
        assertEquals("http://192.168.1.2:8080/", ServerUrl.normalize("http://192.168.1.2:8080"))
        for (input in listOf("file:///a", "ftp://a", "bad", "https://user:password@example.com", "https://example.com?a=b", "https://example.com/#x"))
            assertTrue(input, runCatching { ServerUrl.normalize(input) }.isFailure)
    }
}

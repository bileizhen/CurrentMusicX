package io.github.currencortex.music.core.security
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.KeyGenerator
class SecureTokenStoreTest {
    @Test fun authenticatedEncryptionUsesFreshNonceAndRejectsTampering() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val cipher = TokenCipher(key)
        val first = cipher.encrypt("test-token")
        assertEquals("test-token", cipher.decrypt(first))
        assertFalse(first.contentEquals(cipher.encrypt("test-token")))
        assertFalse(first.toString(Charsets.UTF_8).contains("test-token"))
        first[first.lastIndex] = (first.last().toInt() xor 1).toByte()
        assertTrue(runCatching { cipher.decrypt(first) }.isFailure)
    }
}

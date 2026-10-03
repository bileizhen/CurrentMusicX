package io.github.currencortex.music.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface TokenStore {
    fun read(): String?
    fun write(token: String)
    fun clear()
}
class TokenCipher(private val key: SecretKey) {
    fun encrypt(token: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
    }
    fun decrypt(bytes: ByteArray): String {
        require(bytes.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }
}
class SecureTokenStore(context: Context) : TokenStore {
    private val file = File(context.noBackupFilesDir, "session.aes")
    private val cipher by lazy {
        val alias = "CurrentMusic.Session"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
        TokenCipher(key)
    }
    @Synchronized override fun read(): String? {
        val bytes = try { android.util.AtomicFile(file).readFully() }
        catch (_: java.io.FileNotFoundException) { return null }
        return cipher.decrypt(bytes)
    }
    @Synchronized override fun write(token: String) {
        val atomic = android.util.AtomicFile(file)
        val out = atomic.startWrite()
        try { out.write(cipher.encrypt(token)); atomic.finishWrite(out) }
        catch (e: Exception) { atomic.failWrite(out); throw e }
    }
    @Synchronized override fun clear() { android.util.AtomicFile(file).delete() }
}

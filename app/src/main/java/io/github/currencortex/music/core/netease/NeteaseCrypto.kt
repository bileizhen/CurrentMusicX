package io.github.currencortex.music.core.netease

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Native JCE implementation of NetEase's web/desktop request encoding.
 * Protocol reference: NeteaseCloudMusicApiEnhanced/api-enhanced (MIT, Binaryify).
 * No credentials, request payloads or encrypted responses are logged.
 */
internal object NeteaseCrypto {
    private val eapiKey = "e82ckenh8dichen8".toByteArray()
    private val webKey = "0CoJUm6Qyw8W8jud".toByteArray()
    private val webIv = "0102030405060708".toByteArray()
    private const val separator = "-36cd479b6b5-"
    private const val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private val random = SecureRandom()
    private val publicKey by lazy {
        val encoded = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB"
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(encoded)))
    }
    private fun aes(bytes: ByteArray, key: ByteArray, cbc: Boolean, decrypt: Boolean = false): ByteArray =
        Cipher.getInstance(if (cbc) "AES/CBC/PKCS5Padding" else "AES/ECB/PKCS5Padding").run {
            val mode = if (decrypt) Cipher.DECRYPT_MODE else Cipher.ENCRYPT_MODE
            if (cbc) init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(webIv)) else init(mode, SecretKeySpec(key, "AES"))
            doFinal(bytes)
        }
    fun eapi(path: String, json: String): String {
        val digest = MessageDigest.getInstance("MD5").digest("nobody${path}use${json}md5forencrypt".toByteArray()).hex()
        return aes("$path$separator$json$separator$digest".toByteArray(), eapiKey, false).hex().uppercase()
    }
    fun weapi(json: String, secret: String = buildString { repeat(16) { append(alphabet[random.nextInt(alphabet.length)]) } }): Map<String, String> {
        require(secret.length == 16)
        val first = Base64.getEncoder().encode(aes(json.toByteArray(), webKey, true))
        val params = Base64.getEncoder().encodeToString(aes(first, secret.toByteArray(), true))
        val rsa = Cipher.getInstance("RSA/ECB/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, publicKey)
            doFinal(secret.reversed().toByteArray()).hex().padStart(256, '0')
        }
        return mapOf("params" to params, "encSecKey" to rsa)
    }
    fun decryptResponse(bytes: ByteArray) = aes(bytes, eapiKey, false, decrypt = true)
    internal fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}

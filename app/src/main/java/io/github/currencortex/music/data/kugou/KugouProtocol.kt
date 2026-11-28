package io.github.currencortex.music.data.kugou

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Wire-level constants and signing rules recovered from the KuGou Android client 20.9.2
 * (com.kugou.android, versionCode 20929). See docs/KUGOU_PROTOCOL_ANALYSIS.md for the evidence.
 */
object KugouProtocol {
    /** CommonConfigKeys.C11 default (0x3ed) in `Utils.a(Hashtable)`. */
    const val APP_ID = "1005"
    /** SystemUtils.A4(): the installed versionCode. */
    const val CLIENT_VERSION = "20929"
    /** `Utils.r(params, key)`: default when CommonConfigKeys.E11 is empty. */
    const val SIGN_KEY = "OIlwieks28dk2k092lksi2UIkp"
    /** Signature key used by the H5 / login web endpoints (`apicacher.j`, `StartupPrefetchRequest.t`). */
    const val WEB_SIGN_KEY = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt"
    /** Salt of the `key` parameter accepted by gateway `/v5/url`. */
    const val SONG_URL_SALT = "57ae12eb6890223e355ccfcb74edf70d"
    /** Salt of the `key` parameter accepted by `trackermv.kugou.com/interface/index`. */
    const val MV_KEY_SALT = "kugoumvcloud"
    /** Resource code identifying song comments in `mcomment` / `commentmain`. */
    const val COMMENT_SONG_CODE = "fc4be23b4e972707f36b8a828a93ba8a"
    /** `KG-TID` header sent to `cloudlist.service`. */
    const val CLOUDLIST_TID = "27"
    /** `KG-TID` header sent to `vipos/v1/vip_info/detail/query`. */
    const val VIP_INFO_TID = "524"
    /** Built-in listid for KuGou's "我喜欢" cloud playlist. */
    const val LIKED_LIST_ID = 2
    /** Default `x-router` backend for SMS / password login requests over HTTPS gateway. */
    const val LOGIN_ROUTER = "login.user.kugou.com"
    /** Default `x-router` backend for `v2/get_res_privilege/lite` quality & privilege metadata. */
    const val MEDIASTORE_ROUTER = "media.store.kugou.com"
    /** `businessid` passed by `ShortMessageLoginFragment$x` to `/v8/send_mobile_code`. */
    const val SMS_LOGIN_BUSINESS_ID = 5
    /** Default 1024-bit X.509 RSA public key (`CommonConfigKeys.H11` fallback in `UserLogin$h.w`). */
    const val LOGIN_RSA_PUBLIC_KEY =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDIAG7QOELSYoIJvTFJhMpe1s/gbjDJX51HBNnEl5HXqTW6lQ7LC8jr9fWZTwusknp+sVGzwd40MwP6U5yDE27M/X1+UR4tvOGOqp94TJtQ1EPnWGWXngpeIW5GxoQGao1rmYWAu6oi1z9XkChrsUdC6DJE5E221wf/4WLFxwAtRQIDAQAB"
    const val USER_AGENT = "Android15-AndroidPhone-20929-18-0-NetMusic-wifi"

    const val GATEWAY = "https://gateway.kugou.com/"
    const val COMPLEX_SEARCH = "https://complexsearch.kugou.com/"
    const val MOBILE = "https://mobiles.kugou.com/"
    const val SEARCH_TIP = "http://searchtip.kugou.com/"
    const val LYRICS = "https://lyrics.kugou.com/"
    const val LOGIN = "https://login-user.kugou.com/"

    /** Native Android QR login (`KGQRCodeApi`) uses `appid=1005` + `plat=1` + `SIGN_KEY` so the issued token works on all Android APIs. */
    const val QR_APP_ID = APP_ID

    private val CHINA_MOBILE_REGEX = Regex("^((13|14|15|17|18|19)\\d{9})$")
    private val secureRandom = SecureRandom()

    fun md5(value: String): String = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    /**
     * `Utils.s` / `h5.f`: sort parameter names, concatenate `name=value`, append the optional body
     * (optionally truncated to [maxBodyBytes] for endpoints using `h5.f`, such as `personal_recommend` at 256 bytes),
     * then wrap the whole byte payload with [key] on both sides and MD5 it.
     */
    fun signature(
        params: Map<String, String>,
        body: String = "",
        key: String = SIGN_KEY,
        maxBodyBytes: Int = Int.MAX_VALUE,
    ): String {
        val digest = MessageDigest.getInstance("MD5")
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val sortedBytes = params.toSortedMap().entries.joinToString("") { "${it.key}=${it.value}" }.toByteArray(Charsets.UTF_8)
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        digest.update(keyBytes)
        digest.update(sortedBytes)
        if (bodyBytes.isNotEmpty() && maxBodyBytes > 0) {
            digest.update(bodyBytes, 0, minOf(bodyBytes.size, maxBodyBytes))
        }
        digest.update(keyBytes)
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /** `key` for `/v5/url`: md5(hash + salt + appid + mid + userid), hash lower-cased. */
    fun songUrlKey(hash: String, mid: String, userId: String, appId: String = APP_ID): String =
        md5(hash.lowercase() + SONG_URL_SALT + appId + mid + userId)

    /** `key` for `mcomment` / `commentmain`: md5(appid + signKey + clientver + clienttime). */
    fun commentKey(clientTimeSeconds: Long, appId: String = APP_ID, key: String = SIGN_KEY): String =
        md5(appId + key + CLIENT_VERSION + clientTimeSeconds)

    /** `key` for `login_by_pwd` / `login_by_verifycode` (`pq3.f.c`): md5(appid + signKey + clientver + clienttime_ms). */
    fun loginKey(clientTimeMs: Long, appId: String = APP_ID, key: String = SIGN_KEY): String =
        md5(appId + key + CLIENT_VERSION + clientTimeMs)

    /** `key` for `trackermv.kugou.com/interface/index`: md5(mvHash + "kugoumvcloud"). */
    fun mvKey(mvHash: String): String = md5(mvHash + MV_KEY_SALT)

    /** Matches `com.kugou.common.useraccount.d0.b`: mainland 11-digit mobile number pattern. */
    fun isChinaMobile(value: String): Boolean = CHINA_MOBILE_REGEX.matches(value)

    /** Matches `com.kugou.common.useraccount.d0.g`: 11-digit phone masked with 5 asterisks (`138*****123`). */
    fun maskMobile(mobile: String): String =
        if (mobile.length == 11) "${mobile.substring(0, 3)}*****${mobile.substring(8, 11)}" else mobile

    /** Matches `pq3.a.g`: 16 lowercase hex chars used as the per-request login AES key. */
    fun generateLoginAesKey(): String {
        val bytes = ByteArray(8)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /**
     * Matches `pq3.k0.c` + `h3.m`: zero-pad UTF-8 bytes to a 128-byte RSA block, encrypt with
     * `RSA/ECB/NOPADDING`, and encode as uppercase hex.
     */
    fun rsaEncryptLogin(plaintext: String, publicKeyBase64: String = LOGIN_RSA_PUBLIC_KEY): String {
        val src = plaintext.toByteArray(Charsets.UTF_8)
        require(src.size <= 128) { "RSA login payload exceeds 128-byte block" }
        val block = ByteArray(128)
        System.arraycopy(src, 0, block, 0, src.size)
        val pubKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)))
        val cipher = Cipher.getInstance("RSA/ECB/NOPADDING")
        cipher.init(Cipher.ENCRYPT_MODE, pubKey)
        return cipher.doFinal(block).joinToString("") { "%02X".format(it.toInt() and 0xff) }
    }

    /**
     * Matches `pq3.a.d` + `pq3.a.e`: derive AES-256 key (`md5(aesKey)[0..32)`) and IV (`md5(aesKey)[16..32)`)
     * as ASCII bytes, encrypt UTF-8 plaintext with `AES/CBC/PKCS5Padding`, and encode as lowercase hex.
     */
    fun aesEncryptLogin(plaintext: String, aesKey: String): String {
        val hash = md5(aesKey)
        val keySpec = SecretKeySpec(hash.substring(0, 32).toByteArray(Charsets.US_ASCII), "AES")
        val ivSpec = IvParameterSpec(hash.substring(16, 32).toByteArray(Charsets.US_ASCII))
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /**
     * Matches `pq3.a.a` + `pq3.a.b`: decrypt hex-encoded `secu_params` returned by KuGou login endpoints
     * using the same per-request `aesKey` sent in `pk`.
     */
    fun aesDecryptLogin(cipherHex: String, aesKey: String): String {
        val clean = cipherHex.trim()
        require(clean.length % 2 == 0 && clean.isNotEmpty()) { "Invalid hex ciphertext" }
        val bytes = ByteArray(clean.length / 2) { index ->
            clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
        val hash = md5(aesKey)
        val keySpec = SecretKeySpec(hash.substring(0, 32).toByteArray(Charsets.US_ASCII), "AES")
        val ivSpec = IvParameterSpec(hash.substring(16, 32).toByteArray(Charsets.US_ASCII))
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return String(cipher.doFinal(bytes), Charsets.UTF_8).trim()
    }

    /** Parameters every signed request carries (`Utils.a`), then the signature over all of them. */
    fun signed(
        params: Map<String, String>, mid: String, dfid: String = "-", clientTimeSeconds: Long,
        key: String = SIGN_KEY, appId: String = APP_ID, body: String = "",
        maxBodyBytes: Int = Int.MAX_VALUE,
    ): Map<String, String> {
        val all = linkedMapOf("appid" to appId, "clientver" to CLIENT_VERSION, "clienttime" to clientTimeSeconds.toString(),
            "mid" to mid, "uuid" to mid, "dfid" to dfid.ifBlank { "-" })
        all.putAll(params)
        all["signature"] = signature(all, body, key, maxBodyBytes)
        return all
    }

    /** Matches `com.kugou.common.useraccount.entity.QRCode.getFinalQrcodeString()`. */
    fun qrContent(qrKey: String) = "https://h5.kugou.com/apps/loginQRCode/html/index.html?qrcode=$qrKey"
}

package io.github.currencortex.music.core.update

import io.github.currencortex.music.BuildConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Product-scoped request signing. Credentials never accompany public fallback sources. */
class UpdateProxy(private val endpoint: String, private val keyId: String, private val secret: String,
    private val epochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
    private val nonce: () -> String = { ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { byte -> "%02x".format(byte) } }) {
    private val base = endpoint.toHttpUrlOrNull()?.takeIf {
        it.scheme == "https" && it.host == "updates.bileizhen.top" && it.port == 443 &&
            it.username.isEmpty() && it.password.isEmpty() && it.encodedPath == "/" && it.query == null && it.fragment == null
    }
    val configured: Boolean get() = base != null && keyId.isNotBlank() && secret.length >= 32
    fun assetUrl(official: HttpUrl): String {
        check(configured) { "专用镜像尚未配置" }
        val prefix = "/bileizhen/CurrentMusicX/releases/download/"
        require(official.scheme == "https" && official.host == "github.com" && official.encodedPath.startsWith(prefix) &&
            official.query == null && official.fragment == null) { "更新来源不匹配" }
        return base!!.newBuilder().encodedPath("/download/" + official.encodedPath.removePrefix(prefix)).build().toString()
    }
    fun headers(url: HttpUrl, method: String = "GET", range: String = ""): Map<String, String> {
        if (!configured || url.scheme != "https" || url.host != base!!.host || url.port != base.port || !url.encodedPath.startsWith("/download/") || url.query != null) return emptyMap()
        val timestamp = epochSeconds().toString()
        val nonce = nonce()
        val appId = "com.bileizhen.currentmusic"
        val canonical = listOf("v1", method, url.host, url.encodedPath, range, appId, keyId, timestamp, nonce).joinToString("\n")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val signature = mac.doFinal(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return mapOf("X-CurrentMusic-App" to appId, "X-CurrentMusic-Key-Id" to keyId,
            "X-CurrentMusic-Timestamp" to timestamp, "X-CurrentMusic-Nonce" to nonce, "X-CurrentMusic-Signature" to signature)
    }
    companion object {
        val current by lazy { UpdateProxy(BuildConfig.UPDATE_PROXY_URL, BuildConfig.UPDATE_PROXY_KEY_ID, BuildConfig.UPDATE_PROXY_SECRET) }
    }
}

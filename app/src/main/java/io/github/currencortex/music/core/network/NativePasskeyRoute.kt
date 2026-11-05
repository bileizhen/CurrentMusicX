package io.github.currencortex.music.core.network

import io.github.currencortex.music.core.config.ServerDefaults
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Passkey ceremonies must not follow automatic CDN selection. */
object NativePasskeyRoute {
    fun server(server: String): String {
        val normalized = ServerUrl.normalize(server)
        val pinned = if (CurrentMusicServer.builtIn(normalized)) ServerDefaults.URL else normalized
        require(pinned.toHttpUrl().isHttps) { "通行密钥需要 HTTPS 服务器" }
        return pinned
    }
    fun rpId(base: String) = server(base).toHttpUrl().host
}

package io.github.currencortex.music.core.update

import io.github.currencortex.music.core.network.CurrentMusicServer
import io.github.currencortex.music.core.network.ServerUrl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Public latest-APK endpoint; release size and digest remain authoritative. */
class UpdateProxy(endpoint: String = ENDPOINT) {
    private val base = endpoint.toHttpUrlOrNull()?.takeIf { it.toString() in endpoints }
    val configured: Boolean get() = base != null
    fun assetUrl(official: HttpUrl): String {
        check(configured) { "专用镜像尚未配置" }
        require(official.scheme == "https" && official.host == "github.com" && official.port == 443 &&
            official.username.isEmpty() && official.password.isEmpty() &&
            official.encodedPath.startsWith("/bileizhen/CurrentMusicX/releases/download/") &&
            official.query == null && official.fragment == null) { "更新来源不匹配" }
        return base.toString()
    }
    companion object {
        const val ENDPOINT = "https://music.20110208.xyz/cm/x/download/latest"
        private val endpoints = CurrentMusicServer.presets.map { requireNotNull(it.url) + "x/download/latest" }.toSet()
        val current = UpdateProxy()
        /** Only trusted public presets inherit the updater route; custom account hosts do not. */
        fun forServer(server: String): UpdateProxy {
            val normalized = runCatching { ServerUrl.normalize(server) }.getOrNull()
            return if (normalized != null && CurrentMusicServer.builtIn(normalized))
                UpdateProxy(normalized + "x/download/latest") else current
        }
    }
}
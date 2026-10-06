package io.github.currencortex.music.core.update

import io.github.currencortex.music.core.config.AppMetadata
import java.net.URI
import okhttp3.HttpUrl.Companion.toHttpUrl

enum class UpdateSource(val label: String, val prefix: String) {
    CURRENTMUSIC("CurrentMusic 专用镜像", ""),
    GITHUB("GitHub 原站", ""),
    GEEKERTAO("ghfile.geekertao.top", "https://ghfile.geekertao.top/"),
    DPIK("github.dpik.top", "https://github.dpik.top/");

    /** Try the requested source once, then the other mirrors, with the origin as the final fallback. */
    fun fallbacks(): List<UpdateSource> = when (this) {
        CURRENTMUSIC -> listOf(CURRENTMUSIC, GITHUB, GEEKERTAO, DPIK)
        GITHUB -> listOf(GITHUB) + available.filter { it != GITHUB }
        else -> listOf(this) + available.filter { it != this && it != GITHUB } + GITHUB
    }

    fun url(release: AppRelease): String {
        val official = requireNotNull(release.apkUrl) { "此发布没有 APK" }
        require(official.startsWith("${AppMetadata.RELEASES_PROJECT_URL}/releases/download/")) { "更新来源不匹配" }
        if (this == CURRENTMUSIC) return UpdateProxy.current.assetUrl(official.toHttpUrl())
        if (prefix.isNotEmpty()) require(prefix.startsWith("https://")) { "下载源必须使用 HTTPS" }
        val result = prefix + official
        val uri = URI(result)
        require(uri.scheme == "https" && uri.rawUserInfo == null) { "下载源必须使用 HTTPS" }
        return result
    }

    companion object {
        val available: List<UpdateSource> get() = entries.filter { it != CURRENTMUSIC || UpdateProxy.current.configured }
        val default: UpdateSource get() = if (UpdateProxy.current.configured) CURRENTMUSIC else GITHUB
    }
}

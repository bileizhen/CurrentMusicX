package io.github.currencortex.music.core.update

import io.github.currencortex.music.core.config.AppMetadata
import java.net.URI

enum class UpdateSource(val label: String, val prefix: String) {
    GITHUB("GitHub 原站", ""),
    GEEKERTAO("ghfile.geekertao.top", "https://ghfile.geekertao.top/"),
    DPIK("github.dpik.top", "https://github.dpik.top/");

    fun url(release: AppRelease): String {
        val official = requireNotNull(release.apkUrl) { "此发布没有 APK" }
        require(official.startsWith("${AppMetadata.RELEASES_PROJECT_URL}/releases/download/")) { "更新来源不匹配" }
        if (prefix.isNotEmpty()) require(prefix.startsWith("https://")) { "下载源必须使用 HTTPS" }
        val result = prefix + official
        val uri = URI(result)
        require(uri.scheme == "https" && uri.rawUserInfo == null) { "下载源必须使用 HTTPS" }
        return result
    }

    companion object {
        val available: List<UpdateSource> get() = entries
        val default: UpdateSource get() = GEEKERTAO
    }
}

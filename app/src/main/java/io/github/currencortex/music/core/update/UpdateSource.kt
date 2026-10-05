package io.github.currencortex.music.core.update

import io.github.currencortex.music.core.config.AppMetadata
import java.net.URI

enum class UpdateSource(val label: String) {
    GITHUB("GitHub 原站"), MIRROR("配置的镜像");
    fun url(release: AppRelease): String {
        val official = requireNotNull(release.apkUrl) { "此发布没有 APK" }
        require(official.startsWith("${AppMetadata.RELEASES_PROJECT_URL}/releases/download/")) { "更新来源不匹配" }
        val prefix = if (this == MIRROR) AppMetadata.UPDATE_MIRROR_PREFIX else ""
        if (this == MIRROR) require(prefix.isNotBlank()) { "未配置镜像" }
        val result = prefix + official
        val uri = URI(result)
        require(uri.scheme == "https" && uri.rawUserInfo == null) { "下载源必须使用 HTTPS" }
        return result
    }
    companion object {
        val available: List<UpdateSource> get() = if (AppMetadata.UPDATE_MIRROR_PREFIX.isBlank()) listOf(GITHUB) else entries
    }
}

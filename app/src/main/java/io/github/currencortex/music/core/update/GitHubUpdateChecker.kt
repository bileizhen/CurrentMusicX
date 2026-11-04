package io.github.currencortex.music.core.update

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

class GitHubUpdateChecker(
    private val owner: String,
    private val repository: String,
    private val installedVersion: String,
    private val userAgent: String,
    private val nativeAssetsOnly: Boolean = false,
) : ReleaseChecker {
    override fun check(channel: UpdateChannel): AppRelease? {
        val endpoint = if (channel == UpdateChannel.CI) {
            "https://api.github.com/repos/$owner/$repository/releases?per_page=100"
        } else {
            "https://api.github.com/repos/$owner/$repository/releases/latest"
        }
        return request(endpoint)?.let { parseResponse(it, channel) }
    }

    override fun installedRelease(): AppRelease? {
        if (Version.parse(installedVersion) == null) return null
        val tag = "v${installedVersion.removePrefix("v")}"
        return request("https://api.github.com/repos/$owner/$repository/releases/tags/$tag")
            ?.let(::parseInstalledResponse)
    }

    internal fun parseInstalledResponse(json: String): AppRelease? {
        val release = JSONObject(json)
        if (Version.parse(installedVersion) == null || release.optBoolean("draft") ||
            release.optString("tag_name") != "v${installedVersion.removePrefix("v")}") return null
        return parseRelease(release, requireNativeAsset = false)
    }

    private fun request(endpoint: String): String? {
        val connection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            if (connection.responseCode == 404) return null
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= MAX_RESPONSE_BYTES) {
                    val remaining = MAX_RESPONSE_BYTES + 1 - output.size()
                    val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            check(bytes.size <= MAX_RESPONSE_BYTES) { "Release response is too large" }
            bytes.toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseResponse(json: String, channel: UpdateChannel): AppRelease? =
        if (json.trimStart().startsWith("[")) newestFromList(json, channel == UpdateChannel.CI)
        else if (channel == UpdateChannel.STABLE) newestStable(json) else null

    private fun newestStable(json: String): AppRelease? {
        val current = Version.parse(installedVersion) ?: return null
        val release = JSONObject(json)
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null
        return parseEligible(release, current, allowPrerelease = false)
    }

    private fun newestFromList(json: String, allowPrerelease: Boolean = true): AppRelease? {
        val current = Version.parse(installedVersion) ?: return null
        val releases = JSONArray(json)
        var best: AppRelease? = null
        var bestVersion: Version? = null
        for (index in 0 until releases.length()) {
            val jsonRelease = releases.optJSONObject(index) ?: continue
            if (jsonRelease.optBoolean("draft")) continue
            if (allowPrerelease && (!CI_TAG.matches(jsonRelease.optString("tag_name")) ||
                    !jsonRelease.optBoolean("prerelease"))) continue
            val candidate = parseEligible(jsonRelease, current, allowPrerelease = allowPrerelease) ?: continue
            if (allowPrerelease && (candidate.apkUrl == null || candidate.size !in 1..UpdateDownloader.MAX_APK_BYTES ||
                    !Regex("[a-f0-9]{64}").matches(candidate.sha256))) continue
            val candidateVersion = Version.parse(candidate.version) ?: continue
            if (bestVersion == null || candidateVersion > bestVersion) {
                best = candidate
                bestVersion = candidateVersion
            }
        }
        return best
    }

    private fun parseEligible(release: JSONObject, current: Version, allowPrerelease: Boolean): AppRelease? {
        val tag = release.optString("tag_name")
        val version = Version.parse(tag) ?: return null
        if (version <= current) return null
        val prerelease = release.optBoolean("prerelease") || tag.contains('-')
        if (!allowPrerelease && prerelease) return null

        return parseRelease(release, requireNativeAsset = nativeAssetsOnly)
    }

    private fun parseRelease(release: JSONObject, requireNativeAsset: Boolean): AppRelease? {
        val tag = release.optString("tag_name")
        val prerelease = release.optBoolean("prerelease") || tag.contains('-')

        val expectedPageUrl = "https://github.com/$owner/$repository/releases/tag/$tag"
        val expectedDownloadPrefix = "https://github.com/$owner/$repository/releases/download/$tag/"
        val pageUrl = release.optString("html_url")
        if (pageUrl != expectedPageUrl) return null

        val assets = release.optJSONArray("assets")
        var apkUrl: String? = null
        var assetName: String? = null
        var size = 0L
        var digest = ""
        if (assets != null) {
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                if (name.endsWith(".apk", ignoreCase = true) && url.startsWith(expectedDownloadPrefix) &&
                    (!nativeAssetsOnly || name.startsWith("CurrentMusic-Android-v")) &&
                    (!CI_TAG.matches(tag) || name == "CurrentMusic-Android-$tag.apk")) {
                    apkUrl = url
                    assetName = name
                    size = asset.optLong("size", 0L)
                    digest = asset.optString("digest").removePrefix("sha256:").lowercase()
                    break
                }
            }
        }
        if (requireNativeAsset && apkUrl == null) return null
        return AppRelease(
            version = tag.removePrefix("v"),
            notes = release.optString("body").take(12_000),
            pageUrl = pageUrl,
            apkUrl = apkUrl,
            assetName = assetName,
            prerelease = prerelease,
            size = size,
            sha256 = digest,
        )
    }

    companion object {
        private val CI_TAG = Regex("v[0-9]+\\.[0-9]+\\.[0-9]+-ci\\.[1-9][0-9]*")
        // CI releases include generated PR notes; a page of 100 builds can exceed 512 KiB.
        private const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
    }
}

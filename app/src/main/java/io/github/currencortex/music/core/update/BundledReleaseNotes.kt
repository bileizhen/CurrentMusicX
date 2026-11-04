package io.github.currencortex.music.core.update

import io.github.currencortex.music.core.config.AppMetadata
import org.json.JSONObject

internal fun parseBundledReleaseNotes(json: String, installedVersion: String): AppRelease? = runCatching {
    val data = JSONObject(json)
    if (Version.parse(installedVersion) == null || data.optString("version") != installedVersion) return null
    val notes = data.optString("notes").take(12_000).takeIf { it.isNotBlank() } ?: return null
    AppRelease(installedVersion, notes, "${AppMetadata.RELEASES_URL}/tag/v$installedVersion",
        apkUrl = null, assetName = null, prerelease = installedVersion.contains('-'))
}.getOrNull()

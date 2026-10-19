package io.github.currencortex.music.data.profile

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Versions are keyed by the full asset URL, including its server, not by user ID. */
interface AvatarCache {
    val versions: Map<String, String>
    suspend fun invalidate(urls: Set<String>): Map<String, String>
}

internal fun versionedAvatarUrl(url: String, version: String?): String =
    if (version == null) url else url.toHttpUrlOrNull()?.newBuilder()
        // Fragments participate in Coil's cache key but never reach the HTTP server.
        // Keep signed query parameters intact.
        ?.fragment("currentmusic-avatar=$version")?.build()?.toString() ?: url

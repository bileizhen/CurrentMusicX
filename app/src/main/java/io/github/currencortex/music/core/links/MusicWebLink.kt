package io.github.currencortex.music.core.links

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed interface MusicWebLink {
    data object Home : MusicWebLink
    data class Song(val id: Long) : MusicWebLink
    data class Page(val route: String) : MusicWebLink
    data class ShortLink(val url: HttpUrl) : MusicWebLink

    companion object {
        private val projectHosts = setOf("music.20110208.xyz", "cm.bileizhen.top",
            "currentmusic.geekertao.com", "music.ctcehs.com")
        private val neteaseHosts = setOf("music.163.com", "y.music.163.com")

        /** Only public content routes are accepted; URLs cannot change servers or account state. */
        fun parse(raw: String): MusicWebLink? {
            if (raw.length > 8192) return null
            val url = raw.toHttpUrlOrNull() ?: return null
            if (url.username.isNotEmpty() || url.password.isNotEmpty() ||
                url.port != HttpUrl.defaultPort(url.scheme)) return null
            if (url.host == "163cn.tv") return ShortLink(url.newBuilder().scheme("https").build())
            if (url.host in projectHosts) {
                if (url.encodedPath !in setOf("/", "/app", "/app/", "/app/index.html")) return null
                val fragment = url.encodedFragment.orEmpty()
                if (fragment.isEmpty()) return Home
                val route = fragment.removePrefix("!").substringBefore('?').trimEnd('/')
                if (route in setOf("", "/home")) return Home
                val parts = route.split('/')
                if (parts.size != 3 || parts[0].isNotEmpty()) return null
                val id = positiveId(parts[2]) ?: return null
                return target(parts[1], id, project = true)
            }
            if (url.host !in neteaseHosts) return null
            // Desktop hash routes, mobile /m/ routes and ordinary query-string links.
            val content = url.encodedFragment?.takeIf { it.startsWith('/') || it.startsWith("!/") }
                ?.removePrefix("!")?.let { "https://music.163.com$it".toHttpUrlOrNull() } ?: url
            val route = content.encodedPath.removePrefix("/m/").trim('/')
            if ('/' in route) return null
            val ids = content.queryParameterValues("id")
            if (ids.size != 1) return null
            val id = positiveId(ids.single().orEmpty()) ?: return null
            return target(route, id, project = false)
        }

        private fun positiveId(value: String): Long? = value.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }
            ?.toLongOrNull()?.takeIf { it > 0 }

        private fun target(kind: String, id: Long, project: Boolean): MusicWebLink? = when (kind) {
            "song" -> Song(id)
            "album", "artist", "mv" -> Page("lib/$kind/$id")
            "playlist" -> if (project) null else Page("lib/ncmplaylist/$id")
            "ncmpl" -> if (project) Page("lib/ncmplaylist/$id") else null
            "pl" -> if (project) Page("lib/playlist/$id") else null
            else -> null
        }
    }
}

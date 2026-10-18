package io.github.currencortex.music.feature.profile

/** Keep browsing open; ask for an account only when a personal collection needs one. */
internal fun myMusicDestination(route: String, projectAccount: Boolean, neteaseAccount: Boolean,
    neteaseStale: Boolean, preferNetease: Boolean, nativeAvailable: Boolean): String {
    if (route !in setOf("lib/likes", "lib/playlists")) return route
    if (preferNetease && neteaseAccount && neteaseStale) return "user/binding"
    if (projectAccount || neteaseAccount && !neteaseStale) return route
    return if (nativeAvailable) "user/binding" else "user/login"
}

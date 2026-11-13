package io.github.currencortex.music.feature.support

import java.net.URI

/** Public configuration only. No payment API, tokens or account credentials. */
object SupportConfig {
    const val afdianUrl = "https://afdian.com/a/bi1eizhen"
    const val route = "support-developer"
    const val donationRoute = "official-donation"
}

internal fun supportHttpsUrl(value: String): String? = runCatching {
    val uri = URI(value)
    value.takeIf {
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null && uri.port in -1..65535 &&
            value.none { it.isWhitespace() || it.isISOControl() }
    }
}.getOrNull()

/** Monotonic clock supplied by the UI; a quick return from another app isn't payment evidence. */
internal class SupportClickGate(private val clock: () -> Long) {
    private var lastClick: Long? = null
    fun acquire(): Boolean {
        val now = clock()
        if (lastClick?.let { now - it < 1000 } == true) return false
        lastClick = now
        return true
    }
}

package io.github.currencortex.music.core.logging

/** Shared by stored logs, logcat output, exported reports. */
object LogRedactor {
    private val bearer = Regex("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+")
    private val credentials = Regex(
        "(?i)([\\\"']?\\b(?:authorization|cookie|set-cookie|token|access_token|refresh_token|password|passwd|secret|api_key|signature|sign|loginuuid)[\\\"']?\\s*[:=]\\s*)(?:\\\"[^\\\"]*\\\"|'[^']*'|[^\\s&,;]+)",
    )
    private val headers = Regex("(?im)^(\\s*(?:authorization|cookie|set-cookie)\\s*:\\s*).*$")

    fun redact(text: String): String {
        val safeHeaders = headers.replace(text) { "${it.groupValues[1]}[REDACTED]" }
        val safeBearer = bearer.replace(safeHeaders, "Bearer [REDACTED]")
        return credentials.replace(safeBearer) { "${it.groupValues[1]}[REDACTED]" }
    }
}

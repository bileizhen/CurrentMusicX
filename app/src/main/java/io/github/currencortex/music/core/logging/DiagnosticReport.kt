package io.github.currencortex.music.core.logging

import android.os.Build
import io.github.currencortex.music.BuildConfig
import io.github.currencortex.music.core.config.AppMetadata

object DiagnosticReport {
    fun create(logger: AppLogger): String = create(logger.read(), null)

    internal fun create(logs: String, audioDiagnostics: String?): String = buildString {
        appendLine("${AppMetadata.APP_NAME} diagnostics")
        appendLine("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("package=${BuildConfig.APPLICATION_ID}")
        appendLine("android=${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("generated=${java.time.Instant.now()}")
        appendLine("Sensitive values are redacted. Review this file before sharing.")
        appendLine()
        appendLine("--- logs ---")
        appendLine(logs.ifBlank { "(empty)" })
        audioDiagnostics?.let {
            appendLine()
            appendLine("--- audio output diagnostics ---")
            append(it)
        }
    }
}

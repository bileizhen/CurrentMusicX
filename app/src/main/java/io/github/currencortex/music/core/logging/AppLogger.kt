package io.github.currencortex.music.core.logging

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppLogger(context: Context) : LogSink {
    private val directory = File(context.filesDir, "logs").apply { mkdirs() }
    private val file = File(directory, "app.log")
    private val backup = File(directory, "app.log.1")
    private val lock = Any()
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun debug(tag: String, message: String) = write("D", tag, message, null)
    override fun info(tag: String, message: String) = write("I", tag, message, null)
    override fun warn(tag: String, message: String, error: Throwable?) = write("W", tag, message, error)
    fun error(tag: String, message: String, error: Throwable? = null) = write("E", tag, message, error)

    fun read(): String = synchronized(lock) {
        runCatching {
            buildString {
                if (backup.isFile) append(backup.readText(Charsets.UTF_8)).append('\n')
                if (file.isFile) append(file.readText(Charsets.UTF_8))
            }.trim()
        }.map(LogRedactor::redact).getOrElse { "Unable to read logs: ${it.message}" }
    }

    fun clear() = synchronized(lock) {
        check(!file.exists() || file.delete()) { "Unable to delete current log" }
        check(!backup.exists() || backup.delete()) { "Unable to delete rotated log" }
    }

    private fun write(level: String, tag: String, message: String, error: Throwable?) {
        val safeTag = LogRedactor.redact(tag).replace('\n', ' ').replace('\r', ' ').take(64)
        val safeMessage = LogRedactor.redact(buildString {
            append(message)
            if (error != null) append('\n').append(Log.getStackTraceString(error))
        }).take(MAX_ENTRY_CHARS)
        when (level) {
            "E" -> Log.e(safeTag, safeMessage)
            "W" -> Log.w(safeTag, safeMessage)
            "D" -> Log.d(safeTag, safeMessage)
            else -> Log.i(safeTag, safeMessage)
        }
        synchronized(lock) {
            val line = buildString {
                append(formatter.format(Date()))
                append(' ').append(level).append('/').append(safeTag).append(": ").append(safeMessage)
                append('\n')
            }
            runCatching {
                rotateIfNeeded(line.toByteArray(Charsets.UTF_8).size)
                file.appendText(line, Charsets.UTF_8)
            }
        }
    }

    private fun rotateIfNeeded(incomingBytes: Int) {
        if (file.isFile && file.length() + incomingBytes > MAX_LOG_BYTES) {
            check(!backup.exists() || backup.delete())
            check(file.renameTo(backup))
        }
    }

    companion object {
        private const val MAX_LOG_BYTES = 768L * 1024L
        private const val MAX_ENTRY_CHARS = 16 * 1024
    }
}

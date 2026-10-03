package io.github.currencortex.music.core.logging

interface LogSink {
    fun info(tag: String, message: String)
    fun warn(tag: String, message: String, error: Throwable? = null)
}

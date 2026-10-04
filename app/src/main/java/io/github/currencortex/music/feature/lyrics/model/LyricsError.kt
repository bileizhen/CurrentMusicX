package io.github.currencortex.music.feature.lyrics.model

enum class LyricsErrorCode { NO_LYRICS, NETWORK_ERROR, PARSE_ERROR, UNSUPPORTED_FORMAT, INVALID_TIMELINE }

class LyricsException(val code: LyricsErrorCode, message: String, cause: Throwable? = null) : Exception(message, cause)

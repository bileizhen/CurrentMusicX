package io.github.currencortex.music.feature.lyrics.data

import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.domain.*
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.ttml.TtmlParser
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LyricsIssue(val code: LyricsErrorCode, val detail: String)
data class LyricsResult(val document: LyricsDocument = LyricsDocument(), val error: String? = null, val issues: List<LyricsIssue> = emptyList())

class LyricsRepository(private val ttml: LyricsProvider, private val fallback: LyricsProvider,
    private val cache: LyricsCache, private val log: (String) -> Unit = {}) {
    private val lock = Mutex()
    private val memory = linkedMapOf<List<LyricsKey>, LyricsResult>()
    suspend fun load(song: Song): LyricsResult = withContext(Dispatchers.IO) { lock.withLock {
        val keys = LyricsMatcher.candidates(song)
        memory[keys]?.takeIf { keys.isNotEmpty() }?.let { return@withLock it }
        val issues = mutableListOf<LyricsIssue>()
        fun issue(code: LyricsErrorCode, detail: String) { issues.add(LyricsIssue(code, detail)); log("$code: $detail") }
        fun remember(document: LyricsDocument): LyricsResult {
            val result = LyricsResult(document, issues = issues.toList())
            if (keys.isNotEmpty()) { memory[keys] = result; if (memory.size > 16) memory.remove(memory.keys.first()) }
            return result
        }
        fun parse(raw: String, key: LyricsKey): LyricsDocument {
            val document = TtmlParser.parse(raw)
            if (!LyricsMatcher.matches(document.metadata.values, key)) throw LyricsException(LyricsErrorCode.UNSUPPORTED_FORMAT, "TTML platform metadata does not match ${key.source}/${key.songId}")
            return document
        }
        for (key in keys) {
            try {
                cache.read(key)?.let { return@withLock remember(parse(it, key)) }
            } catch (e: LyricsException) {
                issue(e.code, "Cached ${key.cacheName}: ${e.message}")
                try { cache.remove(key) } catch (e: Exception) { issue(LyricsErrorCode.PARSE_ERROR, "Cache removal: ${e.javaClass.simpleName}") }
            } catch (e: Exception) { issue(LyricsErrorCode.PARSE_ERROR, "Cache read: ${e.javaClass.simpleName}") }
        }
        for (provider in listOf(ttml, fallback)) {
            val result = try { provider.findLyrics(song) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { issue(LyricsErrorCode.NETWORK_ERROR, "Provider: ${e.javaClass.simpleName}"); continue }
            when (result) {
                is LyricsProviderResult.Failure -> issue(result.code, result.detail)
                is LyricsProviderResult.Parsed -> if (result.document.lines.isNotEmpty()) return@withLock remember(result.document)
                    else issue(LyricsErrorCode.NO_LYRICS, "Provider returned an empty document")
                is LyricsProviderResult.Ttml -> try {
                    val document = parse(result.text, result.key)
                    try { cache.write(result.key, result.text) } catch (e: Exception) { issue(LyricsErrorCode.PARSE_ERROR, "Cache write: ${e.javaClass.simpleName}") }
                    return@withLock remember(document)
                } catch (e: LyricsException) { issue(e.code, e.message.orEmpty()) }
            }
        }
        LyricsResult(error = "暂无歌词", issues = issues)
    } }
}

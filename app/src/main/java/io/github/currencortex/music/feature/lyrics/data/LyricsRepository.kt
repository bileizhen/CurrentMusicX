package io.github.currencortex.music.feature.lyrics.data

import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.domain.*
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.ttml.TtmlParser
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LyricsIssue(val code: LyricsErrorCode, val detail: String)
data class LyricsResult(val document: LyricsDocument = LyricsDocument(), val error: String? = null,
    val issues: List<LyricsIssue> = emptyList(), val origin: LyricsOrigin? = null)
data class LyricsSelection(val identity: List<Any?>, val provider: LyricsProvider, val origin: LyricsOrigin? = null,
    val automatic: Boolean = false)

class LyricsRepository(private val ttml: LyricsProvider, private val fallback: LyricsProvider,
    private val cache: LyricsCache, private val log: (String) -> Unit = {},
    private val selection: (() -> LyricsSelection)? = null,
    private val overrides: SongLyricsStore? = null,
    private val autoMatch: (suspend (Song) -> SavedLyrics?)? = null) {
    private val lock = Mutex()
    private val memory = linkedMapOf<Pair<List<Any?>, List<LyricsKey>>, LyricsResult>()
    suspend fun load(song: Song): LyricsResult = withContext(Dispatchers.IO) { lock.withLock {
        // User-selected files/matches are authoritative, including explicit instrumental results.
        overrides?.active(song)?.let { return@withLock LyricsResult(it.document, origin = it.origin) }
        val keys = LyricsMatcher.candidates(song)
        val selected = selection?.invoke()
        val selectedOrigin = if (song.musicSource == io.github.currencortex.music.data.song.MusicSource.NAVIDROME)
            LyricsOrigin("Navidrome", "navidrome") else selected?.origin
        val memoryKey = (selected?.identity.orEmpty() + listOf(selected?.automatic == true, SongLyricsStore.identity(song))) to keys
        val issues = mutableListOf<LyricsIssue>()
        fun issue(code: LyricsErrorCode, detail: String) { issues.add(LyricsIssue(code, detail)); log("$code: $detail") }
        fun remember(document: LyricsDocument, origin: LyricsOrigin? = selectedOrigin): LyricsResult {
            val result = LyricsResult(document, issues = issues.toList(), origin = origin)
            if (keys.isNotEmpty()) { memory[memoryKey] = result; if (memory.size > 16) memory.remove(memory.keys.first()) }
            return result
        }
        var automaticFailed = false
        suspend fun matchAutomatically(): LyricsResult? = try {
            autoMatch?.invoke(song)?.takeIf { it.document.lines.isNotEmpty() }?.let {
                remember(it.document, it.origin)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            automaticFailed = true
            issue(LyricsErrorCode.NETWORK_ERROR, "Automatic matching: ${e.javaClass.simpleName}")
            null
        }
        fun unavailable() = LyricsResult(error = if (automaticFailed) "歌词暂时无法读取，请稍后重试" else "暂无歌词",
            issues = issues.toList())
        // Automatic selection is a fresh comparison on every load. A previous
        // provider/cache result must not take precedence over the current winner.
        if (selected?.automatic == true) return@withLock matchAutomatically() ?: unavailable()
        memory[memoryKey]?.takeIf { keys.isNotEmpty() }?.let { return@withLock it }
        fun parse(raw: String, key: LyricsKey): LyricsDocument {
            val document = TtmlParser.parse(raw)
            if (!LyricsMatcher.matches(document.metadata.values, key)) throw LyricsException(LyricsErrorCode.UNSUPPORTED_FORMAT, "TTML platform metadata does not match ${key.source}/${key.songId}")
            return document
        }
        // An explicitly selected source must not be overridden by cached AMLL TTML.
        for (key in if (selected == null) keys else emptyList()) {
            try {
                cache.read(key)?.let { return@withLock remember(parse(it, key), LyricsOrigin("AMLL 逐字歌词", "amll", key.songId)) }
            } catch (e: LyricsException) {
                issue(e.code, "Cached ${key.cacheName}: ${e.message}")
                try { cache.remove(key) } catch (e: Exception) { issue(LyricsErrorCode.PARSE_ERROR, "Cache removal: ${e.javaClass.simpleName}") }
            } catch (e: Exception) { issue(LyricsErrorCode.PARSE_ERROR, "Cache read: ${e.javaClass.simpleName}") }
        }
        for (provider in selected?.let { listOf(it.provider) } ?: listOf(ttml, fallback)) {
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
                    return@withLock remember(document, LyricsOrigin("AMLL 逐字歌词", "amll", result.key.songId))
                } catch (e: LyricsException) { issue(e.code, e.message.orEmpty()) }
            }
        }
        // A failed default lookup may still have a reliable match on another platform.
        // Cache that document as a lookup result, separately from durable manual choices.
        matchAutomatically() ?: unavailable()
    } }
}

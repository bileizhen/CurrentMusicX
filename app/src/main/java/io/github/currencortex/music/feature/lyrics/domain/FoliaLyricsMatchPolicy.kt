// Adapted from chthollyphile/folia-major f96c65b27f7ca351e8db781301cedfa0a52252e2,
// src/utils/lyrics/{matchScore,sourcePriority,duration}.ts (AGPL-3.0).
// Original Folia contributors retain copyright.
package io.github.currencortex.music.feature.lyrics.domain

import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.LyricsCandidate
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Function
import org.mozilla.javascript.ScriptableObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Folia's 75% gate is a metadata score, never a comparison of lyric bodies. */
object FoliaLyricsMatchPolicy {
    data class Details(val score: Int, val titleMatched: Boolean, val artistMatched: Boolean,
        val albumMatched: Boolean?, val durationMatched: Boolean?) {
        val reliable get() = titleMatched && (artistMatched || albumMatched == true)
        val accepted get() = reliable && score >= 75
    }

    fun details(song: Song, candidate: LyricsCandidate): Details {
        val title = similarity(song.name, candidate.title, ::title)
        val artist = if (song.artists.isBlank()) 1.0 else artistSimilarity(song.artists, candidate.artists)
        val targetAlbum = song.album.isNotBlank()
        val albumText = candidate.album.takeUnless { it.trim().equals("unknown album", true) }.orEmpty()
        val album = if (targetAlbum && albumText.isNotBlank()) similarity(song.album, albumText, ::title) else null
        val identity = title * 45 + artist * 25 + (album?.times(30) ?: if (targetAlbum) 0.0 else 30.0)
        val titleMatched = title >= .65
        val artistMatched = song.artists.isBlank() || artist >= .5
        val albumMatched = if (targetAlbum && album != null) album >= .65 else null
        val reliable = artistMatched || albumMatched == true
        val first = duration(song.durationMs)
        val second = duration(candidate.durationMs)
        val difference = abs(first - second)
        val multiplier = when {
            first <= 0 || second <= 0 -> .9
            difference <= 1000 -> 1.0
            difference <= 3000 -> .95
            difference <= 5000 -> .75
            difference <= 10000 -> .35
            else -> .1
        }
        val durationMatched = if (first <= 0 || second <= 0) null else difference <= 3000
        val capped = if (!titleMatched || !reliable) minOf(identity, 74.0) else identity
        return Details((capped * multiplier).roundToInt().coerceIn(0, 100), titleMatched, artistMatched, albumMatched, durationMatched)
    }

    fun query(song: Song): String {
        val prefix = Regex("^(.+?)\\s+-\\s+(.+)$").find(song.name.trim())
        val title = if (prefix != null && normalize(prefix.groupValues[1]).replace(" ", "") == normalize(song.artists).replace(" ", ""))
            prefix.groupValues[2].trim() else song.name.trim()
        var album = song.album.trim()
        if (album.length > 48) album = album.replace(Regex("\\s*※.*$"), "")
            .replace(Regex("\\s*(歌|歌唱|アーティスト|Artist)\\s*[:：]?\\s*.+$", RegexOption.IGNORE_CASE), "").trim().take(48).trim()
        return listOf(title, song.artists.trim(), album).filter(String::isNotBlank).joinToString(" - ").take(240)
    }

    private fun duration(value: Long): Double = if (value <= 0) 0.0 else if (value > 43_200_000) value / 1000.0 else value.toDouble()
    private val version = Regex("instrumental|inst|off\\s*vocal|karaoke|remix|mix|version|ver\\.?|cover|live|edit|arrange|伴奏|カラオケ|インスト|リミックス|remaster|remastered", RegexOption.IGNORE_CASE)
    private fun title(value: String): String = normalize(value
        .replace(Regex("[（(\\[【]\\s*(feat|featuring|ft)\\.?\\s+[^)\\]）】]+[)\\]）】]", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\b(feat|featuring|ft)\\.?\\s+.+$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("[(\\[（【]([^)\\]）】]+)[)\\]）】]")) { if (version.containsMatchIn(it.groupValues[1])) it.value else "" })

    private fun similarity(first: String, second: String, normalizer: (String) -> String = ::normalize): Double {
        val a = normalizer(first); val b = normalizer(second)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        if (a.contains(b) || b.contains(a)) return minOf(a.length, b.length).toDouble() / max(a.length, b.length)
        val left = a.codePoints().toArray().toSet(); val right = b.codePoints().toArray().toSet()
        return left.intersect(right).size.toDouble() / left.union(right).size
    }

    private fun artistSimilarity(first: String, second: String): Double {
        val split = Regex("[,&、/]|feat\\.?|ft\\.?|featuring|与", RegexOption.IGNORE_CASE)
        val a = first.split(split).map(::normalize).filter(String::isNotEmpty)
        val b = second.split(split).map(::normalize).filter(String::isNotEmpty)
        if (a.isEmpty() || b.isEmpty()) return similarity(first, second)
        fun matches(left: String, right: String) = left == right || left.length >= 3 && right.contains(left) || right.length >= 3 && left.contains(right)
        val tokens = a.count { left -> b.any { matches(left, it) } }.toDouble() / max(a.size, b.size)
        val bonus = if (matches(a.first(), b.first())) max(tokens, .7) else tokens
        return max(bonus, similarity(first, second))
    }

    // Reuse Folia's exact kana and traditional-to-simplified normalization libraries. Only bundled
    // code runs, in Rhino's safe scope without Java bindings; cached strings bound UI work.
    private val cache = linkedMapOf<String, String>()
    private val factory = object : ContextFactory() {
        override fun makeContext(): Context = super.makeContext().apply {
            languageVersion = Context.VERSION_ES6; optimizationLevel = -1; setClassShutter { false }
        }
    }
    private val scope by lazy {
        factory.call { cx -> cx.initSafeStandardObjects().also { scope ->
            for (file in listOf("wanakana-5.3.1.min.js", "opencc-1.4.2-t2cn.js")) {
                val script = checkNotNull(javaClass.getResourceAsStream("/lyrics-match/$file")).bufferedReader().use { it.readText() }
                cx.evaluateString(scope, script, file, 1, null)
            }
            cx.evaluateString(scope, "var foliaT2s = OpenCC.Converter({from:'t',to:'cn'}); function foliaNormalize(s) { return foliaT2s(wanakana.toRomaji(s)); }", "folia-normalize", 1, null)
        } }
    }

    @Synchronized fun normalize(value: String): String {
        val input = value.take(2048)
        return cache.getOrPut(input) {
            val converted = factory.call { cx ->
                Context.toString((ScriptableObject.getProperty(scope, "foliaNormalize") as Function).call(cx, scope, scope, arrayOf(input)))
            }
            if (cache.size >= 512) cache.remove(cache.keys.first())
            converted.lowercase(Locale.ROOT).replace(Regex("[\\p{P}\\p{S}]"), "").replace(Regex("\\s+"), " ").trim()
        }
    }
}

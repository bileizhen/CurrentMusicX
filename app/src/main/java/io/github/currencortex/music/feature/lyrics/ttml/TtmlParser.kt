// Ruby/transliteration additions informed by AMLL f322858, AGPL-3.0-only;
// original AMLL contributors retain attribution. See THIRD_PARTY_NOTICES.md.
package io.github.currencortex.music.feature.lyrics.ttml

import io.github.currencortex.music.feature.lyrics.model.*
import org.xml.sax.*
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

/** Namespace-aware, bounded XML decoding. Timing is taken only from the supplied TTML. */
object TtmlParser {
    const val MAX_BYTES = 2 * 1024 * 1024
    fun parse(xml: String): LyricsDocument {
        if (xml.toByteArray(Charsets.UTF_8).size > MAX_BYTES) throw LyricsException(LyricsErrorCode.PARSE_ERROR, "TTML is too large")
        if (Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(xml))
            throw LyricsException(LyricsErrorCode.UNSUPPORTED_FORMAT, "TTML document declarations are unsupported")
        val handler = TreeHandler()
        try {
            val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
            val reader = factory.newSAXParser().xmlReader
            reader.contentHandler = handler
            reader.errorHandler = handler
            reader.entityResolver = EntityResolver { _, _ -> throw SAXException("External entities are unsupported") }
            reader.parse(InputSource(StringReader(xml.removePrefix("\uFEFF"))))
        } catch (e: SAXException) {
            throw LyricsException(LyricsErrorCode.PARSE_ERROR, "Malformed TTML XML", e)
        } catch (e: java.io.IOException) {
            throw LyricsException(LyricsErrorCode.PARSE_ERROR, "Unable to read TTML", e)
        } catch (e: javax.xml.parsers.ParserConfigurationException) {
            throw LyricsException(LyricsErrorCode.UNSUPPORTED_FORMAT, "XML parser unavailable", e)
        }
        val root = handler.root ?: throw LyricsException(LyricsErrorCode.PARSE_ERROR, "Empty XML document")
        if (root.name != "tt" || root.namespace !in setOf("", "http://www.w3.org/ns/ttml"))
            throw LyricsException(LyricsErrorCode.UNSUPPORTED_FORMAT, "Expected a TTML root")
        if (root.descendants("body").any { it.attr("timeContainer") == "seq" } || root.descendants("div").any { it.attr("timeContainer") == "seq" })
            throw LyricsException(LyricsErrorCode.UNSUPPORTED_FORMAT, "Sequential TTML containers are unsupported")
        val metadata = TtmlMetadataParser.parse(root)
        val head = root.children.firstOrNull { it.name == "head" }
        val body = root.children.firstOrNull { it.name == "body" }
        val rows = body?.descendants("p").orEmpty()
        val primaryAgent = metadata.agents.firstOrNull { it.type == "person" }?.id ?: rows.firstNotNullOfOrNull { it.attr("agent") }
        val lines = rows.map { row -> parseLine(row, head, primaryAgent) }.filter { it.text.isNotBlank() || it.backgroundVocals.isNotEmpty() }.sortedBy { it.startTimeMs }
        if (lines.isEmpty()) throw LyricsException(LyricsErrorCode.NO_LYRICS, "TTML contains no lyrics")
        metadata.durationMs?.let { duration ->
            if (lines.any { it.endTimeMs > duration }) throw LyricsException(LyricsErrorCode.INVALID_TIMELINE, "Lyrics exceed body duration")
        }
        return LyricsDocument(lines, metadata)
    }

    private fun parseLine(node: TtmlNode, head: TtmlNode?, primaryAgent: String?, parent: LyricLine? = null): LyricLine {
        val background = parent != null || "x-bg" in node.attr("role").orEmpty().split(' ')
        val key = node.attr("key") ?: parent?.key
        val agent = node.attr("agent") ?: parent?.agent
        val roles = node.attr("role").orEmpty().split(' ').filter { it.isNotEmpty() }
        val text = StringBuilder()
        val words = mutableListOf<LyricWord>()
        val translations = mutableListOf<LyricAuxiliary>()
        val romans = mutableListOf<LyricAuxiliary>()
        val inlineRomans = mutableListOf<LyricAuxiliary>()
        val bgNodes = mutableListOf<TtmlNode>()
        val ruby = mutableListOf<LyricRuby>()
        val bases = mutableListOf<TtmlBaseRange>()
        val romanTracks = mutableListOf<List<TtmlTimedText>>()
        fun append(container: TtmlNode, insideTimedWord: Boolean = false) {
            for (part in container.content) {
                if (part is String) {
                    // Formatting indentation is not an English word separator; literal inline spaces are.
                    text.append(if (part.isBlank() && ('\n' in part || '\r' in part)) "" else cleanTtmlText(part))
                    continue
                }
                part as TtmlNode
                when {
                    "x-translation" in part.attr("role").orEmpty().split(' ') -> translations.add(LyricAuxiliary(cleanTtmlText(part.text()).trim(), part.attr("lang")))
                    part.hasRole("x-roman") -> if (container === node) {
                        romans.add(LyricAuxiliary(cleanTtmlText(part.text()).trim(), part.attr("lang")))
                        romanTracks.add(ttmlRomanWords(part))
                    } else {
                        inlineRomans.add(LyricAuxiliary(cleanTtmlText(part.text()).trim(), part.attr("lang")))
                    }
                    "x-bg" in part.attr("role").orEmpty().split(' ') -> bgNodes.add(part)
                    part.attr("ruby") in setOf("text", "textContainer", "delimiter") -> Unit // Annotation never enters the primary text.
                    part.name == "br" -> text.append('\n')
                    else -> {
                        val offset = text.length
                        val wordCount = words.size
                        val baseCount = bases.size
                        val timing = part.explicitTiming()
                        append(part, insideTimedWord || timing != null)
                        if (part.attr("ruby") == "base" && text.length > offset)
                            bases.add(TtmlBaseRange(offset, text.length))
                        if (timing != null) {
                            val (from, to) = timing
                            // Zero-duration markers have no singable interval. Preserve their text; the
                            // coverage check below falls this line back to line timing without inventing duration.
                            // A timed wrapper around several timed spans must not duplicate their glyphs.
                            if (to > from && words.size == wordCount && text.length > offset) words.add(LyricWord(text.substring(offset), from, to, offset, text.length,
                                part.attr("agent"), part.attr("role").orEmpty().split(' ').filter { it.isNotEmpty() }))
                        }
                        if (part.attr("ruby") == "container") {
                            val annotations = ttmlRuby(part, bases.drop(baseCount).ifEmpty {
                                if (text.length > offset) listOf(TtmlBaseRange(offset, text.length)) else emptyList()
                            })
                            ruby.addAll(annotations)
                            // The base can inherit the explicitly timed ruby syllables' union.
                            // A surrounding timed word keeps ownership; untimed ruby never invents words.
                            if (timing == null && !insideTimedWord && words.size == wordCount) {
                                annotations.groupBy { it.startOffset to it.endOffset }.forEach { (range, segments) ->
                                    if (segments.all { it.startTimeMs != null && it.endTimeMs != null }) {
                                        val from = segments.minOf { it.startTimeMs!! }
                                        val to = segments.maxOf { it.endTimeMs!! }
                                        if (to > from) words.add(LyricWord(text.substring(range.first, range.second), from, to,
                                            range.first, range.second, part.attr("agent"), part.attr("role").orEmpty().split(' ').filter(String::isNotEmpty)))
                                    }
                                }
                            }
                        }
                        val ownedRomans = part.children.filter { it.hasRole("x-roman") }
                        if (ownedRomans.isNotEmpty()) {
                            val owner = words.indexOfFirst { it.startOffset == offset && it.endOffset == text.length }
                            if (owner >= 0) words[owner] = words[owner].copy(romanization = cleanTtmlText(ownedRomans.first().text()).trim())
                        }
                    }
                }
            }
        }
        append(node)
        val start = node.attr("begin")?.let(TtmlTimeParser::parse) ?: words.minOfOrNull { it.startTimeMs } ?: parent?.startTimeMs
            ?: throw LyricsException(LyricsErrorCode.INVALID_TIMELINE, "Lyric line is missing begin")
        val end = node.attr("end")?.let(TtmlTimeParser::parse) ?: node.attr("dur")?.let { start + TtmlTimeParser.parse(it) }
            ?: words.maxOfOrNull { it.endTimeMs } ?: parent?.endTimeMs
            ?: throw LyricsException(LyricsErrorCode.INVALID_TIMELINE, "Lyric line is missing end")
        if (end <= start || words.any { it.startTimeMs < start || it.endTimeMs > end })
            throw LyricsException(LyricsErrorCode.INVALID_TIMELINE, "Invalid line/word bounds")
        if (ruby.any { it.startTimeMs != null && it.startTimeMs < start || it.endTimeMs != null && it.endTimeMs > end })
            throw LyricsException(LyricsErrorCode.INVALID_TIMELINE, "Ruby timing exceeds its vocal line")
        translations.addAll(TtmlMetadataParser.auxiliary(head, key, background, false))
        romans.addAll(TtmlMetadataParser.auxiliary(head, key, background, true))
        // Word-owned readings remain available to renderers that only consume a line.
        // Keep repeated syllables and source order; prefer an explicit full-line track.
        inlineRomans.filter { it.text.isNotBlank() }.groupBy { it.language }.forEach { (language, readings) ->
            if (romans.none { it.language == language && it.text.isNotBlank() })
                romans.add(LyricAuxiliary(readings.joinToString(" ") { it.text }, language))
        }
        romanTracks.addAll(TtmlMetadataParser.romanWordTracks(head, key, background))
        val auxiliary = translations.filter { it.text.isNotBlank() }.distinct()
        val roman = romans.filter { it.text.isNotBlank() }.distinct()
        // Keep untimed literal text, but don't fabricate word timing for partial lyric coverage.
        val timedWords = alignTtmlRomanization(words.sortedBy { it.startTimeMs }.takeIf { tokens ->
            text.indices.all { index -> text[index].isWhitespace() || tokens.any { index in it.startOffset until it.endOffset } }
        }.orEmpty(), romanTracks).map { word -> word.copy(ruby = ruby.filter {
            it.startOffset >= word.startOffset && it.endOffset <= word.endOffset
        }) }
        if (timedWords.any { word -> word.ruby.any {
            it.startTimeMs != null && it.startTimeMs < word.startTimeMs || it.endTimeMs != null && it.endTimeMs > word.endTimeMs
        } }) throw LyricsException(LyricsErrorCode.INVALID_TIMELINE, "Ruby timing exceeds its word")
        val line = LyricLine(start, end, text.toString(), timedWords,
            (auxiliary.firstOrNull { it.language?.startsWith("zh") == true } ?: auxiliary.firstOrNull())?.text.orEmpty(), roman.firstOrNull()?.text.orEmpty(),
            background, agent != null && primaryAgent != null && agent != primaryAgent, agent, roles, key,
            translations = auxiliary, romanizations = roman, ruby = ruby.distinct())
        return line.copy(backgroundVocals = bgNodes.map { parseLine(it, head, primaryAgent, line) })
    }

    private class TreeHandler : DefaultHandler() {
        var root: TtmlNode? = null
        private val stack = ArrayDeque<TtmlNode>()
        private var count = 0
        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            if (++count > 50000 || stack.size > 48) throw SAXException("TTML structure exceeds limits")
            val attrs = (0 until attributes.length).associate { attributes.getLocalName(it).ifEmpty { attributes.getQName(it).substringAfter(':') } to attributes.getValue(it) }
            val node = TtmlNode(localName.ifEmpty { qName.substringAfter(':') }, uri, attrs)
            if (stack.isEmpty()) root = node else stack.last().content.add(node)
            stack.addLast(node)
        }
        override fun endElement(uri: String, localName: String, qName: String) { stack.removeLast() }
        override fun characters(ch: CharArray, start: Int, length: Int) {
            stack.lastOrNull()?.content?.let { content ->
                val text = String(ch, start, length)
                if (content.lastOrNull() is String) content[content.lastIndex] = content.last().toString() + text else content.add(text)
            }
        }
        override fun error(e: SAXParseException) { throw e }
        override fun fatalError(e: SAXParseException) { throw e }
    }
}

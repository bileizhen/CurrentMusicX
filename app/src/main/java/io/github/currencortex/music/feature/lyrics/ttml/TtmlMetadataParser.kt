package io.github.currencortex.music.feature.lyrics.ttml

import io.github.currencortex.music.feature.lyrics.model.*

internal object TtmlMetadataParser {
    fun parse(root: TtmlNode): LyricsMetadata {
        val head = root.children.firstOrNull { it.name == "head" }
        val values = linkedMapOf<String, MutableList<String>>()
        head?.descendants("meta")?.forEach { node ->
            val key = node.attr("key") ?: return@forEach
            val value = node.attr("value") ?: cleanTtmlText(node.text()).trim()
            if (value.isNotBlank()) values.getOrPut(key) { mutableListOf() }.add(value)
        }
        head?.descendants("title")?.forEach { node ->
            cleanTtmlText(node.text()).trim().takeIf { it.isNotEmpty() }?.let { values.getOrPut("musicName") { mutableListOf() }.add(it) }
        }
        return LyricsMetadata(values.mapValues { it.value.distinct() },
            head?.descendants("agent").orEmpty().mapNotNull { node ->
                node.attr("id")?.let { LyricAgent(it, node.attr("type").orEmpty(), node.descendants("name").joinToString(" / ") { n -> cleanTtmlText(n.text()).trim() }) }
            }, root.attr("lang"), root.children.firstOrNull { it.name == "body" }?.attr("dur")?.let(TtmlTimeParser::parse), "AMLL TTML DB")
    }

    fun auxiliary(head: TtmlNode?, key: String?, background: Boolean, roman: Boolean): List<LyricAuxiliary> {
        if (head == null || key == null) return emptyList()
        return head.descendants(if (roman) "transliteration" else "translation").flatMap { track ->
            track.descendants("text").filter { it.attr("for") == key }.mapNotNull { node ->
                val content = if (background) node.descendants("span").filter { "x-bg" in it.attr("role").orEmpty().split(' ') }.joinToString(" ") { it.text() }
                else node.content.filterNot { it is TtmlNode && "x-bg" in it.attr("role").orEmpty().split(' ') }.joinToString("") { if (it is TtmlNode) it.text() else it.toString() }
                cleanTtmlText(content).trim().takeIf { it.isNotEmpty() }?.let { LyricAuxiliary(it, track.attr("lang")) }
            }
        }
    }
}

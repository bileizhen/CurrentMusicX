package io.github.currencortex.music.feature.lyrics.ttml

/** Parser-private mixed XML content; these nodes never leave the TTML layer. */
internal class TtmlNode(val name: String, val namespace: String, val attributes: Map<String, String>) {
    val content = mutableListOf<Any>()
    val children get() = content.filterIsInstance<TtmlNode>()
    fun attr(name: String) = attributes[name]
    fun descendants(name: String): List<TtmlNode> = children.flatMap { listOfNotNull(it.takeIf { n -> n.name == name }) + it.descendants(name) }
    fun text(): String = content.joinToString("") { if (it is TtmlNode) it.text() else it.toString() }
}

internal fun cleanTtmlText(text: String): String = if ('\n' in text || '\r' in text)
    text.replace(Regex("\\s+"), " ").trim() else text

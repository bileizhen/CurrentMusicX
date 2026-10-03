// Adapted from XBlocker ui/MarkdownText.kt (MIT, Copyright 2026 XBlocker contributors).
package io.github.currencortex.music.feature.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Ordered-list items: the number is kept so the rendering stays readable when copied. */
private val orderedItem = Regex("""^(\d{1,3})[.)]\s+(.+)$""")
private val heading = Regex("""^(#{1,6})\s+(.+)$""")
internal enum class MarkdownKind { TEXT, HEADING, BULLET, ORDERED, RULE, CODE, QUOTE }
internal data class MarkdownBlock(val kind: MarkdownKind, val text: String = "", val level: Int = 0, val number: String = "")

internal fun markdownBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    var fence: String? = null
    val code = mutableListOf<String>()
    for (raw in markdown.replace("\r\n", "\n").lines()) {
        val line = raw.trim()
        if (fence != null) {
            if (line.startsWith(fence) && line.removePrefix(fence).isBlank()) {
                blocks += MarkdownBlock(MarkdownKind.CODE, code.joinToString("\n")); code.clear(); fence = null
            } else code += raw
            continue
        }
        if (line.startsWith("```") || line.startsWith("~~~")) { fence = line.take(3); continue }
        val title = heading.matchEntire(line)
        val ordered = orderedItem.matchEntire(line)
        blocks += when {
            line.isEmpty() -> continue
            line.length >= 3 && line.all { it == '-' || it == '*' || it == '_' } -> MarkdownBlock(MarkdownKind.RULE)
            title != null -> MarkdownBlock(MarkdownKind.HEADING, title.groupValues[2], title.groupValues[1].length)
            line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") -> MarkdownBlock(MarkdownKind.BULLET, line.substring(2))
            ordered != null -> MarkdownBlock(MarkdownKind.ORDERED, ordered.groupValues[2], number = ordered.groupValues[1])
            line.startsWith("> ") -> MarkdownBlock(MarkdownKind.QUOTE, line.substring(2))
            else -> MarkdownBlock(MarkdownKind.TEXT, line)
        }
    }
    if (fence != null) blocks += MarkdownBlock(MarkdownKind.CODE, code.joinToString("\n"))
    return blocks
}

/** The dialog title already includes the release version. */
internal fun stripVersionHeadings(version: String, notes: String): String {
    if (version.isBlank()) return notes
    val versionHeading = Regex("^#{1,6}\\s+v?${Regex.escape(version)}\\s*$")
    return notes.lines().filterNot { versionHeading.matches(it.trim()) }.joinToString("\n").trim()
}

/**
 * Renders the markdown subset that release notes actually use: ##/### headings,
 * "- " bullets, "1. " ordered items, --- rules, **bold**, `code` spans and
 * [label](url) links. Anything else stays plain text, so unknown syntax degrades
 * to readable content instead of breaking layout.
 */
@Composable
internal fun MarkdownText(markdown: String, modifier: Modifier = Modifier, onLinkClick: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val linkStyle = SpanStyle(color = MiuixTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)
    val blocks = remember(markdown) { markdownBlocks(markdown) }
    val onLink: (String) -> Unit = onLinkClick ?: { openLink(context, it) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block.kind) {
                MarkdownKind.RULE ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                MarkdownKind.HEADING -> Text(inlineMarkdown(block.text, linkStyle, onLink),
                    fontSize = when (block.level) { 1 -> 22.sp; 2 -> 19.sp; 3 -> 16.sp; else -> 15.sp }, fontWeight = FontWeight.SemiBold)
                MarkdownKind.BULLET -> Row {
                    Text("•  ")
                    Text(inlineMarkdown(block.text, linkStyle, onLink), modifier = Modifier.weight(1f))
                }
                MarkdownKind.ORDERED -> Row {
                    Text("${block.number}.  ")
                    Text(inlineMarkdown(block.text, linkStyle, onLink), modifier = Modifier.weight(1f))
                }
                MarkdownKind.CODE -> Text(block.text, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                MarkdownKind.QUOTE -> Text(inlineMarkdown(block.text, linkStyle, onLink), modifier = Modifier.padding(start = 12.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                MarkdownKind.TEXT -> Text(inlineMarkdown(block.text, linkStyle, onLink))
            }
        }
    }
}

private val inlineSyntax = Regex("""\*\*(.+?)\*\*|\[([^]]+)]\(([^)]+)\)|`([^`]+)`|(?<!\*)\*([^*]+)\*(?!\*)|(?<!_)_([^_]+)_(?!_)""")

/** Inline parser kept free of composition so the supported subset can be unit tested. */
internal fun inlineMarkdown(text: String, linkStyle: SpanStyle, onLinkClick: (String) -> Unit): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var cursor = 0
    while (cursor < text.length) {
        val match = inlineSyntax.find(text, cursor) ?: run { builder.append(text.substring(cursor)); return builder.toAnnotatedString() }
        builder.append(text.substring(cursor, match.range.first))
        val bold = match.groups[1]
        val code = match.groups[4]
        val italic = match.groups[5] ?: match.groups[6]
        when {
            bold != null -> {
                val start = builder.length
                builder.append(bold.value)
                builder.addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), start, builder.length)
            }
            code != null -> {
                val start = builder.length
                builder.append(code.value)
                builder.addStyle(SpanStyle(fontFamily = FontFamily.Monospace), start, builder.length)
            }
            italic != null -> {
                val start = builder.length
                builder.append(italic.value)
                builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, builder.length)
            }
            else -> {
                val start = builder.length
                builder.append(match.groupValues[2])
                val url = match.groupValues[3]
                if (isWebLink(url)) {
                    builder.addStyle(linkStyle, start, builder.length)
                    builder.addLink(LinkAnnotation.Clickable(tag = url, styles = TextLinkStyles(linkStyle),
                        linkInteractionListener = { onLinkClick(url) }), start, builder.length)
                }
            }
        }
        cursor = match.range.last + 1
    }
    return builder.toAnnotatedString()
}

private fun isWebLink(url: String): Boolean = url.toHttpUrlOrNull()?.let { it.username.isBlank() && it.password.isBlank() } == true

private fun openLink(context: Context, url: String) {
    if (!isWebLink(url)) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}


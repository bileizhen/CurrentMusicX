// Adapted from XBlocker ui/MarkdownText.kt (MIT, Copyright 2026 XBlocker contributors).
package io.github.currencortex.music.feature.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import top.yukonga.miuix.kmp.basic.TextButton

/** Ordered-list items: the number is kept so the rendering stays readable when copied. */
private val orderedItem = Regex("""^(\d{1,3})[.)]\s+(.+)$""")
private val heading = Regex("""^(#{1,6})\s+(.+)$""")
internal enum class MarkdownKind { TEXT, HEADING, BULLET, ORDERED, RULE, CODE, QUOTE, IMAGE }
internal data class MarkdownBlock(val kind: MarkdownKind, val text: String = "", val level: Int = 0,
    val number: String = "", val imageUrl: String = "")
private val imageSyntax = Regex("""!\[([^]]*)]\(\s*(<[^>]+>|[^\s)]+)(?:\s+["'][^"']*["'])?\s*\)|<img\b[^>]*>""", RegexOption.IGNORE_CASE)
private val imageAttributes = Regex("""\b(src|alt)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
private fun imageBlock(match: MatchResult): MarkdownBlock {
    val html = match.value.startsWith("<")
    val attributes = if (html) imageAttributes.findAll(match.value).associate { attribute ->
        attribute.groupValues[1].lowercase() to attribute.groupValues.drop(2).firstOrNull { it.isNotEmpty() }.orEmpty()
    } else emptyMap()
    val alt = if (html) attributes["alt"].orEmpty() else match.groupValues[1]
    val url = (if (html) attributes["src"].orEmpty() else match.groupValues[2].removeSurrounding("<", ">"))
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
    val safe = url.toHttpUrlOrNull()?.let { it.scheme == "https" && it.username.isEmpty() && it.password.isEmpty() } == true
    return if (safe) MarkdownBlock(MarkdownKind.IMAGE, alt, imageUrl = url)
    else MarkdownBlock(MarkdownKind.TEXT, alt.ifBlank { "图片地址不可用" })
}

internal fun markdownBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    var fence: String? = null
    val code = mutableListOf<String>()
    fun appendText(line: String) {
        val title = heading.matchEntire(line)
        val ordered = orderedItem.matchEntire(line)
        val block = when {
            line.isBlank() -> return
            line.length >= 3 && line.all { it == '-' || it == '*' || it == '_' } -> MarkdownBlock(MarkdownKind.RULE)
            title != null -> MarkdownBlock(MarkdownKind.HEADING, title.groupValues[2], title.groupValues[1].length)
            line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") -> MarkdownBlock(MarkdownKind.BULLET, line.substring(2))
            ordered != null -> MarkdownBlock(MarkdownKind.ORDERED, ordered.groupValues[2], number = ordered.groupValues[1])
            line.startsWith("> ") -> MarkdownBlock(MarkdownKind.QUOTE, line.substring(2))
            else -> MarkdownBlock(MarkdownKind.TEXT, line)
        }
        blocks += block
    }
    for (raw in markdown.replace("\r\n", "\n").lines()) {
        val line = raw.trim()
        if (fence != null) {
            if (line.startsWith(fence) && line.removePrefix(fence).isBlank()) {
                blocks += MarkdownBlock(MarkdownKind.CODE, code.joinToString("\n")); code.clear(); fence = null
            } else code += raw
            continue
        }
        if (line.startsWith("```") || line.startsWith("~~~")) { fence = line.take(3); continue }
        var cursor = 0
        for (image in imageSyntax.findAll(line)) {
            appendText(line.substring(cursor, image.range.first).trim())
            val block = imageBlock(image)
            blocks += if (block.kind == MarkdownKind.IMAGE && blocks.count { it.kind == MarkdownKind.IMAGE } >= 8)
                MarkdownBlock(MarkdownKind.TEXT, block.text.ifBlank { "更多图片请查看发布页面" }) else block
            cursor = image.range.last + 1
        }
        appendText(line.substring(cursor).trim())
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
 * [label](url) links, HTTPS Markdown images and GitHub's HTML img tags. Anything else stays plain text, so unknown syntax degrades
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
                MarkdownKind.IMAGE -> ReleaseNoteImage(block.imageUrl, block.text)
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

@Composable
private fun ReleaseNoteImage(url: String, alt: String) {
    val context = LocalContext.current
    var attempt by remember(url) { mutableIntStateOf(0) }
    var loaded by remember(url, attempt) { mutableStateOf(false) }
    var failed by remember(url, attempt) { mutableStateOf(false) }
    var ratio by remember(url) { mutableFloatStateOf(16f / 9f) }
    Box(Modifier.fillMaxWidth().heightIn(max = 240.dp).aspectRatio(ratio)
        .clip(RoundedCornerShape(16.dp)).background(MiuixTheme.colorScheme.onSurface.copy(alpha = .05f))
        .testTag("update_note_image"), contentAlignment = Alignment.Center) {
        AsyncImage(model = remember(context, url, attempt) {
            ImageRequest.Builder(context).data(url).size(1200, 1200)
                .memoryCacheKey("release-note:$url:$attempt").diskCacheKey("release-note:$url:$attempt").build()
        }, contentDescription = alt.ifBlank { "版本更新图片" }, contentScale = ContentScale.Fit,
            modifier = Modifier.matchParentSize(), onSuccess = {
                loaded = true
                if (it.result.image.height > 0) ratio = (it.result.image.width.toFloat() / it.result.image.height).coerceIn(.35f, 3f)
            }, onError = { failed = true })
        if (failed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("图片暂时无法加载", fontSize = 13.sp, modifier = Modifier.testTag("update_note_image_error"))
            TextButton("重新加载图片", onClick = { attempt++ })
        } else if (!loaded) Text("图片加载中…", fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
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


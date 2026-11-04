package io.github.currencortex.music.feature.update

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import org.junit.Assert.*
import org.junit.Test

class MarkdownReleaseNotesTest {
    @Test fun generatedCommentsAreHiddenButHeadingsAndChangesRemain() {
        val blocks = markdownBlocks("""
            <!-- Release notes generated using
            configuration in .github/release.yml at v1.3.3 -->
            ## What's Changed
            ### New features
            * 支持新功能 <!-- internal --> by @author
        """.trimIndent())
        assertEquals(listOf(MarkdownKind.HEADING, MarkdownKind.HEADING, MarkdownKind.BULLET), blocks.map { it.kind })
        assertEquals(listOf(2, 3, 0), blocks.map { it.level })
        assertEquals("支持新功能  by @author", blocks.last().text)
        assertFalse(blocks.any { it.text.contains("<!--") || it.text.contains("configuration") })
    }

    @Test fun commentsDoNotLoadHiddenImagesOrLeakWhenTruncated() {
        assertEquals(listOf("可见", "保留"), markdownBlocks(
            "可见<!-- <img src='https://example.org/hidden.png'> -->\n保留<!-- unfinished\nsecret"
        ).map { it.text })
    }

    @Test fun fencedAndInlineCodeKeepLiteralCommentExamples() {
        val blocks = markdownBlocks("```html\n<!-- code example -->\n```\n使用 `<!-- literal -->` 注释。")
        assertEquals(MarkdownKind.CODE, blocks.first().kind)
        assertEquals("<!-- code example -->", blocks.first().text)
        assertEquals("使用 `<!-- literal -->` 注释。", blocks.last().text)
    }

    @Test fun bareGithubLinksAreCompactAndRetainExactTargetsAndPunctuation() {
        val url = "https://github.com/bileizhen/CurrentMusicX/pull/6#discussion_r123"
        val result = inlineMarkdown("功能 by @author in $url.", SpanStyle()) {}
        assertEquals("功能 by @author in #6.", result.text)
        val link = result.getLinkAnnotations(0, result.length).single()
        assertEquals(url, (link.item as LinkAnnotation.Clickable).tag)
        assertEquals("#6", result.text.substring(link.start, link.end))
    }

    @Test fun autolinksExplicitLabelsAndInlineCodeKeepTheirMeaning() {
        val url = "https://github.com/bileizhen/CurrentMusicX/pull/37"
        assertEquals("自定义", inlineMarkdown("[自定义]($url)", SpanStyle()) {}.text)
        assertEquals("#37", inlineMarkdown("<$url>", SpanStyle()) {}.text)
        val literal = inlineMarkdown("`$url`", SpanStyle()) {}
        assertEquals(url, literal.text)
        assertTrue(literal.getLinkAnnotations(0, literal.length).isEmpty())
        val unsafe = inlineMarkdown("[不安全](javascript:alert)", SpanStyle()) {}
        assertTrue(unsafe.getLinkAnnotations(0, unsafe.length).isEmpty())
        assertEquals("https://example.org/a_(b).", inlineMarkdown("https://example.org/a_(b).", SpanStyle()) {}.text)
        val compare = inlineMarkdown("https://github.com/bileizhen/CurrentMusicX/compare/v1.3.2...v1.3.3", SpanStyle()) {}
        assertEquals("完整改动", compare.text)
        assertEquals("other/repo#6", inlineMarkdown("https://github.com/other/repo/pull/6", SpanStyle()) {}.text)
    }
}

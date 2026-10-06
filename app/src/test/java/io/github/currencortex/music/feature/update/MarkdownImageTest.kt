package io.github.currencortex.music.feature.update

import org.junit.Assert.*
import org.junit.Test

class MarkdownImageTest {
    @Test fun markdownBannerPreservesSurroundingNotesAndVersionHeadingStripping() {
        val notes = "## v1.1.2\n![Current Music](https://example.com/banner.png \"Preview\")\n## 本次更新\n- **图片说明**\n1. 下载更新"
        val blocks = markdownBlocks(stripVersionHeadings("1.1.2", notes))
        assertEquals(listOf(MarkdownKind.IMAGE, MarkdownKind.HEADING, MarkdownKind.BULLET, MarkdownKind.ORDERED), blocks.map { it.kind })
        assertEquals("https://example.com/banner.png", blocks.first().imageUrl)
        assertEquals("Current Music", blocks.first().text)
        assertEquals("1", blocks.last().number)
    }
    @Test fun githubHtmlImagesSupportEmptyAltMixedQuotesAndSignedQueries() {
        val blocks = markdownBlocks("<img width=1280 alt=\"\" src='https://example.com/p.png?a=1&amp;b=2' />\n<img SRC=\"https://example.com/b.png\" ALT=\"横幅\">")
        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it.kind == MarkdownKind.IMAGE })
        assertEquals("", blocks.first().text)
        assertEquals("https://example.com/p.png?a=1&b=2", blocks.first().imageUrl)
        assertEquals("横幅", blocks.last().text)
    }
    @Test fun imageSourcesRequireHttpsWithoutCredentialsAndCodeFencesStayLiteral() {
        val blocks = markdownBlocks("![本地](file:///data/private)\n![不安全](http://example.com/p.png)\n![凭据](https://user:pass@example.com/p.png)\n<img src=\"javascript:alert(1)\" alt=\"脚本\">\n```md\n![示例](https://example.com/p.png)\n```")
        assertFalse(blocks.any { it.kind == MarkdownKind.IMAGE })
        assertEquals(listOf("本地", "不安全", "凭据", "脚本"), blocks.dropLast(1).map { it.text })
        assertEquals(MarkdownKind.CODE, blocks.last().kind)
        assertEquals("![示例](https://example.com/p.png)", blocks.last().text)
    }
    @Test fun inlineImagesKeepTextOrderAndExcessImagesDegradeToCaptions() {
        val inline = markdownBlocks("之前 ![插图](<https://example.com/a.png>) 之后")
        assertEquals(listOf("之前", "插图", "之后"), inline.map { it.text })
        val many = markdownBlocks((1..10).joinToString("\n") { "![图$it](https://example.com/$it.png)" })
        assertEquals(8, many.count { it.kind == MarkdownKind.IMAGE })
        assertEquals("图10", many.last().text)
    }
}

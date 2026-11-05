package io.github.currencortex.music.feature.profile

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class PostMarkdownEditingTest {
    @Test fun wrapsTheSelectionWithoutLosingSurroundingTextOrEmoji() {
        val input = TextFieldValue("听一首🎵好歌", TextRange(3, 5))
        val result = applyPostMarkdown(input, PostMarkdownTool.BOLD)
        assertEquals("听一首**🎵**好歌", result.text)
        assertEquals(TextRange(5, 7), result.selection)
    }
    @Test fun emptyInlineEditsSelectThePlaceholderAndLinksSelectTheUrl() {
        val bold = applyPostMarkdown(TextFieldValue(""), PostMarkdownTool.BOLD)
        assertEquals("**加粗文字**", bold.text)
        assertEquals("加粗文字", bold.text.substring(bold.selection.min, bold.selection.max))
        val link = applyPostMarkdown(TextFieldValue("歌单", TextRange(0, 2)), PostMarkdownTool.LINK)
        assertEquals("[歌单](https://)", link.text)
        assertEquals("https://", link.text.substring(link.selection.min, link.selection.max))
    }
    @Test fun multilineFormattingStopsBeforeAnUnselectedTrailingLine() {
        val value = TextFieldValue("前言\n第一首\n第二首\n结尾", TextRange(3, 11))
        val numbered = applyPostMarkdown(value, PostMarkdownTool.NUMBERED)
        assertEquals("前言\n1. 第一首\n2. 第二首\n结尾", numbered.text)
        assertEquals("前言\n> 第一首\n> 第二首\n结尾", applyPostMarkdown(value, PostMarkdownTool.QUOTE).text)
        assertEquals("前言\n- 第一首\n- 第二首\n结尾", applyPostMarkdown(value, PostMarkdownTool.BULLET).text)
    }
    @Test fun headingsUseTheCurrentLineAndReversedSelectionsAreAccepted() {
        assertEquals("第一行\n# 第二行", applyPostMarkdown(TextFieldValue("第一行\n第二行", TextRange(6)), PostMarkdownTool.HEADING).text)
        assertEquals("*文字*", applyPostMarkdown(TextFieldValue("文字", TextRange(2, 0)), PostMarkdownTool.ITALIC).text)
    }
    @Test fun codeBlocksAndRulesRemainRenderableAndClearImeComposition() {
        val code = applyPostMarkdown(TextFieldValue("a\nb", TextRange(0, 3), TextRange(0, 1)), PostMarkdownTool.CODE)
        assertEquals("```\na\nb\n```", code.text)
        assertNull(code.composition)
        assertEquals("---\n\n", applyPostMarkdown(TextFieldValue(""), PostMarkdownTool.RULE).text)
    }
}

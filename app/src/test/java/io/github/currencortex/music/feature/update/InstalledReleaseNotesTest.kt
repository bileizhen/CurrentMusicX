package io.github.currencortex.music.feature.update

import org.junit.Assert.*
import org.junit.Test

class InstalledReleaseNotesTest {
    private val history = """
        # 更新记录
        ## 1.1.14 · 2026-10-17
        - 评论区与播放
        ### 交互
        - 保留说明子标题
        ## 1.1.13 · 2026-10-16
        - 旧版说明
    """.trimIndent()

    @Test fun installedVersionKeepsSubheadingsAndExcludesOtherVersions() {
        assertEquals("- 评论区与播放\n### 交互\n- 保留说明子标题", installedReleaseNotes(history, "1.1.14"))
        assertEquals("- 旧版说明", installedReleaseNotes(history, "1.1.13"))
    }

    @Test fun isolatedPrereleaseUsesItsInstalledBaseVersion() {
        assertEquals(installedReleaseNotes(history, "1.1.14"), installedReleaseNotes(history, "v1.1.14-alpha.0"))
    }

    @Test fun unknownVersionDoesNotBorrowAnotherReleaseNotes() {
        val notes = installedReleaseNotes(history, "2.0.0")
        assertTrue(notes.contains("未附带更新说明"))
        assertFalse(notes.contains("评论区"))
    }
}

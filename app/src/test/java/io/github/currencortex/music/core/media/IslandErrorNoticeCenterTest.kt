package io.github.currencortex.music.core.media

import io.github.currencortex.music.core.update.prioritizedIslandNotices
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IslandErrorNoticeCenterTest {
    @Test fun undoExpiresAtFiveSecondsAndCannotRunAfterExpiry() = runTest {
        val notices = IslandErrorNoticeCenter(backgroundScope)
        var restored = 0
        notices.showUndo("已取消喜欢") { restored++ }; runCurrent()
        val action = notices.state.value!!.action!!
        assertEquals("撤销", action.label)
        assertTrue(notices.state.value.isActionableIslandNotice())
        advanceTimeBy(4999); runCurrent(); assertNotNull(notices.state.value)
        advanceTimeBy(1); runCurrent(); assertNull(notices.state.value)
        action.invoke()
        assertEquals(0, restored)
    }

    @Test fun undoRunsOnceAndSameTextFromAnotherSongInvalidatesOldAction() = runTest {
        val notices = IslandErrorNoticeCenter(backgroundScope)
        var first = 0; var second = 0
        val oldId = notices.showUndo("已取消喜欢") { first++ }; runCurrent()
        val oldAction = notices.state.value!!.action!!
        val newId = notices.showUndo("已取消喜欢") { second++ }; runCurrent()
        val newAction = notices.state.value!!.action!!
        assertNotEquals(oldId, newId)
        oldAction.invoke(); assertEquals(0, first)
        assertEquals(newId, notices.state.value!!.id)
        newAction.invoke(); newAction.invoke()
        assertEquals(1, second); assertNull(notices.state.value)
    }

    @Test fun messagesUseANeutralNoticeAndRespectShortAndLongLifetimes() = runTest {
        val notices = IslandErrorNoticeCenter(backgroundScope)
        notices.showMessage("  日志已保存  "); runCurrent()
        assertEquals("日志已保存", notices.state.value!!.message)
        assertEquals(IslandNoticeKind.MESSAGE, notices.state.value!!.kind)
        assertEquals(SoundEffectNoticePhase.READY, notices.state.value!!.phase)
        advanceTimeBy(3599); runCurrent(); assertNotNull(notices.state.value)
        advanceTimeBy(1); runCurrent(); assertNull(notices.state.value)
        notices.showMessage("播放已定时关闭", longDuration = true); runCurrent()
        advanceTimeBy(5499); runCurrent(); assertNotNull(notices.state.value)
        advanceTimeBy(1); runCurrent(); assertNull(notices.state.value)
    }

    @Test fun duplicateMessagesExtendTheSameNoticeAndStaleClearCannotHideNewFeedback() = runTest {
        val notices = IslandErrorNoticeCenter(backgroundScope)
        val first = notices.showMessage("版本信息已复制"); runCurrent()
        advanceTimeBy(3000)
        assertEquals(first, notices.showMessage("版本信息已复制")); runCurrent()
        assertEquals(0L, notices.showMessage("  "))
        advanceTimeBy(601); runCurrent()
        assertEquals(first, notices.state.value!!.id)
        val error = notices.show("复制失败"); runCurrent()
        assertNotEquals(first, error)
        notices.clear(first)
        assertEquals(error, notices.state.value!!.id)
        advanceTimeBy(5500); runCurrent(); assertNull(notices.state.value)
    }

    @Test fun aMessageDoesNotConsumeAnOngoingDownload() = runTest {
        val download = SoundEffectNotice(-1, "下载中", SoundEffectNoticeTier.STANDARD,
            kind = IslandNoticeKind.UPDATE_DOWNLOAD, progress = 12)
        val downloads = MutableStateFlow<SoundEffectNotice?>(download)
        val messages = IslandErrorNoticeCenter(backgroundScope)
        val visible = prioritizedIslandNotices(downloads, MutableStateFlow(null), backgroundScope, messages.state)
        messages.showMessage("已收藏到歌单"); runCurrent()
        assertEquals(IslandNoticeKind.MESSAGE, visible.value!!.kind)
        downloads.value = download.copy(progress = 60); runCurrent()
        assertEquals("已收藏到歌单", visible.value!!.message)
        advanceTimeBy(3600); runCurrent()
        assertEquals(60, visible.value!!.progress)
    }

    @Test fun newerErrorsSurviveOldTimersAndExplicitStaleDismissals() = runTest {
        val errors = IslandErrorNoticeCenter(backgroundScope)
        assertEquals(0L, errors.show("  "))
        val old = errors.show("网络连接失败"); runCurrent()
        assertEquals(IslandNoticeKind.ERROR, errors.state.value!!.kind)
        advanceTimeBy(3000)
        val newer = errors.show("没有访问权限"); runCurrent()
        errors.clear(old)
        advanceTimeBy(2501); runCurrent()
        assertEquals(newer, errors.state.value!!.id)
        assertEquals("没有访问权限", errors.state.value!!.message)
        advanceTimeBy(3000); runCurrent()
        assertNull(errors.state.value)
    }

    @Test fun errorTemporarilyInterruptsEffectsAndRestoresTheRetainedDownload() = runTest {
        val download = SoundEffectNotice(1, "下载完成", SoundEffectNoticeTier.STANDARD,
            SoundEffectNoticePhase.READY, kind = IslandNoticeKind.UPDATE_DOWNLOAD)
        val downloads = MutableStateFlow<SoundEffectNotice?>(download)
        val effects = SoundEffectNoticeCenter(backgroundScope)
        val errors = IslandErrorNoticeCenter(backgroundScope)
        val visible = prioritizedIslandNotices(downloads, effects.state, backgroundScope, errors.state)
        val effect = effects.begin("原声", SoundEffectNoticeTier.VIP); runCurrent()
        val error = errors.show("网络连接失败"); runCurrent()
        assertEquals(IslandNoticeKind.ERROR, visible.value!!.kind)
        effects.complete(effect, SoundEffectNoticeResult(SoundEffectNoticePhase.READY, "音效已生效"))
        advanceTimeBy(4000); runCurrent()
        assertEquals(error, visible.value!!.id)
        assertEquals(IslandNoticeKind.ERROR, visible.value!!.kind)
        advanceTimeBy(1501); runCurrent()
        assertEquals(download, visible.value)
        assertTrue(visible.value.isUpdateInstallNotice())
    }
}

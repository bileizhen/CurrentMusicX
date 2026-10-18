package io.github.currencortex.music.core.media

import io.github.currencortex.music.core.update.prioritizedIslandNotices
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IslandErrorNoticeCenterTest {
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

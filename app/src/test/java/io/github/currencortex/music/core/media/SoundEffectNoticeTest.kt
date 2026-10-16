package io.github.currencortex.music.core.media

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SoundEffectNoticeTest {
    @Test fun staleCompletionAndExpiryCannotReplaceOrDismissTheLatestClick() = runTest {
        val center = SoundEffectNoticeCenter(backgroundScope)
        val first = center.begin("旧音效", SoundEffectNoticeTier.VIP)
        center.complete(first, SoundEffectNoticeResult(SoundEffectNoticePhase.READY, "已开启，播放后生效"))
        advanceTimeBy(3000)
        val latest = center.begin("SVIP 音效", SoundEffectNoticeTier.SVIP)
        center.complete(first, SoundEffectNoticeResult(SoundEffectNoticePhase.FAILED, "迟到的失败"))
        advanceTimeBy(1000)
        assertEquals(latest, center.state.value?.id)
        assertEquals(SoundEffectNoticeTier.SVIP, center.state.value?.tier)
        assertEquals(SoundEffectNoticePhase.APPLYING, center.state.value?.phase)
        center.complete(latest, SoundEffectNoticeResult(SoundEffectNoticePhase.READY, "音效已生效"))
        advanceTimeBy(3601); runCurrent()
        assertNull(center.state.value)
    }
    @Test fun cancellationClearsPendingNoticeAndRejectsLateSuccess() = runTest {
        val center = SoundEffectNoticeCenter(backgroundScope)
        val id = center.begin("音效", SoundEffectNoticeTier.STANDARD)
        center.clear()
        center.complete(id, SoundEffectNoticeResult(SoundEffectNoticePhase.READY, "音效已生效"))
        assertNull(center.state.value)
    }
    @Test fun loadingIsNeverSuccessAndPausedOrMissingEngineRemainTruthful() {
        assertNull(originalEffectNoticeResult(true, false, true, false, "正在下载"))
        assertEquals("已开启，播放后生效", originalEffectNoticeResult(false, true, true, false, "已就绪")?.message)
        assertEquals("音效已生效", originalEffectNoticeResult(false, true, true, true, "正在使用原版引擎")?.message)
        assertEquals(SoundEffectNoticePhase.WAITING,
            originalEffectNoticeResult(false, false, true, false, "请先导入原版引擎")?.phase)
        assertEquals(SoundEffectNoticePhase.FAILED,
            originalEffectNoticeResult(false, false, false, false, "当前账号没有权益")?.phase)
        assertEquals(SoundEffectNoticeTier.SVIP, SoundEffectNoticeTier.fromMembership(4))
        assertEquals(SoundEffectNoticeTier.VIP, SoundEffectNoticeTier.fromMembership(3))
        assertEquals(SoundEffectNoticeTier.STANDARD, SoundEffectNoticeTier.fromMembership(0))
    }
}

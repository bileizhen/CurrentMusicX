package io.github.currencortex.music.core.update

import io.github.currencortex.music.core.media.*
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateIslandNoticeTest {
    @Test fun effectTemporarilyInterruptsDownloadThenRestoresItsLatestState() = runTest {
        val download = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
        val enabled = MutableStateFlow(true)
        val controller = UpdateIslandNotice(download, enabled, backgroundScope)
        val effects = SoundEffectNoticeCenter(backgroundScope)
        val visible = prioritizedIslandNotices(controller.state, effects.state, backgroundScope)
        runCurrent()
        download.value = UpdateDownloadState.Downloading(1, 100); runCurrent()
        val id = visible.value!!.id
        advanceTimeBy(45_000); runCurrent()
        val token = effects.begin("原声黑胶", SoundEffectNoticeTier.SVIP); runCurrent()
        assertEquals(IslandNoticeKind.SOUND_EFFECT, visible.value!!.kind)
        assertEquals("原声黑胶", visible.value!!.name)
        download.value = UpdateDownloadState.Downloading(99, 100); runCurrent()
        assertEquals("原声黑胶", visible.value!!.name)
        effects.complete(token, SoundEffectNoticeResult(SoundEffectNoticePhase.READY, "音效已生效")); runCurrent()
        advanceTimeBy(3_601); runCurrent()
        assertEquals(id, visible.value!!.id)
        assertEquals(99, visible.value!!.progress)
        download.value = UpdateDownloadState.Ready(File("verified.apk")); runCurrent()
        assertEquals("下载完成", visible.value!!.name)
        assertEquals("CurrentMusic 更新已校验，点击查看", visible.value!!.message)
        advanceTimeBy(120_000); runCurrent()
        assertTrue(visible.value.isUpdateInstallNotice())
        val next = effects.begin("全景环绕", SoundEffectNoticeTier.VIP); runCurrent()
        assertEquals("全景环绕", visible.value!!.name)
        effects.complete(next, SoundEffectNoticeResult(SoundEffectNoticePhase.READY, "音效已生效")); runCurrent()
        advanceTimeBy(3_601); runCurrent()
        assertTrue(visible.value.isUpdateInstallNotice())
        download.value = UpdateDownloadState.Idle; runCurrent()
        advanceTimeBy(1_801); runCurrent()
        assertNull(visible.value)
    }

    @Test fun failedEffectReturnsToVerifiedDownloadWithoutClearingItsInstallAction() = runTest {
        val controller = UpdateIslandNotice(MutableStateFlow(UpdateDownloadState.Ready(File("verified.apk"))),
            MutableStateFlow(true), backgroundScope)
        val effects = SoundEffectNoticeCenter(backgroundScope)
        val visible = prioritizedIslandNotices(controller.state, effects.state, backgroundScope)
        runCurrent()
        val completed = visible.value
        val token = effects.begin("AI 调音大师", SoundEffectNoticeTier.SVIP); runCurrent()
        effects.complete(token, SoundEffectNoticeResult(SoundEffectNoticePhase.FAILED, "网络异常")); runCurrent()
        assertEquals(SoundEffectNoticePhase.FAILED, visible.value!!.phase)
        assertFalse(visible.value.isUpdateInstallNotice())
        advanceTimeBy(5_001); runCurrent()
        assertEquals(completed, visible.value)
        assertTrue(visible.value.isUpdateInstallNotice())
    }

    @Test fun enablingIslandRestoresTheVerifiedDownloadAndDisablingHidesIt() = runTest {
        val download = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Downloading(1, 100))
        val enabled = MutableStateFlow(false)
        val controller = UpdateIslandNotice(download, enabled, backgroundScope)
        runCurrent(); assertNull(controller.state.value)
        enabled.value = true; runCurrent(); assertEquals(1, controller.state.value!!.progress)
        enabled.value = false; runCurrent(); assertNull(controller.state.value)
        download.value = UpdateDownloadState.Ready(File("verified.apk")); runCurrent()
        enabled.value = true; runCurrent(); assertTrue(controller.state.value.isUpdateInstallNotice())
        enabled.value = false; runCurrent(); assertNull(controller.state.value)
    }

    @Test fun completedDownloadIsRestoredWithoutANewDownloadEvent() = runTest {
        val controller = UpdateIslandNotice(MutableStateFlow(UpdateDownloadState.Ready(File("verified.apk"))),
            MutableStateFlow(true), backgroundScope)
        runCurrent(); assertTrue(controller.state.value.isUpdateInstallNotice())
        advanceTimeBy(120_000); runCurrent(); assertTrue(controller.state.value.isUpdateInstallNotice())
    }

    @Test fun retryCancelsOldFailureExpiryAndCancellationReturnsToNormal() = runTest {
        val download = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Downloading(0, 0))
        val controller = UpdateIslandNotice(download, MutableStateFlow(true), backgroundScope)
        runCurrent(); assertNull(controller.state.value!!.progress)
        download.value = UpdateDownloadState.Failed("网络连接失败"); runCurrent()
        assertEquals(SoundEffectNoticePhase.FAILED, controller.state.value!!.phase)
        advanceTimeBy(4_900)
        download.value = UpdateDownloadState.Downloading(1, 10); runCurrent()
        advanceTimeBy(200); runCurrent()
        assertEquals(SoundEffectNoticePhase.APPLYING, controller.state.value!!.phase)
        download.value = UpdateDownloadState.Idle; runCurrent()
        assertEquals("已取消下载", controller.state.value!!.name)
        advanceTimeBy(1_801); runCurrent(); assertNull(controller.state.value)
    }
}

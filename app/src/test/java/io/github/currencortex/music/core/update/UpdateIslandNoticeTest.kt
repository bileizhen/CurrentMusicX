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
    @Test fun verifiedDownloadKeepsPriorityUntilExplicitlyCleared() = runTest {
        val download = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
        val enabled = MutableStateFlow(true)
        val controller = UpdateIslandNotice(download, enabled, backgroundScope)
        val effects = MutableStateFlow<SoundEffectNotice?>(null)
        val visible = prioritizedIslandNotices(controller.state, effects, backgroundScope)
        runCurrent()
        download.value = UpdateDownloadState.Downloading(1, 100); runCurrent()
        val id = visible.value!!.id
        advanceTimeBy(45_000); runCurrent()
        effects.value = SoundEffectNotice(9, "原声黑胶", SoundEffectNoticeTier.SVIP); runCurrent()
        assertEquals(IslandNoticeKind.UPDATE_DOWNLOAD, visible.value!!.kind)
        download.value = UpdateDownloadState.Downloading(99, 100); runCurrent()
        assertEquals(id, visible.value!!.id)
        assertEquals(99, visible.value!!.progress)
        download.value = UpdateDownloadState.Ready(File("verified.apk")); runCurrent()
        assertEquals("下载完成", visible.value!!.name)
        advanceTimeBy(120_000); runCurrent()
        assertTrue(visible.value.isUpdateInstallNotice())
        download.value = UpdateDownloadState.Idle; runCurrent()
        advanceTimeBy(1_801); runCurrent()
        assertEquals(effects.value, visible.value)
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

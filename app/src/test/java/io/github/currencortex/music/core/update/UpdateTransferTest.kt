package io.github.currencortex.music.core.update

import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class UpdateTransferTest {
    private val release = AppRelease("1.2.0", "notes", "https://github.com/example/app", null, null, false, 10, "0".repeat(64))
    private class Installer : UpdateInstall {
        var permission = false
        var requests = 0
        override fun canInstall() = permission
        override suspend fun request(file: File, release: AppRelease): UpdateInstallResult {
            requests++
            return if (permission) UpdateInstallResult.STARTED else UpdateInstallResult.PERMISSION_REQUIRED
        }
    }
    @Test fun downloadsAreDeduplicatedAndCancelResetsProgress() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var calls = 0
        val downloader = UpdateDownload { _, _, progress -> calls++; progress(4, 10); awaitCancellation() }
        val transfer = UpdateTransfer(downloader, Installer(), scope)
        try {
            transfer.selectRelease(release)
            transfer.download(UpdateSource.GITHUB)
            transfer.download(UpdateSource.GITHUB)
            assertEquals(1, calls)
            assertEquals(UpdateDownloadState.Downloading(4, 10), transfer.state.value.download)
            transfer.cancel()
            assertEquals(UpdateDownloadState.Idle, transfer.state.value.download)
        } finally { scope.cancel() }
    }
    @Test fun changingReleaseCannotKeepAnOldDownloadResult() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val downloader = UpdateDownload { _, _, progress -> progress(3, 10); awaitCancellation() }
        val transfer = UpdateTransfer(downloader, Installer(), scope)
        try {
            transfer.selectRelease(release)
            transfer.download(UpdateSource.GITHUB)
            transfer.selectRelease(release.copy(version = "1.3.0"))
            assertEquals(UpdateDownloadState.Idle, transfer.state.value.download)
            transfer.download(UpdateSource.GITHUB)
            assertEquals(UpdateDownloadState.Downloading(3, 10), transfer.state.value.download)
        } finally { scope.cancel() }
    }
    @Test fun installRequiresExplicitActionAndResumesAfterPermissionIsGranted() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val installer = Installer()
        val file = File("verified.apk")
        val transfer = UpdateTransfer(UpdateDownload { _, _, _ -> file }, installer, scope)
        try {
            transfer.selectRelease(release)
            transfer.download(UpdateSource.GITHUB)
            assertEquals(0, installer.requests)
            assertEquals(UpdateDownloadState.Ready(file), transfer.state.value.download)
            transfer.install()
            assertTrue(transfer.state.value.permissionRequired)
            transfer.onResume()
            assertEquals(1, installer.requests)
            installer.permission = true
            transfer.onResume()
            assertEquals(2, installer.requests)
            assertFalse(transfer.state.value.permissionRequired)
        } finally { scope.cancel() }
    }
}

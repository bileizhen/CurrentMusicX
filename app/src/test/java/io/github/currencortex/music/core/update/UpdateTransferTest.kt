package io.github.currencortex.music.core.update

import java.io.File
import java.io.IOException
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

    @Test fun switchingSourcesResetsProgressAndNeverInstallsAutomatically() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val calls = mutableListOf<UpdateSource>()
        val installer = Installer()
        lateinit var transfer: UpdateTransfer
        val downloader = UpdateDownload { _, source, progress ->
            calls += source
            assertEquals(0, (transfer.state.value.download as UpdateDownloadState.Downloading).received)
            assertEquals(source, transfer.state.value.activeSource)
            progress(4, 10)
            if (source != UpdateSource.GITHUB) throw IOException("Mirror unavailable")
            File("verified.apk")
        }
        transfer = UpdateTransfer(downloader, installer, scope)
        try {
            transfer.selectRelease(release)
            transfer.download(UpdateSource.DPIK)
            assertEquals(UpdateSource.DPIK.fallbacks(), calls)
            assertEquals(UpdateDownloadState.Ready(File("verified.apk")), transfer.state.value.download)
            assertEquals(calls.size, transfer.state.value.attempt)
            assertEquals(0, installer.requests)
        } finally { scope.cancel() }
    }

    @Test fun exhaustedSourcesFailOnceWithUsefulReasonsAndAllowRetry() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var calls = 0
        val transfer = UpdateTransfer(UpdateDownload { _, _, _ -> calls++; throw IOException("HTTP 429") }, Installer(), scope)
        try {
            transfer.selectRelease(release)
            transfer.download(UpdateSource.GEEKERTAO)
            assertEquals(UpdateSource.GEEKERTAO.fallbacks().size, calls)
            val failure = transfer.state.value.download as UpdateDownloadState.Failed
            UpdateSource.available.forEach { assertTrue(failure.message.contains(it.label)) }
            assertTrue(failure.message.contains("HTTP 429"))
            transfer.download(UpdateSource.GITHUB)
            assertEquals(UpdateSource.GEEKERTAO.fallbacks().size + UpdateSource.GITHUB.fallbacks().size, calls)
        } finally { scope.cancel() }
    }

    @Test fun invalidMetadataDoesNotRetryEveryMirror() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var calls = 0
        val transfer = UpdateTransfer(UpdateDownload { _, _, _ -> calls++; throw IllegalArgumentException("安装包校验信息无效") }, Installer(), scope)
        try {
            transfer.selectRelease(release)
            transfer.download(UpdateSource.DPIK)
            assertEquals(1, calls)
            assertEquals(UpdateDownloadState.Failed("安装包校验信息无效"), transfer.state.value.download)
        } finally { scope.cancel() }
    }

    @Test fun cancellingNetworkRequestDoesNotStartAnotherSource() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var calls = 0
        val downloader = UpdateDownload { _, _, _ ->
            calls++
            try { awaitCancellation() }
            catch (_: CancellationException) { throw IOException("Canceled") }
        }
        val transfer = UpdateTransfer(downloader, Installer(), scope)
        try {
            transfer.selectRelease(release)
            transfer.download(UpdateSource.GEEKERTAO)
            transfer.cancel()
            assertEquals(1, calls)
            assertEquals(UpdateDownloadState.Idle, transfer.state.value.download)
        } finally { scope.cancel() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun lateCancelledRequestCannotOverwriteRetryForTheSameRelease() = kotlinx.coroutines.test.runTest {
        val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
        val finishOld = CompletableDeferred<Unit>()
        var calls = 0
        val transfer = UpdateTransfer(UpdateDownload { _, _, progress ->
            if (++calls == 1) withContext(NonCancellable) { finishOld.await(); progress(10, 10); File("old.apk") }
            else { progress(2, 10); awaitCancellation() }
        }, Installer(), scope)
        try {
            transfer.selectRelease(release)
            transfer.download(UpdateSource.GEEKERTAO)
            transfer.cancel()
            transfer.download(UpdateSource.GITHUB)
            finishOld.complete(Unit)
            testScheduler.runCurrent()
            assertEquals(2, calls)
            assertEquals(UpdateDownloadState.Downloading(2, 10), transfer.state.value.download)
            assertEquals(UpdateSource.GITHUB, transfer.state.value.activeSource)
        } finally { finishOld.complete(Unit); scope.cancel() }
    }
}

package io.github.currencortex.music.core.update

import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(val received: Long, val total: Long) : UpdateDownloadState
    data class Ready(val file: File) : UpdateDownloadState
    data class Failed(val message: String) : UpdateDownloadState
}
data class UpdateTransferState(val download: UpdateDownloadState = UpdateDownloadState.Idle,
    val installing: Boolean = false, val permissionRequired: Boolean = false, val message: String? = null,
    val activeSource: UpdateSource? = null, val attempt: Int = 0, val totalSources: Int = 0)

class UpdateTransfer(private val downloader: UpdateDownload, private val installer: UpdateInstall,
                     private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(UpdateTransferState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var release: AppRelease? = null
    private var generation = 0

    fun selectRelease(value: AppRelease) {
        if (release == value) return
        generation++
        job?.cancel()
        job = null
        release = value
        mutable.value = UpdateTransferState()
    }
    fun download(source: UpdateSource) {
        val value = release ?: return
        if (job?.isActive == true || mutable.value.installing || mutable.value.download is UpdateDownloadState.Ready) return
        val request = ++generation
        val sources = source.fallbacks()
        mutable.value = UpdateTransferState(UpdateDownloadState.Downloading(0, value.size),
            activeSource = sources.first(), attempt = 1, totalSources = sources.size)
        job = scope.launch {
            try {
                val failures = mutableListOf<String>()
                for ((index, candidate) in sources.withIndex()) {
                    ensureActive()
                    if (generation != request || release != value) return@launch
                    mutable.update { it.copy(download = UpdateDownloadState.Downloading(0, value.size),
                        activeSource = candidate, attempt = index + 1) }
                    try {
                        val file = downloader.download(value, candidate) { received, total ->
                            if (release == value && generation == request) mutable.update {
                                it.copy(download = UpdateDownloadState.Downloading(received, total)) }
                        }
                        if (release == value && generation == request) mutable.update { it.copy(download = UpdateDownloadState.Ready(file)) }
                        return@launch
                    } catch (error: IOException) {
                        ensureActive()
                        failures += "${candidate.label}：${error.message?.take(120) ?: "连接失败"}"
                    }
                }
                if (release == value && generation == request) mutable.update {
                    it.copy(download = UpdateDownloadState.Failed("所有下载源均失败，请稍后重试。\n" + failures.joinToString("\n"))) }
            } catch (cancelled: CancellationException) {
                if (release == value && generation == request) mutable.value = UpdateTransferState()
                throw cancelled
            } catch (error: Exception) {
                if (release == value && generation == request) mutable.update { it.copy(download = UpdateDownloadState.Failed(error.message ?: "下载失败，请重试")) }
            }
        }
    }
    fun cancel() { job?.cancel() }
    fun install() {
        val value = release ?: return
        val file = (mutable.value.download as? UpdateDownloadState.Ready)?.file ?: return
        if (mutable.value.installing) return
        mutable.update { it.copy(installing = true, message = null) }
        scope.launch {
            try {
                when (installer.request(file, value)) {
                    UpdateInstallResult.STARTED -> mutable.update { it.copy(permissionRequired = false, message = "已请求系统安装") }
                    UpdateInstallResult.PERMISSION_REQUIRED -> mutable.update { it.copy(permissionRequired = true,
                        message = "请允许安装未知应用，返回后继续请求安装") }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutable.update { it.copy(message = error.message ?: "无法请求系统安装", permissionRequired = false) } }
            finally { mutable.update { it.copy(installing = false) } }
        }
    }
    fun onResume() { if (mutable.value.permissionRequired && installer.canInstall()) install() }
}

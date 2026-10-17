package io.github.currencortex.music.core.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import io.github.currencortex.music.MainActivity
import io.github.currencortex.music.core.network.ApiJson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import java.io.File

/** WorkManager persists the selected public asset and task across activity/process recreation. */
class WorkManagerUpdateBackground(context: Context) : UpdateBackground {
    private val work = WorkManager.getInstance(context.applicationContext)
    override val events: Flow<UpdateBackgroundEvent> = work.getWorkInfosForUniqueWorkFlow(NAME).mapNotNull { tasks ->
        val task = tasks.maxByOrNull { info -> info.tags.firstOrNull { it.startsWith(CREATED) }?.removePrefix(CREATED)?.toLongOrNull() ?: 0L }
            ?: return@mapNotNull null
        val release = task.tags.firstOrNull { it.startsWith(ASSET) }?.removePrefix(ASSET)?.let {
            runCatching { ApiJson.decodeFromString<AppRelease>(it) }.getOrNull()
        } ?: return@mapNotNull null
        val installed = Version.parse(io.github.currencortex.music.BuildConfig.VERSION_NAME)
        val target = Version.parse(release.version)
        if (installed != null && target != null && target <= installed) return@mapNotNull null
        val data = if (task.state.isFinished) task.outputData else task.progress
        val download = when (task.state) {
            WorkInfo.State.SUCCEEDED -> data.getString("file")?.let(::File)?.takeIf { it.isFile }?.let(UpdateDownloadState::Ready)
                ?: UpdateDownloadState.Failed("安装包缓存已清理，请重新下载")
            WorkInfo.State.FAILED -> UpdateDownloadState.Failed(data.getString("error") ?: "下载失败，请重试")
            WorkInfo.State.CANCELLED -> UpdateDownloadState.Idle
            else -> UpdateDownloadState.Downloading(data.getLong("received", 0), release.size)
        }
        UpdateBackgroundEvent(task.id.toString(), release, UpdateTransferState(download,
            activeSource = data.getString("source")?.let { runCatching { UpdateSource.valueOf(it) }.getOrNull() },
            attempt = data.getInt("attempt", 1), totalSources = UpdateSource.available.size))
    }.distinctUntilChanged()

    override fun start(release: AppRelease, source: UpdateSource): String {
        // Notes can exceed WorkManager's 10 KiB Data limit. Only public asset identity is needed to resume.
        val compact = ApiJson.encodeToString(release.copy(notes = ""))
        val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
            .setInputData(workDataOf("release" to compact, "source" to source.name))
            .addTag(ASSET + compact).addTag(CREATED + System.currentTimeMillis())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
        work.enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
        return request.id.toString()
    }
    override fun cancel() { work.cancelUniqueWork(NAME) }
    companion object {
        const val NAME = "app-update-download"
        private const val ASSET = "update-asset:"
        private const val CREATED = "update-created:"
        const val OPEN = "open_update_download"
    }
}

/** Shares the same HTTPS, fallback, size and SHA-256 checks as foreground downloads. */
open class UpdateDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private fun release() = ApiJson.decodeFromString<AppRelease>(requireNotNull(inputData.getString("release")))
    override suspend fun getForegroundInfo() = foreground(release(), UpdateTransferState())

    protected open fun downloader(): UpdateDownload = UpdateDownloader(okhttp3.OkHttpClient(),
        File(applicationContext.cacheDir, "updates/$id"))

    override suspend fun doWork(): Result = coroutineScope {
        val release = try { release() } catch (_: Exception) { return@coroutineScope Result.failure() }
        val source = inputData.getString("source")?.let { runCatching { UpdateSource.valueOf(it) }.getOrNull() } ?: UpdateSource.default
        var last = UpdateTransferState()
        try {
            applicationContext.getSystemService(NotificationManager::class.java).cancel(COMPLETE)
            setForeground(foreground(release, last))
            val transfer = UpdateTransfer(downloader(), object : UpdateInstall {
                override fun canInstall() = false
                override suspend fun request(file: File, release: AppRelease) = error("Installation requires a user action")
            }, this)
            transfer.selectRelease(release); transfer.download(source)
            var checkpoint = 0L
            transfer.state.first { state ->
                last = state
                val now = android.os.SystemClock.elapsedRealtime()
                if (state.download !is UpdateDownloadState.Downloading || now - checkpoint >= 500) {
                    checkpoint = now
                    setProgress(progress(state)); setForeground(foreground(release, state))
                }
                state.download is UpdateDownloadState.Ready || state.download is UpdateDownloadState.Failed
            }
            when (val result = last.download) {
                is UpdateDownloadState.Ready -> {
                    completeNotification(release, true)
                    Result.success(progress(last, "file" to result.file.absolutePath))
                }
                is UpdateDownloadState.Failed -> {
                    completeNotification(release, false)
                    Result.failure(progress(last, "error" to result.message))
                }
                else -> Result.failure()
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(workDataOf("error" to (error.message ?: "下载失败，请重试"))) }
    }

    private fun progress(state: UpdateTransferState, vararg extra: Pair<String, Any?>): Data {
        val download = state.download as? UpdateDownloadState.Downloading
        return workDataOf("received" to (download?.received ?: 0L), "source" to state.activeSource?.name,
            "attempt" to state.attempt, *extra)
    }
    private fun openIntent(): PendingIntent = PendingIntent.getActivity(applicationContext, 420,
        Intent(applicationContext, MainActivity::class.java).putExtra(WorkManagerUpdateBackground.OPEN, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun notification(release: AppRelease): NotificationCompat.Builder {
        applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "应用更新", NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat.Builder(applicationContext, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("CurrentMusic v${release.version}").setContentIntent(openIntent()).setOnlyAlertOnce(true)
    }
    private fun foreground(release: AppRelease, state: UpdateTransferState): ForegroundInfo {
        val download = state.download as? UpdateDownloadState.Downloading
        val percent = ((download?.received ?: 0L).toDouble() / release.size.coerceAtLeast(1) * 100).toInt().coerceIn(0, 100)
        val notification = notification(release).setContentText("${state.activeSource?.label ?: "等待网络"} · $percent%")
            .setProgress(100, percent, download == null).setOngoing(true)
            .addAction(0, "取消下载", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(FOREGROUND, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(FOREGROUND, notification)
    }
    private fun completeNotification(release: AppRelease, success: Boolean) {
        val notification = notification(release).setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentText(if (!success) "下载失败，点击查看并重试" else "下载完成，点击查看").setAutoCancel(true).build()
        runCatching { applicationContext.getSystemService(NotificationManager::class.java).notify(COMPLETE, notification) }
    }
    companion object {
        private const val CHANNEL = "app-updates"
        private const val FOREGROUND = 4201
        private const val COMPLETE = 4202
    }
}

package io.github.currencortex.music.core.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import io.github.currencortex.music.CurrentMusicApplication
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import java.io.File

open class MvDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val song = ApiJson.decodeFromString<Song>(requireNotNull(inputData.getString("song")))
        setForeground(notification(song, SongDownloadProgress("准备下载 MV")))
        val result = createEngine().download(song, inputData.getInt("resolution", 1080),
            inputData.getString("directory")?.let(Uri::parse), id.toString()) { progress ->
            setProgress(workDataOf("phase" to progress.phase, "received" to progress.received, "total" to progress.total))
            setForeground(notification(song, progress))
        }
        Result.success(workDataOf("uri" to result.saved.audioUri.toString(), "name" to result.saved.name, "height" to result.height))
    } catch (e: CancellationException) { throw e }
      catch (e: Exception) { Result.failure(workDataOf("error" to (e.message ?: "MV 下载失败，请重试").take(300))) }

    protected open suspend fun createEngine(): MvDownloadEngine {
        val container = (applicationContext as CurrentMusicApplication).awaitContainer()
        container.ready.await(); container.sessionRestored.await()
        val session = container.musicSession()
        fun verify() {
            check(inputData.getLong("account", -1) == (container.accountRepository.state.value.account?.id ?: 0L) &&
                inputData.getString("server") == session.server && session == container.musicSession() &&
                (inputData.getLong("netease_uid", -1) < 0 || inputData.getLong("netease_uid", -1) ==
                    (container.neteaseSessions.state.value.profile?.uid ?: 0L))) { "账号已变化，请重新开始 MV 下载" }
        }
        verify()
        return MvDownloadEngine(File(applicationContext.cacheDir, "mv-downloads"), container.libraryRepository::mvSource,
            SongDownloadStore(applicationContext), ::verify)
    }
    private fun notification(song: Song, progress: SongDownloadProgress): ForegroundInfo {
        applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("mv-downloads", "MV 下载", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, "mv-downloads")
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(song.name).setContentText(progress.phase)
            .setOnlyAlertOnce(true).setOngoing(true).setProgress(100,
                if (progress.total > 0) (progress.received.toDouble() / progress.total * 100).toInt().coerceIn(0, 100) else 0,
                progress.total <= 0)
            .addAction(0, "取消", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
        val notificationId = 0x50000000 or (id.hashCode() and 0x0fffffff)
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(notificationId, notification)
    }
}

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
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.core.network.RequestSession
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import java.io.File

open class SongDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val song = try { ApiJson.decodeFromString<Song>(inputData.getString("song") ?: return Result.failure()) }
            catch (_: Exception) { return Result.failure(workDataOf("error" to "歌曲信息无效")) }
        return try {
            setForeground(notification(song, SongDownloadProgress("准备下载")))
            val engine = createEngine()
            val result = engine.download(song, AudioQuality.from(inputData.getString("quality").orEmpty()),
                inputData.getString("directory")?.let(Uri::parse), id.toString()) { progress ->
                setProgress(workDataOf("phase" to progress.phase, "received" to progress.received, "total" to progress.total))
                setForeground(notification(song, progress))
            }
            Result.success(workDataOf("uri" to result.saved.audioUri.toString(), "name" to result.saved.name,
                "warning" to result.warning, "quality" to result.quality,
                "files" to result.saved.files.map(Uri::toString).toTypedArray()))
        } catch (e: CancellationException) { throw e }
          catch (e: Exception) { Result.failure(workDataOf("error" to (e.message ?: "下载失败，请重试").take(300))) }
    }
    protected open suspend fun createEngine(): SongDownloadEngine {
        val container = (applicationContext as CurrentMusicApplication).awaitContainer()
        container.ready.await(); container.sessionRestored.await()
        fun verifySession() {
            check(inputData.getLong("account", -1) == (container.accountRepository.state.value.account?.id ?: 0L) &&
                inputData.getString("server") == container.accountRepository.server &&
                inputData.getString("provider") == container.audioSettings.access().identity) {
                "账号或音源已变化，请重新开始下载"
            }
        }
        verifySession()
        val session = RequestSession(container.accountRepository.server, container.accountRepository.token)
        return SongDownloadEngine(File(applicationContext.cacheDir, "song-downloads"),
            resolve = { track, quality -> container.musicRepository.source(track.id, quality, session) },
            detail = { track -> container.musicRepository.detail(track.id) },
            lyrics = { track -> container.lyricsRepository.load(track).document },
            store = SongDownloadStore(applicationContext),
            guard = { verifySession() })
    }
    private fun notification(song: Song, progress: SongDownloadProgress): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("song-downloads", "歌曲下载", NotificationManager.IMPORTANCE_LOW))
        val percent = if (progress.total > 0) (progress.received.toDouble() / progress.total * 100).toInt().coerceIn(0, 100) else 0
        val notification = NotificationCompat.Builder(applicationContext, "song-downloads")
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(song.name)
            .setContentText(progress.phase).setOnlyAlertOnce(true).setOngoing(true)
            .setProgress(100, percent, progress.total <= 0)
            .addAction(0, "取消", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
        val notificationId = 0x40000000 or (id.hashCode() and 0x0fffffff)
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(notificationId, notification)
    }
}

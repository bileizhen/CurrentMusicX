package io.github.currencortex.music.core.download

import android.content.Context
import androidx.work.*
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.data.song.Song
import kotlinx.serialization.encodeToString

class MvDownloadManager(context: Context, private val folders: SongDownloadManager) {
    private val context = context.applicationContext
    private val work get() = WorkManager.getInstance(context)
    val directory get() = folders.directory
    fun setDirectory(uri: android.net.Uri) = folders.setDirectory(uri)
    private fun key(song: Song) = "mv-download-${song.mv}"
    fun observe(song: Song) = work.getWorkInfosForUniqueWorkFlow(key(song))
    fun start(song: Song, resolution: Int, account: Long, server: String, neteaseUid: Long) {
        require(song.video && song.mv > 0)
        val request = OneTimeWorkRequestBuilder<MvDownloadWorker>().setInputData(workDataOf(
            "song" to ApiJson.encodeToString(song), "resolution" to resolution,
            "directory" to directory.value?.toString(), "account" to account, "server" to server, "netease_uid" to neteaseUid))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag("mv-download").build()
        work.enqueueUniqueWork(key(song), ExistingWorkPolicy.KEEP, request)
    }
    fun cancel(song: Song) { work.cancelUniqueWork(key(song)) }
}

package io.github.currencortex.music.core.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.*
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.encodeToString

class SongDownloadManager(context: Context, namespace: String = "") {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences("song-downloads$namespace", Context.MODE_PRIVATE)
    private val work get() = WorkManager.getInstance(context)
    val directory = MutableStateFlow(preferences.getString("directory", null)?.let(Uri::parse))
    private val hidden = MutableStateFlow(preferences.getStringSet("hidden", emptySet()).orEmpty().toSet())
    fun setDirectory(uri: Uri?) {
        if (uri != null) context.contentResolver.takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        check(preferences.edit().putString("directory", uri?.toString()).commit()) { "文件夹设置保存失败" }
        directory.value = uri
    }
    private fun key(song: Song) = "song-download-${song.musicSource.name}-${song.id}"
    fun observe(song: Song) = combine(work.getWorkInfosForUniqueWorkFlow(key(song)), hidden) { items, removed ->
        items.lastOrNull { it.id.toString() !in removed }
    }
    fun start(song: Song, quality: AudioQuality, accountId: Long, server: String, provider: String, neteaseUid: Long = -1) {
        val request = OneTimeWorkRequestBuilder<SongDownloadWorker>()
            .setInputData(workDataOf("song" to ApiJson.encodeToString(song), "quality" to quality.value,
                "directory" to directory.value?.toString(), "account" to accountId, "server" to server, "provider" to provider,
                "netease_uid" to neteaseUid))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag("song-download").build()
        work.enqueueUniqueWork(key(song), ExistingWorkPolicy.KEEP, request)
    }
    fun cancel(song: Song) { work.cancelUniqueWork(key(song)) }
    /** Hide only the deleted download. Pruning WorkManager would erase every completed song. */
    @Synchronized fun hideCompleted(id: java.util.UUID) {
        val next = hidden.value + id.toString()
        check(preferences.edit().putStringSet("hidden", next).commit()) { "下载记录更新失败" }
        hidden.value = next
    }
}

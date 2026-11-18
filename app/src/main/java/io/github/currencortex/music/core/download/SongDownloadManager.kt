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
import java.security.MessageDigest

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
    fun ownerKey(song: Song, accountId: Long, server: String, provider: String, revision: String = ""): String {
        if (song.musicSource != io.github.currencortex.music.data.song.MusicSource.QQ_MUSIC) return ""
        // Keep showing the account's current download when the selected LX plugin changes.
        val accountIdentity = provider.substringBefore(":lx:")
        val raw = "$accountId\n${server.trimEnd('/')}\n$accountIdentity\n$revision"
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
    private fun key(song: Song, owner: String = "") =
        "song-download-${song.musicSource.name}-${song.id}" + owner.takeIf(String::isNotBlank)?.let { "-$it" }.orEmpty()
    fun observe(song: Song, owner: String = "") = combine(work.getWorkInfosForUniqueWorkFlow(key(song, owner)), hidden) { items, removed ->
        items.lastOrNull { it.id.toString() !in removed }
    }
    fun start(song: Song, quality: AudioQuality, accountId: Long, server: String, provider: String, neteaseUid: Long = -1, navidromeRevision: String = "") {
        val request = OneTimeWorkRequestBuilder<SongDownloadWorker>()
            .setInputData(workDataOf("song" to ApiJson.encodeToString(song), "quality" to quality.value,
                "directory" to directory.value?.toString(), "account" to accountId, "server" to server, "provider" to provider,
                "netease_uid" to neteaseUid, "navidrome_revision" to navidromeRevision))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag("song-download").build()
        val owner = ownerKey(song, accountId, server, provider, navidromeRevision)
        work.enqueueUniqueWork(key(song, owner), ExistingWorkPolicy.KEEP, request)
    }
    fun cancel(song: Song, owner: String = "") { work.cancelUniqueWork(key(song, owner)) }
    /** Hide only the deleted download. Pruning WorkManager would erase every completed song. */
    @Synchronized fun hideCompleted(id: java.util.UUID) {
        val next = hidden.value + id.toString()
        check(preferences.edit().putStringSet("hidden", next).commit()) { "下载记录更新失败" }
        hidden.value = next
    }
}

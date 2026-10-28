package io.github.currencortex.music.feature.library

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun localSongId(uri: String): Long {
    var hash = -3750763034362895579L
    uri.toByteArray(Charsets.UTF_8).forEach { hash = (hash xor (it.toLong() and 255)) * 1099511628211L }
    return -1L - (hash and Long.MAX_VALUE)
}

internal fun scanLocalMusic(context: Context): List<Song> {
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val columns = mutableListOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DISPLAY_NAME)
    if (Build.VERSION.SDK_INT >= 29) columns.add(MediaStore.MediaColumns.VOLUME_NAME)
    return context.contentResolver.query(collection, columns.toTypedArray(), "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
        "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { cursor ->
        val id = cursor.getColumnIndexOrThrow(columns[0]); val title = cursor.getColumnIndexOrThrow(columns[1])
        val artist = cursor.getColumnIndexOrThrow(columns[2]); val album = cursor.getColumnIndexOrThrow(columns[3])
        val duration = cursor.getColumnIndexOrThrow(columns[4]); val albumId = cursor.getColumnIndexOrThrow(columns[5])
        val display = cursor.getColumnIndexOrThrow(columns[6]); val items = mutableListOf<Song>()
        while (cursor.moveToNext()) {
            val rawId = cursor.getLong(id)
            if (rawId < 0 || rawId == Long.MAX_VALUE) continue
            fun text(column: Int) = cursor.getString(column).orEmpty().takeUnless { it == "<unknown>" }.orEmpty()
            val name = text(title).ifBlank { text(display).substringBeforeLast('.') }.ifBlank { "未知歌曲" }
            val volume = if (Build.VERSION.SDK_INT >= 29) cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.VOLUME_NAME)).orEmpty() else ""
            val base = if (volume.isNotBlank()) MediaStore.Audio.Media.getContentUri(volume) else collection
            val uri = ContentUris.withAppendedId(base, rawId).toString()
            val artId = cursor.getLong(albumId)
            val cover = if (artId > 0) "content://media/${volume.ifBlank { "external" }}/audio/albumart/$artId" else ""
            items += Song(id = localSongId(uri), name = name, artists = text(artist), album = text(album),
                cover = cover, durationMs = cursor.getLong(duration).coerceAtLeast(0), musicSource = MusicSource.LOCAL, localUri = uri)
        }
        items
    } ?: error("系统未返回音乐列表")
}

/** Only scanning and Android permissions are local-specific; the page itself is the playlist page. */
@Composable fun LocalMusicScreen(vm: LibraryDetailViewModel, library: LibraryViewModel,
    onBack: () -> Unit, navigate: (String) -> Unit, play: (List<Song>, Int) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(lifecycle, permission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                if (!granted && allowed) { songs = emptyList(); error = "音乐读取权限已关闭" }
                allowed = granted
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    fun refresh() {
        if (scanning || !allowed) return
        scope.launch {
            scanning = true; error = null
            try {
                val found = withContext(Dispatchers.IO) { scanLocalMusic(context.applicationContext) }
                if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) songs = found
                else { allowed = false; songs = emptyList(); error = "音乐读取权限已关闭" }
            }
            catch (e: CancellationException) { throw e }
            catch (e: SecurityException) { allowed = false; songs = emptyList(); error = "音乐读取权限已关闭" }
            catch (e: Exception) { error = "扫描失败，请重试" }
            finally { scanning = false }
        }
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted; error = if (granted) null else "未获得音频权限，可在系统设置中开启。"
    }
    LaunchedEffect(allowed) { if (allowed) refresh() }
    PlaylistDetailBody(
        LibraryDetailState(title = "本地音乐", description = "手机里的音乐 · 下拉重新扫描",
            songs = songs, loading = scanning && songs.isEmpty(), refreshing = scanning && songs.isNotEmpty(),
            error = error.takeIf { allowed }, loaded = allowed),
        vm, library, onBack, navigate, play, onRename = {}, onDelete = {},
        localMusic = LocalMusicPlaylistActions(allowed, ::refresh, { request.launch(permission) }, {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:${context.packageName}")))
        }, error))
}
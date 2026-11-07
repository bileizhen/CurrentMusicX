package io.github.currencortex.music.ui.component

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.core.storage.DocumentExportFiles
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.player.importLyricsDocument
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.OutputStream
import java.util.UUID

/** Hosted above dialogs/navigation, so changing songs or closing a chooser cannot lose its result. */
internal val LocalDocumentRequests = staticCompositionLocalOf<DocumentRequests> { error("Document request host is missing") }

internal class DocumentRequests(
    private val isBusy: () -> Boolean,
    val importLyrics: (Song) -> Unit,
    private val export: suspend (String, String, (OutputStream) -> Unit) -> Unit,
) {
    val busy: Boolean get() = isBusy()
    suspend fun exportBytes(bytes: ByteArray, name: String, mime: String) = export(name, mime) { it.write(bytes) }
    suspend fun exportFile(file: File, name: String, mime: String = "image/png") =
        export(name, mime) { output -> file.inputStream().use { it.copyTo(output) } }
}

@Serializable private data class DocumentRequest(
    val id: String,
    val name: String = "",
    val song: Song? = null,
    val destination: String? = null,
    val releasePermission: Boolean = false,
)

private data class DocumentTarget(val name: String, val mime: String)
private class CreateDocument : ActivityResultContract<DocumentTarget, Uri?>() {
    override fun createIntent(context: Context, input: DocumentTarget) = Intent(Intent.ACTION_CREATE_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE).setType(input.mime).putExtra(Intent.EXTRA_TITLE, input.name)
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data.takeIf { resultCode == Activity.RESULT_OK }
}

@Composable internal fun rememberDocumentRequests(container: AppContainer): DocumentRequests {
    val context = LocalContext.current.applicationContext
    val resolver = context.contentResolver
    val files = remember(context) { DocumentExportFiles(File(context.noBackupFilesDir, "document-exports")) }
    val scope = rememberCoroutineScope()
    // No bytes or absolute file paths are placed in the Activity saved-state Bundle.
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var preparing by remember { mutableStateOf(false) }
    val request = remember(pending) { pending?.let { ApiJson.decodeFromString<DocumentRequest>(it) } }

    fun receive(uri: Uri?) {
        val current = pending?.let { ApiJson.decodeFromString<DocumentRequest>(it) } ?: return
        if (uri == null) {
            pending = null
            scope.launch(Dispatchers.IO) { files.discard(current.id) }
        } else {
            // Keep access if another recreation happens while the result is being processed.
            val flags = if (current.song == null) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else Intent.FLAG_GRANT_READ_URI_PERMISSION
            val alreadyHeld = resolver.persistedUriPermissions.any {
                it.uri == uri && if (current.song == null) it.isWritePermission else it.isReadPermission
            }
            val acquired = runCatching { resolver.takePersistableUriPermission(uri, flags) }.isSuccess
            pending = ApiJson.encodeToString(current.copy(destination = uri.toString(), releasePermission = acquired && !alreadyHeld))
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), ::receive)
    val exporter = rememberLauncherForActivityResult(CreateDocument(), ::receive)

    LaunchedEffect(pending) {
        val current = request ?: return@LaunchedEffect
        val uri = current.destination?.let(Uri::parse) ?: return@LaunchedEffect
        var finished = false
        try {
            if (current.song != null) importLyricsDocument(container, resolver, uri, current.song)
            else withContext(Dispatchers.IO) {
                // Open the source first: a missing payload must not truncate the selected document.
                files.file(current.id).inputStream().use { input ->
                    checkNotNull(resolver.openOutputStream(uri, "wt")) { "无法打开保存位置" }.use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                }
            }
            finished = true
            container.errorNotices.showMessage(if (current.song != null) "歌词已导入" else "已保存 ${current.name}")
        } catch (cancelled: CancellationException) {
            // Leave request, destination and payload intact; the new host resumes this write.
            throw cancelled
        } catch (failure: Exception) {
            finished = true
            container.errorNotices.show(failure.message ?: "文件处理失败，请重试")
        } finally {
            if (finished) {
                pending = null
                withContext(NonCancellable + Dispatchers.IO) {
                    files.discard(current.id)
                    if (current.releasePermission) {
                        val flags = if (current.song == null) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else Intent.FLAG_GRANT_READ_URI_PERMISSION
                        runCatching { resolver.releasePersistableUriPermission(uri, flags) }
                    }
                }
            }
        }
    }

    return DocumentRequests(isBusy = { pending != null || preparing }, importLyrics = { song ->
        check(pending == null && !preparing) { "请先完成当前文件选择" }
        // Import only needs the immutable song identity, not artwork or playback metadata.
        val target = Song(song.id, "", musicSource = song.musicSource, externalIds = song.externalIds,
            navidrome = song.navidrome, emby = song.emby)
        pending = ApiJson.encodeToString(DocumentRequest(UUID.randomUUID().toString(), song = target))
        try { importer.launch(arrayOf("*/*")) }
        catch (failure: Exception) { pending = null; throw failure }
    }, export = { name, mime, write ->
        check(pending == null && !preparing) { "请先完成当前文件选择" }
        preparing = true
        var staged: String? = null
        try {
            withContext(Dispatchers.IO) { staged = files.stage(write) }
            currentCoroutineContext().ensureActive()
            pending = ApiJson.encodeToString(DocumentRequest(requireNotNull(staged), name = name))
            exporter.launch(DocumentTarget(name, mime))
            staged = null // The result callback now owns the durable payload.
        } catch (failure: Exception) { pending = null; throw failure }
        finally {
            preparing = false
            staged?.let { id -> withContext(NonCancellable + Dispatchers.IO) { files.discard(id) } }
        }
    })
}

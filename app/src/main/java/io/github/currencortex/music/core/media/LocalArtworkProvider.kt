package io.github.currencortex.music.core.media

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import java.io.FileNotFoundException
import java.io.IOException

/** App-private, read-only artwork endpoint shared by Coil and the media session.
 * Read from the granted audio document on demand, so clearing image caches cannot
 * leave persisted songs pointing at deleted artwork files.
 */
class LocalArtworkProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = "image/*"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) =
        throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) =
        throw UnsupportedOperationException()

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val app = requireNotNull(context)
        if (mode != "r" || uri.authority != "${app.packageName}.local-artwork" || uri.path != "/embedded") {
            throw FileNotFoundException("Invalid artwork request")
        }
        val source = uri.getQueryParameter("source")?.let(Uri::parse)
            ?: throw FileNotFoundException("Missing audio document")
        if (source.scheme != "content" || source.authority == uri.authority ||
            !DocumentsContract.isDocumentUri(app, source)) {
            throw FileNotFoundException("Invalid audio document")
        }
        val metadata = MediaMetadataRetriever()
        val picture = try {
            metadata.setDataSource(app, source)
            metadata.embeddedPicture?.takeIf { it.isNotEmpty() }
                ?: throw FileNotFoundException("Audio has no embedded artwork")
        } catch (denied: SecurityException) {
            throw denied
        } catch (error: RuntimeException) {
            throw FileNotFoundException("Cannot read embedded artwork").apply { initCause(error) }
        } finally {
            metadata.release()
        }
        return openPipeHelper(uri, "image/*", null, picture) { output, _, _, _, bytes ->
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(output).use { it.write(bytes) }
            } catch (_: IOException) { /* The image request may have been cancelled. */ }
        }
    }

    companion object {
        fun artworkUri(context: Context, audio: Uri): Uri = Uri.Builder()
            .scheme("content").authority("${context.packageName}.local-artwork")
            .appendPath("embedded").appendQueryParameter("source", audio.toString()).build()
    }
}

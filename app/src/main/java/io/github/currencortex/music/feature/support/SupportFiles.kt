package io.github.currencortex.music.feature.support

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal enum class SupportLinkResult { OPENED, INVALID, NO_APP }

internal fun openSupportLink(context: Context, value: String): SupportLinkResult {
    val url = supportHttpsUrl(value) ?: return SupportLinkResult.INVALID
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        SupportLinkResult.OPENED
    } catch (_: ActivityNotFoundException) {
        SupportLinkResult.NO_APP
    } catch (_: SecurityException) {
        SupportLinkResult.NO_APP
    }
}

/** Copy original pixels, never render a screenshot of the preview or re-encode the code. */
internal object SupportFiles {
    suspend fun saveOfficial(context: Context, bytes: ByteArray, uri: Uri) = withContext(Dispatchers.IO) {
        checkNotNull(context.contentResolver.openOutputStream(uri, "w")).use { it.write(bytes) }
    }

    suspend fun shareOfficial(context: Context, bytes: ByteArray): Intent = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "support-codes").apply { check(mkdirs() || isDirectory) }
        val file = File(directory, "official-donation.jpg")
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "官方捐赠码", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

}

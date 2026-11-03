package io.github.currencortex.music.feature.support

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.RawRes
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import io.github.currencortex.music.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal enum class SupportCode(
    val title: String, val summary: String, val scanner: String,
    val fileName: String, @param:RawRes val image: Int,
) {
    WECHAT("微信赞赏码", "扫码支持开发者", "微信", "wechat-appreciation.png", R.raw.support_wechat_appreciation),
    ALIPAY("支付宝收款码", "使用支付宝直接转账", "支付宝", "alipay-payment.png", R.raw.support_alipay_payment),
}

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
    suspend fun save(context: Context, code: SupportCode, uri: Uri) = withContext(Dispatchers.IO) {
        checkNotNull(context.contentResolver.openOutputStream(uri, "w")).use { output ->
            context.resources.openRawResource(code.image).use { it.copyTo(output) }
        }
    }

    suspend fun shareIntent(context: Context, code: SupportCode): Intent = withContext(Dispatchers.IO) {
        // Exactly two bounded cache files; no payment data or accumulated share history.
        val directory = File(context.cacheDir, "support-codes").apply { check(mkdirs() || isDirectory) }
        val file = File(directory, code.fileName)
        context.resources.openRawResource(code.image).use { input -> file.outputStream().use { input.copyTo(it) } }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, code.title)
            clipData = ClipData.newUri(context.contentResolver, code.title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

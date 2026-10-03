package io.github.currencortex.music.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Optional product links must stay HTTPS; devices without a browser get feedback. */
fun openExternalLink(context: Context, url: String) {
    val uri = Uri.parse(url)
    if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null) {
        Toast.makeText(context, "链接无效", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "未找到可以打开链接的应用", Toast.LENGTH_SHORT).show()
    }
}

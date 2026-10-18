package io.github.currencortex.music.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Optional product links must stay HTTPS; devices without a browser get feedback. */
fun openExternalLink(context: Context, url: String) {
    val uri = Uri.parse(url)
    if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null) {
        context.showMusicError("链接无效")
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        context.showMusicError("未找到可以打开链接的应用")
    }
}

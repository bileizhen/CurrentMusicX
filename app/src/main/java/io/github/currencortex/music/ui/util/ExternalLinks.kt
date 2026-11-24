package io.github.currencortex.music.ui.util

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.currencortex.music.MainActivity

/** Optional product links must stay HTTPS; devices without a browser get feedback. */
fun openExternalLink(context: Context, url: String, excludeCurrentApp: Boolean = false) {
    val uri = Uri.parse(url)
    if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null) {
        context.showMusicError("链接无效")
        return
    }
    try {
        val view = Intent(Intent.ACTION_VIEW, uri)
        val launch = if (excludeCurrentApp) Intent.createChooser(view, "在浏览器中打开")
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, MainActivity::class.java))) else view
        context.startActivity(launch)
    } catch (_: ActivityNotFoundException) {
        context.showMusicError("未找到可以打开链接的应用")
    }
}

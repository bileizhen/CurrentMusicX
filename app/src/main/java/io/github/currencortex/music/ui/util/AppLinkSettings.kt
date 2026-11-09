package io.github.currencortex.music.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

fun openAppLinkSettings(context: Context) {
    val packageUri = Uri.parse("package:${context.packageName}")
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
    val target = if (Build.VERSION.SDK_INT >= 31)
        Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, packageUri) else details
    try {
        context.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(details.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            context.showMusicError("请在系统设置的应用信息中设置默认打开链接")
        }
    }
}

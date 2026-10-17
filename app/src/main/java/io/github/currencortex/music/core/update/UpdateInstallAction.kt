package io.github.currencortex.music.core.update

import android.content.Context
import android.content.Intent
import io.github.currencortex.music.MainActivity

/** A user tap brings the app foreground before it requests the system installer. */
object UpdateInstallAction {
    const val EXTRA = "install_downloaded_update"
    fun open(context: Context) {
        context.startActivity(Intent(context, MainActivity::class.java).putExtra(EXTRA, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
}

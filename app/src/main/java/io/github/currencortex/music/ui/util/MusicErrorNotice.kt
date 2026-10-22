package io.github.currencortex.music.ui.util

import android.content.Context
import io.github.currencortex.music.CurrentMusicApplication

/** Shared island feedback routes for explicit UI actions. */
internal fun Context.showMusicError(message: String) {
    (applicationContext as? CurrentMusicApplication)?.container?.errorNotices?.show(message)
}

internal fun Context.showMusicMessage(message: String, longDuration: Boolean = false) {
    (applicationContext as? CurrentMusicApplication)?.container?.errorNotices?.showMessage(message, longDuration)
}

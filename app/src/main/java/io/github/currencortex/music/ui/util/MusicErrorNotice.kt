package io.github.currencortex.music.ui.util

import android.content.Context
import io.github.currencortex.music.CurrentMusicApplication

/** Shared error route for explicit UI actions; never creates a centered Android Toast. */
internal fun Context.showMusicError(message: String) {
    (applicationContext as? CurrentMusicApplication)?.container?.errorNotices?.show(message)
}

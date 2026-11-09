package io.github.currencortex.music.feature.lyrics.desktop

import android.content.Context
import android.os.Looper
import io.github.currencortex.music.AppContainer
import java.util.IdentityHashMap

/** Playback and recognition lease the same window; neither service can destroy the other's island. */
internal object DesktopLyricsHosts {
    private data class Entry(val host: DesktopLyricsHost, var leases: Int = 0)
    private val entries = IdentityHashMap<AppContainer, Entry>()
    fun acquire(context: Context, container: AppContainer): AutoCloseable {
        check(Looper.myLooper() == Looper.getMainLooper())
        val app = context.applicationContext
        val entry = entries.getOrPut(container) {
            Entry(DesktopLyricsHost(app, container.playbackLyrics.state, container.playerController.state,
                container.desktopLyrics, container.desktopLyricsStatus, container.desktopLyricsSettingsVisible,
                changeSong = { direction ->
                    val player = container.playerController
                    if (direction > 0) player.next(player.state.value.showPause) else player.previous(player.state.value.showPause)
                }, togglePlayback = { container.playerController.toggle() }, appearance = container.settings.state,
                appForeground = container.appForeground, effectNotices = container.islandNotices,
                installUpdate = { io.github.currencortex.music.core.update.UpdateInstallAction.open(app) },
                pageHidden = container.desktopIslandPageHidden, musicSettings = container.musicSettings.state,
                audioLevel = container.dockAudioLevel))
        }
        entry.leases++
        var released = false
        return AutoCloseable {
            check(Looper.myLooper() == Looper.getMainLooper())
            if (!released) {
                released = true
                if (--entry.leases == 0) { entries.remove(container); entry.host.close() }
            }
        }
    }
}

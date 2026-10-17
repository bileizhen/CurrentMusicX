package io.github.currencortex.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.currencortex.music.ui.CurrentMusicApp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStateAtLeast
import io.github.currencortex.music.core.update.UpdateDownloadState
import io.github.currencortex.music.core.update.UpdateInstallAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        (application as CurrentMusicApplication).container.appForeground.value = true
    }
    override fun onStop() {
        if (!isChangingConfigurations) (application as CurrentMusicApplication).container.appForeground.value = false
        super.onStop()
    }
    private val desktopRequest = androidx.compose.runtime.mutableIntStateOf(0)
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleUpdateInstall(intent)
        if (intent.getBooleanExtra(io.github.currencortex.music.core.update.WorkManagerUpdateBackground.OPEN, false)) {
            (application as CurrentMusicApplication).container.updates.reopenDownload()
            intent.removeExtra(io.github.currencortex.music.core.update.WorkManagerUpdateBackground.OPEN)
        }
        if (intent.getBooleanExtra(io.github.currencortex.music.feature.lyrics.desktop.DesktopLyricsHost.OPEN, false)) desktopRequest.intValue++
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(io.github.currencortex.music.feature.lyrics.desktop.DesktopLyricsHost.OPEN, false)) desktopRequest.intValue++
        val launchWindowReady = androidx.compose.runtime.mutableStateOf(android.os.Build.VERSION.SDK_INT < 31)
        if (android.os.Build.VERSION.SDK_INT >= 31) splashScreen.setOnExitAnimationListener {
            it.remove()
            launchWindowReady.value = true
        }
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        val app = application as CurrentMusicApplication
        if (intent.getBooleanExtra(io.github.currencortex.music.core.update.WorkManagerUpdateBackground.OPEN, false)) {
            app.container.updates.reopenDownload()
            intent.removeExtra(io.github.currencortex.music.core.update.WorkManagerUpdateBackground.OPEN)
        }
        val animateLaunch = app.consumeLaunchAnimation() && savedInstanceState == null
        setContent { CurrentMusicApp(app.container, animateLaunch = animateLaunch, launchWindowReady = launchWindowReady.value, openDesktopRequest = desktopRequest.intValue) }
        handleUpdateInstall(intent)
    }
    private fun handleUpdateInstall(request: android.content.Intent) {
        if (!request.getBooleanExtra(UpdateInstallAction.EXTRA, false)) return
        request.removeExtra(UpdateInstallAction.EXTRA)
        lifecycleScope.launch {
            lifecycle.withStateAtLeast(Lifecycle.State.RESUMED) { Unit }
            val container = (application as CurrentMusicApplication).container
            // WorkManager may still be restoring a completed download after process recreation.
            val ready = withTimeoutOrNull(5_000) {
                container.updateTransfer.state.first { it.download is UpdateDownloadState.Ready }
            }
            container.updates.reopenDownload()
            if (ready != null) container.updateTransfer.install()
        }
    }
}

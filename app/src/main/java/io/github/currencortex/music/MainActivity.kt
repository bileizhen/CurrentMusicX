package io.github.currencortex.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.currencortex.music.ui.CurrentMusicApp
import io.github.currencortex.music.core.update.UpdateInstallAction

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
    private val recognitionRequest = androidx.compose.runtime.mutableIntStateOf(0)
    private fun handleRecognition(request: android.content.Intent) {
        if (request.getBooleanExtra(io.github.currencortex.music.feature.recognition.RecognitionDesktopService.OPEN, false)) {
            request.removeExtra(io.github.currencortex.music.feature.recognition.RecognitionDesktopService.OPEN)
            recognitionRequest.intValue++
        }
    }
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleRecognition(intent)
        handleUpdateView(intent)
        if (intent.getBooleanExtra(io.github.currencortex.music.core.update.WorkManagerUpdateBackground.OPEN, false)) {
            (application as CurrentMusicApplication).container.updates.reopenDownload()
            intent.removeExtra(io.github.currencortex.music.core.update.WorkManagerUpdateBackground.OPEN)
        }
        if (intent.getBooleanExtra(io.github.currencortex.music.feature.lyrics.desktop.DesktopLyricsHost.OPEN, false)) desktopRequest.intValue++
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleRecognition(intent)
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
        setContent { CurrentMusicApp(app.container, animateLaunch = animateLaunch, launchWindowReady = launchWindowReady.value, openDesktopRequest = desktopRequest.intValue, openRecognitionRequest = recognitionRequest.intValue) }
        handleUpdateView(intent)
    }
    private fun handleUpdateView(request: android.content.Intent) {
        if (!request.getBooleanExtra(UpdateInstallAction.EXTRA, false)) return
        request.removeExtra(UpdateInstallAction.EXTRA)
        (application as CurrentMusicApplication).container.updates.reopenDownload()
    }
}

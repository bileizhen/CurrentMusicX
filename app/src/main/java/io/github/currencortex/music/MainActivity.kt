package io.github.currencortex.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.currencortex.music.ui.CurrentMusicApp

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
        val animateLaunch = app.consumeLaunchAnimation() && savedInstanceState == null
        setContent { CurrentMusicApp(app.container, animateLaunch = animateLaunch, launchWindowReady = launchWindowReady.value, openDesktopRequest = desktopRequest.intValue) }
    }
}

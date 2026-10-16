package io.github.currencortex.music.ui.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.currencortex.music.data.settings.RefreshRatePreference
import kotlin.math.abs

val LocalRefreshRatePreference = staticCompositionLocalOf { RefreshRatePreference.SYSTEM }

internal data class RefreshMode(val id: Int, val width: Int, val height: Int, val rate: Float)
/** A fixed refresh mode must never change the display's physical resolution. */
internal fun preferredRefreshMode(current: RefreshMode, supported: List<RefreshMode>, rate: Int): Int {
    if (rate == 0) return 0
    val candidates = supported.filter { it.width == current.width && it.height == current.height && it.rate.isFinite() && it.rate > 0 }
    return candidates.minWithOrNull(compareBy<RefreshMode> { abs(it.rate - rate) }.thenBy { it.id != current.id })?.id ?: 0
}

internal fun applyWindowRefreshRate(params: WindowManager.LayoutParams, display: Display?, preference: RefreshRatePreference, systemBalanced: Boolean = true): Boolean {
    val rate = preference.hz.toFloat()
    // An explicit fixed-rate setting requests the physical mode too: a frame-rate hint
    // alone can still allow the compositor to downshift an idle window.
    val modeId = if (display != null) {
        fun Display.Mode.info() = RefreshMode(modeId, physicalWidth, physicalHeight, refreshRate)
        preferredRefreshMode(display.mode.info(), display.supportedModes.map { it.info() }, preference.hz)
    } else 0
    val balanced = preference == RefreshRatePreference.SYSTEM && systemBalanced
    val balanceChanged = Build.VERSION.SDK_INT >= 35 && params.isFrameRatePowerSavingsBalanced != balanced
    if (params.preferredRefreshRate == rate && params.preferredDisplayModeId == modeId && !balanceChanged) return false
    params.preferredRefreshRate = rate; params.preferredDisplayModeId = modeId
    // Fixed rate opts out of Android's adaptive downshift; OEM and hardware limits still apply.
    if (Build.VERSION.SDK_INT >= 35) params.isFrameRatePowerSavingsBalanced = balanced
    return true
}

internal fun applyViewRefreshRate(view: View, preference: RefreshRatePreference) {
    if (Build.VERSION.SDK_INT >= 35) {
        val rate = if (preference == RefreshRatePreference.SYSTEM) View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT else preference.hz.toFloat()
        if (view.requestedFrameRate != rate) view.requestedFrameRate = rate
    }
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.activity() else null
    else -> null
}

/** Compose owns its View's vote each frame, so set the drawing layers' preference. */
fun Modifier.musicRefreshRate(preference: RefreshRatePreference): Modifier =
    if (Build.VERSION.SDK_INT >= 35 && preference != RefreshRatePreference.SYSTEM)
        preferredFrameRate(preference.hz.toFloat()) else this

/** Preferences apply throughout this foreground window; background returns to its prior policy. */
@Composable fun AppRefreshRateRequest(preference: RefreshRatePreference) {
    val view = LocalView.current
    val window = view.context.activity()?.window ?: return
    val owner = LocalLifecycleOwner.current
    DisposableEffect(window, view, owner, preference) {
        val originalRate = window.attributes.preferredRefreshRate
        val originalMode = window.attributes.preferredDisplayModeId
        val originalBalanced = if (Build.VERSION.SDK_INT >= 35) window.attributes.isFrameRatePowerSavingsBalanced else true
        fun restore() {
            val params = window.attributes
            if (params.preferredRefreshRate != originalRate || params.preferredDisplayModeId != originalMode ||
                (Build.VERSION.SDK_INT >= 35 && params.isFrameRatePowerSavingsBalanced != originalBalanced)) {
                params.preferredRefreshRate = originalRate; params.preferredDisplayModeId = originalMode
                if (Build.VERSION.SDK_INT >= 35) params.isFrameRatePowerSavingsBalanced = originalBalanced
                window.attributes = params
            }
        }
        fun update() {
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                val params = window.attributes
                if (applyWindowRefreshRate(params, view.display, preference, originalBalanced)) window.attributes = params
            } else restore()
        }
        val manager = view.context.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) { if (displayId == view.display?.displayId) update() }
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        owner.lifecycle.addObserver(observer)
        manager.registerDisplayListener(listener, Handler(Looper.getMainLooper())); update()
        onDispose { owner.lifecycle.removeObserver(observer); manager.unregisterDisplayListener(listener); restore() }
    }
}

internal data class DisplayRefreshStatus(val current: Float = 0f, val supported: List<Int> = emptyList())
/** Display's reported rate, deliberately separate from rendered FPS in the lyrics island. */
@Composable internal fun rememberDisplayRefreshStatus(): State<DisplayRefreshStatus> {
    val view = LocalView.current
    val status = remember(view) { mutableStateOf(DisplayRefreshStatus()) }
    DisposableEffect(view) {
        val manager = view.context.getSystemService(DisplayManager::class.java)
        fun update() {
            val display = view.display ?: return
            val current = display.mode
            val rates = display.supportedModes.filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .map { kotlin.math.round(it.refreshRate).toInt() }.distinct().sorted()
            status.value = DisplayRefreshStatus(display.refreshRate, rates)
        }
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) { update() }
            override fun onDisplayRemoved(displayId: Int) { update() }
            override fun onDisplayChanged(displayId: Int) { if (displayId == view.display?.displayId) update() }
        }
        manager.registerDisplayListener(listener, Handler(Looper.getMainLooper())); update()
        onDispose { manager.unregisterDisplayListener(listener) }
    }
    return status
}

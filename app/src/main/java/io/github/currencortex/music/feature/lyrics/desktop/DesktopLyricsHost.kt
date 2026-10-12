package io.github.currencortex.music.feature.lyrics.desktop

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.os.*
import android.provider.Settings
import android.view.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.*
import io.github.currencortex.music.MainActivity
import io.github.currencortex.music.R
import io.github.currencortex.music.core.media.MusicService
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.feature.lyrics.data.PlaybackLyricsState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Exactly one service-owned window. The Activity owns neither this lifecycle nor its clock. */
class DesktopLyricsHost(private val context: Context, private val lyrics: StateFlow<PlaybackLyricsState>,
    private val playback: StateFlow<PlayerState>, private val preferences: DesktopLyricsPreferences,
    private val status: MutableStateFlow<DesktopWindowStatus>,
    private val settingsVisible: StateFlow<Boolean> = MutableStateFlow(false)) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val visual = if (Build.VERSION.SDK_INT >= 30) context.createDisplayContext(context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY))
        .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null) else context
    private val window = visual.getSystemService(WindowManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val appOps = context.getSystemService(AppOpsManager::class.java)
    private val displays = context.getSystemService(DisplayManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val textHeight = androidx.compose.runtime.mutableFloatStateOf(160f)
    private var view: android.widget.FrameLayout? = null
    private var composition: ComposeView? = null
    private var systemVisible = true
    private var owner: WindowOwner? = null
    private var params: WindowManager.LayoutParams? = null
    private var closed = false
    private var moving = false
    private var current = DesktopLyricsSettings()
    private var noticeLocked: Boolean? = null
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UNLOCK -> scope.launch { preferences.edit { it.copy(locked = false) } }
                CLOSE -> scope.launch { preferences.edit { it.copy(enabled = false) } }
                else -> visibility()
            }
        }
    }
    private val permission = AppOpsManager.OnOpChangedListener { _, pkg -> if (pkg == context.packageName) handler.post { reconcile() } }
    private val display = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) = Unit
        override fun onDisplayRemoved(id: Int) { updateWindow() }
        override fun onDisplayChanged(id: Int) { updateWindow() }
    }
    init {
        androidx.core.content.ContextCompat.registerReceiver(context, screen, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT); addAction(UNLOCK); addAction(CLOSE)
        }, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        appOps.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, context.packageName, permission)
        displays.registerDisplayListener(display, handler)
        scope.launch { preferences.ready.await(); combine(preferences.state, settingsVisible) { settings, _ -> settings }
            .collect { current = it; reconcile() } }
    }
    private fun reconcile() {
        if (closed) return
        if (!current.enabled) { remove(); return }
        if (!Settings.canDrawOverlays(context)) {
            remove(); status.value = DesktopWindowStatus(error = "悬浮窗权限已关闭，请在应用内主动开启")
            scope.launch { preferences.edit { it.copy(enabled = false) } }; return
        }
        if (view == null) add()
        updateWindow(); visibility(); notification()
    }
    private fun area(): DesktopSafeArea {
        val margin = (8 * context.resources.displayMetrics.density).toInt()
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = window.maximumWindowMetrics
            val b = metrics.bounds
            val i = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            return DesktopSafeArea(b.left + i.left + margin, b.top + i.top + margin, b.right - i.right - margin, b.bottom - i.bottom - margin)
        }
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION") window.defaultDisplay.getRealMetrics(metrics)
        // Older APIs have no WindowMetrics; reserve conservative bars without hidden APIs.
        val bar = (32 * metrics.density).toInt()
        return DesktopSafeArea(margin, bar, metrics.widthPixels - margin, metrics.heightPixels - bar)
    }
    // Drag/saved placement use physical display coordinates, independent of text direction.
    @android.annotation.SuppressLint("RtlHardcoded")
    private fun add() {
        val life = WindowOwner()
        val compose = ComposeView(visual).apply {
            setViewTreeLifecycleOwner(life); setViewTreeSavedStateRegistryOwner(life)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { DesktopLyricsSurface(lyrics, playback, preferences.state, maxTextHeight = { textHeight.floatValue }, drag = ::drag, release = ::release, lock = { scope.launch { preferences.edit { it.copy(locked = true) } } },
                close = { scope.launch { preferences.edit { it.copy(enabled = false) } } }, settings = {
                    context.startActivity(settingsIntent(context))
                }) }
        }
        val root = object : android.widget.FrameLayout(visual) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val maximum = if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED) area().height
                    else minOf(View.MeasureSpec.getSize(heightMeasureSpec), area().height)
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(maximum, View.MeasureSpec.AT_MOST))
            }
            override fun onWindowVisibilityChanged(visibility: Int) {
                super.onWindowVisibilityChanged(visibility)
                val screenHidden = !power.isInteractive || keyguard.isKeyguardLocked || settingsVisible.value
                handler.post { if (!closed && view === this) {
                    if (visibility == View.VISIBLE) systemVisible = true
                    else if (!screenHidden) systemVisible = false
                    visibility()
                } }
            }
        }.apply {
            setViewTreeLifecycleOwner(life); setViewTreeSavedStateRegistryOwner(life)
            addView(compose, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        val p = WindowManager.LayoutParams(1, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags(), PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT; title = "CurrentMusic 桌面歌词"; softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }
        val safe = area()
        val width = minOf((320 * context.resources.displayMetrics.density).toInt(), safe.width)
        val initial = desktopPlacement(safe, width, (160 * context.resources.displayMetrics.density).toInt(), current.x, current.y)
        p.width = width; p.x = initial.x; p.y = initial.y
        val maximum = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch else .8f
        p.alpha = desktopWindowAlpha(current.opacity, current.locked, maximum)
        owner = life; view = root; composition = compose; params = p
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> if (!moving) updateWindow() }
        try { window.addView(root, p) }
        catch (failure: RuntimeException) { android.util.Log.e("DesktopLyrics", "Cannot add overlay", failure); remove(); status.value = DesktopWindowStatus(error = "无法显示悬浮歌词，请检查悬浮窗权限"); scope.launch { preferences.edit { it.copy(enabled = false) } } }
    }
    private fun flags() = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or if (current.locked) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0
    private fun updateWindow() {
        val v = view ?: return; val p = params ?: return
        if (!Settings.canDrawOverlays(context)) { reconcile(); return }
        val a = area()
        val density = visual.resources.displayMetrics.density
        val fontScale = visual.resources.configuration.fontScale
        textHeight.floatValue = (a.height / density * .65f - (if (current.locked) 40f else 90f) * fontScale).coerceAtLeast(40f)
        val width = minOf((320 * context.resources.displayMetrics.density).toInt(), a.width)
        val placement = desktopPlacement(a, width, v.height.coerceAtLeast(1), current.x, current.y)
        val maximum = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch else .8f
        val alpha = desktopWindowAlpha(current.opacity, current.locked, maximum)
        if (!moving && (p.x != placement.x || p.y != placement.y || p.width != width || p.flags != flags() || p.alpha != alpha)) {
            p.width = width; p.x = placement.x; p.y = placement.y; p.flags = flags(); p.alpha = alpha
            try { window.updateViewLayout(v, p) } catch (_: RuntimeException) { remove(); return }
        }
        status.value = status.value.copy(attached = true, alpha = p.alpha, width = width, height = v.height, x = p.x, y = p.y)
    }
    private fun drag(dx: Float, dy: Float) {
        if (current.locked) return
        val p = params ?: return; val v = view ?: return
        moving = true
        val a = area()
        p.x = (p.x + dx).toInt().coerceIn(a.left, (a.right - p.width).coerceAtLeast(a.left))
        p.y = (p.y + dy).toInt().coerceIn(a.top, (a.bottom - v.height).coerceAtLeast(a.top))
        runCatching { window.updateViewLayout(v, p) }.onFailure { remove() }
    }
    private fun release() {
        val p = params ?: return; val v = view ?: return; val a = area()
        moving = false
        val center = p.x + p.width / 2f
        val snap = current.snapEdges || p.x - a.left < 16 * context.resources.displayMetrics.density || a.right - p.x - p.width < 16 * context.resources.displayMetrics.density
        val x = if (snap) if (center < (a.left + a.right) / 2f) 0f else 1f else (center - a.left) / a.width
        val y = (p.y + v.height / 2f - a.top) / a.height
        scope.launch { preferences.edit { it.copy(x = x, y = y) } }
    }
    private fun visibility() {
        val screenHidden = !power.isInteractive || keyguard.isKeyguardLocked || settingsVisible.value
        val visible = view != null && systemVisible && !screenHidden
        // A system-hidden window must retain VISIBLE on its own view, otherwise the
        // app's GONE state would prevent WindowManager from making it visible again.
        view?.visibility = if (screenHidden) View.GONE else View.VISIBLE
        owner?.lifecycleRegistry?.currentState = if (visible) Lifecycle.State.STARTED else Lifecycle.State.CREATED
        status.value = status.value.copy(visible = visible)
    }
    private fun notification() {
        if (view == null || noticeLocked == current.locked) return
        noticeLocked = current.locked
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "桌面歌词控制", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        fun command(action: String, code: Int) = PendingIntent.getBroadcast(context, code,
            Intent(action).setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(context, 813, settingsIntent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notice = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("桌面歌词${if (current.locked) "已锁定" else "编辑中"}")
            .setContentText("${if (current.locked) "触摸穿透 · 点击解锁可拖动" else "拖动歌词调整位置"} · 应用内也可关闭")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .addAction(0, "解锁", command(UNLOCK, 811)).addAction(0, "关闭", command(CLOSE, 812)).build()
        try { notifications.notify(NOTICE, notice) } catch (_: SecurityException) { /* App settings remain an unlock route. */ }
    }
    private fun remove() {
        val v = view
        view = null; params = null; moving = false; noticeLocked = null
        if (v != null) runCatching { window.removeViewImmediate(v) }
        composition?.disposeComposition(); composition = null; systemVisible = true
        owner?.lifecycleRegistry?.currentState = Lifecycle.State.DESTROYED; owner = null
        notifications.cancel(NOTICE); status.value = DesktopWindowStatus()
    }
    override fun close() {
        if (closed) return
        closed = true; scope.cancel(); remove()
        context.unregisterReceiver(screen); appOps.stopWatchingMode(permission); displays.unregisterDisplayListener(display)
    }
    companion object {
        const val UNLOCK = "currentmusic.desktopLyrics.unlock"
        const val CLOSE = "currentmusic.desktopLyrics.close"
        const val OPEN = "currentmusic.desktopLyrics.settings"
        private const val CHANNEL = "desktop_lyrics"
        private const val NOTICE = 810
        fun settingsIntent(context: Context) = Intent(context, MainActivity::class.java).putExtra(OPEN, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

private class WindowOwner : LifecycleOwner, SavedStateRegistryOwner {
    val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    private val saved = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    init { saved.performAttach(); saved.performRestore(null); lifecycleRegistry.currentState = Lifecycle.State.CREATED }
}

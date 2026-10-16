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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.ui.component.musicRefreshRate
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import io.github.currencortex.music.ui.component.applyWindowRefreshRate

/** One visual window, with a transparent pill-sized input window while the island is idle. */
class DesktopLyricsHost(private val context: Context, private val lyrics: StateFlow<PlaybackLyricsState>,
    private val playback: StateFlow<PlayerState>, private val preferences: DesktopLyricsPreferences,
    private val status: MutableStateFlow<DesktopWindowStatus>,
    private val settingsVisible: StateFlow<Boolean> = MutableStateFlow(false),
    private val changeSong: (Int) -> Unit = {}, private val togglePlayback: () -> Unit = {},
    private val appearance: StateFlow<AppearanceSettings> = MutableStateFlow(AppearanceSettings())) : AutoCloseable {

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
    private var view: android.widget.FrameLayout? = null
    private var composition: ComposeView? = null
    private var islandTouchView: View? = null
    private var islandTouchParams: WindowManager.LayoutParams? = null
    private var islandSize = androidx.compose.ui.unit.IntSize.Zero
    private var islandTop: Int? = null
    private var coverBounds = androidx.compose.ui.geometry.Rect.Zero
    private var systemVisible = true
    private var owner: WindowOwner? = null
    private var params: WindowManager.LayoutParams? = null
    private var closed = false
    private val islandMaximumWidth = androidx.compose.runtime.mutableFloatStateOf(640f)
    private val editRequest = androidx.compose.runtime.mutableIntStateOf(0)
    private val islandTapRequest = androidx.compose.runtime.mutableIntStateOf(0)
    private var editing = true
    private var current = DesktopLyricsSettings()
    private var refreshRate = appearance.value.refreshRate
    private var noticeKey: Pair<Boolean, Boolean>? = null
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UNLOCK -> scope.launch {
                    preferences.edit { it.copy(locked = false) }
                    editRequest.intValue++
                }
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
        scope.launch { appearance.map { it.refreshRate }.distinctUntilChanged().collect { refreshRate = it; updateWindow() } }
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
    // Island placement uses physical display coordinates, independent of text direction.
    @android.annotation.SuppressLint("RtlHardcoded")
    private fun add() {
        val life = WindowOwner()
        val compose = ComposeView(visual).apply {
            setViewTreeLifecycleOwner(life); setViewTreeSavedStateRegistryOwner(life)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val config by appearance.collectAsState()
                Box(Modifier.musicRefreshRate(config.refreshRate), propagateMinConstraints = true) {
                DesktopLyricsSurface(lyrics, playback, preferences.state, lock = { scope.launch { preferences.edit { it.copy(locked = true) } } },
                close = { scope.launch { preferences.edit { it.copy(enabled = false) } } }, settings = {
                    context.startActivity(settingsIntent(context))
                }, maxIslandWidth = { islandMaximumWidth.floatValue }, editRequest = editRequest.intValue,
                islandTapRequest = islandTapRequest.intValue,
                changeSong = changeSong,
                togglePlayback = togglePlayback,
                onCoverBounds = { coverBounds = it },
                onIslandTop = { top -> if (islandTop != top) { islandTop = top; updateIslandTouchWindow() } },
                onIslandModeChanged = { fps -> status.value = status.value.copy(islandFps = fps) },
                onEditingChanged = { active ->
                    if (editing != active) { editing = active; updateWindow(); notification() }
                }, onIslandSize = { size ->
                    // Only the transparent input target follows the pill; the visual surface stays fixed.
                    islandSize = size
                    if (view != null) {
                        status.value = status.value.copy(contentWidth = size.width, contentHeight = size.height)
                        updateIslandTouchWindow()
                    }
                }) } }
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
            // DEFAULT cutout mode shoves overlay frames below the bar/cutout, defeating the full-screen island span.
            if (Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val safe = area()
        val width = minOf((640 * context.resources.displayMetrics.density).toInt(), safe.width)
        val initial = placement(safe, width, (160 * context.resources.displayMetrics.density).toInt())
        p.width = initial.width; p.x = initial.x; p.y = initial.y
        val maximum = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch else .8f
        p.alpha = desktopWindowAlpha(current.displayOpacity, touchThrough(), maximum)
        owner = life; view = root; composition = compose; params = p
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateWindow() }
        try { window.addView(root, p) }
        catch (failure: RuntimeException) { android.util.Log.e("DesktopLyrics", "Cannot add overlay", failure); remove(); status.value = DesktopWindowStatus(error = "无法显示悬浮歌词，请检查悬浮窗权限"); scope.launch { preferences.edit { it.copy(enabled = false) } } }
    }
    private fun touchThrough() = current.locked || !editing
    private fun flags() = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or if (touchThrough()) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0
    private fun updateWindow() {
        val v = view ?: return; val p = params ?: return
        if (!Settings.canDrawOverlays(context)) { reconcile(); return }
        val a = area()
        val density = visual.resources.displayMetrics.density
        // A fixed native surface avoids moving the previous frame's buffer when a
        // cue expands. Only the centered pill inside Compose changes its width.
        val desired = 640f
        val requestedWidth = minOf((desired * context.resources.displayMetrics.density).toInt(), a.width)
        val placement = placement(a, requestedWidth, v.height.coerceAtLeast(1))
        val width = placement.width
        islandMaximumWidth.floatValue = width / density
        val maximum = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch else .8f
        val alpha = desktopWindowAlpha(current.displayOpacity, touchThrough(), maximum)
        val refreshChanged = applyWindowRefreshRate(p, v.display, refreshRate)
        if (refreshChanged || p.x != placement.x || p.y != placement.y || p.width != width || p.flags != flags() || p.alpha != alpha) {
            p.width = width; p.x = placement.x; p.y = placement.y; p.flags = flags(); p.alpha = alpha
            try { window.updateViewLayout(v, p) } catch (_: RuntimeException) { remove(); return }
        }
        status.value = status.value.copy(attached = true, alpha = p.alpha, width = width, height = v.height, x = p.x, y = p.y, editing = editing && !current.locked)
        updateIslandTouchWindow()
    }
    private fun removeIslandTouchWindow() {
        val target = islandTouchView
        islandTouchView = null; islandTouchParams = null
        if (target != null) runCatching { window.removeViewImmediate(target) }
        status.value = status.value.copy(islandTouchable = false)
    }
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun updateIslandTouchWindow() {
        val root = view
        val visualParams = params
        if (closed || root == null || visualParams == null || !current.enabled || (editing && !current.locked) ||
            !status.value.visible || islandTop == null || islandSize.width <= 0 || islandSize.height <= 0) {
            removeIslandTouchWindow(); return
        }
        val width = islandSize.width.coerceAtMost(visualParams.width)
        val height = islandSize.height.coerceAtMost(root.height).coerceAtLeast(1)
        val x = visualParams.x + (visualParams.width - width) / 2
        val y = visualParams.y + islandTop!!.coerceIn(0, (root.height - height).coerceAtLeast(0))
        val target = islandTouchView
        if (target == null) {
            val input = View(visual).apply {
                setOnClickListener { islandTapRequest.intValue++ }
                setOnLongClickListener { if (!current.locked) editRequest.intValue++; true }
                var startX = 0f; var startY = 0f; var held = false
                val gestures = GestureDetector(visual, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent): Boolean {
                        startX = e.rawX; startY = e.rawY; held = false; return true
                    }
                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        val origin = params ?: return false
                        if (coverBounds.contains(androidx.compose.ui.geometry.Offset(e.rawX - origin.x, e.rawY - origin.y))) {
                            if (playback.value.song != null && playback.value.canControlPlayback) togglePlayback()
                            return true
                        }
                        return performClick()
                    }
                    override fun onLongPress(e: MotionEvent) { held = true; performLongClick() }
                    override fun onScroll(first: MotionEvent?, last: MotionEvent, dx: Float, dy: Float) = true
                }).apply { setOnDoubleTapListener(null) }
                setOnTouchListener { _, event ->
                    gestures.onTouchEvent(event)
                    if (event.actionMasked == MotionEvent.ACTION_UP && !held) {
                        val dx = event.rawX - startX; val dy = event.rawY - startY
                        if (kotlin.math.abs(dx) >= 32 * resources.displayMetrics.density &&
                            kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.25f && playback.value.song != null && playback.value.canControlPlayback)
                            changeSong(if (dx < 0f) 1 else -1)
                    }
                    true
                }
                contentDescription = "灵动岛：点击封面播放或暂停，点击歌词切换帧率，左右滑动切歌，未锁定时长按编辑"
            }
            val p = WindowManager.LayoutParams(width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                this.x = x; this.y = y; title = "CurrentMusic 灵动岛点击"
                if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = visualParams.layoutInDisplayCutoutMode
            }
            try {
                window.addView(input, p); islandTouchView = input; islandTouchParams = p
                status.value = status.value.copy(islandTouchable = true)
            }
            catch (failure: RuntimeException) { android.util.Log.w("DesktopLyrics", "Cannot add island tap target", failure) }
        } else islandTouchParams?.let { p ->
            if (p.x != x || p.y != y || p.width != width || p.height != height) {
                p.x = x; p.y = y; p.width = width; p.height = height
                try { window.updateViewLayout(target, p) } catch (_: RuntimeException) { removeIslandTouchWindow() }
            }
        }
    }
    private fun placement(area: DesktopSafeArea, width: Int, height: Int): DesktopPlacement {
        // The pill may cross bar and cutout insets; only the physical bounds keep it on screen.
        val bounds = if (Build.VERSION.SDK_INT >= 30) window.maximumWindowMetrics.bounds else null
        val screen = bounds?.let { DesktopSafeArea(it.left, it.top, it.right, it.bottom) } ?: area
        return desktopIslandPlacement(screen, width, height, current.islandY, (screen.left + screen.right) / 2)
    }
    private fun visibility() {
        val screenHidden = !power.isInteractive || keyguard.isKeyguardLocked || settingsVisible.value
        val visible = view != null && systemVisible && !screenHidden
        // A system-hidden window must retain VISIBLE on its own view, otherwise the
        // app's GONE state would prevent WindowManager from making it visible again.
        view?.visibility = if (screenHidden) View.GONE else View.VISIBLE
        owner?.lifecycleRegistry?.currentState = if (visible) Lifecycle.State.STARTED else Lifecycle.State.CREATED
        status.value = status.value.copy(visible = visible)
        updateIslandTouchWindow()
    }
    private fun notification() {
        val key = current.locked to editing
        if (view == null || noticeKey == key) return
        noticeKey = key
        val islandIdle = !editing
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "桌面歌词控制", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        fun command(action: String, code: Int) = PendingIntent.getBroadcast(context, code,
            Intent(action).setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(context, 813, settingsIntent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notice = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("桌面歌词${if (current.locked) "已锁定" else if (islandIdle) "已收起" else "编辑中"}")
            .setContentText("${if (current.locked) "点击切换歌词与帧率 · 左右滑动切歌" else if (islandIdle) "点击切换歌词与帧率 · 左右滑动切歌 · 长按编辑" else "固定居中 · 在设置中调整高度"} · 应用内也可关闭")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .addAction(0, if (current.locked) "解锁" else "编辑", command(UNLOCK, 811)).addAction(0, "关闭", command(CLOSE, 812)).build()
        try { notifications.notify(NOTICE, notice) } catch (_: SecurityException) { /* App settings remain an unlock route. */ }
    }
    private fun remove() {
        removeIslandTouchWindow(); islandSize = androidx.compose.ui.unit.IntSize.Zero; islandTop = null; coverBounds = androidx.compose.ui.geometry.Rect.Zero; islandTapRequest.intValue = 0
        val v = view
        view = null; params = null; noticeKey = null; editing = true
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

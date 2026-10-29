package io.github.currencortex.music.feature.recognition

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.*
import io.github.currencortex.music.feature.lyrics.desktop.*
import io.github.currencortex.music.ui.component.MusicCover
import io.github.currencortex.music.ui.component.MusicIcons
import kotlinx.coroutines.flow.StateFlow
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Only the capsule occupies an input window; the rest of the desktop remains touchable. */
internal class RecognitionIslandWindow(context: Context, state: StateFlow<RecognitionState>,
    private val options: () -> DesktopLyricsSettings, onAction: () -> Unit, onClose: () -> Unit) : AutoCloseable {
    private val visual = if (Build.VERSION.SDK_INT >= 30)
        context.createDisplayContext(context.getSystemService(DisplayManager::class.java).getDisplay(android.view.Display.DEFAULT_DISPLAY))
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null) else context
    private val manager = visual.getSystemService(WindowManager::class.java)
    private val owner = RecognitionWindowOwner()
    private val view = ComposeView(visual).apply {
        setViewTreeLifecycleOwner(owner); setViewTreeSavedStateRegistryOwner(owner)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val current by state.collectAsState()
            MiuixTheme { RecognitionIslandPill(current, onAction, onClose) }
        }
    }
    private var attached = false
    private val params = WindowManager.LayoutParams(1, WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        title = "CurrentMusic 听歌识曲灵动岛"
        if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
    }
    fun show() {
        updatePlacement()
        manager.addView(view, params); attached = true
        owner.registry.currentState = Lifecycle.State.RESUMED
    }
    fun updatePlacement() {
        val density = visual.resources.displayMetrics.density
        val margin = (8 * density).toInt()
        val area = if (Build.VERSION.SDK_INT >= 30) {
            val metrics = manager.maximumWindowMetrics
            val bars = metrics.windowInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
            DesktopSafeArea(metrics.bounds.left + bars.left + margin, metrics.bounds.top + bars.top + margin,
                metrics.bounds.right - bars.right - margin, metrics.bounds.bottom - bars.bottom - margin)
        } else {
            val metrics = visual.resources.displayMetrics
            DesktopSafeArea(margin, (32 * density).toInt(), metrics.widthPixels - margin, metrics.heightPixels - (32 * density).toInt())
        }
        val size = desktopIslandPlacement(area, (320 * density).toInt(), (68 * density).toInt(),
            options().islandY, (area.left + area.right) / 2)
        params.width = size.width; params.y = size.y
        if (attached) manager.updateViewLayout(view, params)
    }
    override fun close() {
        if (attached) { attached = false; runCatching { manager.removeViewImmediate(view) } }
        view.disposeComposition(); owner.registry.currentState = Lifecycle.State.DESTROYED
    }
}

@Composable internal fun RecognitionIslandPill(state: RecognitionState, onAction: () -> Unit, onClose: () -> Unit) {
    val accent = Color(0xFF3985FF)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
        .background(Brush.verticalGradient(listOf(Color(0xFF171B25), Color(0xFF080A10))))
        .border(.8.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(30.dp))
        .testTag("recognition_island")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button, onClick = onAction)
                .semantics { contentDescription = "${recognitionIslandTitle(state)}，${recognitionIslandSubtitle(state)}" }
                .testTag("recognition_island_action").padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.phase == RecognitionPhase.MATCHED && !state.songs.firstOrNull()?.cover.isNullOrBlank())
                    MusicCover(state.songs.first().cover, Modifier.size(36.dp), cornerRadius = 18.dp)
                else Box(Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = .2f)), contentAlignment = Alignment.Center) {
                    Icon(MusicIcons.Microphone, null, Modifier.size(22.dp), tint = accent)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(recognitionIslandTitle(state), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(recognitionIslandSubtitle(state), color = Color(0xFFB4BDC9), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose)
                .semantics { contentDescription = "关闭听歌识曲" }.testTag("recognition_island_close"), contentAlignment = Alignment.Center) {
                Icon(MusicIcons.Close, null, Modifier.size(18.dp), tint = Color(0xFFB4BDC9))
            }
        }
        if (state.phase == RecognitionPhase.RECORDING) Canvas(Modifier.fillMaxWidth().height(3.dp)) {
            drawRect(accent.copy(alpha = .15f))
            drawRect(accent, size = size.copy(width = size.width * (state.seconds / 10f).coerceIn(0f, 1f)))
        }
    }
}

private class RecognitionWindowOwner : LifecycleOwner, SavedStateRegistryOwner {
    val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
    private val saved = SavedStateRegistryController.create(this)
    override val savedStateRegistry get() = saved.savedStateRegistry
    init { saved.performAttach(); saved.performRestore(null); registry.currentState = Lifecycle.State.CREATED }
}

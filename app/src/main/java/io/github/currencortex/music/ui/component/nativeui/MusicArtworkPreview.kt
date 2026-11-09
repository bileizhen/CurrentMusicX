package io.github.currencortex.music.ui.component.nativeui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.currencortex.music.ui.component.LocalMusicDialogBackdrop
import io.github.currencortex.music.ui.component.MusicCover
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.musicDialogMaterial
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.DialogLayout
import kotlin.math.abs

/** A root overlay: only the page is sampled, so neither the lifted cover nor the menu blurs itself. */
@Composable internal fun MusicArtworkPreview(
    cover: String, title: String, anchor: Rect, actions: List<GlassMenuItem>,
    destinations: List<GlassMenuItem>, onDismiss: () -> Unit, previewKind: String = "歌曲",
) {
    val visible = remember { mutableStateOf(true) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    var contentReady by remember { mutableStateOf(false) }
    val onContentReady = remember { { contentReady = true } }
    val dismiss by rememberUpdatedState(onDismiss)
    val close: (() -> Unit) -> Unit = { action ->
        if (!closing) {
            closing = true
            scope.launch {
                progress.animateTo(0f, tween(200))
                visible.value = false
                dismiss()
                action()
            }
        }
    }
    // DialogLayout is mounted by the root host on a later composition. Start only after
    // the real menu has been measured, otherwise it can first appear halfway through.
    LaunchedEffect(contentReady) {
        if (contentReady && !closing) progress.animateTo(1f, spring(.82f, 420f))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { dismiss() }
    val backdrop = LocalMusicDialogBackdrop.current
    val blur = LocalGlassBlurEnabled.current
    val glass = LocalLiquidGlassEnabled.current
    // Root dialog registration must retain one lambda identity as progress/content changes.
    val body = rememberUpdatedState<@Composable () -> Unit> {
        CompositionLocalProvider(LocalLiquidGlassEnabled provides glass, LocalGlassBlurEnabled provides blur) {
            BackHandler { close {} }
            ArtworkPreviewContent(cover, title, anchor, actions, destinations, backdrop,
                progress = { progress.value }, enabled = !closing, close = close,
                previewKind = previewKind, onContentReady = onContentReady)
        }
    }
    val retained: @Composable () -> Unit = remember { { body.value() } }
    DialogLayout(visible, enterTransition = EnterTransition.None, exitTransition = ExitTransition.None,
        enableWindowDim = false, enableAutoLargeScreen = false, content = retained)
}

@Composable private fun ArtworkPreviewContent(
    cover: String, title: String, anchor: Rect, actions: List<GlassMenuItem>,
    destinations: List<GlassMenuItem>, backdrop: Backdrop?, progress: () -> Float,
    enabled: Boolean, close: (() -> Unit) -> Unit,
    previewKind: String, onContentReady: () -> Unit,
) {
    val density = LocalDensity.current
    val blurEnabled = LocalGlassBlurEnabled.current
    val colors = MiuixTheme.colorScheme
    var origin by remember { mutableStateOf(Offset.Zero) }
    var panelHeight by remember { mutableIntStateOf(0) }
    LaunchedEffect(panelHeight) { if (panelHeight > 0) onContentReady() }
    val press = rememberElasticPressState()
    val scroll = rememberScrollState()
    val haptics = LocalHapticFeedback.current
    val rows = remember { mutableMapOf<String, LayoutCoordinates>() }
    var restCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var paintedCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var submenu by remember { mutableStateOf<GlassMenuItem?>(null) }
    var submenuAnchor by remember { mutableStateOf<Rect?>(null) }
    var submenuOpen by remember { mutableStateOf(false) }
    var submenuProgress by remember { mutableFloatStateOf(0f) }
    // Observe only the settled/not-settled boundary in composition. Reading the raw
    // fraction here rebuilt this entire overlay on every frame of a submenu return.
    val submenuAtRest by remember { derivedStateOf { submenuProgress == 0f } }
    val currentEnabled by rememberUpdatedState(enabled && !submenuOpen && submenuAtRest)
    val currentItems by rememberUpdatedState(actions + destinations)
    val currentClose by rememberUpdatedState(close)
    fun activate(item: GlassMenuItem) {
        if (!currentEnabled || !item.enabled) return
        if (item.children.isNotEmpty()) {
            submenuAnchor = rows[item.id]?.takeIf { it.isAttached }?.boundsInWindow()
            submenu = item
            submenuOpen = true
            item.onEnter()
        } else if (item.keepOpen) item.onClick()
        else currentClose(item.onClick)
    }
    val currentActivate by rememberUpdatedState<(GlassMenuItem) -> Unit>(::activate)
    fun hit(position: Offset): GlassMenuItem? {
        val pane = paintedCoordinates?.takeIf { it.isAttached } ?: return null
        val point = pane.windowToLocal(position)
        if (!Rect(0f, 0f, pane.size.width.toFloat(), pane.size.height.toFloat()).contains(point)) return null
        return currentItems.firstOrNull { item ->
            val row = rows[item.id]?.takeIf { it.isAttached }
            item.enabled && row != null && pane.localBoundingBoxOf(row, false).contains(point)
        }
    }
    fun track(position: Offset, start: Boolean) {
        val rest = restCoordinates?.takeIf { it.isAttached }?.windowToLocal(position) ?: return
        // Use the dock's origin-relative pull, including movement inside the pane.
        // Edge-only dragging leaves the menu rigid while sliding between its actions.
        if (start) press.press(rest) else press.drag(rest)
        val next = hit(position)?.id
        if (next != selected) {
            selected = next
            if (next != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    fun cancelPress() { selected = null; press.release() }
    // Match GlassMenu's pointer ownership: one gesture selects across rows, while a tall
    // scrollable menu yields vertical drags to scrolling. Child click semantics remain usable.
    val trackPointer by rememberUpdatedState<(Offset, Boolean) -> Unit>(::track)
    val releasePointer by rememberUpdatedState<(Offset) -> Unit>({ position ->
        val action = if (currentEnabled) hit(position) else null
        cancelPress()
        action?.let { currentActivate(it) }
    })
    val cancelPointer by rememberUpdatedState<() -> Unit>(::cancelPress)
    @Composable fun itemModifier(item: GlassMenuItem): Modifier = Modifier
        .testTag("artwork_preview_${item.id}")
        .onGloballyPositioned { rows[item.id] = it }
        .drawBehind {
            if (selected == item.id) drawRoundRect(colors.onSurface.copy(alpha = .12f),
                cornerRadius = CornerRadius(16.dp.toPx()))
        }.clickable(enabled = currentEnabled && item.enabled, role = Role.Button,
            interactionSource = remember { MutableInteractionSource() }, indication = null) { currentActivate(item) }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("artwork_preview")
        .semantics { paneTitle = "$title · ${previewKind}操作" }
        .onGloballyPositioned { origin = it.boundsInWindow().topLeft }) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val margin = with(density) { 16.dp.toPx() }
        val top = WindowInsets.safeDrawing.getTop(density) + margin
        val bottom = height - WindowInsets.safeDrawing.getBottom(density) - margin
        val available = (bottom - top).coerceAtLeast(1f)
        val coverSize = with(density) { 144.dp.toPx() }.coerceAtMost(available * .38f).coerceAtMost(width - 2 * margin)
        val panelWidth = with(density) { 192.dp.toPx() }.coerceAtMost(width - 2 * margin)
        val gap = with(density) { 8.dp.toPx() }
        val maxPanelHeight = (available - coverSize - gap).coerceAtLeast(1f)
        val menuHeight = panelHeight.toFloat().coerceAtMost(maxPanelHeight)
        val localAnchor = anchor.translate(-origin)
        val targetX = (localAnchor.center.x - panelWidth / 2).coerceIn(margin, (width - margin - panelWidth).coerceAtLeast(margin))
        // Choose the side before clamping the pair. Clamping a below-only stack first would
        // always lift the cover away from a bottom-edge anchor instead of flipping its menu.
        val preferredCoverY = localAnchor.top.coerceIn(top, (bottom - coverSize).coerceAtLeast(top))
        val roomAbove = preferredCoverY - top - gap
        val roomBelow = bottom - preferredCoverY - coverSize - gap
        val menuAbove = roomBelow < menuHeight && roomAbove > roomBelow
        val minCoverY = if (menuAbove) top + menuHeight + gap else top
        val maxCoverY = if (menuAbove) bottom - coverSize else bottom - coverSize - gap - menuHeight
        val targetY = preferredCoverY.coerceIn(minCoverY, maxCoverY.coerceAtLeast(minCoverY))
        val menuY = if (menuAbove) targetY - gap - menuHeight else targetY + coverSize + gap
        val coverX = targetX + (panelWidth - coverSize) / 2
        // Fade a stable blurred layer. Animating its radius invalidates Miuix's blur cache,
        // crosses downsample levels and rebuilds full-window effects during the cover return.
        Box(Modifier.matchParentSize().testTag("artwork_preview_dismiss")
            .graphicsLayer { alpha = progress().coerceIn(0f, 1f) }
            .then(if (blurEnabled && backdrop != null) Modifier.drawBackdrop(backdrop,
                shape = { RectangleShape }, effects = {
                    blur(18.dp.toPx(), 18.dp.toPx())
                }, onDrawSurface = { drawRect(colors.background.copy(alpha = .14f)) })
            else Modifier.background(colors.background.copy(alpha = .82f)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                onClickLabel = "关闭${previewKind}预览", onClick = { if (submenuOpen) submenuOpen = false else close {} }))
        MusicCover(cover, Modifier.size(with(density) { coverSize.toDp() })
            .graphicsLayer {
                val p = progress()
                transformOrigin = TransformOrigin(0f, 0f)
                val startScale = localAnchor.width / coverSize
                scaleX = startScale + (1f - startScale) * p
                scaleY = scaleX
                translationX = localAnchor.left + (coverX - localAnchor.left) * p
                translationY = localAnchor.top + (targetY - localAnchor.top) * p
                shadowElevation = 20.dp.toPx() * p.coerceIn(0f, 1f)
                shape = RoundedCornerShape(16.dp)
                clip = true
            }.testTag("artwork_preview_cover"), pixels = 480, cornerRadius = 16.dp)
        // Keep placement/entrance outside the shared press transform. The whole glass pane,
        // including its contents, follows the pointer and springs back without moving layout.
        ElasticPress(Modifier.width(with(density) { panelWidth.toDp() })
            .heightIn(max = with(density) { maxPanelHeight.toDp() })
            .onSizeChanged { panelHeight = it.height }
            .graphicsLayer {
                val p = progress().coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(.5f, if (menuAbove) 1f else 0f)
                translationX = targetX
                translationY = menuY + (if (menuAbove) 1f else -1f) * (1f - p) * 12.dp.toPx()
                scaleX = (.86f + .14f * p) * (1f - .05f * submenuProgress); scaleY = scaleX
            }.onGloballyPositioned { restCoordinates = it }
            .testTag("artwork_preview_menu_press"), enabled = currentEnabled, state = press, observePointer = false) {
        Column(Modifier.fillMaxWidth().testTag("artwork_preview_menu")
            .onGloballyPositioned { paintedCoordinates = it }
            .semantics {
                val index = currentItems.indexOfFirst { it.id == selected }
                this[GlassMenuInteractionKey] = GlassMenuInteraction(index, press.frame(0f).pull, Rect.Zero)
            }
            .pointerInput(press) {
                awaitEachGesture {
                    val down = awaitFirstDown(false, PointerEventPass.Initial)
                    if (!currentEnabled || down.isConsumed) return@awaitEachGesture
                    val pane = paintedCoordinates?.takeIf { it.isAttached } ?: return@awaitEachGesture
                    trackPointer(pane.localToWindow(down.position), true)
                    var cancelled = false
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            val released = change?.changedToUp() == true
                            if (!currentEnabled || change == null || event.changes.count { it.pressed } > 1 || change.isConsumed)
                                cancelled = true
                            val delta = (change?.position ?: down.position) - down.position
                            if (scroll.maxValue > 0 && abs(delta.y) > viewConfiguration.touchSlop && abs(delta.y) > abs(delta.x)) {
                                cancelled = true
                                break
                            }
                            if (change != null && !cancelled) trackPointer(pane.localToWindow(change.position), false)
                            if (scroll.maxValue == 0 || released || abs(delta.x) > viewConfiguration.touchSlop)
                                event.changes.forEach { it.consume() }
                            if (change?.pressed != true) {
                                if (released && !cancelled) releasePointer(pane.localToWindow(change!!.position))
                                break
                            }
                        } while (!cancelled)
                    } finally { cancelPointer() }
                }
            }
            .musicDialogMaterial(backdrop, RoundedCornerShape(22.dp),
                visibility = { ((progress() - .12f) / .88f).coerceIn(0f, 1f) })
            .verticalScroll(scroll).padding(6.dp)) {
            @Composable fun quickActions() {
            Row(Modifier.fillMaxWidth()) {
                actions.forEach { action ->
                    Column(Modifier.weight(1f).then(itemModifier(action))
                        .padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        action.icon?.let { Icon(it, null, Modifier.size(20.dp), tint = colors.onSurface) }
                        Text(action.label, fontSize = 11.sp, maxLines = 1,
                            color = colors.onSurface.copy(alpha = if (enabled && action.enabled) 1f else .38f))
                    }
                }
            }
            }
            if (!menuAbove) quickActions()
            destinations.forEach { action ->
                Row(Modifier.fillMaxWidth().graphicsLayer { alpha = if(submenu?.id==action.id)1f-submenuProgress else 1f }
                    .then(itemModifier(action)).heightIn(min = 40.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(action.label, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (action.children.isNotEmpty()) Icon(MusicIcons.Forward, null, Modifier.size(14.dp), tint = colors.onSurface.copy(alpha = .5f))
                }
            }
            if (menuAbove) quickActions()
        }
        }
        submenu?.let { retained ->
            val owner = currentItems.firstOrNull { it.id == retained.id } ?: retained
            fun entries(items: List<GlassMenuItem>): List<GlassMenuItem> = items.map { item ->
                item.copy(children = entries(item.children), keepOpen = true, onClick = {
                    // Close both surfaces together; the shared overlay fade also covers
                    // GlassMenu's longer fold so it cannot linger after the cover returns.
                    if (item.keepOpen) item.onClick() else {
                        submenuOpen = false
                        currentClose(item.onClick)
                    }
                })
            }
            Box(Modifier.matchParentSize().graphicsLayer { alpha = progress().coerceIn(0f, 1f) }) {
                GlassMenu(expanded = submenuOpen && enabled, onExpandedChange = { submenuOpen = it },
                    items = entries(owner.children), anchor = submenuAnchor, backdrop = backdrop,
                    tags = GlassMenuTags("artwork_preview_submenu", "artwork_preview_submenu_dismiss", "artwork_preview_submenu"),
                    menuWidth = 224.dp, rootOwner = owner, showDividers = false, pullPastEdges = false,
                    flipRootAtEdges = true,
                    parentWidth = with(density) { panelWidth.toDp() }, onParentTransition = { submenuProgress = it },
                    toolbar = {})
            }
        }
    }
}

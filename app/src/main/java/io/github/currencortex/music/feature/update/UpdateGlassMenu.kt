package io.github.currencortex.music.feature.update

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import top.yukonga.miuix.kmp.basic.Text
import io.github.currencortex.music.ui.component.iosIndicatorSpecular
import io.github.currencortex.music.ui.component.liquid.lens
import io.github.currencortex.music.ui.component.liquid.vibrancy
import top.yukonga.miuix.kmp.blur.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

internal val UpdateButtonGlowKey = SemanticsPropertyKey<Float>("UpdateButtonGlow")

@Composable internal fun rememberUpdateBackdrop() =
    if (Build.VERSION.SDK_INT >= 33 && LocalView.current.isHardwareAccelerated && isRuntimeShaderSupported())
        rememberLayerBackdrop() else null

@Composable internal fun UpdateGlassButton(backdrop: Backdrop?, tag: String, description: String,
    onClick: () -> Unit, onBoundsChanged: (Rect) -> Unit = {}, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .92f else 1f,
        spring(dampingRatio = .64f, stiffness = 650f), label = "update glass press")
    val light = remember { Animatable(0f) }
    var lightCenter by remember { mutableStateOf(Offset.Unspecified) }
    LaunchedEffect(interaction) {
        var pulse: Job? = null
        interaction.interactions.collect { event ->
            when (event) {
                is PressInteraction.Press -> {
                    lightCenter = event.pressPosition
                    pulse?.cancel()
                    pulse = launch { light.animateTo(1f, tween(55)) }
                }
                is PressInteraction.Release -> {
                    pulse?.cancel()
                    pulse = launch {
                        // Complete a visible pulse even when down/up occur in a single frame.
                        light.animateTo(1f, tween(40))
                        delay(40)
                        light.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
                    }
                }
                is PressInteraction.Cancel -> {
                    pulse?.cancel()
                    pulse = launch { light.animateTo(0f, tween(90)) }
                }
            }
        }
    }
    Box(Modifier.size(48.dp).onGloballyPositioned { onBoundsChanged(it.boundsInWindow()) }
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .updateGlass(backdrop, CircleShape, .23f)
        .drawBehind {
            val strength = light.value
            if (strength > .001f) {
                drawCircle(Color.White.copy(alpha = .10f * strength))
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .08f * strength), Color.Transparent),
                    center = if (lightCenter.isSpecified) lightCenter else center, radius = size.width * .8f))
                val edge = 1.dp.toPx()
                drawCircle(Color.White.copy(alpha = .24f * strength), radius = (size.minDimension - edge) / 2,
                    style = Stroke(edge))
            }
        }
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = description; this[UpdateButtonGlowKey] = light.value }
        .testTag(tag), contentAlignment = Alignment.Center) { content() }
}

internal data class UpdateMenuAction(val label: String, val dividerBefore: Boolean = false, val onClick: () -> Unit)
internal data class UpdateMenuInteraction(val selected: Int, val displacement: Offset, val highlight: Rect)
internal val UpdateMenuInteractionKey = SemanticsPropertyKey<UpdateMenuInteraction>("UpdateMenuInteraction")
internal data class UpdateMenuWaves(val path: Float, val scale: Float, val opacity: Float)
internal val UpdateMenuWavesKey = SemanticsPropertyKey<UpdateMenuWaves>("UpdateMenuWaves")

/** Keep the shield until every closing wave settles, including an interrupted close. */
@Composable internal fun BoxScope.UpdateGlassMenu(open: Boolean, backdrop: Backdrop?,
    onDismiss: () -> Unit, toolbar: @Composable () -> Unit, actions: List<UpdateMenuAction>,
    anchor: Rect? = null) {
    val motion = remember { UpdateMenuMotion() }
    val targetOpen by rememberUpdatedState(open)
    LaunchedEffect(motion) {
        snapshotFlow { targetOpen }.collect { target ->
            // Let animateTo interrupt each wave: cancelling its owner first would erase velocity.
            launch { motion.animate(target) }
        }
    }
    val shown by remember(open) { derivedStateOf { open || motion.visible } }
    if (shown) {
        Box(Modifier.matchParentSize().testTag("update_menu_dismiss")
            .semantics { contentDescription = "关闭更新菜单" }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                onClick = onDismiss))
    }
    toolbar()
    BackHandler(enabled = shown && !open) {}
    if (shown) {
        val currentActions by rememberUpdatedState(actions)
        val haptics = LocalHapticFeedback.current
        val density = LocalDensity.current
        val dragLimit = with(density) { 7.dp.toPx() }
        val inset = with(density) { 6.dp.toPx() }
        var panelCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        var panelBounds by remember { mutableStateOf(Rect.Zero) }
        val geometry = updateMenuGeometry(anchor, panelBounds)
        val rows = remember { mutableStateMapOf<Int, Rect>() }
        var selected by remember { mutableIntStateOf(-1) }
        var lastHighlight by remember { mutableStateOf(Rect.Zero) }
        var snapHighlight by remember { mutableStateOf(true) }
        var displacement by remember { mutableStateOf(Offset.Zero) }
        var spot by remember { mutableStateOf(Offset.Unspecified) }
        val dx by animateFloatAsState(displacement.x, spring(.68f, 520f), label = "menu follow x")
        val dy by animateFloatAsState(displacement.y, spring(.68f, 520f), label = "menu follow y")
        val pressure by animateFloatAsState(if (selected >= 0) 1.012f else 1f,
            spring(.7f, 600f), label = "menu pressure")
        val highlightTop by animateFloatAsState(lastHighlight.top,
            if (snapHighlight) snap() else spring(.8f, 900f), label = "menu highlight top")
        val highlightHeight by animateFloatAsState(lastHighlight.height,
            if (snapHighlight) snap() else spring(.8f, 900f), label = "menu highlight height")
        val highlightAlpha by animateFloatAsState(if (selected >= 0) 1f else 0f,
            tween(if (selected >= 0) 55 else 200), label = "menu highlight alpha")
        Box(Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 64.dp, end = 16.dp)
            .widthIn(min = 120.dp, max = 192.dp).width(IntrinsicSize.Max)
            .onGloballyPositioned { panelBounds = it.boundsInWindow() }) {
        Box(Modifier.fillMaxWidth()
            .graphicsLayer {
                val travel = geometry.translation(motion.path.value)
                transformOrigin = geometry.origin
                scaleX = motion.scale.value.coerceAtLeast(0f) * pressure; scaleY = scaleX
                translationX = travel.x + dx; translationY = travel.y + dy
                alpha = motion.opacity.value.coerceIn(0f, 1f)
                shape = RoundedCornerShape(24.dp); shadowElevation = 16.dp.toPx()
                ambientShadowColor = Color.Black.copy(alpha = .18f)
                spotShadowColor = Color.Black.copy(alpha = .20f)
            }
            .updateGlass(backdrop, RoundedCornerShape(24.dp), .68f, neutral = true)
            .drawBehind {
                // A local light follows the finger; it fades with the shared selection mask.
                if (spot.isSpecified && highlightAlpha > .001f) {
                    drawRoundRect(Brush.radialGradient(
                        listOf(Color.White.copy(alpha = .09f * highlightAlpha), Color.Transparent),
                        center = spot, radius = size.width * .8f),
                        cornerRadius = CornerRadius(24.dp.toPx()))
                }
                if (lastHighlight.width > 0f) drawRoundRect(Color.White.copy(alpha = .17f * highlightAlpha),
                    topLeft = Offset(inset, highlightTop),
                    size = androidx.compose.ui.geometry.Size((size.width - inset * 2).coerceAtLeast(0f), highlightHeight),
                    cornerRadius = CornerRadius(18.dp.toPx()))
            }
            .onGloballyPositioned { panelCoordinates = it }
            .pointerInput(open) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var cancelled = !open || down.isConsumed
                    down.consume()
                    fun track(position: Offset) {
                        spot = position
                        val hit = rows.entries.firstOrNull { it.value.contains(position) }?.key ?: -1
                        if (hit != selected && hit >= 0) {
                            // Retarget one capsule instead of flashing a separate background on each row.
                            snapHighlight = selected < 0
                            lastHighlight = rows.getValue(hit)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        selected = hit
                        displacement = Offset(
                            ((position.x / size.width.coerceAtLeast(1) - .5f) * dragLimit * 2).coerceIn(-dragLimit, dragLimit),
                            ((position.y / size.height.coerceAtLeast(1) - .5f) * dragLimit * 2).coerceIn(-dragLimit, dragLimit))
                    }
                    try {
                        if (!cancelled) track(down.position)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            // Compose synthesizes consumed up changes for ACTION_CANCEL. Capture
                            // the real, unconsumed release before claiming the event ourselves.
                            val released = change?.changedToUp() == true
                            if (event.changes.any { it.isConsumed } || event.changes.count { it.pressed } > 1 || change == null)
                                cancelled = true
                            event.changes.forEach { it.consume() }
                            if (cancelled) selected = -1 else change?.let { track(it.position) }
                            if (change?.pressed != true) {
                                if (!cancelled && released) currentActions.getOrNull(selected)?.onClick?.invoke()
                                break
                            }
                        } while (true)
                    } finally {
                        selected = -1; displacement = Offset.Zero
                    }
                }
            }
            .testTag("update_overflow_panel")
            .semantics {
                paneTitle = "更新菜单"
                this[UpdateMenuWavesKey] = UpdateMenuWaves(motion.path.value, motion.scale.value, motion.opacity.value)
                this[UpdateMenuInteractionKey] = UpdateMenuInteraction(selected, Offset(dx, dy),
                    Rect(lastHighlight.left, highlightTop, lastHighlight.right, highlightTop + highlightHeight))
            }) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                actions.forEachIndexed { index, action ->
                    if (action.dividerBefore) Box(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        .fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .08f)))
                    Box(Modifier.fillMaxWidth().heightIn(min = 44.dp)
                        .onGloballyPositioned { row ->
                            panelCoordinates?.takeIf { it.isAttached && row.isAttached }?.let {
                                val bounds = it.localBoundingBoxOf(row, clipBounds = false)
                                if (rows[index] != bounds) rows[index] = bounds
                            }
                        }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                            role = Role.Button, onClick = { if (open) action.onClick() })
                        .testTag("update_menu_action_$index")
                        .padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.CenterStart) {
                        Text(action.label, color = Color(0xFFF7F4FF), fontSize = 16.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        }
    }
}

@Composable private fun Modifier.updateGlass(backdrop: Backdrop?, shape: Shape, tint: Float, neutral: Boolean = false): Modifier =
    if (Build.VERSION.SDK_INT >= 33 && backdrop != null) highApiUpdateGlass(backdrop, shape, tint, neutral)
    else background(Color(if (neutral) 0xFF242424 else 0xFF242332).copy(alpha = .96f), shape)
        .border(1.dp, Color.White.copy(alpha = .13f), shape)

@RequiresApi(33)
private fun Modifier.highApiUpdateGlass(backdrop: Backdrop, shape: Shape, tint: Float, neutral: Boolean): Modifier =
    drawBackdrop(backdrop, shape = { shape }, effects = {
        if (neutral) colorControls(brightness = 0f, contrast = 1f, saturation = .12f) else vibrancy()
        blur((if (neutral) 20.dp else 8.dp).toPx(), (if (neutral) 20.dp else 8.dp).toPx())
        lens(refractionHeight = 12.dp.toPx(), refractionAmount = 14.dp.toPx())
    }, highlight = { iosIndicatorSpecular.copy(alpha = .75f) },
        onDrawSurface = { drawRect(Color(if (neutral) 0xFF262626 else 0xFF171624).copy(alpha = tint)) })

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
import androidx.compose.ui.graphics.*
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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import top.yukonga.miuix.kmp.basic.Text
import io.github.currencortex.music.ui.component.iosIndicatorSpecular
import io.github.currencortex.music.ui.component.liquid.lens
import io.github.currencortex.music.ui.component.liquid.vibrancy
import top.yukonga.miuix.kmp.blur.*

@Composable internal fun rememberUpdateBackdrop() =
    if (Build.VERSION.SDK_INT >= 33 && LocalView.current.isHardwareAccelerated && isRuntimeShaderSupported())
        rememberLayerBackdrop() else null

@Composable internal fun UpdateGlassButton(backdrop: Backdrop?, tag: String, description: String,
    onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .92f else 1f,
        spring(dampingRatio = .64f, stiffness = 650f), label = "update glass press")
    val light by animateFloatAsState(if (pressed) .09f else 0f, tween(120), label = "update glass light")
    Box(Modifier.size(48.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        .updateGlass(backdrop, CircleShape, .23f)
        .drawBehind { drawCircle(Color.White.copy(alpha = light)) }
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = description }.testTag(tag), contentAlignment = Alignment.Center) { content() }
}

internal data class UpdateMenuAction(val label: String, val dividerBefore: Boolean = false, val onClick: () -> Unit)
internal data class UpdateMenuInteraction(val selected: Int, val displacement: Offset, val highlight: Rect)
internal val UpdateMenuInteractionKey = SemanticsPropertyKey<UpdateMenuInteraction>("UpdateMenuInteraction")

/** The panel is anchored below the overflow button; its drawing and touch shield share one clock. */
@Composable internal fun BoxScope.UpdateGlassMenu(open: Boolean, backdrop: Backdrop?,
    onDismiss: () -> Unit, toolbar: @Composable () -> Unit, actions: List<UpdateMenuAction>) {
    val expansion = remember { Animatable(0f) }
    LaunchedEffect(open) { expansion.animateTo(if (open) 1f else 0f,
        tween(if (open) 280 else 180, easing = FastOutSlowInEasing)) }
    val shown by remember(open) { derivedStateOf { open || expansion.value > .001f || expansion.isRunning } }
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
        val rows = remember { mutableStateMapOf<Int, Rect>() }
        var selected by remember { mutableIntStateOf(-1) }
        var lastHighlight by remember { mutableStateOf(Rect.Zero) }
        var snapHighlight by remember { mutableStateOf(true) }
        var displacement by remember { mutableStateOf(Offset.Zero) }
        val dx by animateFloatAsState(displacement.x, spring(.68f, 520f), label = "menu follow x")
        val dy by animateFloatAsState(displacement.y, spring(.68f, 520f), label = "menu follow y")
        val pressure by animateFloatAsState(if (selected >= 0) 1.012f else 1f,
            spring(.7f, 600f), label = "menu pressure")
        val highlightTop by animateFloatAsState(lastHighlight.top,
            if (snapHighlight) snap() else spring(.8f, 900f), label = "menu highlight top")
        val highlightHeight by animateFloatAsState(lastHighlight.height,
            if (snapHighlight) snap() else spring(.8f, 900f), label = "menu highlight height")
        val highlightAlpha by animateFloatAsState(if (selected >= 0) 1f else 0f,
            tween(if (selected >= 0) 90 else 160), label = "menu highlight alpha")
        Box(Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 64.dp, end = 16.dp).width(204.dp)) {
        Box(Modifier.fillMaxWidth()
            .graphicsLayer {
                val p = expansion.value
                transformOrigin = TransformOrigin(1f, 0f)
                scaleX = (.80f + .20f * p) * pressure; scaleY = scaleX
                translationX = dx; translationY = -6.dp.toPx() * (1f - p) + dy; alpha = p
                shape = RoundedCornerShape(24.dp); shadowElevation = 12.dp.toPx()
                ambientShadowColor = Color.Black.copy(alpha = .18f)
                spotShadowColor = Color.Black.copy(alpha = .20f)
            }
            .updateGlass(backdrop, RoundedCornerShape(24.dp), .72f)
            .drawBehind {
                if (lastHighlight.width > 0f) drawRoundRect(Color.White.copy(alpha = .12f * highlightAlpha),
                    topLeft = Offset(inset, highlightTop),
                    size = androidx.compose.ui.geometry.Size((size.width - inset * 2).coerceAtLeast(0f), highlightHeight),
                    cornerRadius = CornerRadius(22.dp.toPx()))
            }
            .onGloballyPositioned { panelCoordinates = it }
            .pointerInput(open) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var cancelled = !open || down.isConsumed
                    down.consume()
                    fun track(position: Offset) {
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
                this[UpdateMenuInteractionKey] = UpdateMenuInteraction(selected, Offset(dx, dy),
                    Rect(lastHighlight.left, highlightTop, lastHighlight.right, highlightTop + highlightHeight))
            }) {
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                actions.forEachIndexed { index, action ->
                    if (action.dividerBefore) Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .08f)))
                    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .onGloballyPositioned { row ->
                            panelCoordinates?.takeIf { it.isAttached && row.isAttached }?.let {
                                val bounds = it.localBoundingBoxOf(row, clipBounds = false)
                                if (rows[index] != bounds) rows[index] = bounds
                            }
                        }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                            role = Role.Button, onClick = { if (open) action.onClick() })
                        .testTag("update_menu_action_$index")
                        .padding(horizontal = 20.dp, vertical = 14.dp), contentAlignment = Alignment.CenterStart) {
                        Text(action.label, color = Color(0xFFF7F4FF), fontSize = 14.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        }
    }
}

@Composable private fun Modifier.updateGlass(backdrop: Backdrop?, shape: Shape, tint: Float): Modifier =
    if (Build.VERSION.SDK_INT >= 33 && backdrop != null) highApiUpdateGlass(backdrop, shape, tint)
    else background(Color(0xFF242332).copy(alpha = .94f), shape)
        .border(1.dp, Color.White.copy(alpha = .13f), shape)

@RequiresApi(33)
private fun Modifier.highApiUpdateGlass(backdrop: Backdrop, shape: Shape, tint: Float): Modifier =
    drawBackdrop(backdrop, shape = { shape }, effects = {
        vibrancy(); blur(8.dp.toPx(), 8.dp.toPx())
        lens(refractionHeight = 12.dp.toPx(), refractionAmount = 14.dp.toPx())
    }, highlight = { iosIndicatorSpecular.copy(alpha = .75f) },
        onDrawSurface = { drawRect(Color(0xFF171624).copy(alpha = tint)) })

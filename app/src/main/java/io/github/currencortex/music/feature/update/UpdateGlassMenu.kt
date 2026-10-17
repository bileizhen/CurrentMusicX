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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
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
    val scale by animateFloatAsState(if (pressed) .93f else 1f, tween(140), label = "update glass press")
    Box(Modifier.size(48.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        .updateGlass(backdrop, CircleShape, .23f)
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = description }.testTag(tag), contentAlignment = Alignment.Center) { content() }
}

/** The panel is anchored below the overflow button; its drawing and touch shield share one clock. */
@Composable internal fun BoxScope.UpdateGlassMenu(open: Boolean, backdrop: Backdrop?,
    onDismiss: () -> Unit, toolbar: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
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
        Column(Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 64.dp, end = 16.dp).width(204.dp)
            .graphicsLayer {
                val p = expansion.value
                transformOrigin = TransformOrigin(1f, 0f)
                scaleX = .80f + .20f * p; scaleY = scaleX
                translationY = -6.dp.toPx() * (1f - p); alpha = p
            }
            .updateGlass(backdrop, RoundedCornerShape(24.dp), .62f)
            .pointerInput(open) {
                awaitEachGesture {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (!open) event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
            .padding(vertical = 6.dp).testTag("update_overflow_panel")
            .semantics { paneTitle = "更新菜单" }, content = content)
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

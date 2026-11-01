// SPDX-License-Identifier: GPL-3.0-only
// upstream TonalButton / LiquidGlassStyle, commit 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7.
// Native Compose material backend; public components contain no update business logic.
package io.github.currencortex.music.ui.component.nativeui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun rememberGlassBackdrop() = if (Build.VERSION.SDK_INT >= 33 && LocalView.current.isHardwareAccelerated &&
    isRuntimeShaderSupported()) rememberLayerBackdrop() else null

/** CommonPopupBox for a setting's value. The row can call open too; only the
 * selected anchor owns the captured glyph and the carried opening pointer. */
@Composable internal fun GlassMenuAnchor(enabled:Boolean=true,onOpen:()->Unit,
    content:@Composable (open:()->Unit,target:Modifier)->Unit) {
    val trigger=LocalGlassMenuTrigger.current
    val source=rememberGraphicsLayer()
    var origin by remember {mutableStateOf(Offset.Zero)}
    val click by rememberUpdatedState(onOpen)
    val currentEnabled by rememberUpdatedState(enabled)
    val open=remember(trigger,source){{
        if(currentEnabled) {trigger?.layer=source;click()}
    }}
    val target=Modifier.onGloballyPositioned {origin=it.boundsInWindow().topLeft}
        .pointerInput(trigger) {
            awaitEachGesture {
                val down=awaitFirstDown(false,PointerEventPass.Initial)
                if(!currentEnabled||down.isConsumed)return@awaitEachGesture
                down.consume();open()
                var moved=false;var cancelled=false
                try {
                    do {
                        val event=awaitPointerEvent(PointerEventPass.Initial)
                        val change=event.changes.firstOrNull{it.id==down.id}
                        val released=change?.changedToUp()==true
                        if(change==null||event.changes.count{it.pressed}>1||change.isConsumed)cancelled=true
                        if(change!=null) {
                            moved=moved||(change.position-down.position).getDistance()>viewConfiguration.touchSlop
                            if(moved&&!cancelled)trigger?.move?.invoke(change.position+origin)
                            event.changes.forEach{it.consume()}
                            if(!change.pressed) {
                                if(moved&&!cancelled&&released)trigger?.release?.invoke(change.position+origin)
                                break
                            }
                        }else break
                    }while(!cancelled)
                }finally{trigger?.cancel?.invoke()}
            }
        }.drawWithContent {
            val hidden=trigger!=null&&trigger.layer===source&&trigger.hidden
            if(!hidden)source.record {this@drawWithContent.drawContent()}
            if(!hidden)drawLayer(source)
        }
    content(open,target)
}

/** Circular toolbar control. The opening press is carried into its popup without lifting a finger. */
@Composable fun GlassIconButton(onClick: () -> Unit, description: String, modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalGlassBackdrop.current, size: Dp = 44.dp, enabled: Boolean = true, opensMenu: Boolean = false,
    onBoundsChanged: (Rect) -> Unit = {}, surface: Boolean = true, content: @Composable () -> Unit) {
    val press = rememberElasticPressState()
    val trigger = LocalGlassMenuTrigger.current.takeIf { opensMenu }
    var windowOrigin by remember { mutableStateOf(Offset.Zero) }
    val sourceLayer = rememberGraphicsLayer()
    val click by rememberUpdatedState(onClick)
    val currentEnabled by rememberUpdatedState(enabled)
    val pointer = if (trigger == null) Modifier else Modifier.pointerInput(trigger) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!currentEnabled || down.isConsumed) return@awaitEachGesture
            down.consume()
            val startedOpen = trigger.open
            trigger.layer = sourceLayer
            click()
            var moved = false
            var cancelled = false
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    val released = change?.changedToUp() == true
                    if (change == null || event.changes.count { it.pressed } > 1 || change.isConsumed) cancelled = true
                    if (change != null) {
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                        if (moved && !startedOpen && !cancelled) trigger.move?.invoke(change.position + windowOrigin)
                        event.changes.forEach { it.consume() }
                        if (!change.pressed) {
                            if (!startedOpen && moved && !cancelled && released) trigger.release?.invoke(change.position + windowOrigin)
                            else trigger.cancel?.invoke()
                            break
                        }
                    } else break
                } while (!cancelled)
            } finally { trigger.cancel?.invoke() }
        }
    }
    ElasticPress(modifier.size(size).then(pointer).onGloballyPositioned {
        val bounds = it.boundsInWindow(); windowOrigin = bounds.topLeft; onBoundsChanged(bounds)
    }.semantics { contentDescription = description }
        .clickable(enabled=enabled,role=Role.Button,interactionSource=remember{MutableInteractionSource()},indication=null,onClick={
            if (trigger != null) trigger.layer = sourceLayer
            onClick()
        }), enabled, press) {
        Box(Modifier.size(size).graphicsLayer { alpha = if(trigger?.hidden == true && trigger.layer === sourceLayer) 0f else 1f }
            .then(if (surface) Modifier.glassSurface(backdrop, CircleShape, glow={press.amount}) else Modifier)
            .drawBehind { if (press.amount > 0f) drawCircle(Color.White.copy(alpha = .06f * press.amount.coerceIn(0f, 1f))) }
            , contentAlignment = Alignment.Center) {
            Box(Modifier.drawWithContent {
                sourceLayer.record { this@drawWithContent.drawContent() }
                sourceLayer.alpha = 1f
                drawLayer(sourceLayer)
            }) { content() }
        }
    }
}

/** Shared filled/tonal action; the full pill stretches, while its hit target stays fixed. */
@Composable fun GlassActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    primary: Boolean = false, destructive: Boolean = false, enabled: Boolean = true,
    minHeight: Dp = 44.dp, backdrop: Backdrop? = LocalGlassBackdrop.current, fillWidth:Boolean=true) {
    val colors = MiuixTheme.colorScheme
    val press = rememberElasticPressState()
    val fill = if (destructive) colors.error else colors.primary
    val ink = if(destructive)Color.White else if(primary)colors.onPrimary else colors.onBackground
    ElasticPress(modifier.clickable(enabled=enabled,role=Role.Button,
        interactionSource=remember{MutableInteractionSource()},indication=null,onClick=onClick), enabled, press) {
        Box(Modifier.then(if(fillWidth)Modifier.fillMaxWidth()else Modifier).heightIn(min = minHeight).clip(CircleShape)
            .then(if (primary || destructive) Modifier.glassSurface(backdrop, CircleShape, tint = fill,glow={press.amount})
                else Modifier.glassSurface(backdrop, CircleShape,glow={press.amount}))
            .drawBehind { drawRect(ink.copy(alpha = .06f * press.amount.coerceIn(0f, 1f))) }
            .padding(horizontal = 16.dp, vertical = 11.dp), contentAlignment = Alignment.Center) {
            Text(label, color = ink.copy(alpha = if (enabled) 1f else .38f), fontSize = 15.sp,
                lineHeight = 20.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** One elastic capsule for related actions; children keep independent click targets. */
@Composable fun GlassButtonGroup(modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalGlassBackdrop.current, content: @Composable RowScope.() -> Unit) {
    val press = rememberElasticPressState()
    ElasticPress(modifier, state = press) {
        Row(Modifier.glassSurface(backdrop, CircleShape, glow = { press.amount }),
            verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** An action inside GlassButtonGroup: feedback and semantics without another glass outline. */
@Composable fun GlassGroupIconButton(onClick: () -> Unit, description: String, modifier: Modifier = Modifier,
    size: Dp = 44.dp, enabled: Boolean = true, content: @Composable () -> Unit) {
    val press = rememberElasticPressState()
    val ink = MiuixTheme.colorScheme.onSurface
    ElasticPress(modifier.size(size).semantics { contentDescription = description }
        .clickable(enabled = enabled, role = Role.Button, interactionSource = remember { MutableInteractionSource() },
            indication = null, onClick = onClick), enabled, press) {
        Box(Modifier.size(size).clip(CircleShape).drawBehind {
            drawCircle(ink.copy(alpha = .06f * press.amount.coerceIn(0f, 1f)))
        }, contentAlignment = Alignment.Center) { content() }
    }
}

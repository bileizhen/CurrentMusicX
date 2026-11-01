// SPDX-License-Identifier: GPL-3.0-only
// CommonSwitch adapted from upstream lib/ui/switch.dart
// @ 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7. See THIRD_PARTY_NOTICES.md.
package io.github.currencortex.music.ui.component.nativeui

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.ui.component.liquid.rememberCombinedBackdrop
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.*

private val switchBack=CubicBezierEasing(.175f,.885f,.32f,1.275f)
private val switchPress=CubicBezierEasing(.215f,.61f,.355f,1f)
val GlassSwitchPosition=SemanticsPropertyKey<Float>("GlassSwitchPosition")
@Immutable data class GlassSwitchFrame(val raw:Float,val position:Float,val lift:Float,
    val width:Float,val height:Float,val dragging:Boolean,val glass:Boolean)
val GlassSwitchGeometry=SemanticsPropertyKey<GlassSwitchFrame>("GlassSwitchGeometry")

/** CommonSwitch's tap lift, raw controller, directional back curve and drag handoff.
 * A null callback lets a containing menu row own interaction and pass its pressed state. */
@Composable fun GlassSwitch(checked:Boolean,onCheckedChange:((Boolean)->Unit)?,modifier:Modifier=Modifier,
    enabled:Boolean=true,pressed:Boolean=false) {
    val colors=MiuixTheme.colorScheme
    val scope=rememberCoroutineScope()
    val toggle=remember {Animatable(if(checked)1f else 0f)}
    val press=remember {Animatable(0f)}
    var toggleJob by remember {mutableStateOf<Job?>(null)}
    var pressJob by remember {mutableStateOf<Job?>(null)}
    var tapLiftJob by remember {mutableStateOf<Job?>(null)}
    var settleEpoch by remember {mutableIntStateOf(0)}
    var held by remember {mutableStateOf(false)}
    var dragged by remember {mutableStateOf<Float?>(null)}
    var linearPosition by remember {mutableStateOf(false)}
    var opening by remember {mutableStateOf(true)}
    val currentChecked by rememberUpdatedState(checked)
    val callback by rememberUpdatedState(onCheckedChange)
    val currentEnabled by rememberUpdatedState(enabled)
    val externallyPressed by rememberUpdatedState(pressed)
    val rtl=LocalLayoutDirection.current==LayoutDirection.Rtl
    val travel=with(LocalDensity.current){20.dp.toPx()}
    fun liftTo(target:Float) {
        pressJob?.cancel()
        pressJob=scope.launch {
            press.animateTo(target,tween((150*abs(target-press.value)).roundToInt().coerceAtLeast(1),easing=LinearEasing))
        }
    }
    fun handlePress() {tapLiftJob?.cancel();liftTo(1f)}
    fun liftAcross() {
        handlePress()
        tapLiftJob=scope.launch {delay(200);if(!held&&!externallyPressed)liftTo(0f)}
    }
    fun settle(value:Boolean) {
        settleEpoch++
        toggleJob?.cancel()
        val start=dragged?:toggle.value
        val target=if(value)1f else 0f
        opening=target>start
        toggleJob=scope.launch {
            toggle.snapTo(start);dragged=null
            toggle.animateTo(target,tween((300*abs(target-start)).roundToInt().coerceAtLeast(1),easing=LinearEasing))
        }
    }
    LaunchedEffect(checked) {
        val raw=dragged?:toggle.value
        if(raw==0f||raw==1f)linearPosition=false
        // Row/accessibility changes lift too, just as didUpdateWidget does upstream.
        if(enabled&&onCheckedChange!=null&&abs(raw-(if(checked)1f else 0f))>.5f)liftAcross()
        settle(checked)
    }
    LaunchedEffect(pressed,enabled) {
        if(enabled&&pressed)handlePress()
        else if(!held&&tapLiftJob?.isActive!=true)liftTo(0f)
    }
    val trackBackdrop=rememberGlassBackdrop()
    val parentBackdrop=LocalGlassBackdrop.current
    val thumbBackdrop=if(parentBackdrop!=null&&trackBackdrop!=null)rememberCombinedBackdrop(parentBackdrop,trackBackdrop)else trackBackdrop
    val raw=(dragged?:toggle.value).coerceIn(0f,1f)
    val position=if(linearPosition)raw else if(opening)switchBack.transform(raw)else 1-switchBack.transform(1-raw)
    val lift=switchPress.transform(press.value.coerceIn(0f,1f))
    val stretch=sin(PI.toFloat()*raw)
    val thumbWidth=glassLerp(glassLerp(24f,34f,stretch),40f+10f*stretch,lift)
    val thumbHeight=glassLerp(glassLerp(24f,22f,stretch),40f-2f*stretch,lift)
    val lens=LocalLiquidGlassEnabled.current&&Build.VERSION.SDK_INT>=33&&thumbBackdrop!=null&&lift>0f
    val trackColor=if(enabled)lerp(colors.surfaceContainerHighest,colors.primary,raw)
        else lerp(colors.surfaceContainerHighest.copy(alpha=.12f),colors.onSurface.copy(alpha=.12f),raw)
    // Miuix's outline is a dim border, not Material's medium-gray outline role.
    val thumbColor=if(enabled)lerp(colors.onBackgroundVariant,colors.onPrimary,raw)
        else lerp(colors.onSurface.copy(alpha=.38f),colors.surface,raw)
    val thumbFill=if(lens)thumbColor.copy(alpha=thumbColor.alpha*(1-lift))else lerp(thumbColor,Color.White,lift)
    Box(modifier.size(60.dp,48.dp).semantics {
        this[GlassSwitchPosition]=raw
        this[GlassSwitchGeometry]=GlassSwitchFrame(raw,position,lift,thumbWidth,thumbHeight,dragged!=null,lens)
    }.then(if(onCheckedChange==null)Modifier.clearAndSetSemantics{}else Modifier.semantics {
        role=Role.Switch;toggleableState=ToggleableState(checked)
        if(!enabled)disabled()
        onClick {if(enabled)callback?.invoke(!currentChecked);enabled}
    }.pointerInput(rtl,enabled) {
        awaitEachGesture {
            val down=awaitFirstDown(false)
            if(!currentEnabled||down.isConsumed)return@awaitEachGesture
            down.consume();held=true;handlePress()
            var last=down.position;var total=Offset.Zero;var moving=false;var released=false
            try {
                while(true) {
                    val event=awaitPointerEvent()
                    val c=event.changes.firstOrNull {it.id==down.id}?:break
                    val up=c.changedToUp()
                    if(c.isConsumed||event.changes.count {it.pressed}>1)break
                    total+=c.position-last
                    if(!moving&&abs(total.y)>viewConfiguration.touchSlop&&abs(total.y)>abs(total.x))break
                    if(!moving&&abs(total.x)>viewConfiguration.touchSlop) {
                        moving=true;linearPosition=true;toggleJob?.cancel()
                        dragged=(toggle.value+total.x*(if(rtl)-1 else 1)/travel).coerceIn(0f,1f)
                    }else if(moving)dragged=((dragged?:toggle.value)+(c.position.x-last.x)*(if(rtl)-1 else 1)/travel).coerceIn(0f,1f)
                    last=c.position
                    if(moving)c.consume()
                    if(!c.pressed) {
                        if(up&&currentEnabled) {
                            c.consume();released=true;held=false
                            if(!moving) {callback?.invoke(!currentChecked);liftAcross()}
                            else {
                                if(!externallyPressed)liftTo(0f)
                                val target=(dragged?:toggle.value)>=.5f
                                if(target!=currentChecked)callback?.invoke(target)
                            }
                            // Wait for the owner's accepted value; an owner can reject the change.
                            val epoch=settleEpoch
                            scope.launch {withFrameNanos{};if(epoch==settleEpoch&&!held)settle(currentChecked)}
                        }
                        break
                    }
                }
            }finally {
                held=false
                if(!released) {
                    tapLiftJob?.cancel();if(!externallyPressed)liftTo(0f)
                    settle(currentChecked)
                }
            }
        }
    })) {
        Canvas(Modifier.matchParentSize().then(if(trackBackdrop!=null)Modifier.layerBackdrop(trackBackdrop)else Modifier)) {
            val trackSize=Size(52.dp.toPx(),32.dp.toPx())
            drawRoundRect(trackColor,Offset((size.width-trackSize.width)/2,(size.height-trackSize.height)/2),trackSize,CornerRadius(trackSize.height/2))
        }
        val along=16f+20f*position
        val centerX=4f+if(rtl)52f-along else along
        Box(Modifier.absoluteOffset((centerX-thumbWidth/2).dp,(24f-thumbHeight/2).dp)
            .requiredSize(thumbWidth.dp,thumbHeight.dp)
            .then(if(lift>0f)Modifier.dropShadow(CircleShape,Shadow(radius=6.dp,offset=DpOffset(0.dp,2.dp),color=Color.Black.copy(alpha=.2f*lift)))else Modifier)
            .then(if(lens)Modifier.glassSurface(thumbBackdrop,CircleShape,lens=true,strength={lift})else Modifier)
            .background(thumbFill,CircleShape))
    }
}

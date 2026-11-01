// SPDX-License-Identifier: GPL-3.0-only
// Reuses the upstream-derived ElasticPress and LiquidGlass material backend.
// upstream's SliderDefaultsM3 itself is a Material slider, not a glass slider.
package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.ui.component.liquid.rememberCombinedBackdrop
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt

@Immutable data class GlassSliderFrame(val fraction:Float,val held:Boolean,val thumb:ElasticPressFrame,val glass:Float)
val GlassSliderGeometry=SemanticsPropertyKey<GlassSliderFrame>("GlassSliderGeometry")

/** Shared setting slider. Only the glass thumb stretches; its value and touch track stay fixed.
 * Horizontal drags preview continuously and commit once on release. Vertical swipes belong to
 * the page, and cancelled drags restore the preview without committing. */
@Composable fun GlassSlider(value:Float,onValueChange:(Float)->Unit,modifier:Modifier=Modifier,
    enabled:Boolean=true,valueRange:ClosedFloatingPointRange<Float> = 0f..1f,steps:Int=0,
    onValueChangeFinished:(()->Unit)?=null,reverseDirection:Boolean=false,
    showKeyPoints:Boolean=false,keyPoints:List<Float>?=null,magnetThreshold:Float=.02f,
    hapticEffect:SliderDefaults.SliderHapticEffect=SliderDefaults.DefaultHapticEffect,
    backdrop:Backdrop?=LocalGlassBackdrop.current) {
    require(valueRange.start.isFinite()&&valueRange.endInclusive.isFinite()&&valueRange.start<valueRange.endInclusive)
    require(steps>=0)
    val colors=MiuixTheme.colorScheme
    val density=LocalDensity.current
    val reversed=(LocalLayoutDirection.current==LayoutDirection.Rtl)!=reverseDirection
    val haptics=LocalHapticFeedback.current
    val press=rememberElasticPressState()
    val radius=with(density){14.dp.toPx()}
    val growth=with(density){16.dp.toPx()}
    val span=valueRange.endInclusive-valueRange.start
    val coerced=if(value.isFinite())value.coerceIn(valueRange)else valueRange.start
    val change by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onValueChangeFinished)
    val current by rememberUpdatedState(coerced)
    var width by remember {mutableFloatStateOf(0f)}
    var held by remember {mutableStateOf(false)}
    var preview by remember {mutableStateOf<Float?>(null)}
    val displayed by animateFloatAsState(preview?:coerced,
        if(held)snap()else spring(dampingRatio=1f,stiffness=500f),label="glass slider value")
    val fraction=((displayed-valueRange.start)/span).coerceIn(0f,1f)
    val points=remember(keyPoints,steps,valueRange) {
        (keyPoints?:if(steps>0)(0..steps+1).map {valueRange.start+span*it/(steps+1)}else emptyList())
            .filter {it.isFinite()&&it in valueRange}.distinct().sorted()
    }
    fun resolve(target:Float):Float {
        val bounded=target.coerceIn(valueRange)
        if(steps>0)return valueRange.start+span*((bounded-valueRange.start)/span*(steps+1)).roundToInt()/(steps+1)
        val near=points.minByOrNull {abs(it-bounded)}
        return if(near!=null&&abs(near-bounded)<=span*magnetThreshold.coerceIn(0f,1f))near else bounded
    }
    val resolveValue by rememberUpdatedState<(Float)->Float> {resolve(it)}
    val trackBackdrop=rememberGlassBackdrop()
    val thumbBackdrop=if(backdrop!=null&&trackBackdrop!=null)rememberCombinedBackdrop(backdrop,trackBackdrop)else trackBackdrop?:backdrop
    val glassAmount=if(enabled&&LocalLiquidGlassEnabled.current&&android.os.Build.VERSION.SDK_INT>=33&&thumbBackdrop!=null)
        press.amount.coerceIn(0f,1f)else 0f
    Box(modifier.fillMaxWidth().heightIn(min=48.dp).onSizeChanged {
        width=it.width.toFloat()
        press.size=Size(growth*2,growth*2)
    }
        .semantics {
            progressBarRangeInfo=ProgressBarRangeInfo(coerced,valueRange,steps)
            if(!enabled)disabled()
            this[GlassSliderGeometry]=GlassSliderFrame(fraction,held,press.frame(growth),glassAmount)
            setProgress {target->
                if(!enabled||!target.isFinite())false else {
                    change(resolveValue(target));finish?.invoke();true
                }
            }
        }.onKeyEvent {event->
            if(!enabled||event.type!=KeyEventType.KeyDown)false else {
                val direction=when(event.key) {
                    Key.DirectionRight->if(reversed)-1 else 1
                    Key.DirectionLeft->if(reversed)1 else -1
                    Key.DirectionUp->1
                    Key.DirectionDown->-1
                    else->0
                }
                val target=when(event.key) {
                    Key.MoveHome->valueRange.start
                    Key.MoveEnd->valueRange.endInclusive
                    else->if(direction==0)null else {
                        if(points.isNotEmpty()) {
                            if(direction>0)points.firstOrNull {it>current+.00001f}?:valueRange.endInclusive
                            else points.lastOrNull {it<current-.00001f}?:valueRange.start
                        }else current+direction*span/(if(steps>0)steps+1 else 100)
                    }
                }
                if(target==null)false else {change(resolveValue(target));finish?.invoke();true}
            }
        }.focusable(enabled)
        .pointerInput(enabled,reversed,valueRange,radius,steps,keyPoints,magnetThreshold,hapticEffect) {
            if(!enabled)return@pointerInput
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                if(down.isConsumed)return@awaitEachGesture
                val start=current
                val travel=(size.width-2*radius).coerceAtLeast(1f)
                val startFraction=(start-valueRange.start)/span
                val thumbX=radius+(if(reversed)1-startFraction else startFraction)*travel
                // Grabbing the thumb preserves the finger's offset instead of jumping the value.
                val grab=if(abs(down.position.x-thumbX)<=radius*1.5f)down.position.x-thumbX else 0f
                var moving=false;var committed=false;var lastValue=start
                held=true;preview=start;press.press(Offset.Zero)
                fun update(point:Offset) {
                    val x=point.x-grab
                    val visual=((x-radius)/travel).coerceIn(0f,1f)
                    val target=resolveValue(valueRange.start+(if(reversed)1-visual else visual)*span)
                    if(target!=lastValue) {
                        val edge=target==valueRange.start||target==valueRange.endInclusive
                        val step=points.any {abs(it-target)<span*.00001f}
                        if(hapticEffect!=SliderDefaults.SliderHapticEffect.None&&
                            (edge||(hapticEffect==SliderDefaults.SliderHapticEffect.Step&&step)))
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        lastValue=target;preview=target;change(target)
                    }
                    press.drag(Offset(x-x.coerceIn(radius,size.width-radius),(point.y-down.position.y)*.5f))
                }
                try {
                    while(true) {
                        val event=awaitPointerEvent()
                        val c=event.changes.firstOrNull {it.id==down.id}?:break
                        if(c.isConsumed||event.changes.count {it.pressed}>1)break
                        val delta=c.position-down.position
                        if(!moving&&abs(delta.y)>viewConfiguration.touchSlop&&abs(delta.y)>abs(delta.x))break
                        if(!moving&&abs(delta.x)>viewConfiguration.touchSlop)moving=true
                        if(moving) {update(c.position);c.consume()}
                        if(!c.pressed) {
                            if(c.changedToUpIgnoreConsumed()) {
                                update(c.position);c.consume();committed=true;finish?.invoke()
                            }
                            break
                        }
                    }
                }finally {
                    if(!committed&&lastValue!=start)change(start)
                    preview=null;held=false;press.release()
                }
            }
        },contentAlignment=Alignment.CenterStart) {
        // Capture only the inert track, never a parent layer containing the thumb itself.
        val center=radius+(if(reversed)1-fraction else fraction)*(width-2*radius).coerceAtLeast(0f)
        val trackColor=if(enabled)colors.sliderBackground else colors.disabledSecondary
        val fillColor=if(enabled)colors.primary else colors.disabledPrimarySlider
        val thumbColor=if(enabled)colors.onPrimary else colors.disabledOnPrimary
        Canvas(Modifier.fillMaxWidth().height(28.dp)
            .then(if(trackBackdrop!=null)Modifier.layerBackdrop(trackBackdrop)else Modifier)
            .clip(CircleShape).background(trackColor)) {
                // Match the original Miuix track, including the rounded fill beyond the knob.
                drawLine(fillColor,Offset(if(reversed)size.width else 0f,size.height/2),
                    Offset(center,size.height/2),size.height,StrokeCap.Round)
                if(showKeyPoints)points.forEach {point->
                    val p=(point-valueRange.start)/span
                    val x=radius+(if(reversed)1-p else p)*(size.width-2*radius).coerceAtLeast(0f)
                    drawCircle(if(p<=fraction)colors.sliderKeyPointForeground else colors.sliderKeyPoint,
                        size.height/7.5f,Offset(x,size.height/2))
                }
        }
        val x=radius+(if(reversed)1-fraction else fraction)*(width-2*radius).coerceAtLeast(0f)
        val frame=press.frame(growth)
        val swell=1f+frame.lift*.25f
        val diameter=20.16f+(40f-20.16f)*glassAmount
        val lensWidth=diameter*frame.scaleX/swell
        val lensHeight=diameter*frame.scaleY/swell
        // Like GlassSwitch, size and position the actual lens before it samples the backdrop.
        // Scaling a smaller captured lens afterwards also scales its samples and rim optics.
        Box(Modifier.align(AbsoluteAlignment.CenterLeft).absoluteOffset {
            IntOffset((x+frame.pull.x-with(density){lensWidth.dp.toPx()}/2).roundToInt(),frame.pull.y.roundToInt())
        }.requiredSize(lensWidth.dp,lensHeight.dp)) {
            Box(Modifier.matchParentSize().graphicsLayer {alpha=glassAmount}
                .glassSurface(thumbBackdrop,CircleShape,lens=true))
            Box(Modifier.matchParentSize().background(thumbColor.copy(alpha=thumbColor.alpha*(1-glassAmount)),CircleShape))
        }
    }
}

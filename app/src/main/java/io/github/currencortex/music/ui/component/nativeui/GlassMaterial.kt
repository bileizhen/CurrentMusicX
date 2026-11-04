// SPDX-License-Identifier: GPL-3.0-only
// LiquidGlassStyle / liquid_glass.frag adapted from chen08209's upstream project
// @ 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7. See THIRD_PARTY_NOTICES.md.
package io.github.currencortex.music.ui.component.nativeui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.*

/** The existing appearance switch controls the full optical material; there is no quality tier UI. */
val LocalLiquidGlassEnabled = staticCompositionLocalOf { true }
val LocalGlassBlurEnabled = staticCompositionLocalOf { true }
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }
val LiquidGlassOpticsKey = androidx.compose.ui.semantics.SemanticsPropertyKey<Boolean>("LiquidGlassOptics")

internal interface GlassOpticalShape : Shape {
    fun opticalGeometry(size: Size, density: Density, direction: LayoutDirection): GlassOpticalGeometry
}
internal data class GlassOpticalGeometry(val pane: Rect, val radius: Float,
    val droplet: Rect = Rect.Zero, val blend: Float = 0f,
    val extra: Rect = Rect.Zero, val extraBlend: Float = 0f)

@Composable fun Modifier.glassSurface(backdrop: Backdrop?, shape: Shape, menu: Boolean = false,
    tint: Color? = null, glow: () -> Float = { 0f }, lens: Boolean = false,
    menuProgress: () -> Float = { if(menu)1f else 0f }, frost:()->Float = {if(menu)12f else 2.5f},
    clipToShape:Boolean=true,strength:()->Float={1f},materialStrength:()->Float=strength,
    alert: Boolean = false): Modifier {
    val dark = MiuixTheme.colorScheme.onBackground.luminance() > .5f
    val glass = LocalLiquidGlassEnabled.current && Build.VERSION.SDK_INT >= 33
    val frosted = LocalGlassBlurEnabled.current
    val source = LocalContext.current.assets
    val shader = remember(source) { source.open("shaders/liquid_glass.agsl").bufferedReader().use { it.readText() } }
    val fill = tint ?: if(dark) Color(0xFF252528) else Color(0xFFFDFDFD)
    val gaussian=remember {object:LinkedHashMap<Float,RenderEffect>(32,.75f,true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<Float,RenderEffect>?)=size>32
    }}
    // Miuix's retained inner clip does not invalidate when only Shape changes. Clip outside
    // that node, so animated droplets and unfolding menus cannot retain a one-frame outline.
    val clipped = semantics {this[LiquidGlassOpticsKey]=glass&&backdrop!=null}
        .then(if(clipToShape)Modifier.graphicsLayer { this.shape=shape;clip=true }
            else if(!glass)Modifier.drawWithContent {
                val path=when(val outline=shape.createOutline(size,layoutDirection,this)) {
                    is Outline.Generic->outline.path
                    is Outline.Rounded->Path().apply{addRoundRect(outline.roundRect)}
                    is Outline.Rectangle->Path().apply{addRect(outline.rect)}
                }
                clipPath(path){this@drawWithContent.drawContent()}
            }else Modifier)
    val scene = LocalPageGlassScene.current
    val sample = if (backdrop != null && scene != null && scene.owns(backdrop))
        remember(scene, backdrop) { PageGlassSample(backdrop, scene) } else null
    val positioned = if (sample == null) clipped else clipped.onGloballyPositioned(sample::place)
    return if(backdrop!=null && Build.VERSION.SDK_INT>=33) positioned.drawBackdrop(sample ?: backdrop,
        shape={RectangleShape},effects={
            val amount=strength().coerceIn(0f,1f)
            val material=materialStrength().coerceIn(0f,1f)
            val sigma = if(frosted&&!lens) frost()*material else 0f
            // Frost may be reduced internally by Skia, but the optical pass and SDF mask
            // run at full resolution. Downsampling the finished pane softens its boundary.
            val sigmaPx=round(sigma*density*16)/16
            if(sigmaPx>0f) effect(gaussian.getOrPut(sigmaPx) {
                android.graphics.RenderEffect.createBlurEffect(sigmaPx,sigmaPx,
                    android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
            })
            if(glass && size.width.isFinite() && size.height.isFinite() && size.width>0f && size.height>0f) {
                val geometry=(shape as? GlassOpticalShape)?.opticalGeometry(size,this,layoutDirection)
                    ?: when(val outline=shape.createOutline(size,layoutDirection,this)) {
                        is Outline.Rounded -> outline.roundRect.let { GlassOpticalGeometry(Rect(it.left,it.top,it.right,it.bottom),it.topLeftCornerRadius.x) }
                        else -> GlassOpticalGeometry(Rect(Offset.Zero,size),size.minDimension/2)
                    }
                applyLiquidGlass(shader,geometry,fill,menuProgress().coerceIn(0f,1f),dark,tint!=null,glow(),lens,amount,material,alert)
            }
        },onDrawSurface={ if(!glass)drawOutline(shape.createOutline(size,layoutDirection,this),fill.copy(alpha=(if(menu).92f else .82f)*strength().coerceIn(0f,1f))) })
    else clipped.drawBehind {drawOutline(shape.createOutline(size,layoutDirection,this),fill.copy(alpha=.97f*strength().coerceIn(0f,1f)))}
}

private fun BackdropEffectScope.applyLiquidGlass(source:String,g:GlassOpticalGeometry,tint:Color,
    menu:Float,dark:Boolean,tinted:Boolean,glow:Float,lens:Boolean,amount:Float,material:Float,alert:Boolean) {
    val pad=padding
    val sf=downscaleFactor.coerceAtLeast(1).toFloat()
    val d=density
    val pane=g.pane
    val drop=g.droplet
    val extra=g.extra
    val r=g.radius.coerceIn(0f,pane.size.minDimension/2)
    val pair=if(drop.isEmpty)pane else Rect(min(pane.left,drop.left),min(pane.top,drop.top),
        max(pane.right,drop.right),max(pane.bottom,drop.bottom))
    val union=if(extra.isEmpty)pair else Rect(min(pair.left,extra.left),min(pair.top,extra.top),
        max(pair.right,extra.right),max(pair.bottom,extra.bottom))
    val saturation=if(lens)1f else if(tinted)1.2f else glassLerp(if(dark)2.84f else 2.08f,if(dark)1.75f else 2f,menu)
    val brightness=if(lens&&dark)1.33f else glassLerp(1f,if(dark)1.8f else 1.1f,menu)
    val opacity=if(lens)0f else if(tinted).82f else glassLerp(if(dark).62f else .54f,if(dark).63f else .55f,menu)
    val rim=if(lens){if(dark).3f else .6f}else glassLerp(if(tinted){if(dark).14f else .2f}else .28f,0f,menu)
    // LiquidGlassStyle.tinted has its own hairline gains; regular glass uses .25/.75.
    val hairline=if(alert)0f else if(lens).42f else if(dark)0f else if(tinted).34f else .25f
    val sideHairline=if(lens).42f else if(tinted){if(dark).73f else .5f}else .75f
    val halfWidth=pane.width/2/d;val halfHeight=pane.height/2/d;val round=r/d
    var lastTurn=0f
    fun turn(along:Float,down:Float):Float {
        lastTurn=max(lastTurn,atan2(halfWidth-min(along*round,halfWidth),halfHeight-min(down*round,halfHeight)))
        return lastTurn
    }
    val t1=turn(1.53f,0f);val t2=turn(1f,0f);val t3=turn(.3f,.3f)
    val t4=turn(0f,1f);val t5=turn(0f,1.5f);val t6=turn(0f,2f)
    // Shader coordinates are dp, input samples are in the padded/downsampled capture's pixels.
    runtimeShaderEffect("LiquidGlassUltra",source,"content") {
        setFloatUniform("uFrame",-pad/d,-pad/d,sf/d,sf/d)
        setFloatUniform("uSample",-pad/d,-pad/d,sf/d,sf/d)
        setFloatUniform("uPane",pane.center.x/d,pane.center.y/d,pane.width/2/d,pane.height/2/d)
        setFloatUniform("uDrop",drop.center.x/d,drop.center.y/d,drop.width/2/d,drop.height/2/d)
        setFloatUniform("uExtra",extra.center.x/d,extra.center.y/d,extra.width/2/d,extra.height/2/d)
        setFloatUniform("uExtraBlend",g.extraBlend/d)
        setFloatUniform("uGeometry",r/d,g.blend/d,if(drop.width>.1f)1f else 0f,sf/d)
        setFloatUniform("uHalfMinor",union.size.minDimension/2/d)
        setFloatUniform("uOptics",(if(lens)-4.3f else 60f)*amount,
            glassLerp(1f,saturation,material),glassLerp(1f,brightness,material),if(lens)5.5f else 20f)
        setFloatUniform("uTint",tint.red,tint.green,tint.blue,opacity*material)
        setFloatUniform("uLight",rim*amount,if(lens)1.1f else 1.2f,if(lens)0f else .17f,if(lens)0f else .38f)
        setFloatUniform("uEdge",hairline*amount,
            sideHairline*(if(lens)1f else 1-menu)*amount,(if(dark).22f else .16f)*menu*amount,
            (if(lens)0f else if(dark)glassLerp(-.4f,-.53f,menu)else .4f*(1-menu))*material)
        setFloatUniform("uLens",if(lens)6.5f*amount else 0f,if(lens)1.5f*amount else 0f,if(lens).045f*amount else 0f)
        setFloatUniform("uGlow",pane.center.x/d,pane.center.y/d,glow.coerceIn(0f,1f)*.16f*amount,pane.height/d/sqrt(2f))
        setFloatUniform("uBounds",(size.width+2*pad)/sf,(size.height+2*pad)/sf)
        setFloatUniform("uShineTurns0",0f,t1,t2,t3)
        setFloatUniform("uShineTurns1",t4,t5,t6,PI.toFloat()/2)
    }
}

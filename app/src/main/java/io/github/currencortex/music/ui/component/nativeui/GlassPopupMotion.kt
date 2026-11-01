// SPDX-License-Identifier: GPL-3.0-only
// upstream popup.dart _openingOf / _paintReturn and motion.dart riseAndFall,
// commit 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7. See THIRD_PARTY_NOTICES.md.
package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.*

@Immutable data class GlassPopupFrame(val bounds: Rect, val radius: Float, val droplet: Rect,
    val content: Float, val glyph: Float, val progress: Float,
    val blend:Float=0f, val frost:Float=2.5f)

internal fun glassSpring(time: Float, stiffness: Float, damping: Float, start: Float = 0f,
    end: Float = 1f, velocity: Float = 0f): Float {
    val omega = sqrt(stiffness); val decay = damping * omega
    val frequency = omega * sqrt((1 - damping * damping).coerceAtLeast(.000001f))
    val offset = start - end
    return end + exp(-decay * time) * (offset * cos(frequency * time) +
        (velocity + decay * offset) / frequency * sin(frequency * time))
}
internal fun glassRiseAndFall(time: Float, peak: Float, sharpness: Float): Float {
    if (time <= 0f) return 0f
    val x = time / peak
    return x.pow(sharpness) * exp(sharpness * (1 - x))
}
internal fun glassLerp(a: Float, b: Float, t: Float) = a + (b - a) * t
internal fun glassLerp(a: Rect, b: Rect, t: Float) = Rect(glassLerp(a.left,b.left,t),
    glassLerp(a.top,b.top,t),glassLerp(a.right,b.right,t),glassLerp(a.bottom,b.bottom,t))
private fun rectAt(center: Offset, width: Float, height: Float): Rect = Rect(
    center - Offset(width.coerceAtLeast(0f)/2, height.coerceAtLeast(0f)/2),
    Size(width.coerceAtLeast(0f),height.coerceAtLeast(0f)))

internal fun glassOpening(anchor: Rect, card: Rect, time: Float, density: Float): GlassPopupFrame {
    val p = glassSpring(time, 400f, .754f)
    val scale = glassLerp(.41f * min(anchor.width,anchor.height) / card.width, 1f, p)
    val height = glassLerp(card.width, card.height, p)
    val distance = card.center.y - anchor.center.y
    val dip = sign(distance) * (min(1f, abs(distance)/(30*density))*23*density + .124f*abs(distance)) *
        glassRiseAndFall(time,.098f,3.3f)
    val center = anchor.center * (1-p) + card.center*p + Offset(0f,dip)
    val corners = 24*density + card.width*.48f * glassSpring(max(0f,time-.07f),324f,.75f,1f,0f)
    val extent = glassLerp(1f,.25f,p)
    val inner = card.deflate(max(anchor.width,anchor.height)*.25f/2 + (28+8)*density)
    val sunk = anchor.center*.75f + card.center*.25f
    val target = if(inner.isEmpty) card.center else Offset(sunk.x.coerceIn(inner.left,inner.right),sunk.y.coerceIn(inner.top,inner.bottom))
    return GlassPopupFrame(rectAt(center,card.width*scale,height*scale),max(0f,min(height/2,corners)*scale),
        rectAt(anchor.center*(1-p)+target*p,anchor.width*extent,anchor.height*extent),p.coerceIn(0f,1f),
        1f-(p/.25f).coerceIn(0f,1f),p,28*density,12f)
}

private val popupEaseIn = CubicBezierEasing(.42f,0f,1f,1f)
private val glyphEase = CubicBezierEasing(.25f,.1f,.25f,1f)
private val absorptionEase = CubicBezierEasing(.42f,0f,.58f,1f)
internal fun glassReturning(anchor: Rect, start: GlassPopupFrame, time: Float, density: Float): GlassPopupFrame {
    val ringing = 1 - popupEaseIn.transform(((time-.4f)/.1f).coerceIn(0f,1f))
    val p = 1 + (glassSpring(time,165f,.8f)-1)*ringing
    val from = start.bounds
    val distance = from.center.y-anchor.center.y
    val dip = min(.21f*abs(distance),22*density)*glassRiseAndFall(time,.072f,2.4f)
    val lift = min(.25f*abs(distance),19*density)*glassRiseAndFall(time,.176f,4.9f)
    val scale = glassLerp(1f,.41f*min(anchor.width,anchor.height)/max(from.width,from.height),p)
    val platter = rectAt(from.center*(1-p)+anchor.center*p+Offset(0f,sign(distance)*dip*ringing),
        from.width*scale,glassLerp(from.height,from.width,p)*scale)
    val droplet = rectAt(start.droplet.center*(1-p)+anchor.center*p-Offset(0f,sign(distance)*lift*ringing),
        glassLerp(start.droplet.width,anchor.width,p),glassLerp(start.droplet.height,anchor.height,p))
    // Upstream absorbs the neck between 60% and 90% of dismissal. A fixed blend
    // leaves a swollen rim until the final frame, then visibly snaps to the real button.
    val absorbed=absorptionEase.transform(((time/.5f-.6f)/.3f).coerceIn(0f,1f))
    return GlassPopupFrame(platter,max(0f,glassLerp(start.radius,from.width/2,p)*scale),droplet,
        start.content*(1-time/.1f).coerceIn(0f,1f),.2f+.8f*glyphEase.transform((time/.47f).coerceIn(0f,1f)),1-p,
        28*density*min(1f,time/.05f)*(1-absorbed),glassLerp(12f,2.5f,absorbed))
}

/** Captures the painted frame before retargeting, including a close interrupted by another press. */
@Stable internal class GlassPopupMotion {
    val clock = Animatable(1f)
    var opening by mutableStateOf(false)
        private set
    private var from by mutableStateOf<GlassPopupFrame?>(null)
    var shown by mutableStateOf(false)
        private set
    fun frame(anchor: Rect, card: Rect, density: Float): GlassPopupFrame {
        val settled = GlassPopupFrame(card,24*density,rectAt(anchor.center*.75f+card.center*.25f,
            anchor.width*.25f,anchor.height*.25f),1f,0f,1f,28*density,12f)
        if (opening) {
            if(clock.value>=1f) return settled
            val next=glassOpening(anchor,card,clock.value*.45f,density)
            val old=from ?: return next
            val first=glassOpening(anchor,card,0f,density)
            val residual=1-next.progress.coerceIn(0f,1f)
            return next.copy(bounds=Rect(next.bounds.topLeft+(old.bounds.topLeft-first.bounds.topLeft)*residual,
                Size((next.bounds.width+(old.bounds.width-first.bounds.width)*residual).coerceAtLeast(0f),
                    (next.bounds.height+(old.bounds.height-first.bounds.height)*residual).coerceAtLeast(0f))),
                radius=max(0f,next.radius+(old.radius-first.radius)*residual),
                droplet=glassLerp(next.droplet,old.droplet.translate(next.droplet.topLeft-first.droplet.topLeft),residual),
                content=next.content+(old.content-first.content)*residual,
                glyph=next.glyph+(old.glyph-first.glyph)*residual)
        }
        return glassReturning(anchor,from ?: settled,clock.value*.5f,density)
    }
    suspend fun animate(open: Boolean, anchor: Rect, card: Rect, density: Float) {
        val previous=if(shown) frame(anchor,card,density) else null
        clock.stop()
        clock.snapTo(0f)
        from=previous
        opening=open
        shown=true
        clock.animateTo(1f,tween(if(open)450 else 500,easing=LinearEasing))
        if(!open) shown=false
    }
}

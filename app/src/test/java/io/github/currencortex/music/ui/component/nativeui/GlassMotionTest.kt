package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class GlassMotionTest {
    private val anchor=Rect(300f,24f,344f,68f)
    private val card=Rect(152f,16f,344f,225f)
    private fun assertRect(expected:Rect,actual:Rect) {
        assertEquals(expected.left,actual.left,.001f);assertEquals(expected.top,actual.top,.001f)
        assertEquals(expected.right,actual.right,.001f);assertEquals(expected.bottom,actual.bottom,.001f)
    }

    @Test fun interruptedReturnReopensFromItsPaintedGeometry()=runTest {
        val clock=BroadcastFrameClock()
        val motion=GlassPopupMotion()
        var tick=0L
        fun frames(n:Int) {repeat(n){clock.sendFrame(tick++*16_000_000);runCurrent()}}
        backgroundScope.launch(clock){motion.animate(true,anchor,card,1f)};runCurrent();frames(8)
        val opening=motion.frame(anchor,card,1f)
        backgroundScope.launch(clock){motion.animate(false,anchor,card,1f)};runCurrent()
        assertRect(opening.bounds,motion.frame(anchor,card,1f).bounds)
        assertRect(opening.droplet,motion.frame(anchor,card,1f).droplet)
        frames(7)
        val returning=motion.frame(anchor,card,1f)
        backgroundScope.launch(clock){motion.animate(true,anchor,card,1f)};runCurrent()
        val reopening=motion.frame(anchor,card,1f)
        assertRect(returning.bounds,reopening.bounds)
        assertRect(returning.droplet,reopening.droplet)
        assertEquals(returning.radius,reopening.radius,.001f)
        frames(40)
        assertEquals(card,motion.frame(anchor,card,1f).bounds)
        backgroundScope.launch(clock){motion.animate(false,anchor,card,1f)};runCurrent()
        frames(35)
        assertFalse(motion.shown)
    }

    @Test fun dropletReturnsExactlyToSourceAndAllIntermediateExtentsStayFinite() {
        val opened=glassOpening(anchor,card,.45f,1f)
        for(i in 0..500) {
            val f=glassReturning(anchor,opened,i/1000f,1f)
            assertTrue(f.bounds.width>0f&&f.bounds.height>0f&&f.radius>=0f)
            assertTrue(f.bounds.left.isFinite()&&f.droplet.width.isFinite())
        }
        val returned=glassReturning(anchor,opened,.5f,1f)
        assertEquals(anchor,returned.droplet)
        assertEquals(1f,returned.glyph,.001f)
        assertEquals(0f,returned.content,.001f)
        val handoff=glassReturning(anchor,opened,.48f,1f)
        assertEquals("The goo neck is absorbed before handoff",0f,handoff.blend,.0001f)
        assertEquals("Frost already matches the real button",2.5f,handoff.frost,.0001f)
        assertEquals("Source glyph finishes before the route is removed",1f,handoff.glyph,.0001f)
    }

    @Test fun pullIsSymmetricAndApproachesItsLimitWithoutJumping() {
        val limit=20f
        var prior=0f
        for(i in 0..1000) {
            val pull=elasticRubberBand(i.toFloat(),limit)
            assertTrue(pull>=prior&&pull<limit)
            assertEquals(-pull,elasticRubberBand(-i.toFloat(),limit),.001f)
            prior=pull
        }
        assertEquals(0f,elasticRubberBand(100f,0f),0f)
    }
}

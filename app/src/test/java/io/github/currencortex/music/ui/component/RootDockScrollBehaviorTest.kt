package io.github.currencortex.music.ui.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RootDockScrollBehaviorTest {
    private val behavior = RootDockScrollBehavior(12f, 24f)
    private fun scroll(y: Float, x: Float = 0f,
        source: NestedScrollSource = NestedScrollSource.UserInput) =
        behavior.onPostScroll(Offset(x, y), Offset.Zero, source)

    @Test fun browsingCollapsesWithoutConsumingListMovement() {
        assertEquals(Offset.Zero, scroll(-5f))
        assertFalse(behavior.collapsed)
        assertEquals(Offset.Zero, scroll(-7f))
        assertTrue(behavior.collapsed)
    }

    @Test fun reverseScrollRevealsWithoutRequiringTheWholeListToBeTraversed() {
        scroll(-80f)
        scroll(12f)
        assertTrue(behavior.collapsed)
        scroll(12f)
        assertFalse(behavior.collapsed)
    }

    @Test fun jitterAndHorizontalCarouselsDoNotToggleNavigation() {
        repeat(20) { scroll(-5f); scroll(5f) }
        assertFalse(behavior.collapsed)
        scroll(-30f, 120f)
        assertFalse(behavior.collapsed)
        scroll(-12f)
        scroll(30f, -120f)
        assertTrue(behavior.collapsed)
    }

    @Test fun topOverscrollRevealsButBottomOverscrollDoesNotCollapse() {
        behavior.onPostScroll(Offset.Zero, Offset(0f, -100f), NestedScrollSource.UserInput)
        assertFalse(behavior.collapsed)
        scroll(-12f)
        assertEquals(Offset.Zero, behavior.onPostScroll(Offset.Zero, Offset(0f, 1f), NestedScrollSource.UserInput))
        assertFalse(behavior.collapsed)
    }

    @Test fun flingsAndAutomaticScrollingDoNotReverseTheUserDecision() {
        scroll(-100f, source = NestedScrollSource.SideEffect)
        assertFalse(behavior.collapsed)
        scroll(-12f)
        scroll(100f, source = NestedScrollSource.SideEffect)
        assertTrue(behavior.collapsed)
    }

    @Test fun separateGesturesDoNotAccumulateTinyDrags() = runTest {
        repeat(3) {
            scroll(-5f)
            assertEquals(Velocity.Zero, behavior.onPreFling(Velocity(0f, -10f)))
        }
        assertFalse(behavior.collapsed)
    }

    @Test fun coveredOrSecondaryPagesCannotChangeTheDock() {
        var enabled = true
        val gated = RootDockScrollBehavior(12f, 24f) { enabled }
        gated.onPostScroll(Offset(0f, -20f), Offset.Zero, NestedScrollSource.UserInput)
        enabled = false
        gated.onPostScroll(Offset(0f, 60f), Offset.Zero, NestedScrollSource.UserInput)
        assertTrue(gated.collapsed)
    }
}

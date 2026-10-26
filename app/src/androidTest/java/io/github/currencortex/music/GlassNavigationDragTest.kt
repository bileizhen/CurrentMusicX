package io.github.currencortex.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.ui.component.miuix.animation.DampedDragAnimation
import io.github.currencortex.music.ui.component.miuix.modifier.inspectDragGestures
import io.github.currencortex.music.ui.component.rootNavigationSwipe
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

/** Real nested pointer handlers, without shaders, playback or positional animation timing. */
class GlassNavigationDragTest {
    @get:Rule val compose = createComposeRule()
    private var selected by mutableIntStateOf(0)
    private val selections = mutableListOf<Int>()
    private var starts = 0
    private var stops = 0
    private var moves = 0
    private var clicks = 0
    private var observerEnds = 0
    private var observerCancels = 0

    private fun select(index: Int) {
        if (selected != index) {
            selected = index
            selections += index
        }
    }

    private fun fixture(initial: Int, canDrag: Boolean = true) {
        selected = initial
        compose.setContent {
            val scope = rememberCoroutineScope()
            val stride = with(LocalDensity.current) { 80.dp.toPx() }
            val target = remember { mutableFloatStateOf(initial.toFloat()) }
            val motion = remember(scope, stride) {
                DampedDragAnimation(scope, initial.toFloat(), 0f..3f, .001f, 1f, 78f / 56f,
                    canDrag = { canDrag },
                    onDragStarted = { starts++ },
                    onDragStopped = {
                        stops++
                        select(target.floatValue.roundToInt())
                    },
                    onDrag = { _, amount ->
                        if (amount != Offset.Zero) moves++
                        target.floatValue = (target.floatValue + amount.x / stride).coerceIn(0f, 3f)
                    })
            }
            Box(Modifier.width(320.dp).height(96.dp).testTag("navigation")
                .then(rootNavigationSwipe(selected, ::select))) {
                Box(Modifier.offset(x = 80.dp * initial).width(80.dp).height(56.dp)
                    .testTag("indicator").clickable { clicks++ }
                    .pointerInput(Unit) {
                        // The real highlight is an observer on the same modifier chain.
                        inspectDragGestures(ignoreConsumed = true,
                            onDragEnd = { observerEnds++ },
                            onDragCancel = { observerCancels++ }) { _, _ -> }
                    }
                    .then(motion.modifier))
            }
        }
    }

    private fun dragIndicator(toRight: Boolean) {
        compose.onNodeWithTag("navigation").performTouchInput {
            val start = Offset(width * (if (toRight) .375f else .625f), height * .25f)
            val end = Offset(width * (if (toRight) .98f else .02f), start.y)
            down(start)
            // Several events cross parent touch slop before reaching the edge.
            repeat(12) { step ->
                moveTo(start + (end - start) * ((step + 1) / 12f), delayMillis = 16)
            }
            up()
        }
    }

    private fun assertIndicatorGesture(expected: Int, expectedSelections: List<Int>) {
        compose.runOnIdle {
            assertEquals(expected, selected)
            assertEquals(expectedSelections, selections)
            assertEquals(1, starts)
            assertEquals(1, stops)
            assertEquals("Passive highlight must finish rather than cancel on consumed movement", 1, observerEnds)
            assertEquals(0, observerCancels)
            assertEquals("A drag must not trigger the underlying click", 0, clicks)
        }
    }

    @Test fun draggingIndicatorToRightEdgeSelectsOnlyTheDestination() {
        fixture(initial = 1)
        dragIndicator(toRight = true)
        assertIndicatorGesture(3, listOf(3))
    }

    @Test fun draggingIndicatorToLeftEdgeSelectsOnlyTheDestination() {
        fixture(initial = 2)
        dragIndicator(toRight = false)
        assertIndicatorGesture(0, listOf(0))
    }

    @Test fun rejectedIndicatorMovementStillOwnsTheGesture() {
        fixture(initial = 1, canDrag = false)
        dragIndicator(toRight = true)
        assertIndicatorGesture(1, emptyList())
        compose.runOnIdle { assertEquals(0, moves) }
    }

    @Test fun pressingWithoutMovementPreservesClick() {
        fixture(initial = 1)
        compose.onNodeWithTag("indicator").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(1, clicks)
            assertEquals(0, moves)
            assertEquals(emptyList<Int>(), selections)
        }
    }

    @Test fun movementBelowTouchSlopPreservesClick() {
        fixture(initial = 1)
        compose.onNodeWithTag("indicator").performTouchInput {
            down(center)
            moveBy(Offset(1f, 0f), delayMillis = 16)
            up()
        }
        compose.runOnIdle {
            assertEquals("A one-pixel hand movement must still count as a tap", 1, clicks)
            assertEquals(1, selected)
            assertEquals(emptyList<Int>(), selections)
        }
    }

    @Test fun draggingOutsideIndicatorStillUsesParentNavigation() {
        fixture(initial = 1)
        compose.onNodeWithTag("navigation").performTouchInput {
            swipe(Offset(width * .8f, height * .8f), Offset(width * .2f, height * .8f), 240)
        }
        compose.runOnIdle {
            assertEquals(2, selected)
            assertEquals(listOf(2), selections)
            assertEquals(0, starts)
            assertEquals(0, stops)
        }
    }
}

package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.component.RootTabTransition
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ElasticLazyRowTest {
    @get:Rule val compose = createComposeRule()

    private fun checkOverflow(direction: Float, inVerticalList: Boolean = false) {
        lateinit var press: ElasticPressState
        var growth = 0f
        // Production does not read press animation state through diagnostic semantics.
        compose.setContent {
            press = rememberElasticPressState()
            growth = with(LocalDensity.current) { 16.dp.toPx() }
            val row: @Composable () -> Unit = {
                ElasticLazyRow(Modifier.fillMaxWidth().testTag("elastic_row")) {
                    item { ElasticPress(Modifier.testTag("elastic_card"), state = press) {
                        Box(Modifier.size(140.dp, 220.dp).background(Color.Cyan))
                    } }
                    // Match the scrollable home shelves, even before the first horizontal swipe.
                    items(8) { Box(Modifier.size(140.dp, 220.dp).background(Color.Red)) }
                }
            }
            val container = Modifier.fillMaxSize().background(Color.Black)
            if (inVerticalList) {
                RootTabTransition(0, rememberSaveableStateHolder(), {}) { tab ->
                    if (tab == 0) LazyColumn(container) {
                        // Leave room inside the parent viewport for upward paint overflow.
                        item { Spacer(Modifier.height(120.dp)) }
                        item { row() }
                        item { Box(Modifier.size(10.dp).testTag("following_item")) }
                        item { Spacer(Modifier.height(1200.dp)) }
                    }
                }
            } else {
                Column(container.padding(top = 120.dp)) {
                    row()
                    Box(Modifier.size(10.dp).testTag("following_item"))
                }
            }
        }
        val row = compose.onNodeWithTag("elastic_row").fetchSemanticsNode().boundsInRoot
        val following = compose.onNodeWithTag("following_item").fetchSemanticsNode().boundsInRoot
        val card = compose.onNodeWithTag("elastic_card").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            press.press(Offset(press.size.width / 2, press.size.height / 2))
            press.drag(Offset(press.size.width / 2, press.size.height / 2 + direction * press.size.height))
        }
        compose.mainClock.advanceTimeBy(320)
        val currentRow = compose.onNodeWithTag("elastic_row").fetchSemanticsNode().boundsInRoot
        val frame = compose.runOnIdle { press.frame(growth) }
        val swell = card.height * (frame.scaleY - 1) / 2
        val paintedEdge = if (direction < 0) card.top + frame.pull.y - swell else card.bottom + frame.pull.y + swell
        assertTrue("Drag must extend beyond the resting row", if (direction < 0) paintedEdge < currentRow.top - 8 else paintedEdge > currentRow.bottom + 8)
        // Small square fixtures fit inside Compose's built-in 30.dp shadow allowance,
        // so they cannot detect a stale cross-axis viewport for real artwork cards.
        val extension = if (direction < 0) row.top - paintedEdge else paintedEdge - row.bottom
        assertTrue("Exercise overflow beyond the default shadow allowance", extension > growth * 2)
        val x = card.center.x.toInt()
        val y = (paintedEdge + if (direction < 0) 3 else -3).toInt()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        assertTrue("Deformed card must remain painted outside the resting row",
            android.graphics.Color.green(bitmap.getPixel(x, y)) > 200)
        bitmap.recycle()
        val currentFollowing = compose.onNodeWithTag("following_item").fetchSemanticsNode().boundsInRoot
        assertEquals("Painting overflow must not change row layout", row, currentRow)
        assertEquals("Following content must not jump", following, currentFollowing)
        compose.runOnIdle { press.release() }
        compose.mainClock.advanceTimeBy(1800)
        val settled = compose.onRoot().captureToImage().asAndroidBitmap()
        assertEquals("Released card must clear its former overflow", android.graphics.Color.BLACK, settled.getPixel(x, y))
        settled.recycle()
        compose.mainClock.autoAdvance = true
    }

    @Test fun upwardPullExpandsPaintingWithoutMovingFollowingContent() = checkOverflow(-1f)
    @Test fun downwardPullExpandsPaintingWithoutMovingFollowingContent() = checkOverflow(1f)
    @Test fun stationaryRowInVerticalListUpdatesUpwardBoundary() = checkOverflow(-1f, inVerticalList = true)
    @Test fun stationaryRowInVerticalListUpdatesDownwardBoundary() = checkOverflow(1f, inVerticalList = true)
}

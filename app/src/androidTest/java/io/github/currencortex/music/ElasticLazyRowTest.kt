package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.ui.component.nativeui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ElasticLazyRowTest {
    @get:Rule val compose = createComposeRule()

    private fun checkOverflow(direction: Float) {
        lateinit var press: ElasticPressState
        compose.setContent {
            press = rememberElasticPressState()
            Column(Modifier.fillMaxSize().background(Color.Black).padding(top = 120.dp)) {
                ElasticLazyRow(Modifier.fillMaxWidth().testTag("elastic_row")) {
                    item { ElasticPress(Modifier.testTag("elastic_card"), state = press) {
                        Box(Modifier.size(100.dp).background(Color.Cyan))
                    } }
                    item { Box(Modifier.size(100.dp).background(Color.Red)) }
                }
                Box(Modifier.size(10.dp).testTag("following_item"))
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
        val frame = compose.onNodeWithTag("elastic_card").fetchSemanticsNode().config[ElasticPressGeometry]
        val swell = card.height * (frame.scaleY - 1) / 2
        val paintedEdge = if (direction < 0) card.top + frame.pull.y - swell else card.bottom + frame.pull.y + swell
        assertTrue("Drag must extend beyond the resting row", if (direction < 0) paintedEdge < row.top - 8 else paintedEdge > row.bottom + 8)
        val x = card.center.x.toInt()
        val y = (paintedEdge + if (direction < 0) 3 else -3).toInt()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        assertTrue("Deformed card must remain painted outside the resting row",
            android.graphics.Color.green(bitmap.getPixel(x, y)) > 200)
        bitmap.recycle()
        assertEquals("Painting overflow must not change row layout", row,
            compose.onNodeWithTag("elastic_row").fetchSemanticsNode().boundsInRoot)
        assertEquals("Following content must not jump", following,
            compose.onNodeWithTag("following_item").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { press.release() }
        compose.mainClock.advanceTimeBy(1800)
        val settled = compose.onRoot().captureToImage().asAndroidBitmap()
        assertEquals("Released card must clear its former overflow", android.graphics.Color.BLACK, settled.getPixel(x, y))
        settled.recycle()
        compose.mainClock.autoAdvance = true
    }

    @Test fun upwardPullExpandsPaintingWithoutMovingFollowingContent() = checkOverflow(-1f)
    @Test fun downwardPullExpandsPaintingWithoutMovingFollowingContent() = checkOverflow(1f)
}

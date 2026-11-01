package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Source glyphs must stay identical, and revealing a page must not lay out its text on every frame. */
class PageContainerTextMotionTest {
    @get:Rule val compose = createComposeRule()
    private val open = mutableStateOf(false)
    private val compositions = AtomicInteger()
    private val measures = AtomicInteger()
    private val textLayouts = AtomicInteger()
    private var density = 1f

    private fun show(sourceTop: Float = 150f) {
        compose.mainClock.autoAdvance = false
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            density = LocalDensity.current.density
            var origin by remember { mutableStateOf<PageContainerOrigin?>(null) }
            CompositionLocalProvider(LocalPageContainerSource provides { entry, action -> origin = entry; action() }) {
                Box(Modifier.fillMaxSize().background(Color.LightGray).clipToBounds()) {
                    val source = rememberPageNavigationSource("元数据标题", "元数据不是源行的字形")
                    Column(Modifier.offset(y = sourceTop.dp).padding(horizontal = 16.dp).fillMaxWidth().height(96.dp)
                        .then(source.modifier).clip(RoundedCornerShape(26.dp)).background(Color.White)
                        .testTag("text_source").clickable { source.open { open.value = true } }.padding(18.dp)) {
                        BasicText("实际源行 Aa 字形", style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF883355)))
                        BasicText("自定义字号和排版保持一致", style = TextStyle(fontSize = 15.sp, color = Color.DarkGray))
                    }
                    PageContainerTransform(open.value, origin, tag = "text_transform") {
                        SideEffect { compositions.incrementAndGet() }
                        Column(Modifier.fillMaxSize().background(Color.White).testTag("text_destination")
                            .layout { measurable, constraints ->
                                measures.incrementAndGet()
                                val child = measurable.measure(constraints)
                                layout(child.width, child.height) { child.place(0, 0) }
                            }.padding(24.dp)) {
                            repeat(12) { i -> BasicText("设置文字 $i · Text Aa", style = TextStyle(fontSize = 22.sp),
                                onTextLayout = { textLayouts.incrementAndGet() }) }
                        }
                    }
                }
            }
        } }
        compose.waitForIdle()
    }

    @Test fun openingUsesTheActualSourceGlyphsRatherThanRebuildingAPreference() {
        assertSourceGlyphs(150f)
    }

    @Test fun aPartlyClippedSourceKeepsItsGlyphsAtTheirOriginalPositions() {
        assertSourceGlyphs(-24f)
    }

    private fun assertSourceGlyphs(top: Float) {
        show(top)
        val bounds = compose.onNodeWithTag("text_source").fetchSemanticsNode().boundsInRoot
        val before = compose.onRoot().captureToImage().asAndroidBitmap()
        compose.onNodeWithTag("text_source").performClick()
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
        val frame = compose.onNodeWithTag("text_transform").fetchSemanticsNode().config[PageContainerGeometry]
        assertTrue("Compare the first frame before the source starts travelling", frame.progress < .002f)
        val after = compose.onRoot().captureToImage().asAndroidBitmap()
        var total = 0; var changed = 0
        val inset = (12 * density).toInt()
        for (y in bounds.top.toInt() + inset until bounds.bottom.toInt() - inset step 2) {
            for (x in bounds.left.toInt() + inset until bounds.right.toInt() - inset step 2) {
                val a = before.getPixel(x, y); val b = after.getPixel(x, y)
                val delta = listOf(0, 8, 16).maxOf { kotlin.math.abs(((a shr it) and 255) - ((b shr it) and 255)) }
                total++; if (delta > 10) changed++
            }
        }
        before.recycle(); after.recycle()
        assertTrue("The source's existing font and layout must survive the first frame: $changed/$total pixels changed",
            changed < total * .01f)
    }

    @Test fun revealAndReturnDoNotRecomposeRemeasureOrReflowPageTextPerFrame() {
        show()
        compose.onNodeWithTag("text_source").performClick()
        compose.mainClock.advanceTimeBy(64); compose.waitForIdle()
        val count = Triple(compositions.get(), measures.get(), textLayouts.get())
        repeat(24) { compose.mainClock.advanceTimeBy(32); compose.waitForIdle() }
        assertEquals("Opening keeps a full-size laid-out page", count, Triple(compositions.get(), measures.get(), textLayouts.get()))
        compose.runOnIdle { open.value = false }
        compose.mainClock.advanceTimeBy(32); compose.waitForIdle()
        val closing = Triple(compositions.get(), measures.get(), textLayouts.get())
        repeat(12) { compose.mainClock.advanceTimeBy(32); compose.waitForIdle() }
        assertEquals("Returning cannot rebuild text at the source-proxy boundary", closing,
            Triple(compositions.get(), measures.get(), textLayouts.get()))
        compose.mainClock.advanceTimeBy(160); compose.waitForIdle()
        compose.onNodeWithTag("text_transform").assertDoesNotExist()
    }
}

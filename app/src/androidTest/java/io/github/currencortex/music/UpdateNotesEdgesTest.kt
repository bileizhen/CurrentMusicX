package io.github.currencortex.music

import android.os.Build
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.feature.update.UpdateNotesEdgesKey
import io.github.currencortex.music.feature.update.updateNotesEdges
import io.github.currencortex.music.ui.component.ScrollEdgeBlurKey
import io.github.currencortex.music.ui.component.scrollEdgeBlur
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UpdateNotesEdgesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun radialEdgesFollowTheReversedChatListAndLeaveTheCenterSharp() {
        lateinit var list: androidx.compose.foundation.lazy.LazyListState
        lateinit var scope: CoroutineScope
        val longChat = mutableStateOf(true)
        compose.setContent {
            list = rememberLazyListState()
            scope = rememberCoroutineScope()
            Box(Modifier.fillMaxSize().background(Color(0xFFF5F6F8))) {
                LazyColumn(Modifier.width(300.dp).height(240.dp)
                    .scrollEdgeBlur(list.canScrollForward, list.canScrollBackward, radial = true).testTag("chat_edges"),
                    state = list, reverseLayout = true) {
                    items(if (longChat.value) 60 else 2) { index ->
                        BasicText("第 ${index + 1} 条聊天消息 · 听音乐",
                            Modifier.padding(vertical = 6.dp), style = TextStyle(color = Color.Black, fontSize = 18.sp, lineHeight = 24.sp))
                    }
                }
            }
        }
        val node = compose.onNodeWithTag("chat_edges")
        val latest = node.fetchSemanticsNode().config[ScrollEdgeBlurKey]
        assertTrue(latest.top); assertFalse(latest.bottom)
        if (Build.VERSION.SDK_INT >= 33) assertTrue("The radial mask must compile on the phone", latest.blurred)
        fun moveTo(index: Int) {
            compose.runOnIdle { scope.launch { list.scrollToItem(index, 9) } }
            compose.waitForIdle()
        }
        moveTo(30)
        val middle = node.fetchSemanticsNode().config[ScrollEdgeBlurKey]
        assertTrue(middle.top && middle.bottom)
        val bitmap = node.captureToImage().asAndroidBitmap()
        var sharpPixels = 0
        for (y in bitmap.height / 3 until bitmap.height * 2 / 3) {
            for (x in 0 until bitmap.width) {
                if (android.graphics.Color.red(bitmap.getPixel(x, y)) < 25) sharpPixels++
            }
        }
        assertTrue("The radial edge effect must preserve text in the center", sharpPixels > 100)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "chat-edge-blur.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        moveTo(59)
        val oldest = node.fetchSemanticsNode().config[ScrollEdgeBlurKey]
        assertFalse(oldest.top); assertTrue(oldest.bottom)
        compose.runOnIdle { longChat.value = false }
        compose.waitForIdle()
        val short = node.fetchSemanticsNode().config[ScrollEdgeBlurKey]
        assertFalse(short.top || short.bottom)
    }

    @Test fun onlyHiddenContentEdgesBlurAndTheCenterStaysReadable() {
        lateinit var scroll: ScrollState
        lateinit var scope: CoroutineScope
        val longNotes = mutableStateOf(true)
        compose.setContent {
            scroll = rememberScrollState()
            scope = rememberCoroutineScope()
            Box(Modifier.fillMaxSize().background(Color(0xFF231C24))) {
                Column(Modifier.width(300.dp).height(240.dp).updateNotesEdges(scroll)
                    .verticalScroll(scroll).testTag("edge_notes")) {
                    repeat(if (longNotes.value) 60 else 2) { index ->
                        BasicText("第 ${index + 1} 项更新说明 · 播放与体验优化",
                            Modifier.padding(vertical = 6.dp), style = TextStyle(color = Color.White, fontSize = 18.sp))
                    }
                }
            }
        }
        val node = compose.onNodeWithTag("edge_notes")
        val start = node.fetchSemanticsNode().config[UpdateNotesEdgesKey]
        assertFalse(start.top); assertTrue(start.bottom)
        if (Build.VERSION.SDK_INT >= 33) assertTrue("Hardware shader must compile", start.blurred)
        fun moveTo(position: Int) {
            compose.runOnIdle { scope.launch { scroll.scrollTo(position) } }
            compose.waitForIdle()
        }
        moveTo(scroll.maxValue / 2 + 9)
        val middle = node.fetchSemanticsNode().config[UpdateNotesEdgesKey]
        assertTrue(middle.top && middle.bottom)
        val bitmap = node.captureToImage().asAndroidBitmap()
        var readablePixels = 0
        for (y in bitmap.height / 3 until bitmap.height * 2 / 3) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (android.graphics.Color.red(pixel) > 220 && android.graphics.Color.alpha(pixel) > 220) readablePixels++
            }
        }
        assertTrue("The edge shader must preserve sharp text in the center", readablePixels > 100)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "update-notes-edge-blur.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        moveTo(scroll.maxValue)
        val end = node.fetchSemanticsNode().config[UpdateNotesEdgesKey]
        assertTrue(end.top); assertFalse(end.bottom)
        compose.runOnIdle { longNotes.value = false }
        val short = node.fetchSemanticsNode().config[UpdateNotesEdgesKey]
        assertFalse(short.top || short.bottom)
    }
}

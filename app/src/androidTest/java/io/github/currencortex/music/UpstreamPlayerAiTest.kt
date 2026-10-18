package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.currencortex.music.core.network.RequestSession
import io.github.currencortex.music.data.profile.*
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.player.*
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*

class UpstreamPlayerAiTest {
    @get:Rule val compose = createComposeRule()
    private val server = MockWebServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: PlayerAiController
    @Before fun setup() {
        server.start()
        val expected = RequestSession(server.url("/cm/").toString(), "fixture")
        controller = PlayerAiController(PlayerAiRepository({ expected }), scope) { expected }
        controller.open(Song(7, "锁定的歌曲", "歌手"))
    }
    @After fun finish() { controller.close(); scope.cancel(); server.shutdown() }
    @Test fun privateDialogStreamsMarkdownAndCardsThenKeepsFollowupContext() {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
            "event: start\ndata: {\"ok\":true}\n\nevent: delta\ndata: {\"text\":\"**正在更新**\"}\n\n" +
            "event: done\ndata: {\"text\":\"**最终回答**\",\"cards\":[{\"type\":\"song\",\"id\":9,\"name\":\"推荐歌曲\"}]}\n\n"))
        var selected: PostAttachment? = null
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            top.yukonga.miuix.kmp.basic.Scaffold {
                PlayerAiDialog(controller, { controller.close() }, { selected = it })
            }
        } }
        compose.onNodeWithText("锁定的歌曲").assertExists()
        compose.onNodeWithTag("player_ai_suggestion").performClick()
        compose.onNodeWithTag("player_ai_send").performClick()
        compose.waitUntil(8000) { controller.state.value.turns.lastOrNull()?.complete == true }
        compose.onNodeWithText("最终回答").assertExists()
        compose.onNodeWithTag("music_attachment_song_9").performScrollTo().performClick()
        assertEquals(9L, selected?.id)
        assertEquals("/cm/ai/live", server.takeRequest().path)
        assertEquals(1, server.requestCount)
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("event: done\ndata: {\"text\":\"追问回答\"}\n\n"))
        compose.onNodeWithTag("player_ai_input").performTextInput("继续介绍")
        compose.onNodeWithTag("player_ai_send").performClick()
        compose.waitUntil(8000) { controller.state.value.turns.size == 2 && !controller.state.value.busy }
        assertTrue(server.takeRequest().body.readUtf8().contains("\"reset\":false"))
        compose.runOnIdle { controller.close(); controller.open(Song(8, "下一首")) }
        assertTrue(controller.state.value.turns.isEmpty())
    }
}

package io.github.currencortex.music.feature.profile

import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.data.profile.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ChatEventMergeTest {
    @Test fun replayedMessagesReplaceByIdWithoutDuplicatesAndDeletesRemoveOnlyTheirTarget() {
        val initial = listOf(ChatMessage(1, content = "一"), ChatMessage(3, content = "旧"))
        val event = ChatLiveEvent(7, "message", ApiJson.encodeToJsonElement(ChatMessage(3, content = "新")))
        val updated = applyChatEvent(initial, event)
        assertEquals(listOf(1L, 3L), updated.map { it.id })
        assertEquals("新", updated.last().content)
        assertEquals(updated, applyChatEvent(updated, event))
        assertEquals(listOf(1L), applyChatEvent(updated, ChatLiveEvent(8, "delete", buildJsonObject { put("id", 3) })).map { it.id })
        assertSame(updated, applyChatEvent(updated, ChatLiveEvent(9, "heartbeat", JsonNull)))
    }
    @Test fun mentionIdsMatchWholeNicknamesAndAreDeduplicated() {
        val users = listOf(ProfileUser(1, nickname = "小明"), ProfileUser(2, nickname = "小明同学"),
            ProfileUser(3, nickname = "A+B"), ProfileUser(4, nickname = ""))
        assertEquals(listOf(2L, 3L), chatMentions("@小明同学 @A+B @A+B", users))
        assertEquals(listOf(1L), chatMentions("你好 @小明", users))
        assertTrue(chatMentions("hello@example.com", users).isEmpty())
    }
}

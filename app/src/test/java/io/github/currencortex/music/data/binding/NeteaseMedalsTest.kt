package io.github.currencortex.music.data.binding

import io.github.currencortex.music.core.network.ApiException
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class NeteaseMedalsTest {
    private fun parse(json: String) = parseNeteaseMedals(Json.parseToJsonElement(json).jsonObject, 42)

    @Test fun onlyEarnedMedalsAreShownAndImagesUseHttps() {
        val result = parse("""{"medalNum":2,"obtainMedals":[
            {"medalCode":123,"medalName":"黑胶收藏家","medalPicUrl":"http://p1.music.126.net/medal.png"},
            {"medalCode":456,"medalName":"音乐达人","medalPicUrl":"https://p2.music.126.net/medal.png"}],
            "categoryMedalBlockVos":[{"medalVOS":[{"medalName":"未获得"}]}]}""")
        assertEquals(42L, result.userId)
        assertEquals(2, result.total)
        assertEquals(listOf("黑胶收藏家", "音乐达人"), result.medals.map { it.name })
        assertEquals("123", result.medals.first().code)
        assertEquals("https://p1.music.126.net/medal.png", result.medals.first().image)
    }
    @Test fun emptyMedalsAreDifferentFromAnInvalidResponse() {
        assertTrue(parse("""{"medalNum":0,"obtainMedals":[]}""").medals.isEmpty())
        assertThrows(ApiException::class.java) { parse("""{}""") }
        assertThrows(ApiException::class.java) { parse("""{"obtainMedals":null}""") }
    }
    @Test fun duplicateAndNamelessEntriesDoNotProduceBlankTiles() {
        val result = parse("""{"medalNum":-1,"obtainMedals":[{},null,
            {"medalCode":1,"medalName":"音乐达人"},{"medalCode":1,"medalName":"音乐达人"}]}""")
        assertEquals(1, result.total)
        assertEquals(1, result.medals.size)
    }
}

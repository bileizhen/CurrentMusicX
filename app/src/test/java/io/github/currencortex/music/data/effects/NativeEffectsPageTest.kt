package io.github.currencortex.music.data.effects

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class NativeEffectsPageTest {
    @Test fun prefersNewThemeArtworkAndPreservesServerOrderWithoutGrantingRights() {
        val rows = parseNativeEffectsPage(Json.parseToJsonElement("""{"code":200,"data":[
            {"id":3600400,"name":"全景环绕","soundCategory":1,"type":3,"exposeDescText":"声浪环绕左右","useCount":12345,"trackCount":9,
             "newLightImg":"http://p5.music.126.net/new.png","lightImg":"https://p5.music.126.net/old.png",
             "darkImg":"https://p6.music.126.net/dark.png","newLightGif":"https://p5.music.126.net/motion.webp"},
            {"id":100,"name":"智能音效","soundCategory":2,"type":3},
            {"id":3600400,"name":"重复"},{"id":0,"name":"无效"},null]}""").jsonObject)
        assertEquals(listOf(3600400L, 100L), rows.map { it.id })
        assertEquals("https://p5.music.126.net/new.png", rows.first().image(false))
        assertEquals("https://p6.music.126.net/dark.png", rows.first().image(true))
        assertEquals("https://p5.music.126.net/motion.webp", rows.first().animation(false))
        assertEquals(3, rows.first().membership)
        assertEquals(12345L, rows.first().useCount)
        assertEquals(9L, rows.first().trackCount)
        assertNull(rows.last().useCount)
        assertFalse(rows.first().smart)
        assertTrue(rows.last().smart)
    }
    @Test fun rejectsUnsafeMediaAndFallsBackFromBlankNewFields() {
        val item = parseNativeEffectsPage(Json.parseToJsonElement("""{"code":200,"data":[{"id":1,"name":"音效",
            "newLightImg":"null","lightImg":"https://p5.music.126.net/valid.png",
            "newDarkImg":"file:///data/private.png","darkImg":"https://music.126.net.attacker.test/trap.png",
            "darkGif":"https://user:pass@p5.music.126.net/secret.webp"}]}""").jsonObject).single()
        assertEquals("https://p5.music.126.net/valid.png", item.image(false))
        assertEquals(item.image(false), item.image(true))
        assertEquals("", item.darkAnimation)
    }
    @Test(expected = IllegalArgumentException::class) fun serverErrorIsNotAnEmptySuccessfulCatalog() {
        parseNativeEffectsPage(buildJsonObject { put("code", 503); put("data", buildJsonArray {}) })
    }
}

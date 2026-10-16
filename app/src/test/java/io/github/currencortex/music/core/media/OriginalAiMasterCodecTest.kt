package io.github.currencortex.music.core.media

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class OriginalAiMasterCodecTest {
    private fun resource(name: String) = Json.parseToJsonElement(javaClass.classLoader!!
        .getResource("original-ai-master/$name.json")!!.readText()).jsonObject
    private fun codec() = OriginalAiMasterCodec(sequenceOf(File("src/main/assets/netease-ai-master-codec.js"),
        File("app/src/main/assets/netease-ai-master-codec.js")).first { it.isFile }.readBytes())

    @Test fun everyOriginalBaseProducesNativePlanAndItsActualCurve() {
        val base = resource("base"); val codec = codec()
        assertEquals(32, base.size)
        base.forEach { (key, value) ->
            val result = codec.compose(base, resource("diy"), key, null)
            val output = Json.parseToJsonElement(result.json).jsonObject["aev2"]!!.jsonObject
            assertEquals(gains(value.jsonObject["effect"]!!.jsonObject), gains(output))
            assertTrue(NeteasePresetParameters.fromJson(result.json).usesAev2)
            assertEquals(ORIGINAL_AI_MASTER_PRESET_ID, result.preset().id)
            assertFalse(result.songSpecific)
        }
    }

    @Test fun songSpecificOriginalWinsOverBaseAndCannotBeMoodSubstitution() {
        val song = resource("song")
        val result = codec().compose(resource("base"), resource("diy"), "key_warm", song)
        assertTrue(result.songSpecific)
        val actual = Json.parseToJsonElement(result.json).jsonObject["aev2"]!!.jsonObject
        assertEquals(gains(song["aev2"]!!.jsonObject), gains(actual))
        assertNotEquals(ORIGINAL_MOOD_PRESET_ID, result.preset().id)
    }

    @Test fun originalDiyRulesCloseUnsupportedBassVocalAndResolveLiveVibe() {
        val result = codec().compose(resource("base"), resource("diy"), "key_transparent", null,
            OriginalAiMasterCodec.DIY_KEYS)
        assertEquals(listOf("en_treble", "en_vibe"), result.usedDiy)
        val output = Json.parseToJsonElement(result.json).jsonObject["aev2"]!!.jsonObject
        assertEquals(-3.2, output["cmp"]!!.jsonObject["pregain"]!!.jsonPrimitive.double, 0.00001)
        assertTrue(output["bt"]!!.jsonObject["on"]!!.jsonPrimitive.boolean)
    }

    @Test fun originalSpecialStereoQualityClosesDiyAndScalesEqOnlyWhenRequested() {
        val base = resource("base"); val codec = codec()
        val normal = codec.compose(base, resource("diy"), "key_warm", null)
        val spatial = codec.compose(base, resource("diy"), "key_warm", null,
            OriginalAiMasterCodec.DIY_KEYS, supportStereo = true)
        fun gains(json: String) = Json.parseToJsonElement(json).jsonObject["aev2"]!!.jsonObject["eq"]!!
            .jsonObject["eqs"]!!.jsonArray.map { it.jsonPrimitive.double }
        assertTrue(spatial.usedDiy.isEmpty())
        gains(normal.json).zip(gains(spatial.json)).forEach { (a, b) -> assertEquals(a * 0.8, b, 0.00001) }
    }

    @Test fun allOriginalDiyCombinationsProduceSupportedNativeChainsIncludingZeroTimingCompressor() {
        val base = resource("base"); val diy = resource("diy"); val song = resource("song")
        val algorithm = codec(); val keys = OriginalAiMasterCodec.DIY_KEYS.toList()
        for (mask in 0 until (1 shl keys.size)) {
            val selected = keys.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet()
            for (songResource in listOf(null, song)) for (spatial in listOf(false, true)) {
                val output = algorithm.compose(base, diy, "key_warm", songResource, selected, spatial)
                assertTrue(NeteasePresetParameters.fromJson(output.json).usesAev2)
            }
        }
    }

    @Test(expected = IllegalArgumentException::class) fun arbitraryExecutableAlgorithmIsRejected() {
        OriginalAiMasterCodec("var NeteaseAiMaster = {composeEffect:function(){}};".toByteArray())
    }

    @Test(expected = IllegalArgumentException::class) fun missingActualSongAndUnknownBaseCannotPretendSuccess() {
        codec().compose(resource("base"), resource("diy"), "key_missing", null)
    }

    private fun gains(effect: JsonObject) = effect["eq"]!!.jsonObject["eqs"]!!.jsonArray.map { it.jsonPrimitive.double }
}

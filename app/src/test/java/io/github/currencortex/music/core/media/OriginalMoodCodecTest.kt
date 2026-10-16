package io.github.currencortex.music.core.media

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class OriginalMoodCodecTest {
    @Test fun everyOriginalEmotionAndGenreTemplateHasACompleteSupportedDspPlan() {
        val profiles = Json.parseToJsonElement(resource("all-profiles.json")).jsonArray
        assertEquals(64, profiles.size)
        profiles.forEachIndexed { index, parameters ->
            try {
                NeteasePresetParameters.fromJson(buildJsonObject { put("aev2", parameters) }.toString())
            } catch (failure: IllegalArgumentException) {
                throw AssertionError("Original template $index is unsupported: ${failure.message}", failure)
            }
        }
    }
    private fun resource(name: String) = javaClass.classLoader!!.getResource("original-mood/$name")!!.readText()
    private val inputs = listOf(MoodTensorInfo("emotion_idx", 0, 32, listOf(1)),
        MoodTensorInfo("genre_idx", 0, 32, listOf(1)), MoodTensorInfo("features", 2, 32, listOf(1, 64)),
        MoodTensorInfo("base_extra_flat", 2, 32, listOf(1, 32)))
    private val outputs = listOf(MoodTensorInfo("aev2", 2, 32, listOf(1, 32)))
    private fun codec(): OriginalMoodCodec {
        val root = sequenceOf(File("src/main/assets/netease-original/mood-codec.js"),
            File("app/src/main/assets/netease-original/mood-codec.js")).first { it.isFile }
        return OriginalMoodCodec(root.readBytes())
    }

    @Test fun originalPreprocessingMatchesIndependentNodeGoldenEveryValue() {
        val prepared = codec().prepare(Json.parseToJsonElement(resource("descriptor.json")).jsonObject, inputs, outputs)
        val golden = Json.parseToJsonElement(resource("golden.json")).jsonObject["prepared"]!!.jsonObject
        assertTrue(prepared.infer)
        assertEquals(11, prepared.emotion)
        assertEquals(0, prepared.genre)
        for (name in listOf("emotion_idx", "genre_idx", "features", "base_extra_flat")) {
            val actual = prepared.value["tensors"]!!.jsonArray.map { it.jsonObject }.first { it["name"]!!.jsonPrimitive.content == name }["data"]!!.jsonArray
            val expected = golden["tensors"]!!.jsonArray.map { it.jsonObject }.first { it["name"]!!.jsonPrimitive.content == name }["data"]!!.jsonArray
            assertEquals(expected.map { it.jsonPrimitive.double }, actual.map { it.jsonPrimitive.double })
        }
        assertEquals(64, prepared.features.size)
        assertEquals(32, prepared.extra.size)
    }

    @Test fun originalPostprocessingPreservesNegativeGainsAndNestedAev2() {
        val codec = codec()
        val prepared = codec.prepare(Json.parseToJsonElement(resource("descriptor.json")).jsonObject, inputs, outputs)
        val result = codec.finish(prepared, prepared.extra)
        val expected = Json.parseToJsonElement(resource("golden.json")).jsonObject["finished"]!!.jsonObject["aev2"]
        assertEquals(expected, Json.parseToJsonElement(result.json).jsonObject["aev2"])
        assertEquals("nostalgic", result.emotion)
        assertEquals(1, result.preset().type)
        assertTrue(NeteasePresetParameters.fromJson(result.json).usesAev2)
    }

    @Test fun missingFeaturesUsesOriginalTemplateWithoutInventingInference() {
        val codec = codec()
        val prepared = codec.prepare(buildJsonObject { put("song_id", 1) }, inputs, outputs)
        assertFalse(prepared.infer)
        assertFalse(codec.finish(prepared, null).inferred)
    }

    @Test fun realDspVisualDimensionsAndCopywritingMatchIndependentNodeExecution() {
        val codec = codec()
        val prepared = codec.prepare(Json.parseToJsonElement(resource("descriptor.json")).jsonObject, inputs, outputs)
        val visual = codec.finish(prepared, prepared.extra).visualExtra!!
        val expected = Json.parseToJsonElement(resource("visual-golden.json")).jsonObject
        assertEquals(expected["copywriting"], visual["copywriting"])
        val actualDimensions = visual["dimensions"]!!.jsonArray.map { it.jsonObject }
        val expectedDimensions = expected["dimensions"]!!.jsonArray.map { it.jsonObject }
        assertEquals(6, actualDimensions.size)
        actualDimensions.zip(expectedDimensions).forEach { (actual, golden) ->
            for (key in listOf("name", "rank", "visual", "intensity")) assertEquals(golden[key], actual[key])
            for (key in listOf("change", "fx_activation")) assertEquals(golden[key]!!.jsonPrimitive.double,
                actual[key]!!.jsonPrimitive.double, 0.000001)
            assertEquals(2, actual["value"]!!.jsonArray.size)
            actual["value"]!!.jsonArray.zip(golden["value"]!!.jsonArray).forEach { (a, g) ->
                assertEquals(g.jsonPrimitive.double, a.jsonPrimitive.double, 0.000001)
            }
        }
    }

    @Test fun visualExplanationRecomputesFromDifferentDspOutputRatherThanEmotionLabelOnly() {
        val codec = codec()
        val prepared = codec.prepare(Json.parseToJsonElement(resource("descriptor.json")).jsonObject, inputs, outputs)
        val original = codec.finish(prepared, prepared.extra)
        // Preserve the valid original model tensor; index 10 is the reverb decay time.
        // An all-zero tensor creates an invalid zero decay and is correctly rejected by the DSP parser.
        val changedOutput = prepared.extra.copyOf().apply { this[10] = 3.2f }
        val changed = codec.finish(prepared, changedOutput)
        assertEquals(original.emotion, changed.emotion)
        assertNotEquals(original.visualExtra?.get("dimensions"), changed.visualExtra?.get("dimensions"))
        assertEquals(original.emotion, changed.visualExtra!!["copywriting"]!!.jsonObject["emotion"]!!.jsonPrimitive.content)
    }

    @Test fun unknownGenreAndEmotionRemainInOriginalFallbackFlow() {
        val row = Json.parseToJsonElement(resource("descriptor.json")).jsonObject.toMutableMap()
        row["genre_id"] = JsonPrimitive(99999999); row["emotion_label"] = JsonPrimitive("未知标签")
        val codec = codec()
        val prepared = codec.prepare(JsonObject(row), inputs, outputs)
        val result = codec.finish(prepared, if (prepared.infer) prepared.extra else null)
        assertEquals("pop-family", result.family)
        assertTrue(NeteasePresetParameters.fromJson(result.json).usesAev2)
    }

    @Test fun originalPackagedSongOverridesApplyWithoutRemoteCodeOrForcedDefault() {
        val row = Json.parseToJsonElement(resource("descriptor.json")).jsonObject.toMutableMap()
        row["song_id"] = JsonPrimitive(300380)
        val codec = codec()
        val prepared = codec.prepare(JsonObject(row), inputs, outputs)
        assertTrue(prepared.infer)
        assertEquals("calm", codec.finish(prepared, prepared.extra).emotion)
        assertEquals(18, OriginalMoodCodec.defaultConfig["emotionTagOverride"]!!.jsonObject.values.sumOf { it.jsonArray.size })
    }

    @Test(expected = IllegalArgumentException::class) fun editedAlgorithmCannotExecute() {
        OriginalMoodCodec("function moodPrepare(){for(;;){}}".toByteArray())
    }

    @Test(expected = IllegalArgumentException::class) fun modelOutputRejectsNanBeforeEnteringJs() {
        val codec = codec()
        val prepared = codec.prepare(Json.parseToJsonElement(resource("descriptor.json")).jsonObject, inputs, outputs)
        codec.finish(prepared, FloatArray(32) { Float.NaN })
    }
}

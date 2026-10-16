package io.github.currencortex.music.core.media

import org.junit.Assert.*
import org.junit.Test

/** Exact decoded original CDN payloads; these do not grant permission to enable their DSP. */
class OriginalSpeakerMaterialParametersTest {
    @Test fun everyActualSpeakerMaterialIsCompletelyUnderstoodByTheNativeParameterBridge() {
        for (id in listOf(-100, -101, -102, -200, -201, -202, -300, -400)) {
            val raw = requireNotNull(javaClass.getResourceAsStream("/original-speaker/$id.json"))
                .bufferedReader().use { it.readText() }
            val plan = NeteasePresetParameters.fromJson(raw, soundType = 9)
            assertTrue("$id must retain original EQ switch", plan.parameterCalls.any { it.first == "setGraphEQON" })
            if (id in listOf(-100, -101, -102, -200, -201, -202)) {
                assertTrue("$id must use original speaker spatial DSP", plan.parameterCalls.any {
                    it.first == "setSpeakerSpatialON" && it.second == listOf(true)
                })
            } else {
                assertTrue("$id must use original ALC", plan.parameterCalls.any {
                    it.first == "setALCON" && it.second == listOf(true)
                })
            }
        }
    }
}

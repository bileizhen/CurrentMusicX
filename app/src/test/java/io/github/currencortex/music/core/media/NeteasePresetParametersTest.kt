package io.github.currencortex.music.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class NeteasePresetParametersTest {
    @Test fun originalStereoParametersKeepTheirUnitsAndArgumentOrder() {
        val plan = NeteasePresetParameters.fromJson("""
            {"eq":{"on":false},"bt":{"on":false},"rvb":{"on":false},
             "se":{"on":true,"presence":6,"stereoizer":4,"sshaper":true,"ambience":1}}
        """.trimIndent())
        assertEquals(listOf(6f, 4f, true, 1), plan.parameterCalls.first { it.first == "setStereoEnhancerParams" }.second)
        assertEquals(listOf("setGraphEQON", "setBassTrebleON", "setStereoEnhancerParams", "setStereoEnhancerON", "setReverbON"), plan.parameterCalls.map { it.first })
    }

    @Test fun reverbMapsEveryNestedSectionWithoutConvertingMillisecondsOrPercentages() {
        val plan = NeteasePresetParameters.fromJson("""
            {"rvb":{"on":true,
              "er":{"on":true,"pattern":4,"rsize":70,"sdelay":40},
              "rvb":{"pdelay":25,"dtime":1.5,"hfdamping":2.79,"density":60,"rshape":40,"q":8,"diffusion":80,"swidth":150},
              "tc":{"on":true,"f":[{"band":1,"insert":3,"curve":0,"gain":-1,"freq":200,"q":1}]},
              "il":{"center":0,"lfe":0},"rl":{"front":0,"rear":0,"center":0,"lfe":0},
              "ol":{"dry":0,"er":-23,"rvb":-20}}}
        """.trimIndent())
        assertEquals(listOf(true, 4, 70f, 40f), plan.parameterCalls.first { it.first == "setReverbERParams" }.second)
        assertEquals(listOf(25f, 1.5f, 2.79f, 60f, 40f, 8, 80f, 150f), plan.parameterCalls.first { it.first == "setReverbParams" }.second)
        assertEquals(listOf(1, 3, 0, -1f, 200f, 1f), plan.parameterCalls.first { it.first == "setReverbFilterParams" }.second)
        assertEquals(listOf(0f, -23f, -20f), plan.parameterCalls.first { it.first == "setReverbOutputLevelParams" }.second)
        assertEquals("setReverbON" to listOf(true), plan.parameterCalls.last())
        assertEquals(8, plan.parameterCalls.size)
    }

    @Test fun unknownParametersAreRejectedBeforeAnyNativeCall() {
        assertThrows(IllegalArgumentException::class.java) {
            NeteasePresetParameters.fromJson("""{"se":{"on":true,"presence":6,"stereoizer":4,"sshaper":true,"ambience":1,"unknownProcessor":3}}""")
        }
    }

    @Test fun gainArrayCannotOverrunNativeTenBandStorage() {
        for (count in listOf(0, 9, 11, 100)) {
            assertThrows(IllegalArgumentException::class.java) {
                NeteasePresetParameters.fromJson("""{"eq":{"on":true,"eqs":[${List(count) { "0" }.joinToString()}]}}""")
            }
        }
        val plan = NeteasePresetParameters.fromJson("""{"eq":{"on":true,"eqs":[0,0,0,0,0,0,0,0,0,0]}}""")
        assertEquals(2, plan.parameterCalls.size)
    }

    @Test fun rejectsNonFiniteCoercedTypesAndUnsafeNativeIndices() {
        listOf(
            """{"rotate":{"on":true,"velocity":1e309}}""",
            """{"rotate":{"on":true,"velocity":"1"}}""",
            """{"rotate":{"on":1,"velocity":1}}""",
            """{"peq":{"on":true,"gain":0,"f":[{"band":999,"type":6,"gain":0,"freq":1000,"q":1,"on":true}]}}""",
            """{"peq":{"on":true,"gain":0,"f":[{"band":1,"type":6,"gain":0,"freq":1000,"q":0,"on":true}]}}""",
        ).forEach { text -> assertThrows(RuntimeException::class.java) { NeteasePresetParameters.fromJson(text) } }
    }

    @Test fun bassTrebleExtensionUsesFrequencyThenQ() {
        val plan = NeteasePresetParameters.fromJson("""
            {"bt":{"on":true,"bass":2,"treble":1,"bassFreq":200,"bassQ":0.5,"trebleFreq":6000,"trebleQ":0.8}}
        """.trimIndent())
        assertEquals(listOf(200f, 0.5f, 6000f, 0.8f), plan.parameterCalls.first { it.first == "setBassTrebleExtraParams" }.second)
    }

    @Test fun disabledLimiterUsesOriginalFallbackCurve() {
        val plan = NeteasePresetParameters.fromJson("""{"limiter":{"on":false}}""")
        assertEquals(listOf("setLimiterParams" to listOf(0f, 0.002f, 0.04f, 0f, true, 4f)), plan.parameterCalls)
    }

    @Test fun firRejectsEmptyResourceAndBuildsNativeFirPlan() {
        assertThrows(IllegalArgumentException::class.java) { NeteasePresetParameters.fromFir(byteArrayOf()) }
        val plan = NeteasePresetParameters.fromFir(byteArrayOf(1, 2, 3))
        assertEquals(setOf("fir"), plan.modules)
        assertEquals(listOf("setFIRParams", "setFIRON"), plan.parameterCalls.map { it.first })
        assertTrue(plan.parameterCalls[1].second.single() as Boolean)
    }

    @Test fun disabledModuleStillRejectsUnknownNestedProcessor() {
        assertThrows(IllegalArgumentException::class.java) {
            NeteasePresetParameters.fromJson("""{"rvb":{"on":false,"tc":{"on":false,"f":[{"unknown":1}]}}}""")
        }
    }

    @Test fun aev2ReplacesWholePackageAndLegacyRouteRemainsAvailable() {
        val json = """{"eq":{"on":false},"aev2":{"bt":{"on":true,"bass":2,"treble":1}}}"""
        val current = NeteasePresetParameters.fromJson(json)
        assertTrue(current.usesAev2)
        assertEquals(setOf("bt"), current.modules)
        assertEquals("setBassTrebleParams", current.parameterCalls.first().first)
        val legacy = NeteasePresetParameters.fromJson(json, preferAev2 = false)
        assertEquals(setOf("eq"), legacy.modules)
        assertEquals(false, legacy.usesAev2)
        assertTrue(NeteasePresetParameters.fromJson(json, preferAev2 = false, soundType = 9).usesAev2)
        assertTrue(NeteasePresetParameters.fromJson("""{"aev2":{"eq":{"on":false}}}""", preferAev2 = false).usesAev2)
        assertEquals(false, NeteasePresetParameters.fromJson("""{"eq":{"on":false},"aev2":{}}""").usesAev2)
    }

    @Test fun customOrderIsPreservedAndValidatedBeforeNativeValidation() {
        val plan = NeteasePresetParameters.fromJson("""{"order":["eq","rvb"],"eq":{"on":false}}""")
        assertEquals("setCustomEffectChainOrder" to listOf(listOf("eq", "rvb")), plan.parameterCalls.first())
        assertThrows(IllegalArgumentException::class.java) {
            NeteasePresetParameters.fromJson("""{"order":["eq","eq"],"eq":{"on":false}}""")
        }
    }

    @Test fun extendedEffectsKeepNativeArgumentOrderAndDefaults() {
        val plan = NeteasePresetParameters.fromJson("""
            {"spatial":{"on":true,"global":1,"fl":2,"fr":3,"c":4,"sl":5,"sr":6,"rl":7,"rr":8,"lfe":9,"width":10,"delay":11,"cutoff":12,"enhance":true},
             "spspatial":{"on":false},
             "alc":{"on":true,"type":2,"hold":1,"st":2,"lt":3,"attack":4,"release":5,"lookahead":6,"minisize":7,"xyknee":8,"xypoints":[1,null,3]},
             "noise":{"on":true,"type":1,"link":2,"wet":3,"dry":4,"stime":5,"ltime":6,"attack":7,"release":8,"peqonoff":true,"peq":{"f":[{"type":6,"gain":2,"freq":1000,"q":1,"on":true}]}},
             "chorus":{"on":true,"rate":1,"depth":2,"delay":3,"mix":4,"voices":5},
             "flanger":{"on":true,"rate":1,"depth":2,"delay":3,"feedback":4,"mix":5},
             "dly":{"on":true,"delay":1,"feedback":2,"dry":3,"wet":4},
             "channelDelay":{"on":true,"delayMs":[1,2,3],"dryWet":[4,5]},
             "earFinger":{"on":false},"hearAudio":{"on":true,"mEarLoss":[1,2,3]}}
        """.trimIndent())
        assertEquals((1..12).map(Int::toFloat) + true, plan.parameterCalls.first { it.first == "setSpatialAudioParams" }.second)
        assertEquals(listOf(2,1f,2f,3f,4f,5f,6f,7,8f,listOf(1f,0f,3f)), plan.parameterCalls.first { it.first == "setALCParams" }.second)
        assertEquals(listOf(1,2,3f,4f,5f,6f,7f,8f), plan.parameterCalls.first { it.first == "setNoiseGenParams" }.second)
        assertEquals(listOf(listOf(1f,2f),listOf(4f,5f)), plan.parameterCalls.first { it.first == "setChannelDelayParams" }.second)
        assertEquals(listOf(1f,2f,3f,4f,5), plan.parameterCalls.first { it.first == "setChorusParams" }.second)
        assertEquals(listOf("setHearAudioON","setHearAudioGram"), plan.parameterCalls.takeLast(2).map { it.first })
    }

    @Test fun earFingerprintRequiresAnExplicitVerifiedResourceResolver() {
        val json = """{"earFinger":{"on":true,"mHrtfPath":"profile.hrtf"}}"""
        assertThrows(IllegalArgumentException::class.java) { NeteasePresetParameters.fromJson(json) }
        val plan = NeteasePresetParameters.fromJson(json, resolveResource = { "/private/verified/$it" })
        assertEquals(listOf("/private/verified/profile.hrtf"), plan.parameterCalls.first().second)
    }

    @Test fun originalMoodMetadataDoesNotChangeDspCalls() {
        val plain = NeteasePresetParameters.fromJson("""{"rvb":{"on":false}}""")
        val named = NeteasePresetParameters.fromJson("""{"aev2":{"effect_name":"nostalgic",
            "rvb":{"on":false,"rvb":{"on":false},"tc":{"on":false,"f":[{"name":"Highpass","band":0}]}}}}""")
        assertEquals(plain.parameterCalls, named.parameterCalls)
        assertThrows(IllegalArgumentException::class.java) {
            NeteasePresetParameters.fromJson("""{"effect_name":32,"eq":{"on":false}}""")
        }
    }

    @Test fun disabledOriginalPeqPlaceholdersKeepOriginalZeroDefaultsAndNativeCall() {
        val plan = NeteasePresetParameters.fromJson("""{"peq":{"on":true,"gain":0,"f":[{"on":false}]}}""")
        assertEquals(listOf(0, 0, 0f, 0f, 0f, false),
            plan.parameterCalls.first { it.first == "setParametricEQFilterParams" }.second)
    }
}

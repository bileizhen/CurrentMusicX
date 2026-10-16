package io.github.currencortex.music.data.effects

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class OriginalSpeakerEffectsTest {
    @Test fun originalOrientationAndVolumeThresholdsChooseSixDistinctMaterials() {
        val scene = OriginalSpeakerScene.NORMAL
        assertEquals(-200L, originalSpeakerId(scene, false, 0f))
        assertEquals(-200L, originalSpeakerId(scene, false, .199f))
        assertEquals(-201L, originalSpeakerId(scene, false, .2f))
        assertEquals(-201L, originalSpeakerId(scene, false, .749f))
        assertEquals(-202L, originalSpeakerId(scene, false, .75f))
        assertEquals(-100L, originalSpeakerId(scene, true, .1f))
        assertEquals(-101L, originalSpeakerId(scene, true, .5f))
        assertEquals(-102L, originalSpeakerId(scene, true, 1f))
        assertEquals(-300L, originalSpeakerId(OriginalSpeakerScene.SHOWER, true, .1f))
        assertEquals(-400L, originalSpeakerId(OriginalSpeakerScene.SLEEP, false, 1f))
    }
    @Test fun speakerNeedsRealMatchingFutureSvipAndDoesNotUseCatalogMembershipLabels() {
        fun member(id: Long = 42, code: Int = 300, until: Long = 2000) = buildJsonObject {
            put("userId", id); put("redplus", buildJsonObject { put("vipCode", code); put("expireTime", until) })
        }
        assertEquals(OriginalSpeakerEntitlement(42, true, 2000), originalSpeakerEntitlement(42, member(), 1000))
        assertFalse(originalSpeakerEntitlement(42, member(id = 43), 1000).canUse)
        assertFalse(originalSpeakerEntitlement(42, member(code = 100), 1000).canUse)
        assertFalse(originalSpeakerEntitlement(42, member(until = 1000), 1000).canUse)
    }
    @Test fun parsesDedicatedOuterSoundMaterialFieldsAndRejectsWrongSources() {
        fun row(url: String = "http://d1.music.126.net/dmusic/speaker.ncae", id: Int = -201) = buildJsonObject {
            put("id", 105); put("materialId", id); put("materialName", "竖屏中音量")
            put("meta", url); put("md5", "0123456789abcdef0123456789abcdef"); put("fileSize", 555)
        }
        val parsed = requireNotNull(parseOriginalSpeakerMaterial(row()))
        assertEquals(-201L, parsed.id)
        assertTrue(parsed.url.startsWith("https://"))
        assertEquals(555L, parsed.size)
        assertNull(parseOriginalSpeakerMaterial(row("https://d1.music.126.net.attacker.com/speaker.ncae")))
        assertNull(parseOriginalSpeakerMaterial(row("https://user@d1.music.126.net/speaker.ncae")))
        assertNull(parseOriginalSpeakerMaterial(row(id = 105)))
    }
    @Test fun originalPackagedDeviceMappingsOverrideOnlyExplicitModelAndOrientationSlots() {
        val huawei = requireNotNull(originalSpeakerDeviceOverride("RVL-AL09", -201))
        assertEquals(-201L, huawei.id)
        assertEquals("RVL-AL09_Portrait_L2", huawei.name)
        assertEquals("58f527be10953b101bb713cae910b9d5", huawei.md5)
        assertNotNull(originalSpeakerDeviceOverride("SHARK KSR-A0", -101))
        assertNull(originalSpeakerDeviceOverride("SHARK KSR-A0", -201))
        assertNull(originalSpeakerDeviceOverride("RMX5060", -201))
        assertNull(originalSpeakerDeviceOverride("RVL-AL09", -300))
    }
}

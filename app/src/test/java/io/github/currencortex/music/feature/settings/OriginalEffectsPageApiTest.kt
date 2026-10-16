package io.github.currencortex.music.feature.settings

import io.github.currencortex.music.core.media.PlayerState
import android.media.AudioDeviceInfo
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class OriginalEffectsPageApiTest {
    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test fun originalAndroidAuthStopMapsOnlyItsExactLocalTarget() {
        assertEquals(65536L, originalPageStopTarget(116, 100, 3600400))
        assertEquals(8L, originalPageStopTarget(116, 106, 3600400))
        assertEquals(256L, originalPageStopTarget(127, 3600400, 3600400))
        assertNull(originalPageStopTarget(127, 3600400, 3601400))
        assertNull(originalPageStopTarget(127, 3600400, null))
        assertNull(originalPageStopTarget(116, 999, 999))
        assertNull(originalPageStopTarget(127, 0, 0))
        assertNull(originalPageStopTarget(999, 100, 100))
    }

    @Test fun targetedStopCancelsOnlyMatchingPendingApply() {
        assertTrue(originalPageStopMatchesApply(65536, 100, OriginalPageApplyTarget(65536, null)))
        assertTrue(originalPageStopMatchesApply(8, 106, OriginalPageApplyTarget(8, null)))
        assertTrue(originalPageStopMatchesApply(256, 3600400, OriginalPageApplyTarget(256, 3600400)))
        assertFalse(originalPageStopMatchesApply(65536, 100, OriginalPageApplyTarget(8, null)))
        assertFalse(originalPageStopMatchesApply(8, 106, OriginalPageApplyTarget(256, 3600400)))
        assertFalse(originalPageStopMatchesApply(256, 3600400, OriginalPageApplyTarget(256, 3601400)))
        assertFalse(originalPageStopMatchesApply(256, 3600400, null))
    }

    @Test fun liveSvipMembershipIsNotVetoedByUnrelatedBenefitFields() {
        val result = originalPageMembership(json("""{"userId":10,"redplus":{"vipCode":300,"expireTime":5000},"canUse":false}"""), 10, 1000)
        assertEquals("300", result["vipType"]!!.jsonPrimitive.content)
        assertTrue(result["moodAllowed"]!!.jsonPrimitive.boolean)
        assertFalse(result["smartAllowed"]!!.jsonPrimitive.boolean)
    }

    @Test fun expiredPlusFallsBackOnlyToRealStillActiveBlackMembership() {
        val member = json("""{"userId":10,"redplus":{"vipCode":300,"expireTime":999},"associator":{"vipCode":100,"expireTime":2000}}""")
        assertEquals("100", originalPageMembership(member, 10, 1000)["vipType"]!!.jsonPrimitive.content)
        assertEquals("-1", originalPageMembership(member, 10, 2000)["vipType"]!!.jsonPrimitive.content)
        assertTrue(runCatching { originalPageMembership(member, 11, 1000) }.exceptionOrNull() is OriginalEffectsPageApiException)
    }

    @Test fun fileHashComesFromTypeOneMaterialWithoutDowngradingSvipOrChangingStrategy() {
        val effect = originalPageEffect(json("""{
            "id":3595400,"name":"原声黑胶","type":4,"soundType":1,
            "md5":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","newVipStrategyCode":"keep-original-strategy",
            "vipAuthRespItem":{"canUse":false},
            "soundMaterialList":[{"type":1,"md5":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            "link":"http://d1.music.126.net/dmusic/original.ncae"}]
        }"""))!!
        assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", effect.presetMd5)
        assertEquals("https://d1.music.126.net/dmusic/original.ncae", effect.presetLink)
        assertEquals(4, effect.memberType)
        assertEquals("keep-original-strategy", effect.strategyCode)
        assertTrue(effect.hasVipAuthStrategy)
        assertFalse(effect.publicOriginal)
        assertFalse(effect.legacyVipOriginal)
    }

    @Test fun invalidMaterialDoesNotFallBackToParentsUnrelatedLogicalHash() {
        assertNull(originalPageEffect(json("""{"id":1,"name":"test","type":1,
            "md5":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","link":"https://d1.music.126.net/top.ncae",
            "soundMaterialList":[{"type":1,"md5":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","link":"https://evil.invalid/preset.ncae"}]}""")))
    }

    @Test fun malformedOrOverflowedPrivilegeTypeCannotWrapIntoPublicType() {
        assertNull(originalPageEffect(json("""{"id":1,"name":"test","type":4294967297,
            "md5":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","link":"https://d1.music.126.net/test.ncae"}""")))
    }

    @Test fun networkBoundaryRejectsWritesAndAlternateHostsOrEncodedPaths() {
        assertEquals("/api/usertool/home/sound/order", originalPageReadPath("/api/usertool/home/sound/order"))
        for (path in listOf("/api/vipauth/app/interests/auth/set", "/api/vipauth/app/interests/auth/stop",
            "https://evil.invalid/api/usertool/home/sound/order", "/api/usertool/home/sound/%6frder",
            "/api/usertool/home/sound/order?header=cookie", "/api/login/cellphone")) {
            val failure = runCatching { originalPageReadPath(path) }.exceptionOrNull()
            assertTrue("Rejected $path", failure is OriginalEffectsPageApiException && failure.code == 403)
        }
    }

    @Test fun playbackJsonReportsRealLoadingPlayingAndErrorStates() {
        assertEquals("stopped", playerStateJson(PlayerState())["playState"]!!.jsonPrimitive.content)
        assertEquals("buffering", playerStateJson(PlayerState(loading = true))["playState"]!!.jsonPrimitive.content)
        assertEquals("playing", playerStateJson(PlayerState(playing = true))["playState"]!!.jsonPrimitive.content)
        assertEquals("error", playerStateJson(PlayerState(error = "failed"))["playState"]!!.jsonPrimitive.content)
    }

    @Test fun mediaRouteWinsOverConnectedButUnusedHeadphonesOrSpeakerPresence() {
        val speaker = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        val bluetooth = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        assertTrue(originalPageIsSpeaker(listOf(speaker), listOf(speaker, bluetooth)))
        assertFalse(originalPageIsSpeaker(listOf(bluetooth), listOf(speaker, bluetooth)))
        assertFalse(originalPageIsSpeaker(listOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE), listOf(speaker)))
    }

    @Test fun olderApiDeviceFallbackDoesNotAssumeSpeakerWhenAnExternalOutputExists() {
        val speaker = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        assertTrue(originalPageIsSpeaker(null, listOf(speaker, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)))
        for (external in listOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_BLE_HEADSET)) {
            assertFalse(originalPageIsSpeaker(null, listOf(speaker, external)))
        }
        assertFalse(originalPageIsSpeaker(null, emptyList()))
    }

    @Test fun missingOrOtherSongVisualMetadataIsNormalNoContentWithoutInventedModelValues() {
        for (value in listOf(originalPageEmotionVisualExtra(false, null, null, null),
            originalPageEmotionVisualExtra(true, null, 1, 1),
            originalPageEmotionVisualExtra(true, "calm", 1, 2))) {
            assertEquals(204, value["code"]!!.jsonPrimitive.int)
            assertFalse(value["available"]!!.jsonPrimitive.boolean)
            assertFalse(value.containsKey("dimensions"))
            assertFalse(value.containsKey("copywriting"))
        }
        val matched = originalPageEmotionVisualExtra(true, "calm", 1, 1)
        assertEquals(200, matched["code"]!!.jsonPrimitive.int)
        assertEquals("calm", matched["copywriting"]!!.jsonObject["emotion"]!!.jsonPrimitive.content)
        assertFalse(matched.containsKey("dimensions"))
    }
}

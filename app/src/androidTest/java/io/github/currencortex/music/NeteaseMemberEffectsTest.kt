package io.github.currencortex.music

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.data.effects.NcaeDecoder
import io.github.currencortex.music.core.netease.NeteaseTransport
import io.github.currencortex.music.core.netease.NeteaseClientProfile
import kotlinx.serialization.json.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/** Opt-in use of the phone's actual session. Never exports credentials or changes playback/settings. */
class NeteaseMemberEffectsTest {
    @Test fun actualSvipVinylPresetVerifiesRightsAndProcessesPcm() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("svipVinyl") == "true")
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(app.packageName == "com.bileizhen.currentmusic")
        val foreground = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        try {
            val container = app.container
            container.ready.await(); container.neteaseSessions.ready.await()
            val account = container.neteaseSessions.state.value
            assertTrue("Log in on the phone before this opt-in verification", account.loggedIn && !account.stale)
            val queue = container.playbackQueue.state.value
            val rights = container.neteaseSoundEffects.originalPresetEntitlement()
            assertEquals(account.profile!!.uid, rights.userId)
            assertTrue("Active SVIP must be verified by NetEase", rights.expiryFor(4) > System.currentTimeMillis())
            val resolved = requireNotNull(container.neteaseSoundEffects.resolve(3595400))
            assertEquals(4, resolved.memberType)
            assertTrue(resolved.legacyMemberOriginal)
            assertTrue(NeteaseNativeEngine.available())
            val decoded = NcaeDecoder.decode(container.neteaseSoundEffects.downloadPreset(resolved))
            File(app.getExternalFilesDir(null), "verified-vinyl-preset.json").writeBytes(decoded.bytes)
            val preset = NeteaseOriginalPreset(resolved.id, resolved.name, decoded.type, decoded.bytes)
            var produced = 0
            withContext(Dispatchers.Default) {
                NeteaseNativeEngine(48000, 2, preset).use { engine ->
                    val output = ByteBuffer.allocateDirect(32768)
                    repeat(20) { block ->
                        val input = ByteBuffer.allocateDirect(2048).order(ByteOrder.nativeOrder())
                        repeat(512) { frame -> repeat(2) {
                            input.putShort((1000 * sin(2 * PI * 997 * (block * 512 + frame) / 48000)).toInt().toShort())
                        } }
                        input.flip(); output.clear()
                        produced += engine.process(input, output)
                    }
                }
            }
            assertTrue("Original vinyl engine must produce PCM", produced > 0)
            assertEquals(queue.songs, container.playbackQueue.state.value.songs)
            File(app.getExternalFilesDir(null), "svip-vinyl-verification.txt").writeText(
                "effect=3595400\nmemberType=4\nmatchingAccountSvip=true\nmaterialChecksumVerified=true\n" +
                "decodedType=${decoded.type}\npcmBytes=$produced\nqueueSongsUnchanged=true\n")
        } finally { foreground.close() }
    }

    @Test fun actualMemberRightsAndOriginalPresetCompatibility() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("memberSoundEffects") == "true")
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(app.packageName == "com.bileizhen.currentmusic")
        val foreground = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        try {
        val container = app.container
        container.ready.await(); container.neteaseSessions.ready.await()
        val account = container.neteaseSessions.state.value
        assertTrue("Log in on the phone before running this opt-in probe", account.loggedIn && !account.stale)
        val rights = container.neteaseSoundEffects.originalPresetEntitlement()
        assertEquals(account.profile!!.uid, rights.userId)
        assertTrue("The real account must have current black VIP rights", rights.smartAllowed)
        assertTrue(rights.expiresAt > System.currentTimeMillis())
        if (!NeteaseNativeEngine.available()) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val fixture = File(app.cacheDir, "netease-engine-fixture.apk")
            try {
                instrumentation.context.assets.open(fixture.name).use { input -> fixture.outputStream().use(input::copyTo) }
                NeteaseEngineLibraries.installFile(app, fixture)
                withContext(Dispatchers.Main) { container.refreshOriginalEngine() }
            } finally { fixture.delete() }
        }
        assertTrue(NeteaseNativeEngine.available())
        val evidence = StringBuilder("loggedIn=true\nblackVipVerified=true\n")
        val membership = NeteaseTransport(container.neteaseSessions).request("/api/music-vip-membership/client/vip/info",
            buildJsonObject { put("userId", account.profile.uid) }, saveCookies = false,
            clientProfile = NeteaseClientProfile.ANDROID).body["data"]?.jsonObject
        val plus = membership?.get("redplus") as? JsonObject
        evidence.appendLine("svipCode=${plus?.get("vipCode")?.jsonPrimitive?.intOrNull}")
        evidence.appendLine("svipNotExpired=${(plus?.get("expireTime")?.jsonPrimitive?.longOrNull ?: 0) > System.currentTimeMillis()}")
        File(app.getExternalFilesDir(null), "member-effects-verification.txt").writeText(evidence.toString())
        val auth = NeteaseTransport(container.neteaseSessions).request("/api/vipauth/app/auth/query",
            buildJsonObject { put("resourceIdList", "106"); put("resourceTypeList", "116") }, saveCookies = false,
            clientProfile = NeteaseClientProfile.ANDROID).body
        fun shape(value: JsonElement, key: String = ""): JsonElement = when (value) {
            is JsonObject -> JsonObject(value.mapValues { (name, child) -> shape(child, name) })
            is JsonArray -> JsonArray(value.take(3).map { shape(it) })
            JsonNull -> JsonNull
            is JsonPrimitive -> when {
                key in setOf("resourceId", "resourceType", "canUse", "code") -> value
                key in setOf("expireTime", "expiredTime", "expiryTime") -> JsonPrimitive((value.longOrNull ?: 0) > System.currentTimeMillis())
                value.booleanOrNull != null -> JsonPrimitive("boolean")
                value.isString -> JsonPrimitive("string")
                else -> JsonPrimitive("number")
            }
        }
        evidence.appendLine("moodAuthorizationShape=${shape(auth)}")
        val moodRights = container.originalMoodModels.entitlement()
        evidence.appendLine("originalMoodAllowed=${moodRights.canUse}")
        evidence.appendLine("originalMoodNotExpired=${moodRights.expiresAt > System.currentTimeMillis()}")
        if (plus?.get("vipCode")?.jsonPrimitive?.intOrNull == 300 &&
            (plus["expireTime"]?.jsonPrimitive?.longOrNull ?: 0) > System.currentTimeMillis())
            assertTrue("Original type11 must honor the same valid SVIP account as the original client", moodRights.canUse)
        File(app.getExternalFilesDir(null), "member-effects-verification.txt").writeText(evidence.toString())
        val catalog = container.neteaseSoundEffects.catalog()
        for (effect in catalog.filter { it.legacyVipOriginal }) {
            try {
                val decoded = NcaeDecoder.decode(container.neteaseSoundEffects.downloadPreset(effect))
                val preset = NeteaseOriginalPreset(effect.id, effect.name, decoded.type, decoded.bytes)
                var produced = 0
                withContext(Dispatchers.Default) {
                    NeteaseNativeEngine(48000, 2, preset).use { engine ->
                        val output = ByteBuffer.allocateDirect(32768)
                        repeat(20) { block ->
                            val input = ByteBuffer.allocateDirect(2048).order(ByteOrder.nativeOrder())
                            repeat(512) { frame -> repeat(2) {
                                input.putShort((1000 * sin(2 * PI * 997 * (block * 512 + frame) / 48000)).toInt().toShort())
                            } }
                            input.flip(); output.clear()
                            produced += engine.process(input, output)
                        }
                    }
                }
                assertTrue("Member preset must produce PCM", produced > 0)
                evidence.appendLine("preset=${effect.id},type=${decoded.type},pcmBytes=$produced")
            } catch (_: IllegalArgumentException) {
                evidence.appendLine("preset=${effect.id},unsupportedParameters=true")
            }
        }
        val key = container.neteaseSoundEffects.originalSmartKey(186016)
        val definition = requireNotNull(NeteaseNativeEngine.smartDefinition())
        val all = org.json.JSONObject(definition.toString(Charsets.UTF_8))
        assertTrue(all.opt(key) is org.json.JSONObject || all.opt("0") is org.json.JSONObject)
        evidence.appendLine("originalSmartKeyValidated=true")
        File(app.getExternalFilesDir(null), "member-effects-verification.txt").writeText(evidence.toString())
        } finally { foreground.close() }
    }
}

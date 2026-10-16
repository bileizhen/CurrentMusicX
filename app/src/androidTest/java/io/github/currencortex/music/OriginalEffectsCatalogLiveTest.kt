package io.github.currencortex.music

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.data.effects.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.sin

/** Opt-in account-backed catalog verification. Separate engines, no queue or saved-effect writes. */
class OriginalEffectsCatalogLiveTest {
    @Test fun everyCurrentPresetVerifiesMaterialAndProducesNativePcm() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("allEffects") == "true")
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(app.packageName == "com.bileizhen.currentmusic")
        val foreground = ActivityScenario.launch(MainActivity::class.java)
        try {
            val container = app.container
            container.ready.await(); container.neteaseSessions.ready.await()
            assertTrue(container.neteaseSessions.state.value.loggedIn)
            val directory = File(app.getExternalFilesDir(null), "effects-catalog-coverage").apply { mkdirs() }
            val effects = container.neteaseSoundEffects.catalog().associateByTo(linkedMapOf()) { it.id }
            listOf(100L, 104L, 105L, 106L).forEach(effects::remove)
            for (expose in 1..3) {
                val raw = container.requestOriginalEffectsPage("/api/usertool/sound/queryAllSound",
                    buildJsonObject { put("exposeType", expose) })
                val rows = raw["data"] as? JsonArray ?: error("Missing catalog floor $expose")
                for (row in rows.filterIsInstance<JsonObject>()) {
                    val id = row["id"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 } ?: continue
                    if (id in listOf(100L, 104L, 105L, 106L)) continue
                    effects[id] = parseOriginalSoundEffect(row) ?: effects[id] ?: NeteaseSoundEffect(id,
                        row["name"]?.jsonPrimitive?.contentOrNull.orEmpty(), "",
                        memberType = row["type"]?.jsonPrimitive?.intOrNull ?: 0,
                        soundType = row["soundType"]?.jsonPrimitive?.intOrNull ?: 1)
                }
            }
            assertTrue("A complete catalog must contain the newer vinyl series", effects.containsKey(3595400))
            val neutral = process(NeteaseOriginalPreset(1, "neutral", 1, "{\"eq\":{\"on\":false}}".toByteArray()))
            val results = mutableListOf<JsonObject>()
            fun save() = File(directory, "report.json").writeText(JsonArray(results).toString())
            for ((id, metadata) in effects.toSortedMap()) {
                try {
                    val effect = container.neteaseSoundEffects.resolve(id) ?: metadata
                    assertEquals(id, effect.id)
                    check(effect.publicOriginal || effect.legacyMemberOriginal) { "Unsupported metadata: type=${effect.memberType}, sound=${effect.soundType}, strategy=${effect.strategyCode}, auth=${effect.hasVipAuthStrategy}" }
                    val bytes = container.neteaseSoundEffects.downloadPreset(effect)
                    val decoded = NcaeDecoder.decode(bytes)
                    File(directory, "$id.${if (decoded.type == 1) "json" else "bin"}").writeBytes(decoded.bytes)
                    val output = process(NeteaseOriginalPreset(id, effect.name, decoded.type, decoded.bytes))
                    check(output.isNotEmpty() && output.any { it != 0.toByte() }) { "No non-silent PCM output" }
                    results += buildJsonObject {
                        put("id", id); put("name", effect.name); put("memberType", effect.memberType)
                        put("status", "ok"); put("format", decoded.type); put("pcmBytes", output.size)
                        put("materialMd5", effect.presetMd5)
                        put("differentFromNeutral", !output.contentEquals(neutral))
                        put("pcmSha256", MessageDigest.getInstance("SHA-256").digest(output).joinToString("") { "%02x".format(it.toInt() and 255) })
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    results += buildJsonObject { put("id", id); put("name", metadata.name); put("status", "failed")
                        put("error", "${failure.javaClass.simpleName}: ${failure.message}".take(300)) }
                }
                save()
            }
            val failed = results.filter { it["status"]!!.jsonPrimitive.content != "ok" }
            assertTrue("Catalog failures: ${failed.joinToString { "${it["id"]}:${it["name"]}:${it["error"]}" }}", failed.isEmpty())
        } finally { foreground.close() }
    }

    private suspend fun process(preset: NeteaseOriginalPreset): ByteArray = withContext(Dispatchers.Default) {
        val bytes = ByteArrayOutputStream()
        NeteaseNativeEngine(48000, 2, preset).use { native ->
            val input = ByteBuffer.allocateDirect(2048).order(ByteOrder.nativeOrder())
            val output = ByteBuffer.allocateDirect(32768)
            repeat(32) { block ->
                input.clear()
                repeat(512) { frame -> repeat(2) { channel ->
                    val t = (block * 512 + frame) / 48000.0
                    val sample = 2400 * sin(2 * PI * (if (channel == 0) 997 else 431) * t)
                    input.putShort(sample.toInt().toShort())
                } }
                input.flip(); output.clear()
                val count = native.process(input, output)
                check(count in 0..output.capacity())
                repeat(count) { bytes.write(output.get(it).toInt()) }
            }
            repeat(4) {
                output.clear()
                val count = native.drain(output)
                check(count in 0..output.capacity())
                repeat(count) { bytes.write(output.get(it).toInt()) }
            }
        }
        bytes.toByteArray()
    }
}

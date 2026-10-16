package io.github.currencortex.music

import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

/** Local model fixture, original JNI, original preprocessing and actual synthetic DSP; no playback. */
class OriginalMoodEffectsTest {
    @Test fun originalModelSignatureInferenceAndAev2ProcessSyntheticPcm() {
        val i = InstrumentationRegistry.getInstrumentation()
        org.junit.Assume.assumeTrue(i.context.assets.list("").orEmpty().contains("netease-engine-fixture.apk"))
        NeteaseNativeEngine.configure(i.targetContext)
        val apk = File(i.targetContext.cacheDir, "mood-engine-fixture.apk")
        i.context.assets.open("netease-engine-fixture.apk").use { source -> apk.outputStream().use(source::copyTo) }
        try { NeteaseEngineLibraries.installFile(i.targetContext, apk) } finally { apk.delete() }
        assertTrue(NeteaseEngineLibraries.moodAvailable())
        val model = File(i.targetContext.cacheDir, "emofxnet-test.mnn")
        i.context.assets.open("original-mood/emofxnet.mnn").use { source -> model.outputStream().use(source::copyTo) }
        try {
            OriginalMoodModel(model).use { engine ->
                val codec = OriginalMoodCodec(i.targetContext.assets.open("netease-original/mood-codec.js").use { it.readBytes() })
                val row = Json.parseToJsonElement(i.context.assets.open("original-mood/descriptor.json").bufferedReader().use { it.readText() }).jsonObject
                val prepared = codec.prepare(row, engine.inputs, engine.outputs)
                assertTrue(prepared.infer)
                assertEquals(64, prepared.features.size)
                val output = engine.infer(prepared.emotion, prepared.genre, prepared.features, prepared.extra)
                assertEquals(32, output.size); assertTrue(output.all(Float::isFinite))
                val result = codec.finish(prepared, output)
                assertTrue(NeteasePresetParameters.fromJson(result.json).usesAev2)
                val pcm = ByteBuffer.allocateDirect(512 * 4).order(ByteOrder.nativeOrder())
                val out = ByteBuffer.allocateDirect(32768)
                var produced = 0; var nonzero = false
                NeteaseNativeEngine(48000, 2, result.preset()).use { fx ->
                    repeat(24) { block ->
                        pcm.clear(); repeat(1024) { pcm.putShort((2000 * sin((it + block * 1024) * 0.07)).toInt().toShort()) }; pcm.flip()
                        out.clear(); val count = fx.process(pcm, out)
                        assertTrue(count in 0..out.capacity() && count % 4 == 0)
                        produced += count
                        while (out.hasRemaining()) if (out.get() != 0.toByte()) nonzero = true
                    }
                }
                assertTrue(produced > 0); assertTrue(nonzero)
                File(i.targetContext.getExternalFilesDir(null), "original-mood-native.txt").writeText(
                    "inputs=${engine.inputs.map { it.name to it.shape }},outputs=${engine.outputs.map { it.name to it.shape }},finite=${output.size},emotion=${result.emotion},pcmBytes=$produced\n")
            }
        } finally { model.delete() }
    }
}

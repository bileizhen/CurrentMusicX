package io.github.currencortex.music

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.data.effects.NcaeDecoder
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Before
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.sin

/** Original native code, synthetic PCM only: never opens a speaker or audio focus. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class NeteaseOriginalEffectsTest {
    @Before fun loadImportedEngine() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = instrumentation.context.assets.list("").orEmpty().contains("netease-engine-fixture.apk")
        assumeTrue(BuildConfig.NETEASE_ORIGINAL_EFFECTS || fixture)
        NeteaseNativeEngine.configure(instrumentation.targetContext)
        if (fixture) {
            val copy = File(instrumentation.targetContext.cacheDir, "netease-engine-fixture.apk")
            try {
                instrumentation.context.assets.open(copy.name).use { input -> copy.outputStream().use(input::copyTo) }
                NeteaseEngineLibraries.installFile(instrumentation.targetContext, copy)
            } finally { copy.delete() }
            assertNotNull("Import must retain the original smart definition", NeteaseNativeEngine.smartDefinition())
        }
        assertTrue("Original engine must load through the real import path", NeteaseNativeEngine.available())
    }
    private val publicPresetIds = listOf(1001L, 1002L, 1003L, 1004L, 1005L, 1006L, 1008L,
        5001L, 6001L, 6002L, 7002L, 8001L, 8002L, 8003L, 9000L, 10000L, 11000L,
        12000L, 14000L, 27500L)
    private fun resource(id: Long): NeteaseOriginalPreset {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("original-effects/$id.ncae").use { it.readBytes() }
        val decoded = NcaeDecoder.decode(bytes)
        return NeteaseOriginalPreset(id, "原版$id", decoded.type, decoded.bytes)
    }
    private fun input(block: Int, rate: Int = 48000, channels: Int = 2): ByteBuffer =
        ByteBuffer.allocateDirect(512 * channels * 2).order(ByteOrder.nativeOrder()).apply {
            repeat(512) { frame -> repeat(channels) { channel ->
                putShort((1000 * sin(2 * PI * (if (channel == 0) 997 else 1709) * (block * 512 + frame) / rate)).toInt().toShort())
            } }
            flip()
        }
    private fun collect(preset: NeteaseOriginalPreset, rate: Int = 48000, channels: Int = 2): ByteArray {
        val output = ByteBuffer.allocateDirect(32768)
        val result = ByteArrayOutputStream()
        NeteaseNativeEngine(rate, channels, preset).use { engine ->
            repeat(48) { block ->
                output.clear()
                val count = engine.process(input(block, rate, channels), output)
                assertTrue("preset=${preset.id}: invalid native frame count $count", count in 0..output.capacity() && count % (channels * 2) == 0)
                val bytes = ByteArray(count); output.get(bytes); result.write(bytes)
            }
            var drained = false
            repeat(128) {
                if (!drained) {
                    output.clear()
                    val count = engine.drain(output)
                    assertTrue("preset=${preset.id}: invalid drain count $count", count in 0..output.capacity() && count % (channels * 2) == 0)
                    val bytes = ByteArray(count); output.get(bytes); result.write(bytes)
                    drained = count == 0
                }
            }
            assertTrue("preset=${preset.id}: native tail must finish within bounded buffers", drained)
        }
        return result.toByteArray().also {
            assertTrue("preset=${preset.id}: native lost input frames", it.size >= (48 * 512 - 512) * channels * 2)
            assertTrue("preset=${preset.id}: native output is silent", it.any { sample -> sample != 0.toByte() })
        }
    }

    @Test fun allTwentyPublicOriginalJsonAndFirPresetsReallyProcessPcm() {
        assertTrue("Preview must contain loadable original engine", NeteaseNativeEngine.available())
        val baseline = collect(NeteaseOriginalPreset(0, "无音效", 1, """{"eq":{"on":false}}""".toByteArray()))
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val packaged = assets.list("original-effects")!!.filter { it.endsWith(".ncae") }.mapNotNull { it.substringBefore('.').toLongOrNull() }.toSet()
        assertTrue("The full 20-preset fixture set is required", packaged.containsAll(publicPresetIds))
        val evidence = StringBuilder("publicPresets=${publicPresetIds.size},sampleRate=48000,channels=2\n")
        val evidenceFile = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "original-effects-native.txt")
        evidenceFile.writeText(evidence.toString())
        publicPresetIds.forEach { id ->
            val preset = resource(id)
            val output = collect(preset)
            val changed = !baseline.contentEquals(output)
            // A flat stereo preset is permitted to be neutral for a particular input waveform.
            if (id in setOf(1001L, 1004L, 1005L, 1008L))
                assertTrue("preset=$id: expected audible-processing preset did not change PCM", changed)
            val plan = if (preset.type == 1) NeteasePresetParameters.fromJson(preset.bytes.toString(Charsets.UTF_8)) else NeteasePresetParameters.fromFir(preset.bytes)
            evidence.appendLine("id=$id,type=${preset.type},aev2=${plan.usesAev2},modules=${plan.modules.sorted()},bytes=${output.size},changed=$changed,hash=${output.contentHashCode()}")
            evidenceFile.writeText(evidence.toString())
        }
        collect(resource(1001), 44100, 1)
        collect(resource(1004), 44100, 1)
        collect(resource(1005), 96000, 2)
        evidenceFile.appendText("additionalFormats=44100/mono/1001,44100/mono/1004,96000/stereo/1005\n")
    }

    @Test fun processorSwitchesToOriginalAndBackAndDrainsWithoutLosingFinalBuffers() {
        var selected: NeteaseOriginalPreset? = null
        val errors = mutableListOf<String>()
        val processor = NeteaseOriginalAudioProcessor({ selected }, { _, message -> errors.add(message) })
        processor.configure(AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)); processor.flush()
        fun bufferBytes(buffer: ByteBuffer) = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val pass = input(0); val expected = bufferBytes(pass.duplicate())
        processor.queueInput(pass)
        assertArrayEquals(expected, bufferBytes(processor.output))
        try {
            publicPresetIds.forEachIndexed { index, id ->
                val preset = resource(id)
                repeat(2) { cycle ->
                    selected = preset.copy(bytes = preset.bytes.copyOf())
                    repeat(4) { block ->
                        val source = input(index * 20 + cycle * 4 + block)
                        processor.queueInput(source)
                        assertFalse(source.hasRemaining())
                        val output = processor.output
                        assertEquals(0, output.remaining() % 4)
                        bufferBytes(output)
                    }
                    selected = null
                    val restored = input(index + cycle + 7); val original = bufferBytes(restored.duplicate())
                    processor.queueInput(restored)
                    assertArrayEquals("preset=$id: disabling must restore exact PCM", original, bufferBytes(processor.output))
                }
                // Flush while still selected must release and recreate the engine, preserving selection.
                selected = preset
                processor.queueInput(input(index)); bufferBytes(processor.output)
                processor.flush()
                repeat(4) { processor.queueInput(input(it)); bufferBytes(processor.output) }
                processor.queueEndOfStream()
                var finished = false
                repeat(128) {
                    if (!finished) { bufferBytes(processor.output); finished = processor.isEnded }
                }
                assertTrue("preset=$id: processor failed to drain after flush", finished)
                processor.flush()
            }
        } finally { processor.reset() }
        assertTrue(errors.toString(), errors.isEmpty())
    }

    @Test fun originalAev2PresetUsesItsCompleteNewPackage() {
        val preset = resource(1004)
        val current = NeteasePresetParameters.fromJson(preset.bytes.toString(Charsets.UTF_8))
        assertTrue(current.usesAev2)
        assertEquals(setOf("rvb", "se", "peq", "limiter", "bt", "eq", "cmp"), current.modules)
        val legacy = NeteasePresetParameters.fromJson(preset.bytes.toString(Charsets.UTF_8), preferAev2 = false)
        assertFalse(legacy.usesAev2)
        assertEquals(setOf("eq", "bt", "rvb", "se"), legacy.modules)
        val oldPcm = collect(preset.copy(bytes = org.json.JSONObject(preset.bytes.toString(Charsets.UTF_8)).apply { remove("aev2") }.toString().toByteArray()))
        assertFalse("aev2 must process its own complete curve rather than silently use outer EQ", oldPcm.contentEquals(collect(preset)))
    }

    @Test fun laterSelectionOnUnsupportedFormatRejectsAndPreservesPcm() {
        var selected: NeteaseOriginalPreset? = null
        val errors = mutableListOf<String>()
        val failedPresets = mutableListOf<NeteaseOriginalPreset?>()
        val processor = NeteaseOriginalAudioProcessor({ selected }, { preset, message -> failedPresets.add(preset); errors.add(message) })
        processor.configure(AudioFormat(48000, 6, C.ENCODING_PCM_16BIT)); processor.flush()
        val bytes = ByteArray(6 * 2 * 512) { (it * 17).toByte() }
        fun send() {
            val input = ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { flip() }
            processor.queueInput(input)
            val output = processor.output
            assertArrayEquals(bytes, ByteArray(output.remaining()).also { output.get(it) })
        }
        send(); selected = resource(1001); val requested = selected; send(); send()
        assertEquals(1, errors.size)
        assertSame("Failure must retain the exact failed selection, not just its ID", requested, failedPresets.single())
        processor.reset()
    }

    @Test fun processorSuccessCallbacksRetainIdentityWhenSmartPresetIdIsReused() {
        val first = resource(1001).copy(id = OriginalSmartEffectsController.SMART_PRESET_ID)
        val second = resource(1008).copy(id = OriginalSmartEffectsController.SMART_PRESET_ID)
        var selected: NeteaseOriginalPreset? = first
        val succeeded = mutableListOf<NeteaseOriginalPreset>()
        val errors = mutableListOf<String>()
        val processor = NeteaseOriginalAudioProcessor({ selected }, { _, message -> errors.add(message) }, succeeded::add)
        processor.configure(AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)); processor.flush()
        fun send(block: Int) {
            processor.queueInput(input(block))
            val output = processor.output
            output.position(output.limit())
        }
        try {
            repeat(12, ::send)
            selected = second
            repeat(12) { send(it + 12) }
            assertTrue(errors.toString(), errors.isEmpty())
            assertEquals(2, succeeded.size)
            assertSame(first, succeeded[0])
            assertSame(second, succeeded[1])
            // Simulate queued main-thread delivery after the newer preset became current.
            val currentCallbacks = succeeded.filter { it === selected }
            assertEquals(1, currentCallbacks.size)
            assertSame(second, currentCallbacks.single())
        } finally { processor.reset() }
    }

    @Test fun liveSelectionRestoresManualCurveAndViewingEqualizerDoesNotStackEffects() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveSoundEffects") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        val container = AppContainer(context, "original-native-${java.util.UUID.randomUUID()}", useNativeNetease = false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val track = android.media.AudioTrack.Builder().setAudioFormat(android.media.AudioFormat.Builder()
            .setSampleRate(48000).setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(8192).setTransferMode(android.media.AudioTrack.MODE_STREAM).build()
        try {
            container.ready.await(); container.neteaseSessions.ready.await()
            container.equalizerSettings.select(io.github.currencortex.music.data.settings.EqualizerPreset.BASS)
            container.equalizerSettings.setEnabled(true)
            withContext(Dispatchers.Main) {
                container.equalizer.attach(track.audioSessionId)
                scope.launch {
                    kotlinx.coroutines.flow.combine(container.smartTuning.effective, container.originalSoundEffects.selection) { manual, original ->
                        if (original == null) manual else manual.copy(enabled = false)
                    }.collect { container.equalizer.configure(it) }
                }
            }
            val catalog = container.neteaseSoundEffects.catalog()
            val effect = catalog.first { it.id == 1001L }
            withContext(Dispatchers.Main) { container.originalSoundEffects.select(effect) }
            withTimeout(20_000) {
                while (container.originalSoundEffects.selection.value == null || container.equalizer.state.value.active) {
                    check(container.originalSoundEffects.state.value.error == null) { container.originalSoundEffects.state.value.error!! }
                    delay(25)
                }
            }
            val saved = container.equalizerSettings.snapshot()
            withContext(Dispatchers.Main) {
                val store = androidx.lifecycle.ViewModelStore()
                store.put("equalizer", io.github.currencortex.music.feature.settings.EqualizerViewModel(container))
                store.clear()
                assertFalse("Viewing and closing EQ must not stack Android EQ over original DSP", container.equalizer.state.value.active)
                assertNotNull(container.originalSoundEffects.selection.value)
                container.originalSoundEffects.clear()
            }
            withTimeout(5_000) { while (!container.equalizer.state.value.active) delay(25) }
            assertEquals(saved, container.equalizerSettings.snapshot())
            assertEquals(android.media.AudioTrack.PLAYSTATE_STOPPED, track.playState)
        } finally { scope.cancel(); container.close(); track.release() }
    }
}

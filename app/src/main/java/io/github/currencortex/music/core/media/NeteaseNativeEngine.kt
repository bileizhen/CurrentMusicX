package io.github.currencortex.music.core.media

import android.content.Context
import android.net.Uri
import com.netease.cloudmusic.media.audiofx.effects.AudioEffects
import java.nio.ByteBuffer

data class NeteaseOriginalPreset(val id: Long, val name: String, val type: Int, val bytes: ByteArray)

/** One original engine per playback sink, confined to its audio thread. */
internal class NeteaseNativeEngine(sampleRate: Int, channels: Int, preset: NeteaseOriginalPreset) : AutoCloseable {
    private val effects: AudioEffects
    init {
        check(available()) { "原版音效引擎未加载" }
        require(sampleRate in 8_000..192_000 && channels in 1..2)
        val parameters = if (preset.type == 1) NeteasePresetParameters.fromJson(preset.bytes.toString(Charsets.UTF_8), channels = channels)
            else { require(preset.type == 2); NeteasePresetParameters.fromFir(preset.bytes) }
        effects = AudioEffects()
        try {
            check(NeteasePcmBridge.initialize(effects.nativeInstance, sampleRate, channels)) { "原版音效初始化失败" }
            parameters.applyTo(effects)
        } catch (failure: Throwable) { effects.close(); throw failure }
    }

    fun process(input: ByteBuffer, output: ByteBuffer): Int = NeteasePcmBridge.process(
        effects.nativeInstance, input, input.position(), input.remaining(), output, output.capacity())

    fun drain(output: ByteBuffer): Int = NeteasePcmBridge.drain(effects.nativeInstance, output, output.capacity())
    override fun close() = effects.close()

    companion object {
        fun configure(context: Context) = NeteaseEngineLibraries.configure(context)
        fun available(): Boolean = NeteaseEngineLibraries.available()
        fun smartDefinition(): ByteArray? = NeteaseEngineLibraries.smartDefinition()
        fun moodDefinition(): ByteArray? = NeteaseEngineLibraries.moodDefinition()
        suspend fun installFromApk(context: Context, uri: Uri) = NeteaseEngineLibraries.installFromApk(context, uri)
        suspend fun installFromInstalled(context: Context) = NeteaseEngineLibraries.installFromInstalled(context)
    }
}

@androidx.annotation.Keep
internal object NeteasePcmBridge {
    external fun initialize(handle: Long, sampleRate: Int, channels: Int): Boolean
    external fun process(handle: Long, input: ByteBuffer, offset: Int, length: Int, output: ByteBuffer, capacity: Int): Int
    external fun drain(handle: Long, output: ByteBuffer, capacity: Int): Int
}

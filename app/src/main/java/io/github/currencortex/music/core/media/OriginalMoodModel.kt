package io.github.currencortex.music.core.media

import com.netease.cloudmusic.core.mnn.MNNNetNative
import com.netease.cloudmusic.core.mnn.MNNTensorInfo
import java.io.Closeable
import java.io.File

/** Original MNN interpreter, isolated from the PCM thread; callers serialize inference with close. */
class OriginalMoodModel(model: File) : Closeable {
    private var net = 0L
    private var session = 0L
    val inputs: List<MoodTensorInfo>
    val outputs: List<MoodTensorInfo>

    init {
        loadLibraries()
        require(model.isFile && model.length() in 1..2_097_152) { "原版情绪模型文件无效" }
        try {
            net = MNNNetNative.nativeCreateNetFromFile(model.absolutePath)
            check(net != 0L) { "原版情绪模型加载失败" }
            session = MNNNetNative.nativeCreateSession(net, 0, 1, emptyArray(), emptyArray())
            check(session != 0L) { "原版情绪模型初始化失败" }
            inputs = MNNNetNative.nativeGetSessionInputAll(net, session).map(::metadata)
            outputs = MNNNetNative.nativeGetSessionOutputAll(net, session).map(::metadata)
            check(inputs.map { it.name }.toSet() == setOf("emotion_idx", "genre_idx", "features", "base_extra_flat"))
            val sizes = inputs.associate { it.name to it.size }
            check(sizes == mapOf("emotion_idx" to 1, "genre_idx" to 1, "features" to 64, "base_extra_flat" to 32))
            check(outputs.any { it.name == "aev2" && it.size == 32 && it.typeCode == 2 && it.typeBits == 32 })
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    @Synchronized fun infer(emotion: Int, genre: Int, features: FloatArray, extra: FloatArray): FloatArray {
        check(session != 0L && net != 0L) { "原版情绪模型已关闭" }
        require(features.size == 64 && extra.size == 32 && features.all(Float::isFinite) && extra.all(Float::isFinite))
        inputs.forEach { info ->
            val tensor = MNNNetNative.nativeGetSessionInput(net, session, info.name)
            check(tensor != 0L) { "原版情绪模型输入不存在" }
            when (info.name) {
                "emotion_idx", "genre_idx" -> {
                    val value = if (info.name == "emotion_idx") emotion else genre
                    if (info.typeCode == 0 && info.typeBits == 32)
                        MNNNetNative.nativeSetInputIntData(net, tensor, intArrayOf(value))
                    else if (info.typeCode == 2 && info.typeBits == 32)
                        MNNNetNative.nativeSetInputFloatData(net, tensor, floatArrayOf(value.toFloat()))
                    else error("原版情绪模型索引类型不兼容")
                }
                else -> {
                    check(info.typeCode == 2 && info.typeBits == 32) { "原版情绪模型特征类型不兼容" }
                    MNNNetNative.nativeSetInputFloatData(net, tensor, if (info.name == "features") features else extra)
                }
            }
        }
        check(MNNNetNative.nativeRunSession(net, session) == 0) { "原版情绪模型计算失败" }
        val tensor = MNNNetNative.nativeGetSessionOutput(net, session, "aev2")
        check(tensor != 0L && MNNNetNative.nativeTensorGetDimensions(tensor).fold(1, Int::times) == 32)
        check(MNNNetNative.nativeTensorGetData(tensor, null) == 32)
        return FloatArray(32) { Float.NaN }.also {
            MNNNetNative.nativeTensorGetData(tensor, it)
            check(it.all(Float::isFinite)) {
                "原版情绪模型返回无效结果"
            }
        }
    }

    @Synchronized override fun close() {
        if (session != 0L && net != 0L) MNNNetNative.nativeReleaseSession(net, session)
        session = 0L
        if (net != 0L) MNNNetNative.nativeReleaseNet(net)
        net = 0L
    }

    private fun metadata(value: MNNTensorInfo): MoodTensorInfo {
        require(value.shape.isNotEmpty() && value.shape.all { it in 1..4096 })
        return MoodTensorInfo(value.name, value.typeCode, value.typeBits, value.shape.toList())
    }

    companion object {
        @Volatile private var loaded = false
        @Synchronized fun loadLibraries() {
            if (loaded) return
            NeteaseEngineLibraries.loadMoodLibraries()
            loaded = true
        }
    }
}

data class MoodTensorInfo(val name: String, val typeCode: Int, val typeBits: Int, val shape: List<Int>) {
    val size: Int get() = shape.fold(1, Math::multiplyExact)
}

package io.github.currencortex.music.core.media

import kotlinx.serialization.json.*
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

/** Executes only the locally imported, hash-verified JSK audio algorithms, without Java bindings. */
class OriginalMoodCodec(source: ByteArray) {
    private val factory = BoundedFactory()
    private val scope: Scriptable

    init {
        require(source.size in 1..1_048_576)
        require(java.security.MessageDigest.getInstance("SHA-256").digest(source).joinToString("") { "%02x".format(it.toInt() and 255) } ==
            "bb756e69dffae120e355b00e3242d656922a0f2c82c7555e7abeb3a975a8059a") { "原版情绪算法校验失败" }
        scope = bounded(8_000) { cx ->
            cx.initSafeStandardObjects().also {
                cx.evaluateString(it, source.toString(Charsets.UTF_8), "netease-mood-codec", 1, null)
                listOf("moodPrepare", "moodFinish").forEach { name ->
                    check(ScriptableObject.getProperty(it, name) is Function)
                }
            }
        }
    }

    @Synchronized fun prepare(row: JsonObject, inputs: List<MoodTensorInfo>, outputs: List<MoodTensorInfo>,
        config: JsonObject = defaultConfig): PreparedMood {
        fun metadata(values: List<MoodTensorInfo>) = buildJsonObject {
            values.forEach { tensor -> put(tensor.name, buildJsonObject {
                put("name", tensor.name); put("size", tensor.size)
                put("shape", JsonArray(tensor.shape.map(::JsonPrimitive)))
                put("dtype", if (tensor.typeCode == 0) "int32" else "float32")
            }) }
        }
        val signature = buildJsonObject { put("inputs", metadata(inputs)); put("outputs", metadata(outputs)) }
        return PreparedMood(invoke("moodPrepare", listOf(row, signature, config)))
    }

    @Synchronized fun finish(prepared: PreparedMood, output: FloatArray?): MoodEffectResult {
        require(!prepared.infer || output?.size == 32 && output.all(Float::isFinite))
        val result = invoke("moodFinish", listOf(prepared.value,
            JsonArray(output?.map(::JsonPrimitive).orEmpty())))
        val parameters = result["aev2"] as? JsonObject ?: error("原版情绪音效返回格式无效")
        val raw = buildJsonObject { put("aev2", parameters) }.toString()
        NeteasePresetParameters.fromJson(raw)
        return MoodEffectResult(raw, (result["emotion"] as? JsonPrimitive)?.content.orEmpty(),
            (result["family"] as? JsonPrimitive)?.content.orEmpty(), prepared.infer,
            result["visualExtra"] as? JsonObject)
    }

    private fun invoke(name: String, arguments: List<JsonElement>): JsonObject = bounded(2_000) { cx ->
        val parser = ScriptableObject.getProperty(scope, "JSON") as Scriptable
        val parse = ScriptableObject.getProperty(parser, "parse") as Function
        val stringify = ScriptableObject.getProperty(parser, "stringify") as Function
        val args = arguments.map { parse.call(cx, scope, parser, arrayOf(it.toString())) }.toTypedArray()
        val result = (ScriptableObject.getProperty(scope, name) as Function).call(cx, scope, scope, args)
        val encoded = Context.toString(stringify.call(cx, scope, parser, arrayOf(result)))
        check(encoded.length <= 256 * 1024)
        Json.parseToJsonElement(encoded).jsonObject
    }

    private fun <T> bounded(millis: Long, block: (Context) -> T): T {
        factory.deadline.set(System.nanoTime() + millis * 1_000_000)
        return try { factory.call { cx -> block(cx) } } finally { factory.deadline.remove() }
    }

    companion object {
        /** 9.5.30 packaged play#emotionEffect data, not remote executable code. */
        internal val defaultConfig: JsonObject = Json.parseToJsonElement("""{"degradeSongIds":[],"defaultTemplateSongIds":[],"emotionTagOverride":{"calm":[300380],"energetic":[2638621269,2724740617],"nostalgic":[2624157484,68378,3382908505],"sad":[1428512815,1919926527,2147408164,1803546393,1891456146],"lonely":[1848147683],"confident":[3315467929,503207093,516823325,1981813581],"romantic":[516818336],"depressed":[22789469]}}""").jsonObject
    }

    private class BoundedFactory : ContextFactory() {
        val deadline = ThreadLocal<Long>()
        override fun makeContext(): Context = super.makeContext().apply {
            languageVersion = Context.VERSION_ES6
            optimizationLevel = -1
            instructionObserverThreshold = 10_000
            setClassShutter { false }
        }
        override fun observeInstructionCount(cx: Context, instructionCount: Int) {
            if (System.nanoTime() > (deadline.get() ?: 0L)) error("原版音效算法执行超时")
        }
    }
}

data class PreparedMood internal constructor(internal val value: JsonObject) {
    val infer: Boolean get() = value["infer"]?.jsonPrimitive?.booleanOrNull == true
    private fun tensor(name: String): JsonArray = value["tensors"]!!.jsonArray
        .map { it.jsonObject }.first { it["name"]!!.jsonPrimitive.content == name }["data"]!!.jsonArray
    val emotion: Int get() = tensor("emotion_idx").single().jsonPrimitive.int
    val genre: Int get() = tensor("genre_idx").single().jsonPrimitive.int
    val features: FloatArray get() = tensor("features").map { it.jsonPrimitive.float }.toFloatArray()
    val extra: FloatArray get() = tensor("base_extra_flat").map { it.jsonPrimitive.float }.toFloatArray()
}

data class MoodEffectResult(val json: String, val emotion: String, val family: String, val inferred: Boolean,
    val visualExtra: JsonObject? = null) {
    fun preset(): NeteaseOriginalPreset = NeteaseOriginalPreset(ORIGINAL_MOOD_PRESET_ID,
        "网易云 AI 情绪调音师", 1, json.toByteArray(Charsets.UTF_8))
}

const val ORIGINAL_MOOD_PRESET_ID = -111L

package io.github.currencortex.music.core.media

import kotlinx.serialization.json.*
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

/** Original 104 composeEffect, recovered from the APK's own JSK sourcemap. No MNN/mood substitution. */
class OriginalAiMasterCodec(source: ByteArray) {
    private val factory = BoundedFactory()
    private val scope: Scriptable

    init {
        require(source.size in 1..131_072)
        require(java.security.MessageDigest.getInstance("SHA-256").digest(source).joinToString("") {
            "%02x".format(it.toInt() and 255)
        } == "0d2cbcdf9646422c62dbb1eedc7e146d19e1a0312c4f24c83d1df6c96ccc2137") { "原版 AI 调音大师算法校验失败" }
        scope = bounded { cx -> cx.initSafeStandardObjects().also {
            cx.evaluateString(it, source.toString(Charsets.UTF_8), "netease-ai-master", 1, null)
        } }
    }

    @Synchronized fun compose(base: JsonObject, diy: JsonObject, baseKey: String,
        song: JsonObject?, selected: Set<String> = emptySet(), supportStereo: Boolean = false): OriginalAiMasterResult {
        require(baseKey.length in 1..64 && selected.size <= DIY_KEYS.size && DIY_KEYS.containsAll(selected))
        // Original hf1/q.w uses the selected base key only when the song-specific file is absent.
        val baseSelection = base[baseKey] as? JsonObject
        require(song != null || baseSelection != null) { "网易云未提供此歌曲的调音资源" }
        val params = buildJsonObject {
            put("baseJson", if (song == null) buildJsonObject { put(baseKey, requireNotNull(baseSelection)) } else JsonNull)
            put("songJson", song ?: JsonNull)
            put("diyJson", JsonObject(diy.filterKeys { it in selected }))
            put("supportStereo", supportStereo)
        }
        val result = bounded { cx ->
            val json = ScriptableObject.getProperty(scope, "JSON") as Scriptable
            val parse = ScriptableObject.getProperty(json, "parse") as Function
            val stringify = ScriptableObject.getProperty(json, "stringify") as Function
            val api = ScriptableObject.getProperty(scope, "NeteaseAiMaster") as Scriptable
            val function = ScriptableObject.getProperty(api, "composeEffect") as Function
            val argument = parse.call(cx, scope, json, arrayOf(params.toString()))
            val raw = function.call(cx, scope, api, arrayOf(argument))
            val encoded = Context.toString(stringify.call(cx, scope, json, arrayOf(raw)))
            require(encoded.length <= 262_144)
            Json.parseToJsonElement(encoded).jsonObject
        }
        check(result["success"]?.jsonPrimitive?.booleanOrNull == true) { "原版 AI 调音大师合成失败" }
        val merged = result["mergedJson"] as? JsonObject ?: error("调音输出格式无效")
        NeteasePresetParameters.fromJson(merged.toString())
        return OriginalAiMasterResult(merged.toString(), result["usedDiyConfig"]?.jsonArray
            ?.map { it.jsonPrimitive.content }.orEmpty(), song != null)
    }

    private fun <T> bounded(block: (Context) -> T): T {
        factory.deadline.set(System.nanoTime() + 2_000_000_000L)
        return try { factory.call { block(it) } } finally { factory.deadline.remove() }
    }

    companion object { val DIY_KEYS: Set<String> = setOf("en_bass", "en_treble", "en_vocal", "en_vibe", "en_live") }
    private class BoundedFactory : ContextFactory() {
        val deadline = ThreadLocal<Long>()
        override fun makeContext(): Context = super.makeContext().apply {
            languageVersion = Context.VERSION_ES6; optimizationLevel = -1
            instructionObserverThreshold = 10_000; setClassShutter { false }
        }
        override fun observeInstructionCount(cx: Context, instructionCount: Int) {
            check(System.nanoTime() <= (deadline.get() ?: 0)) { "原版调音算法执行超时" }
        }
    }
}

data class OriginalAiMasterResult(val json: String, val usedDiy: List<String>, val songSpecific: Boolean) {
    fun preset() = NeteaseOriginalPreset(ORIGINAL_AI_MASTER_PRESET_ID, "网易云 AI 调音大师", 1, json.toByteArray())
}

const val ORIGINAL_AI_MASTER_PRESET_ID: Long = -104L

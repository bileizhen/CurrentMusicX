package io.github.currencortex.music.core.media

import com.netease.cloudmusic.media.audiofx.effects.AudioEffects
import org.json.JSONArray
import org.json.JSONObject

/** A completely validated plan. Parsing never calls the native library or partially changes it. */
class NeteasePresetParameters private constructor(
    val modules: Set<String>,
    private val calls: List<Call>,
    val usesAev2: Boolean = false,
) {
    internal data class Call(val name: String, val arguments: List<Any>, val run: (AudioEffects) -> Unit)
    internal val parameterCalls: List<Pair<String, List<Any>>>
        get() = calls.map { it.name to it.arguments }

    /** Call only on the DSP owner thread, with the original library already loaded. */
    fun applyTo(effects: AudioEffects) {
        check(effects.nativeInstance != 0L) { "网易云音效引擎尚未初始化" }
        effects.resetParams()
        calls.forEach { it.run(effects) }
        effects.setON(true)
    }

    companion object {
        private const val MAX_JSON_BYTES = 256 * 1024
        private const val MAX_FIR_BYTES = 8 * 1024 * 1024

        private val moduleNames = setOf("eq", "bt", "se", "rvb", "rotate", "peq", "cmp", "limiter",
            "spatial", "spspatial", "alc", "noise", "chorus", "flanger", "dly", "channelDelay", "channeldelay", "earFinger", "hearAudio")

        /** Mirrors AudioEffectManager's whole-package aev2 replacement, rather than merging curves.
         * The compatible 9.5.30 engine supports the new configuration. Legacy callers may opt out.
         * External HRTF paths must be explicitly resolved into a verified local resource by the owner.
         */
        fun fromJson(text: String, preferAev2: Boolean = true, soundType: Int = 1, channels: Int = 2,
            resolveResource: (String) -> String? = { null }): NeteasePresetParameters {
            require(text.length <= MAX_JSON_BYTES && text.toByteArray(Charsets.UTF_8).size <= MAX_JSON_BYTES) {
                "音效参数文件过大"
            }
            var depth = 0
            var quoted = false
            var escaped = false
            text.forEach { char ->
                if (quoted) {
                    if (escaped) escaped = false
                    else if (char == '\\') escaped = true
                    else if (char == '"') quoted = false
                } else when (char) {
                    '"' -> quoted = true
                    '{', '[' -> { depth++; require(depth <= 12) { "音效参数嵌套过深" } }
                    '}', ']' -> depth--
                }
            }
            val envelope = JSONObject(text)
            require(channels in 1..12) { "音效声道数量无效" }
            val version = envelope.opt("version")
            require(version == null || version is String && version in setOf("v1", "v2", "v3")) { "音效参数版本无效" }
            // Current full-surround materials contain separate stereo / 5.1 / 7.1.4
            // plans. Select the sink's plan; never merge their delays or EQ bands.
            val document = if (version == "v3") {
                require(envelope.keys().asSequence().all { it in setOf("0", "2", "6", "12", "version", "effect_name") }) { "音效包含未知声道配置" }
                val key = if (channels == 1) "2" else channels.toString()
                val selected = if (envelope.has(key)) envelope.opt(key) else envelope.opt("0")
                selected as? JSONObject ?: throw IllegalArgumentException("音效缺少当前声道的参数")
            } else JSONObject(envelope.toString()).apply { remove("version") }
            require(document.keys().asSequence().all { it in moduleNames || it == "aev2" || it == "order" || it == "effect_name" }) { "音效包含未知处理模块" }
            val newer = if (document.has("aev2") && !document.isNull("aev2"))
                document.opt("aev2") as? JSONObject ?: throw IllegalArgumentException("aev2 格式无效") else null
            fun hasEffect(o: JSONObject) = moduleNames.any { o.has(it) && !o.isNull(it) }
            val useNew = (preferAev2 || soundType == 9 || !hasEffect(document)) && newer != null && hasEffect(newer)
            val selected = if (useNew) newer else JSONObject(document.toString()).apply { remove("aev2") }
            return Parser(selected, useNew, resolveResource).parse()
        }

        /** Bytes must already have passed the original NCAE container verification/decoder. */
        fun fromFir(decodedBytes: ByteArray): NeteasePresetParameters {
            require(decodedBytes.isNotEmpty() && decodedBytes.size <= MAX_FIR_BYTES) { "FIR 音效数据大小无效" }
            val owned = decodedBytes.copyOf()
            return NeteasePresetParameters(setOf("fir"), listOf(
                Call("setFIRParams", listOf(owned.size)) { it.setFIRParams(owned) },
                Call("setFIRON", listOf(true)) { it.setFIRON(true) },
            ))
        }
    }

    private class Parser(private val root: JSONObject, private val usesAev2: Boolean,
        private val resolveResource: (String) -> String?) {
        private val result = mutableListOf<Call>()
        private fun call(name: String, vararg values: Any, apply: (AudioEffects) -> Unit) {
            result += Call(name, values.toList(), apply)
        }

        fun parse(): NeteasePresetParameters {
            root.only(*(moduleNames + setOf("order", "effect_name")).toTypedArray())
            root.metadata("effect_name")
            require(root.length() > 0) { "音效参数为空" }
            root.arrayOrNull("order")?.let { array ->
                require(array.length() <= 32) { "音效链条过长" }
                val order = Array(array.length()) { i ->
                    val value = array.opt(i) as? String ?: throw IllegalArgumentException("音效链名称格式无效")
                    require(value.isNotBlank() && value.length <= 80 && value.none { it.code < 32 }) { "音效链名称无效" }
                    value
                }
                require(order.distinct().size == order.size) { "音效链包含重复模块" }
                if (order.isNotEmpty()) call("setCustomEffectChainOrder", order.toList()) {
                    // The original Java applier also continues when this optional
                    // order is rejected (e.g. vendor lists containing effect_name).
                    it.setCustomEffectChainOrder(order)
                }
            }
            root.module("eq")?.let(::equalizer)
            root.module("bt")?.let(::bassTreble)
            root.module("se")?.let(::stereo)
            root.module("rvb")?.let(::reverb)
            root.module("rotate")?.let(::rotate)
            root.module("peq")?.let(::parametric)
            root.module("cmp")?.let(::compressor)
            root.module("limiter")?.let(::limiter)
            root.module("spatial")?.let(::spatial)
            root.module("spspatial")?.let(::speakerSpatial)
            root.module("alc")?.let(::alc)
            root.module("noise")?.let(::noise)
            root.module("chorus")?.let(::chorus)
            root.module("flanger")?.let(::flanger)
            root.module("dly")?.let(::delay)
            require(!(root.has("channelDelay") && root.has("channeldelay"))) { "重复的声道延迟模块" }
            (root.module("channelDelay") ?: root.module("channeldelay"))?.let(::channelDelay)
            root.module("earFinger")?.let(::earFinger)
            root.module("hearAudio")?.let(::hearing)
            return NeteasePresetParameters(root.keys().asSequence().filter { it != "order" }.toSet(), result.toList(), usesAev2)
        }

        private fun equalizer(o: JSONObject) {
            o.only("on", "eqs", "useable", "fileName")
            val on = o.on()
            val gains = o.arrayOrNull("eqs")?.floats(10, 10, -60f, 24f)
            if (on) {
                requireNotNull(gains) { "音效缺少 10 段均衡器参数" }
                call("setGraphEQGains", gains.toList()) { it.setGraphEQGains(gains) }
            }
            call("setGraphEQON", on) { it.setGraphEQON(on) }
        }

        private fun bassTreble(o: JSONObject) {
            o.only("on", "bass", "treble", "bassFreq", "bassQ", "trebleFreq", "trebleQ", "useable")
            val on = o.on()
            if (on) {
                val bass = o.f("bass", -60f, 24f)
                val treble = o.f("treble", -60f, 24f)
                call("setBassTrebleParams", bass, treble) { it.setBassTrebleParams(bass, treble) }
                val extra = listOf("bassFreq", "bassQ", "trebleFreq", "trebleQ")
                if (extra.any(o::has)) {
                    val bf = o.finite("bassFreq")
                    val bq = o.finite("bassQ")
                    val tf = o.finite("trebleFreq")
                    val tq = o.finite("trebleQ")
                    // Lao0/a.g leaves native defaults intact for zero-Q beans.
                    // Its final bound is on treble gain, not treble Q.
                    if (bq in 0.1f..10f && tq >= 0.1f && treble <= 10f) {
                        call("setBassTrebleExtraParams", bf, bq, tf, tq) { it.setBassTrebleExtraParams(bf, bq, tf, tq) }
                    }
                }
            } else o.validateNumbers()
            call("setBassTrebleON", on) { it.setBassTrebleON(on) }
        }

        private fun stereo(o: JSONObject) {
            o.only("on", "presence", "stereoizer", "sshaper", "ambience", "useable")
            val on = o.on()
            if (on) {
                val p = o.f("presence", -60f, 60f)
                val s = o.f("stereoizer", -60f, 60f)
                val shape = o.b("sshaper")
                val a = o.i("ambience", 0, 7)
                call("setStereoEnhancerParams", p, s, shape, a) { it.setStereoEnhancerParams(p, s, shape, a) }
            } else o.validateNumbers()
            call("setStereoEnhancerON", on) { it.setStereoEnhancerON(on) }
        }

        private fun reverb(o: JSONObject) {
            o.only("on", "er", "rvb", "tc", "il", "rl", "ol", "useable", "fileName")
            val on = o.on()
            val er = o.module("er")?.also { it.only("on", "pattern", "rsize", "sdelay") }
            val rvb = o.module("rvb")?.also {
                it.only("pdelay", "dtime", "hfdamping", "density", "rshape", "q", "diffusion", "swidth", "on")
                if (it.has("on")) require(it.opt("on") is Boolean) { "混响参数标记无效" }
            }
            val tc = o.module("tc")?.also { it.only("on", "f") }
            tc?.arrayOrNull("f")?.objects(32)?.forEach {
                it.only("band", "insert", "curve", "gain", "freq", "q", "name"); it.metadata("name")
            }
            val il = o.module("il")?.also { it.only("center", "lfe") }
            val rl = o.module("rl")?.also { it.only("front", "rear", "center", "lfe") }
            val ol = o.module("ol")?.also { it.only("dry", "er", "rvb") }
            if (on) {
                er?.let {
                    if (it.on()) {
                        val p = it.i("pattern", 0, 30)
                        val r = it.f("rsize", 0f, 1_000f)
                        val d = it.f("sdelay", 0f, 10_000f)
                        call("setReverbERParams", true, p, r, d) { e -> e.setReverbERParams(true, p, r, d) }
                    } else call("setReverbERParams", false, 0, 0f, 0f) { e -> e.setReverbERParams(false, 0, 0f, 0f) }
                }
                rvb?.let {
                    val p = it.f("pdelay", 0f, 10_000f)
                    val d = it.f("dtime", 0.001f, 120f)
                    val h = it.f("hfdamping", 0f, 100f)
                    val den = it.f("density", 0f, 1_000f)
                    val r = it.f("rshape", 0f, 1_000f)
                    val q = it.i("q", 0, 64)
                    val dif = it.f("diffusion", 0f, 1_000f)
                    val w = it.f("swidth", 0f, 1_000f)
                    call("setReverbParams", p, d, h, den, r, q, dif, w) { e -> e.setReverbParams(p, d, h, den, r, q, dif, w) }
                }
                tc?.let {
                    val enabled = it.on()
                    val bands = it.arrayOrNull("f")
                    require(!enabled || bands != null) { "混响滤波参数不完整" }
                    bands?.objects(4)?.forEach { b ->
                        b.only("band", "insert", "curve", "gain", "freq", "q", "name")
                        val band = b.i("band", 0, 3)
                        val insert = b.i("insert", 0, 3)
                        val curve = b.i("curve", 0, 2)
                        val gain = b.f("gain", -120f, 24f)
                        val freq = b.f("freq", 1f, 192_000f)
                        val q = b.f("q", 0.001f, 100f)
                        if (enabled) call("setReverbFilterParams", band, insert, curve, gain, freq, q) { e -> e.setReverbFilterParams(band, insert, curve, gain, freq, q) }
                    }
                    call("setReverbTCEnable", enabled) { e -> e.setReverbTCEnable(enabled) }
                }
                il?.let {
                    val c = it.f("center", -120f, 24f); val l = it.f("lfe", -120f, 24f)
                    call("setReverbInputLevelParams", c, l) { e -> e.setReverbInputLevelParams(c, l) }
                }
                rl?.let {
                    val f = it.f("front", -120f, 24f); val r = it.f("rear", -120f, 24f)
                    val c = it.f("center", -120f, 24f); val l = it.f("lfe", -120f, 24f)
                    call("setReverbLevelParams", f, r, c, l) { e -> e.setReverbLevelParams(f, r, c, l) }
                }
                ol?.let {
                    val d = it.f("dry", -120f, 24f); val e = it.f("er", -120f, 24f); val r = it.f("rvb", -120f, 24f)
                    call("setReverbOutputLevelParams", d, e, r) { a -> a.setReverbOutputLevelParams(d, e, r) }
                }
            }
            o.validateNumbers()
            call("setReverbON", on) { it.setReverbON(on) }
        }

        private fun rotate(o: JSONObject) {
            o.only("on", "velocity", "useable")
            val on = o.on()
            if (on) {
                val v = o.f("velocity", -100f, 100f)
                call("setRotate3DParams", v) { it.setRotate3DParams(v) }
            } else o.validateNumbers()
            call("setRotate3DON", on) { it.setRotate3DON(on) }
        }

        private fun parametric(o: JSONObject) {
            o.only("on", "gain", "f", "useable")
            val on = o.on()
            val filters = o.arrayOrNull("f")?.objects(32)
            if (on) {
                val gain = o.f("gain", -60f, 24f)
                call("setParametricEQParams", gain) { it.setParametricEQParams(gain) }
                requireNotNull(filters) { "参数均衡器缺少滤波器" }
            }
            filters?.forEach { b ->
                b.only("band", "type", "gain", "freq", "q", "on")
                if (on) {
                    val enabled = b.on()
                    // Original PeqF beans supply zeros for disabled placeholder entries.
                    // Retain their explicit native calls, including the band-zero behavior.
                    val band = if (b.has("band")) b.i("band", 0, 15) else 0
                    val type = if (b.has("type")) b.i("type", 0, 10) else 0
                    val gain = if (b.has("gain")) b.f("gain", -60f, 24f) else 0f
                    val freq = if (b.has("freq")) b.f("freq", if (enabled) 1f else 0f, 192_000f) else 0f
                    val q = if (b.has("q")) b.f("q", 0f, 100f) else 0f
                    require(!enabled || freq > 0f) { "启用的均衡器滤波器频率必须大于零" }
                    // Original low/high-pass and shelving presets legitimately use
                    // Q=0; the native filter interprets it according to its type.
                    call("setParametricEQFilterParams", band, type, gain, freq, q, enabled) { e -> e.setParametricEQFilterParams(band, type, gain, freq, q, enabled) }
                } else b.validateNumbers()
            }
            call("setParametricEQON", on) { it.setParametricEQON(on) }
        }

        private fun compressor(o: JSONObject) {
            o.only("on", "threshold", "ratio", "attack", "release", "range", "pregain", "postgain", "postGain", "core", "useable")
            val on = o.on()
            if (on) {
                require(!(o.has("postgain") && o.has("postGain"))) { "重复的压缩器增益字段" }
                val t = o.f("threshold", -120f, 24f); val r = o.f("ratio", 1f, 100f)
                // The original AI master uses zero attack/release for its pregain-only compressor.
                val a = o.f("attack", 0f, 60f); val rel = o.f("release", 0f, 120f)
                val range = o.f("range", -120f, 120f); val pre = o.f("pregain", -60f, 24f)
                val post = o.f(if (o.has("postgain")) "postgain" else "postGain", -60f, 24f)
                call("setCompressorParams", t, r, a, rel, range, pre, post) { it.setCompressorParams(t, r, a, rel, range, pre, post) }
            } else o.validateNumbers()
            call("setCompressorON", on) { it.setCompressorON(on) }
        }

        private fun limiter(o: JSONObject) {
            o.only("on", "threshold", "attack", "release", "outputgain", "infiniteRatio", "ratio", "useable", "core")
            if (o.has("core")) require(o.opt("core") is Boolean) { "限幅器标记无效" }
            if (o.on()) {
                val t = o.f("threshold", -120f, 24f); val a = o.f("attack", 0.000001f, 60f)
                val r = o.f("release", 0.000001f, 120f); val g = o.f("outputgain", -60f, 24f)
                val inf = o.b("infiniteRatio"); val ratio = o.f("ratio", 1f, 100f)
                call("setLimiterParams", t, a, r, g, inf, ratio) { it.setLimiterParams(t, a, r, g, inf, ratio) }
            } else {
                o.validateNumbers()
                // Original Lao0/a.g uses this default instead of disabling the limiter.
                call("setLimiterParams", 0f, 0.002f, 0.04f, 0f, true, 4f) { it.setLimiterParams(0f, 0.002f, 0.04f, 0f, true, 4f) }
            }
        }

        private fun spatial(o: JSONObject) {
            val names = listOf("global", "fl", "fr", "c", "sl", "sr", "rl", "rr", "lfe", "width", "delay", "cutoff")
            o.only(*(names + listOf("on", "enhance", "useable")).toTypedArray())
            val on = o.on()
            if (on) {
                val p = names.map { o.finite(it) }
                val enhance = o.b("enhance")
                call("setSpatialAudioParams", *p.toTypedArray(), enhance) { e -> e.setSpatialAudioParams(
                    p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8], p[9], p[10], p[11], enhance) }
            }
            o.validateNumbers()
            call("setSpatialAudioON", on) { it.setSpatialAudioON(on) }
        }

        private fun speakerSpatial(o: JSONObject) {
            val ints = listOf("upmix", "lronoff", "lrangle", "otheronoff")
            val floats = listOf("global", "center", "lfe", "fl", "fr", "left", "right", "rl", "rr", "ul", "ur")
            o.only(*(ints + floats + listOf("on", "useable")).toTypedArray())
            val on = o.on()
            if (on) {
                val i = ints.map { o.integer(it) }; val p = floats.map { o.finite(it) }
                call("setSpeakerSpatialParams", *(i + p).toTypedArray<Any>()) { e -> e.setSpeakerSpatialParams(
                    i[0], i[1], i[2], i[3], p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8], p[9], p[10]) }
            }
            o.validateNumbers()
            call("setSpeakerSpatialON", on) { it.setSpeakerSpatialON(on) }
        }

        private fun alc(o: JSONObject) {
            o.only("on", "type", "hold", "st", "lt", "attack", "release", "lookahead", "minisize", "xyknee", "xypoints", "useable")
            val on = o.on()
            val points = o.arrayOrNull("xypoints")?.let { array ->
                require(array.length() <= 256) { "ALC 控制点过多" }
                FloatArray(array.length()) { index ->
                    if (array.isNull(index)) 0f else finiteValue(array.opt(index), "xypoints")
                }
            }?.takeIf { it.isNotEmpty() }
            if (on) {
                val type = o.integer("type"); val hold = o.finite("hold"); val st = o.finite("st"); val lt = o.finite("lt")
                val attack = o.finite("attack"); val release = o.finite("release"); val look = o.finite("lookahead")
                val mini = o.integer("minisize"); val knee = o.finite("xyknee")
                call("setALCParams", type, hold, st, lt, attack, release, look, mini, knee, points?.toList() ?: emptyList<Float>()) {
                    it.setALCParams(type, hold, st, lt, attack, release, look, mini, knee, points)
                }
            }
            o.validateNumbers()
            call("setALCON", on) { it.setALCON(on) }
        }

        private fun noise(o: JSONObject) {
            o.only("on", "type", "link", "wet", "dry", "stime", "ltime", "attack", "release", "peqonoff", "peq", "useable")
            val on = o.on()
            val peq = o.module("peq")?.also { it.only("f") }
            val filters = peq?.arrayOrNull("f")?.objects(32)
            filters?.forEach {
                // NoiseBand has no native band slot; the original handler uses
                // each entry's list index, and defaults absent bean fields to zero.
                it.only("band", "type", "gain", "freq", "q", "on")
                if (it.has("band")) it.i("band", 0, 31)
            }
            if (on) {
                val type = o.integer("type"); val link = o.integer("link")
                val wet = o.finite("wet"); val dry = o.finite("dry"); val st = o.finite("stime"); val lt = o.finite("ltime")
                val attack = o.finite("attack"); val release = o.finite("release")
                call("setNoiseGenParams", type, link, wet, dry, st, lt, attack, release) { it.setNoiseGenParams(type, link, wet, dry, st, lt, attack, release) }
                val peqOn = o.b("peqonoff")
                call("setNoiseGenPeqOnOff", peqOn) { it.setNoiseGenPeqOnOff(peqOn) }
                filters?.forEachIndexed { index, b ->
                    val t = if (b.has("type")) b.i("type", 0, 10) else 0
                    val g = b.finite("gain"); val f = b.finite("freq"); val q = b.finite("q"); val enabled = b.on()
                    call("setNoiseGenPeqFilterParams", index, t, g, f, q, enabled) { it.setNoiseGenPeqFilterParams(index, t, g, f, q, enabled) }
                }
            }
            o.validateNumbers()
            call("setNoiseGenON", on) { it.setNoiseGenON(on) }
        }

        private fun chorus(o: JSONObject) {
            o.only("on", "rate", "depth", "delay", "mix", "voices", "useable")
            val on = o.on()
            if (on) {
                val r = o.finite("rate"); val d = o.finite("depth"); val delay = o.finite("delay"); val mix = o.finite("mix"); val voices = o.integer("voices")
                call("setChorusParams", r, d, delay, mix, voices) { it.setChorusParams(r, d, delay, mix, voices) }
            }
            o.validateNumbers()
            call("setChorusON", on) { it.setChorusON(on) }
        }

        private fun flanger(o: JSONObject) {
            o.only("on", "rate", "depth", "delay", "feedback", "mix", "useable")
            val on = o.on()
            if (on) {
                val r = o.finite("rate"); val d = o.finite("depth"); val delay = o.finite("delay"); val feedback = o.finite("feedback"); val mix = o.finite("mix")
                call("setFlangerParams", r, d, delay, feedback, mix) { it.setFlangerParams(r, d, delay, feedback, mix) }
            }
            o.validateNumbers()
            call("setFlangerON", on) { it.setFlangerON(on) }
        }

        private fun delay(o: JSONObject) {
            o.only("on", "delay", "feedback", "dry", "wet", "useable")
            val on = o.on()
            if (on) {
                val delay = o.finite("delay"); val feedback = o.finite("feedback"); val dry = o.finite("dry"); val wet = o.finite("wet")
                call("setDelayParams", delay, feedback, dry, wet) { it.setDelayParams(delay, feedback, dry, wet) }
            }
            o.validateNumbers()
            call("setDelayON", on) { it.setDelayON(on) }
        }

        private fun channelDelay(o: JSONObject) {
            o.only("on", "channelCount", "delayMs", "dryWet", "useable")
            val on = o.on()
            val delays = o.arrayOrNull("delayMs")
            val mixes = o.arrayOrNull("dryWet")
            if (on && delays != null && mixes != null) {
                // The original applier truncates to the smaller list and the native 12-channel limit.
                require(delays.length() <= 256 && mixes.length() <= 256) { "声道延迟数组过长" }
                val count = minOf(delays.length(), mixes.length(), 12)
                val d = FloatArray(count) { finiteValue(delays.opt(it), "delayMs") }
                val m = FloatArray(count) { finiteValue(mixes.opt(it), "dryWet") }
                call("setChannelDelayParams", d.toList(), m.toList()) { it.setChannelDelayParams(d, m) }
            }
            o.validateNumbers()
            call("setChannelDelayON", on) { it.setChannelDelayON(on) }
        }

        private fun earFinger(o: JSONObject) {
            o.only("on", "mHrtfPath", "useable")
            val on = o.on()
            if (on) {
                val path = o.opt("mHrtfPath") as? String ?: throw IllegalArgumentException("耳纹音效缺少 HRTF 资源")
                val local = resolveResource(path) ?: throw IllegalArgumentException("耳纹音效需要经过验证的本地 HRTF 资源")
                require(local.isNotBlank() && local.length <= 4096 && '\u0000' !in local) { "HRTF 资源路径无效" }
                call("setEarFingerParams", local) { it.setEarFingerParams(local) }
            }
            call("setEarFingerAudioON", on) { it.setEarFingerAudioON(on) }
        }

        private fun hearing(o: JSONObject) {
            o.only("on", "mEarLoss", "useable")
            val on = o.on()
            if (on) {
                val losses = o.arrayOrNull("mEarLoss") ?: throw IllegalArgumentException("听力补偿缺少听力曲线")
                val values = losses.floats(1, 64, -Float.MAX_VALUE, Float.MAX_VALUE)
                call("setHearAudioON", true) { it.setHearAudioON(true) }
                call("setHearAudioGram", values.toList()) { it.setHearAudioGram(values) }
            } else call("setHearAudioON", false) { it.setHearAudioON(false) }
        }

        private fun finiteValue(value: Any?, name: String): Float {
            val n = value as? Number ?: throw IllegalArgumentException("音效参数 $name 需要数值")
            val d = n.toDouble(); val f = d.toFloat()
            require(d.isFinite() && f.isFinite()) { "音效参数 $name 不是有限数值" }
            return f
        }

        // The original Java bean constructors default absent primitive fields to zero/false.
        private fun JSONObject.finite(name: String): Float = if (!has(name)) 0f else finiteValue(opt(name), name)
        private fun JSONObject.integer(name: String): Int = if (!has(name)) 0 else i(name, Int.MIN_VALUE, Int.MAX_VALUE)

        private fun JSONObject.only(vararg names: String) {
            val supported = names.toSet()
            keys().forEach { key -> require(key in supported) { "暂不支持的网易云音效参数：$key" } }
            if (has("useable")) b("useable")
            if (has("core")) b("core")
        }

        private fun JSONObject.module(name: String): JSONObject? {
            if (!has(name) || isNull(name)) return null
            return opt(name) as? JSONObject ?: throw IllegalArgumentException("音效模块 $name 格式无效")
        }

        private fun JSONObject.on(): Boolean = b("on")
        private fun JSONObject.b(name: String): Boolean =
            if (!has(name)) false else opt(name) as? Boolean ?: throw IllegalArgumentException("音效参数 $name 需要布尔值")

        private fun JSONObject.f(name: String, min: Float, max: Float): Float {
            val number = opt(name) as? Number ?: throw IllegalArgumentException("音效参数 $name 需要数值")
            val double = number.toDouble()
            require(double.isFinite() && double >= min.toDouble() && double <= max.toDouble()) { "音效参数 $name 超出支持范围" }
            return double.toFloat()
        }

        private fun JSONObject.i(name: String, min: Int, max: Int): Int {
            val n = opt(name) as? Number ?: throw IllegalArgumentException("音效参数 $name 需要整数")
            val d = n.toDouble()
            require(d.isFinite() && d >= min && d <= max && d == d.toInt().toDouble()) { "音效参数 $name 超出支持范围" }
            return d.toInt()
        }

        private fun JSONObject.arrayOrNull(name: String): JSONArray? {
            if (!has(name) || isNull(name)) return null
            return opt(name) as? JSONArray ?: throw IllegalArgumentException("音效参数 $name 需要数组")
        }

        private fun JSONArray.floats(minCount: Int, maxCount: Int, min: Float, max: Float): FloatArray {
            require(length() in minCount..maxCount) { "音效数组长度无效" }
            return FloatArray(length()) { i ->
                val n = opt(i) as? Number ?: throw IllegalArgumentException("音效数组需要数值")
                val d = n.toDouble()
                require(d.isFinite() && d >= min.toDouble() && d <= max.toDouble()) { "音效数组数值超出支持范围" }
                d.toFloat()
            }
        }

        private fun JSONArray.objects(maxCount: Int): List<JSONObject> {
            require(length() <= maxCount) { "音效滤波器数量过多" }
            return List(length()) { opt(it) as? JSONObject ?: throw IllegalArgumentException("音效滤波器格式无效") }
        }

        private fun JSONObject.metadata(key: String) {
            if (has(key)) require(opt(key) is String && getString(key).length <= 512) { "音效元数据无效" }
        }

        private fun JSONObject.validateNumbers(depth: Int = 0) {
            require(depth <= 8) { "音效参数嵌套过深" }
            keys().forEach { name ->
                when (val value = opt(name)) {
                    is Number -> require(value.toDouble().isFinite()) { "音效参数 $name 不是有限数值" }
                    is JSONObject -> value.validateNumbers(depth + 1)
                    is JSONArray -> {
                        val maxCount = if (name in setOf("xypoints", "delayMs", "dryWet")) 256 else 64
                        require(value.length() <= maxCount) { "音效参数数组过长" }
                        repeat(value.length()) { i ->
                            when (val entry = value.opt(i)) {
                                is Number -> require(entry.toDouble().isFinite()) { "音效数组包含无效数值" }
                                is JSONObject -> entry.validateNumbers(depth + 1)
                                else -> require(name == "xypoints" && entry == JSONObject.NULL) { "音效数组数据无效" }
                            }
                        }
                    }
                    is Boolean -> Unit
                    is String -> require(name in setOf("fileName", "effect_name", "name") &&
                        value.length <= if (name == "fileName") 1024 else 512) { "音效参数 $name 格式无效" }
                    else -> throw IllegalArgumentException("音效参数 $name 格式无效")
                }
            }
        }
    }
}

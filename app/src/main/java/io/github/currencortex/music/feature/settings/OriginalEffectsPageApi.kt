package io.github.currencortex.music.feature.settings

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.BuildConfig
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.effects.NeteaseSoundEffect
import io.github.currencortex.music.data.effects.parseOriginalSoundEffect
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*

/** A narrow host for the recovered original page; it never exposes native credentials to JavaScript. */
class OriginalEffectsPageApi(private val container: AppContainer, private val vm: SoundEffectsViewModel) {
    // Only accessed on Main. A slow verification must never queue a user's next selection/off.
    private var applyGeneration = 0L
    private var pendingApply: Deferred<JsonObject>? = null
    private var pendingControllerType: Long? = null
    private var pendingApplyTarget: OriginalPageApplyTarget? = null

    suspend fun request(method: String, params: JsonObject): JsonElement {
        if (method.length > 160 || params.toString().length > 32_768) fail("页面请求过大", 400)
        return when (method) {
            "app.info", "getAppInfo" -> buildJsonObject {
                put("appName", "CurrentMusic"); put("version", BuildConfig.VERSION_NAME)
                put("clientVersion", BuildConfig.VERSION_NAME); put("compatibilityVersion", "9.5.30")
                put("platform", "android")
            }
            "vipType", "nm.rnVipCashier.vipType" -> getOriginalRights()
            "getOriginalRights" -> getOriginalRights()
            "currentEffect", "nm.playEffect.currentEffect" -> currentEffect()
            "applyEffect", "applyEffectV2", "nm.playEffect.applyEffectV2" -> startApply(params)
            "disableEffect", "nm.playEffect.disableEffect" -> disableEffect(params)
            "host.stopEffect" -> stopEffect(params)
            "getCurrentSong", "nm.play.getCurrentSong" -> songJson(container.playerController.state.value.song)
            "getPlayerState", "nm.play.getPlayerState" -> playerStateJson(container.playerController.state.value)
            "getIsSpeakerOn", "nm.urlAudio.getIsSpeakerOn" -> speakerRoute()
            "isLogin", "nm.user.isLogin" -> {
                container.neteaseSessions.ready.await()
                val account = container.neteaseSessions.state.value
                buildJsonObject { put("login", account.loggedIn && !account.stale && (account.profile?.uid ?: 0) > 0) }
            }
            "playerPlay", "nm.play.playerPlay" -> playerPlay()
            "playerPause", "nm.play.playerPause" -> withContext(Dispatchers.Main.immediate) {
                if (!container.playerController.state.value.canControlPlayback) fail("当前播放由房主或投屏端控制", 409)
                container.playerController.pause(); playerStateJson(container.playerController.state.value)
            }
            "getCurPlayingList", "nm.playingList.getCurPlayingList" -> buildJsonObject {
                val queue = container.playerController.queue.state.value
                put("songIds", JsonArray(queue.songs.filter { it.musicSource == MusicSource.NETEASE && !it.video }.map { JsonPrimitive(it.id.toString()) }))
                put("songIndex", queue.index)
            }
            "playSongs", "nm.play.playSongs" -> playSongs(params)
            "queryCache", "cache.query" -> queryCache(params)
            "addCache", "cache.add" -> addCache(params)
            "network" -> network(params)
            "getEmotionVisualExtra", "nm.playEffect.getEmotionVisualExtra" -> emotionVisualExtra(null)
            "getEmotionVisualExtraForSong", "nm.playEffect.getEmotionVisualExtraForSong" ->
                emotionVisualExtra(params.long("songId") ?: fail("缺少有效歌曲 ID", 400))
            "systemEffectExist", "nm.playEffect.supportSystemEffect" -> buildJsonObject {
                put("isSupport", false); put("message", "此原版页面宿主暂未接入系统音效面板")
            }
            "currentDeviceEffect", "nm.playEffect.currentDeviceEffectV2" -> buildJsonObject {
                put("id", 0); put("name", ""); put("supported", false)
            }
            "getUsingEQ", "nm.customeq.getUsingEQ" -> buildJsonObject {
                put("id", 0); put("isUsing", false); put("supported", false)
                put("message", "未移植网易云个性化听感偏好；本机均衡器通过 type=1 控制")
            }
            "getAiDiy", "nm.playEffect.getAiDiy" -> buildJsonObject {
                put("selected", buildJsonArray {}); put("supported", false)
            }
            "currentTwinkle", "nm.playEffect.currentTwinkle" -> buildJsonObject {
                put("type", 0); put("id", 0); put("supported", false)
            }
            "abTestList", "abtest.getList" -> hostCapabilities(params)
            "openUrl", "openURL", "nm.navigator.openURL" -> navigationRequest(params)
            "exitApp" -> buildJsonObject { put("action", "close"); put("handled", false) }
            "showToast", "toast.show", "showVipToast", "nm.vipToast.show" -> {
                val text = params.text("text").ifBlank { params.text("message") }.take(200)
                container.errorNotices.showMessage(text)
                buildJsonObject { put("action", "toast"); put("text", text); put("handled", true) }
            }
            else -> fail("此原版页面功能尚未移植：${method.take(100)}")
        }
    }

    /** Cheap, credential-free event payload. Reading state does not re-query membership or mutate DSP. */
    suspend fun snapshot(): JsonObject = withContext(Dispatchers.Main.immediate) {
        val player = container.playerController.state.value
        val account = container.neteaseSessions.state.value
        buildJsonObject {
            put("currentEffect", currentEffect()); put("song", songJson(player.song))
            put("player", playerStateJson(player)); put("accountRevision", account.revision)
            put("loggedIn", account.loggedIn && !account.stale)
            put("moodLoading", vm.originalMood.value.loading); put("smartLoading", vm.originalSmart.value.loading)
            put("originalLoading", vm.original.value.loading)
        }
    }

    private fun currentEffect(): JsonObject {
        val mood = vm.originalMood.value
        val smart = vm.originalSmart.value
        val single = vm.original.value
        val choice = when {
            mood.enabled -> Triple(8, 106L, "AI 情绪调音师")
            smart.enabled -> Triple(65536, 100L, "智能音效")
            single.selectedId != null && container.originalSoundEffects.selection.value?.id == single.selectedId ->
                Triple(256, single.selectedId, single.selectedName.orEmpty())
            vm.manual.value.enabled && !vm.preferences.value.smartEnabled -> Triple(1, 0L, "均衡器")
            else -> Triple(0, 0L, "")
        }
        return buildJsonObject {
            put("type", choice.first); put("id", choice.second); put("name", choice.third)
            put("processing", when (choice.first) { 8 -> mood.processing; 65536 -> smart.processing; 256 -> single.processing; else -> false })
        }
    }

    private suspend fun startApply(params: JsonObject): JsonObject = coroutineScope {
        val requestScope = this
        val operation = withContext(Dispatchers.Main.immediate) {
            invalidateApply()
            val token = applyGeneration
            pendingApplyTarget = OriginalPageApplyTarget(params.long("type"),
                (params["params"] as? JsonObject)?.long("id"))
            requestScope.async(Dispatchers.Default, start = CoroutineStart.LAZY) {
                try { applyEffect(params, token) }
                finally { withContext(NonCancellable + Dispatchers.Main.immediate) {
                    if (token == applyGeneration) {
                        pendingApply = null; pendingControllerType = null; pendingApplyTarget = null
                    }
                } }
            }.also { pendingApply = it }
        }
        operation.start()
        operation.await()
    }

    private fun invalidateApply() {
        applyGeneration++
        pendingApply?.cancel(CancellationException("已被新的音效选择或关闭操作替代"))
        pendingApply = null
        cancelPendingController()
        pendingControllerType = null
        pendingApplyTarget = null
    }

    private fun cancelPendingController() {
        when (pendingControllerType) {
            8L -> if (vm.originalMood.value.let { it.loading || it.requestedEnabled && !it.enabled })
                container.originalMoodEffects.disable()
            65536L -> if (vm.originalSmart.value.let { it.loading || it.requestedEnabled && !it.enabled })
                container.originalSmartEffects.disable()
            256L -> if (vm.original.value.loading) container.originalSoundEffects.clear()
        }
    }

    private fun ensureCurrentApply(token: Long) {
        if (token != applyGeneration) throw CancellationException("已被新的音效选择或关闭操作替代")
    }

    private suspend fun applyEffect(params: JsonObject, token: Long): JsonObject {
        val type = params.long("type") ?: fail("缺少音效类型", 400)
        container.neteaseSessions.ready.await()
        try {
            withTimeout(60_000) {
                when (type) {
                    8L -> {
                        withContext(Dispatchers.Main.immediate) {
                            ensureCurrentApply(token); pendingControllerType = type; vm.originalMoodEnabled(true)
                        }
                        val result = vm.originalMood.first { !it.loading && it.message != "正在验证网易云 AI 情绪专属权益" }
                        if (!result.enabled) fail(result.message, 409)
                    }
                    65536L -> {
                        withContext(Dispatchers.Main.immediate) {
                            ensureCurrentApply(token); pendingControllerType = type; vm.originalSmartEnabled(true)
                        }
                        val result = vm.originalSmart.first { !it.loading && it.message != "正在验证网易云黑胶权益" }
                        if (!result.enabled) fail(result.message, 409)
                    }
                    256L -> {
                        val material = params["params"] as? JsonObject ?: fail("缺少音效素材", 400)
                        val id = material.long("id") ?: fail("缺少音效 ID", 400)
                        val effect = resolveEffect(id, material)
                        vm.originalUnavailableReason(effect)?.let { fail(it, 409) }
                        withContext(Dispatchers.Main.immediate) {
                            ensureCurrentApply(token); pendingControllerType = type; vm.useOriginal(effect)
                        }
                        val result = vm.original.first { !it.loading }
                        if (result.selectedId != id || container.originalSoundEffects.selection.value?.id != id || result.error != null)
                            fail(result.error ?: "原版音效尚未成功启用", 409)
                    }
                    1L -> withContext(Dispatchers.Main.immediate) {
                        ensureCurrentApply(token)
                        // Enable the user's current curve; do not replace a custom curve with a preset.
                        container.originalMoodEffects.disable(); container.originalSmartEffects.disable()
                        container.originalSoundEffects.clear()
                        container.soundEffectsSettings.setSmartEnabled(false)
                        ensureCurrentApply(token)
                        container.equalizerSettings.setEnabled(true)
                        val value = container.equalizerSettings.snapshot()
                        ensureCurrentApply(token)
                        container.smartTuning.previewManual(value); container.equalizer.configure(value)
                    }
                    2L -> fail("AI 调音大师尚未移植；请使用已接入的原版 AI 情绪调音师")
                    4L -> fail("网易云外放音效尚未移植")
                    else -> fail("此类型的原版个性化音效尚未移植：$type")
                }
            }
        } catch (_: TimeoutCancellationException) {
            withContext(Dispatchers.Main.immediate) { if (token == applyGeneration) cancelPendingController() }
            fail("原版音效验证仍未完成，请检查网络后重试", 408)
        }
        return withContext(Dispatchers.Main.immediate) { ensureCurrentApply(token); currentEffect() }
    }

    private suspend fun resolveEffect(id: Long, supplied: JsonObject): NeteaseSoundEffect {
        val parsed = originalPageEffect(supplied)
        if (parsed != null && parsed.id != id) fail("音效素材与所选项目不一致", 400)
        if (parsed == null) container.neteaseSoundEffects.catalog().firstOrNull { it.id == id }?.let { return it }
        // Re-read authoritative material/permission fields; never trust a Web cache on enable.
        return container.neteaseSoundEffects.resolve(id) ?: fail("此音效尚无可验证的标准原版素材", 409)
    }

    private suspend fun disableEffect(params: JsonObject): JsonObject = withContext(Dispatchers.Main.immediate) {
        invalidateApply()
        when (params.long("type") ?: fail("缺少音效类型", 400)) {
            8L -> vm.originalMoodEnabled(false)
            65536L -> vm.originalSmartEnabled(false)
            256L -> vm.clearOriginal()
            1L -> {
                container.equalizerSettings.setEnabled(false)
                val value = container.equalizerSettings.snapshot()
                container.smartTuning.previewManual(value); container.equalizer.configure(value)
            }
            0L -> vm.turnOff()
            else -> fail("此原版音效类型尚未移植，未改变其他音效")
        }
        currentEffect()
    }

    /** Original Android closeAll only sends auth/stop. This is local DSP control, not that server write. */
    private suspend fun stopEffect(params: JsonObject): JsonObject = withContext(Dispatchers.Main.immediate) {
        val interestsType = params.long("interestsType") ?: fail("缺少权益类型", 400)
        val interestsId = params.long("interestsId") ?: fail("缺少权益 ID", 400)
        val target = originalPageStopTarget(interestsType, interestsId, vm.original.value.selectedId)
        if (target != null) {
            if (originalPageStopMatchesApply(target, interestsId, pendingApplyTarget)) invalidateApply()
            when (target) {
                8L -> container.originalMoodEffects.disable()
                65536L -> container.originalSmartEffects.disable()
                256L -> container.originalSoundEffects.clear()
            }
        }
        buildJsonObject {
            put("source", "local-control"); put("handled", target != null)
            put("currentEffect", currentEffect())
        }
    }

    private suspend fun getOriginalRights(): JsonObject {
        container.neteaseSessions.ready.await()
        val owner = container.neteaseSessions.state.value
        val uid = owner.profile?.uid ?: 0
        if (!owner.loggedIn || owner.stale || uid <= 0) return buildJsonObject {
            put("vipType", "-1"); put("userId", 0); put("expiresAt", 0)
        }
        val raw = container.requestOriginalEffectsPage("/api/music-vip-membership/client/vip/info", buildJsonObject { put("userId", uid) })
        if (container.neteaseSessions.state.value != owner) fail("网易云账号已切换，请刷新音效页", 409)
        if (raw.long("code") != 200L) fail("网易云会员状态查询失败", 502)
        val member = raw["data"] as? JsonObject ?: fail("网易云会员信息格式无效", 502)
        if (member.long("userId") != uid) fail("网易云会员账号校验失败", 409)
        return originalPageMembership(member, uid)
    }

    private suspend fun network(params: JsonObject): JsonObject {
        val path = originalPageReadPath(params.text("url"))
        val verb = params.text("method").ifBlank { "GET" }.uppercase()
        if (verb !in setOf("GET", "POST")) fail("原版音效页只支持读取接口", 405)
        fun body(name: String): JsonObject = when (val value = params[name]) {
            null, JsonNull -> buildJsonObject {}
            is JsonObject -> value
            else -> fail("页面请求 $name 格式无效", 400)
        }
        val query = body("query")
        val data = body("data")
        val input = JsonObject(query + data)
        if (input.size > 64 || input.keys.any { it.lowercase() in setOf("cookie", "cookies", "header", "authorization", "csrf_token", "__csrf") })
            fail("页面请求参数无效", 400)
        val body = container.requestOriginalEffectsPage(path, input)
        return buildJsonObject { put("status", 200); put("body", body) }
    }

    private suspend fun queryCache(params: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val key = cacheKey(params)
        val stored = container.originalEffectsPageCache.getString(key, null)
        buildJsonObject { put("value", stored?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() } ?: JsonNull) }
    }

    private suspend fun addCache(params: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val key = cacheKey(params)
        val value = params["value"] ?: fail("缺少页面偏好值", 400)
        if (value.toString().length > 8192) fail("页面偏好值过大", 400)
        if (!container.originalEffectsPageCache.edit().putString(key, value.toString()).commit()) fail("页面偏好保存失败", 500)
        buildJsonObject { put("saved", true) }
    }

    private fun cacheKey(params: JsonObject): String = params.text("key").takeIf {
        it.length in 1..160 && it.none { char -> char.code < 32 }
    } ?: fail("页面偏好键无效", 400)

    private fun emotionVisualExtra(songId: Long?): JsonObject {
        val state = vm.originalMood.value
        return originalPageEmotionVisualExtra(state.enabled, state.emotion, state.songId, songId)
    }

    private suspend fun speakerRoute(): JsonObject = withContext(Dispatchers.Default) {
        val manager = container.originalEffectsPageAudioManager()
        val routed = if (manager != null && Build.VERSION.SDK_INT >= 33) runCatching {
            // Media policy routing, not the unrelated voice-call speakerphone toggle.
            manager.getAudioDevicesForAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .map { it.type }.takeIf { it.isNotEmpty() }
        }.getOrNull() else null
        val connected = if (manager != null) runCatching {
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }
        }.getOrDefault(emptyList()) else emptyList()
        buildJsonObject {
            put("isSpeaker", originalPageIsSpeaker(routed, connected))
            put("routeKnown", routed != null)
            put("source", if (routed != null) "media-policy" else "connected-output-devices")
        }
    }

    private suspend fun playerPlay(): JsonObject = withContext(Dispatchers.Main.immediate) {
        val player = container.playerController
        if (!player.state.value.canControlPlayback) fail("当前播放由房主或投屏端控制", 409)
        if (player.state.value.song == null) fail("当前没有可播放的歌曲", 409)
        if (!player.state.value.showPause) player.toggle().join()
        playerStateJson(player.state.value)
    }

    private suspend fun playSongs(params: JsonObject): JsonObject {
        val ids = (params["songIds"] as? JsonArray)?.map { it.jsonPrimitive.longOrNull ?: fail("歌曲 ID 无效", 400) }
            ?.takeIf { it.size in 1..20 && it.all { id -> id > 0 } } ?: fail("缺少可播放的歌曲 ID", 400)
        val requestedIndex = params.long("songIndex") ?: 0
        if (requestedIndex !in 0 until ids.size.toLong()) fail("歌曲位置无效", 400)
        val index = requestedIndex.toInt()
        val player = container.playerController
        if (!player.state.value.canControlPlayback) fail("当前播放由房主或投屏端控制", 409)
        val known = player.queue.state.value.songs.filter { it.musicSource == MusicSource.NETEASE && !it.video }.associateBy { it.id }
        val songs = ids.map { known[it] ?: container.musicRepository.detail(it) }
        withContext(Dispatchers.Main.immediate) { player.playList(songs, index) }
        return playerStateJson(player.state.value)
    }

    private fun navigationRequest(params: JsonObject): JsonObject {
        val url = params.text("url").ifBlank { params.text("URL") }
        val parsed = runCatching { java.net.URI(url) }.getOrNull() ?: fail("页面链接无效", 400)
        val allowed = parsed.scheme == "orpheus" || (parsed.scheme == "https" && parsed.host == "music.163.com" && parsed.userInfo == null)
        if (!allowed || url.length > 4096 || url.any { it.code < 32 }) fail("此页面链接不能由宿主打开", 400)
        return buildJsonObject { put("action", "navigate"); put("url", url); put("handled", false) }
    }

    private fun hostCapabilities(params: JsonObject): JsonObject = buildJsonObject {
        put("source", "CurrentMusic host capabilities")
        put("data", buildJsonObject {
            (params["list"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().take(16).forEach { row ->
                val name = row.text("name")
                if (name.length in 1..100) put(name, buildJsonObject {
                    put("expGroupName", if (name == "hy-qingxvyx") "t1" else "c")
                })
            }
        })
    }

    private fun songJson(song: Song?): JsonObject = buildJsonObject {
        if (song != null && song.musicSource == MusicSource.NETEASE && !song.video) {
            put("songId", song.id); put("songName", song.name); put("artistName", song.artists)
            put("albumName", song.album); put("coverUrl", song.cover); put("duration", song.durationMs)
        }
    }
}

class OriginalEffectsPageApiException(message: String, val code: Int = 501) : IllegalStateException(message)
private fun fail(message: String, code: Int = 501): Nothing = throw OriginalEffectsPageApiException(message, code)
private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.long(key: String) = (get(key) as? JsonPrimitive)?.longOrNull

internal data class OriginalPageApplyTarget(val type: Long?, val id: Long?)

/** A delayed stop for an old card must not turn off a different standard effect. */
internal fun originalPageStopTarget(interestsType: Long, interestsId: Long, singleId: Long?): Long? = when {
    interestsType == 116L && interestsId == 100L -> 65536L
    interestsType == 116L && interestsId == 106L -> 8L
    interestsType == 127L && interestsId > 0 && interestsId == singleId -> 256L
    else -> null
}

internal fun originalPageStopMatchesApply(type: Long, id: Long, pending: OriginalPageApplyTarget?): Boolean =
    pending != null && pending.type == type && (type != 256L || pending.id == id)

internal fun playerStateJson(state: PlayerState): JsonObject = buildJsonObject {
    put("playState", when { state.error != null -> "error"; state.loading || state.resolving -> "buffering"
        state.playing -> "playing"; state.song == null -> "stopped"; else -> "paused" })
    put("position", state.positionMs); put("duration", state.durationMs)
}

internal fun originalPageMembership(member: JsonObject, uid: Long, now: Long = System.currentTimeMillis()): JsonObject {
    if (uid <= 0 || member.long("userId") != uid) fail("网易云会员账号校验失败", 409)
    fun deadline(name: String, code: Long): Long {
        val row = member[name] as? JsonObject ?: return 0
        return if (row.long("vipCode") == code) (row.long("expireTime") ?: 0).takeIf { it > now } ?: 0 else 0
    }
    val plus = deadline("redplus", 300)
    val black = deadline("associator", 100)
    return buildJsonObject {
        put("userId", uid); put("vipType", when { plus > 0 -> "300"; black > 0 -> "100"; else -> "-1" })
        put("expiresAt", if (plus > 0) plus else black); put("moodAllowed", plus > 0); put("smartAllowed", black > 0)
    }
}

internal fun originalPageReadPath(url: String): String {
    val allowed = setOf(
        "/api/usertool/home/sound/order", "/api/usertool/home/sound/order/firstBlock",
        "/api/usertool/sound/queryAllSound", "/api/usertool/home/sound/info",
        "/api/usertool/sound/new/page/module/personal", "/api/user/custom/eq/function/privilege",
        "/api/vipauth/app/interests/auth/query", "/api/vipauth/app/auth/query",
        "/api/vipauth/app/auth/sound-effect/recommend", "/api/v3/discovery/recommend/songs",
        "/api/v6/playlist/detail", "/api/vip-rights/svip/sound/effect/get",
        "/api/vip-rights/emotion/audio/effect/get", "/api/usertool/sound/mobile/all",
        "/api/usertool/sound/personal", "/api/usertool/sound/twinkle/all", "/api/purchased/redvip/vipstatus")
    if (url !in allowed) fail("原版音效页不允许请求此接口；权益登记与统计写入尚未移植", 403)
    return url
}

/** Original material-file MD5 is distinct from the parent sound's logical/configuration MD5. */
internal fun originalPageEffect(row: JsonObject): NeteaseSoundEffect? = parseOriginalSoundEffect(row)

/** API 33+ media-policy route wins over mere device presence; older APIs stay conservative. */
@android.annotation.SuppressLint("InlinedApi") // Device type is a constant classification, not an API call.
internal fun originalPageIsSpeaker(routed: List<Int>?, connected: List<Int>): Boolean {
    val speakers = setOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE)
    if (!routed.isNullOrEmpty()) return routed.any { it in speakers }
    val builtIn = speakers + AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
    return connected.any { it in speakers } && connected.none { it !in builtIn }
}

/** Missing visual metadata is an ordinary read result; it is not a failed effect operation. */
internal fun originalPageEmotionVisualExtra(enabled: Boolean, emotion: String?, songId: Long?, requested: Long?): JsonObject =
    buildJsonObject {
        if (!enabled || emotion.isNullOrBlank() || songId == null || requested != null && requested != songId) {
            put("code", 204); put("available", false)
        } else {
            put("code", 200); put("available", true); put("songId", songId)
            put("copywriting", buildJsonObject { put("emotion", emotion) })
            // The original TSX supplies its own dimensions/description fallback.
        }
    }

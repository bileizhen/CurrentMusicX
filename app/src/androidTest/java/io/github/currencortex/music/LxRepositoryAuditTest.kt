package io.github.currencortex.music

import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.HttpDataSource
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.data.lx.*
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Opt-in audit of externally supplied public scripts. Results never contain script keys or URLs. */
class LxRepositoryAuditTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private lateinit var output: File
    private lateinit var progress: File
    @Volatile private var lastScriptDiagnostic: String? = null

    @Test fun exportCompletedAudit() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("lxRepositoryAuditExport") == "true")
        check(BuildConfig.APPLICATION_ID.endsWith(".verification"))
        val root = File(context.getExternalFilesDir(null), "lx-repository-audit")
        val suffix = runSuffix()
        val payload = buildJsonObject {
            for (name in listOf("results$suffix.jsonl", "progress$suffix.jsonl")) {
                val file = File(root, name)
                check(file.length() < 1024 * 1024)
                put(name, file.readText())
            }
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
            putString("stream", "\nLX_AUDIT_EXPORT " + java.util.Base64.getEncoder().encodeToString(payload.toString().toByteArray(Charsets.UTF_8)) + "\n")
        })
    }

    private fun runSuffix(): String = InstrumentationRegistry.getArguments().getString("lxAuditRun").orEmpty().let {
        check(it.isEmpty() || it.matches(Regex("[a-z0-9-]{1,30}")))
        if (it.isEmpty()) "" else "-$it"
    }

    private fun checkpoint(path: String, phase: String) {
        progress.appendText(buildJsonObject {
            put("path", path); put("phase", phase); put("elapsedMs", SystemClock.elapsedRealtime())
        }.toString() + "\n")
    }

    // Script/server errors may include credentials. Record only diagnostic categories or safe identifiers.
    private fun diagnostic(message: String?): String {
        val text = message.orEmpty()
        Regex("^([A-Za-z_$][A-Za-z0-9_$.]{0,60}) is not defined").find(text)?.let { return "MISSING_GLOBAL:${it.groupValues[1]}" }
        return when {
            text.contains("超时") || text.contains("timeout", true) -> "TIMEOUT"
            text.contains("网易云") && text.contains("接口") -> "NO_USABLE_NETEASE_DECLARATION"
            text.contains("checksum", true) || text.contains("校验") -> "SCRIPT_CHECKSUM_REJECTED"
            text.contains("limit", true) || text.contains("频繁") || text.contains("限流") -> "SOURCE_RATE_LIMITED"
            text.contains("授权") || text.contains("激活") || text.contains("unauthorized", true) -> "SOURCE_AUTH_REQUIRED"
            text.contains("Cannot read", true) || text.contains("is not a function", true) -> "SCRIPT_TYPE_ERROR"
            text.contains("播放链接") -> "INVALID_AUDIO_URL_RESULT"
            text.contains("Source request failed") || text.contains("network", true) -> "SOURCE_NETWORK_ERROR"
            text.contains("脚本信息") || text.contains("名称") -> "INVALID_SCRIPT_METADATA"
            text.contains("1 MB") -> "SCRIPT_SIZE_LIMIT"
            else -> "SCRIPT_OR_UPSTREAM_ERROR"
        }
    }

    @Test fun auditEveryUniqueRepositoryScriptOnNetease() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Requires an explicit repository audit", args.getString("lxRepositoryAudit") == "true")
        check(BuildConfig.APPLICATION_ID.endsWith(".verification")) { "Never run against the user's package" }
        val root = File(context.getExternalFilesDir(null), "lx-repository-audit")
        val manifest = Json.parseToJsonElement(File(root, "manifest.json").readText()).jsonObject
        val suffix = runSuffix()
        output = File(root, "results$suffix.jsonl"); output.writeText("")
        progress = File(root, "progress$suffix.jsonl"); progress.writeText("")
        val filter = args.getString("lxAuditPaths")?.split(',')?.toSet()
        val scripts = manifest.getValue("scripts").jsonArray.filter { filter == null || it.jsonObject.getValue("path").jsonPrimitive.content in filter }
        check(scripts.isNotEmpty())
        val primary = Song(2135426631, "5:20AM", "刀酱", "5:20AM", durationMs = 142000)
        val secondary = Song(327721, "读心术", "卓文萱", durationMs = 260300)
        for (item in scripts) {
            val entry = item.jsonObject
            val id = entry.getValue("id").jsonPrimitive.content
            check(id.matches(Regex("[a-f0-9]{64}")))
            val path = entry.getValue("path").jsonPrimitive.content
            checkpoint(path, "BEGIN")
            val started = SystemClock.elapsedRealtime()
            val runtime = LxRuntime(context)
            lastScriptDiagnostic = null
            runtime.failureObserver = { lastScriptDiagnostic = diagnostic(it) }
            val cases = mutableListOf<JsonObject>()
            var name = ""; var version = ""; var status = "INIT_FAILED"; var reason = ""
            var qualities = emptyList<String>()
            var initializationMs = 0L
            try {
                val file = File(root, "$id.js")
                val bytes = file.inputStream().use { lxReadBounded(it, LX_MAX_SCRIPT_BYTES) }
                check(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } == id)
                val script = bytes.toString(Charsets.UTF_8)
                val info = lxScriptInfo(script)
                name = info.name; version = info.version
                checkpoint(path, "INITIALIZE")
                val capabilities = withTimeout(45_000) { runtime.inspect(info, script) }
                initializationMs = SystemClock.elapsedRealtime() - started
                qualities = capabilities.single { it.platform == "wy" }.qualities
                status = "INITIALIZED"
                checkpoint(path, "INITIALIZED")
                for (quality in if (args.getString("lxAuditQuick") == "true") qualities.take(1) else qualities) {
                    cases += testTrack(path, runtime, info, script, primary, quality)
                }
                // A song-specific failure is not enough to label a source universally unusable.
                if (cases.none { it["status"]?.jsonPrimitive?.content == "DECODE_READY" }) {
                    cases += testTrack(path, runtime, info, script, secondary, qualities.first())
                }
                status = if (cases.any { it["status"]?.jsonPrimitive?.content == "DECODE_READY" }) "SAMPLE_PLAYABLE" else "NO_SAMPLE_SUCCEEDED"
            } catch (_: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive(); reason = "INITIALIZATION_TIMEOUT"
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                reason = lastScriptDiagnostic ?: diagnostic(error.message)
                if (reason == "NO_USABLE_NETEASE_DECLARATION") status = "NO_NETEASE_INTERFACE"
            } finally { runtime.close() }
            val result = buildJsonObject {
                put("path", path); put("aliases", entry.getValue("aliases")); put("sha256", id)
                put("name", name); put("version", version); put("status", status); put("reason", reason)
                put("qualities", JsonArray(qualities.map(::JsonPrimitive))); put("initializationMs", initializationMs)
                put("elapsedMs", SystemClock.elapsedRealtime() - started); put("cases", JsonArray(cases))
                put("api", Build.VERSION.SDK_INT); put("model", Build.MODEL); put("commit", manifest.getValue("commit"))
            }
            output.appendText("$result\n")
            checkpoint(path, "DONE:$status")
            println("LX_REPOSITORY_RESULT path=$path status=$status")
            delay(300)
        }
        check(output.readLines().size == scripts.size) { "Incomplete audit" }
    }

    private suspend fun testTrack(path: String, runtime: LxRuntime, info: LxSourceInfo, script: String,
        song: Song, quality: String): JsonObject {
        checkpoint(path, "${song.id}:$quality:RESOLVE")
        val started = SystemClock.elapsedRealtime()
        var resolvedMs = 0L; var status = "RESOLVE_FAILED"; var reason = ""; var httpCode: Int? = null
        var mime: String? = null; var sampleRate: Int? = null; var channels: Int? = null
        var duration: Long? = null; var playbackCode: Int? = null; var playedMs: Long? = null; var rendered: Int? = null
        var player: ExoPlayer? = null
        lastScriptDiagnostic = null
        try {
            withTimeout(55_000) {
                val requested = when (quality) {
                    "128k" -> AudioQuality.STANDARD; "320k" -> AudioQuality.EXHIGH
                    "flac" -> AudioQuality.LOSSLESS; else -> AudioQuality.HIRES
                }
                val request = lxTrackRequest(song, requested, listOf(LxCapability("wy", listOf(quality))))
                val url = runtime.resolve(info, script, request.data)
                resolvedMs = SystemClock.elapsedRealtime() - started
                status = "DECODE_FAILED"
                checkpoint(path, "${song.id}:$quality:DECODE")
                val instance = withContext(Dispatchers.Main) {
                    ExoPlayer.Builder(context).setLoadControl(androidx.media3.exoplayer.DefaultLoadControl.Builder()
                        .setBufferDurationsMs(1000, 2000, 250, 500).build()).build().apply {
                        volume = 0f
                        setMediaItem(androidx.media3.common.MediaItem.fromUri(url)); prepare()
                    }
                }
                player = instance
                withTimeout(25_000) {
                    while (withContext(Dispatchers.Main) { instance.playbackState != Player.STATE_READY && instance.playerError == null }) delay(50)
                }
                withContext(Dispatchers.Main) {
                    val error = instance.playerError
                    if (error != null) {
                        playbackCode = error.errorCode
                        var cause: Throwable? = error
                        repeat(12) {
                            if (cause is HttpDataSource.InvalidResponseCodeException) httpCode = (cause as HttpDataSource.InvalidResponseCodeException).responseCode
                            cause = cause?.cause
                        }
                        reason = if (httpCode != null) "HTTP_$httpCode" else "MEDIA3_${error.errorCode}"
                    } else {
                        val format = instance.audioFormat
                        if (instance.playbackState == Player.STATE_READY && format != null && instance.duration > 0) {
                            status = "DECODE_READY"; mime = format.sampleMimeType
                            sampleRate = format.sampleRate; channels = format.channelCount; duration = instance.duration
                        } else reason = "NO_READY_AUDIO_FORMAT"
                    }
                }
                if (status == "DECODE_READY") {
                    withContext(Dispatchers.Main) { instance.play() }
                    withTimeout(5000) {
                        while (withContext(Dispatchers.Main) {
                            instance.playerError == null && (instance.currentPosition < 250 ||
                                (instance.audioDecoderCounters?.renderedOutputBufferCount ?: 0) <= 0)
                        }) delay(50)
                    }
                    withContext(Dispatchers.Main) {
                        if (instance.playerError != null) { status = "DECODE_FAILED"; reason = "PLAYBACK_ERROR" }
                        else {
                            playedMs = instance.currentPosition
                            rendered = instance.audioDecoderCounters?.renderedOutputBufferCount
                            if ((playedMs ?: 0) < 250 || (rendered ?: 0) <= 0) { status = "DECODE_FAILED"; reason = "NO_RENDERED_AUDIO" }
                        }
                        instance.pause()
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive(); reason = "TIMEOUT"; status = if (resolvedMs > 0) "DECODE_TIMEOUT" else "RESOLVE_TIMEOUT"
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { reason = lastScriptDiagnostic ?: diagnostic(error.message) }
        finally { withContext(NonCancellable + Dispatchers.Main) { player?.release() } }
        return buildJsonObject {
            put("songId", song.id); put("quality", quality); put("status", status); put("reason", reason)
            put("resolvedMs", resolvedMs); put("elapsedMs", SystemClock.elapsedRealtime() - started)
            httpCode?.let { put("httpCode", it) }; playbackCode?.let { put("playbackCode", it) }
            mime?.let { put("mime", it) }; sampleRate?.let { put("sampleRate", it) }
            channels?.let { put("channels", it) }; duration?.let { put("durationMs", it) }
            playedMs?.let { put("playedMs", it) }; rendered?.let { put("renderedAudioBuffers", it) }
        }
    }
}

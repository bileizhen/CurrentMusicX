package io.github.currencortex.music

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Explicit real-account reads. Does not delete, invite, follow, leave, send messages or touch playback. */
class NeteaseListenTogetherFateLiveTest {
    @Test fun realMemberCanReadNativePartnerFateStatistics() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("listenFate") == "true")
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(app.packageName == "com.bileizhen.currentmusic")
        val container = app.container
        val output = File(requireNotNull(app.getExternalFilesDir(null)), "listen-fate-live.json")
        val summary = linkedMapOf<String, JsonElement>("success" to JsonPrimitive(false), "readOnly" to JsonPrimitive(true))
        var stage = "initialization"
        fun save() { output.writeText(JsonObject(summary).toString()) }
        fun stat(name: String, value: Long?) {
            assertTrue("Native fate statistics must be absent or nonnegative", (value ?: 0) >= 0)
            summary[name] = value?.let(::JsonPrimitive) ?: JsonNull
        }
        save()
        try {
            withTimeout(120_000) {
                container.ready.await(); container.neteaseSessions.ready.await()
                val repository = container.listenTogetherFate
                val expected = repository.captureSession()
                stage = "historyMembership"
                val history = container.listenTogetherHistory.history(expected)
                repository.checkSession(expected)
                summary["vip"] = JsonPrimitive(history.vip)
                assertTrue("A current membership is required to verify native fate reports", history.vip)
                val owner = expected.credential.profile?.uid
                val most = history.mostListen?.takeIf { it.user.userId != owner && it.ltType > 0 }
                val row = history.records.firstOrNull { it.user.userId != owner && it.ltType > 0 }
                val partner = most?.user ?: row?.user
                val scene = most?.ltType ?: row?.ltType ?: 0
                assertTrue("An existing accessible partner is required", partner != null)
                val target = requireNotNull(partner).userId
                stage = "fate"
                val report = try { repository.fate(expected, target, scene) } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    if ((error as? ApiException)?.kind == ErrorKind.Parse) {
                        // Recheck entitlement before a values-free structural probe;
                        // never use raw diagnostics to bypass the protected report gate.
                        try {
                            if (container.listenTogetherHistory.history(expected).vip) {
                                repository.checkSession(expected)
                                val raw = container.requestOriginalEffectsPage("/api/listen/together/single/record/new",
                                    buildJsonObject { put("partnerUid", target); put("scene", scene) })
                                repository.checkSession(expected)
                                summary["responseSchema"] = shape(raw)
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { summary["responseSchemaUnavailable"] = JsonPrimitive(true) }
                    }
                    throw error
                }
                repository.checkSession(expected)
                assertTrue("Report must match the selected partner", report.partnerUid == target && report.peer.userId == target)
                assertTrue("Report must match the captured local account", report.self.userId == owner)
                summary["targetMatches"] = JsonPrimitive(true)
                summary["ownerMatches"] = JsonPrimitive(true)
                summary["masked"] = JsonPrimitive(report.peer.masked)
                summary["hasMostPlayedSong"] = JsonPrimitive(report.mostPlayedSongName.isNotBlank())
                summary["canInvite"] = JsonPrimitive(report.canInvite)
                summary["openActivity"] = JsonPrimitive(report.openActivity)
                stat("totalDurationMs", report.totalDurationMs)
                stat("totalSongCount", report.totalSongCount)
                stat("commonRedSongCount", report.commonRedSongCount)
                stat("daysSinceFirstListen", report.daysSinceFirstListen)
                stat("longestDurationMs", report.longestDurationMs)
                summary["hasFirstListenTime"] = JsonPrimitive(report.firstListenTimeMs != null)
                summary["hasLatestLateNightTime"] = JsonPrimitive(report.latestLateNightTimeMs != null)
                if (InstrumentationRegistry.getArguments().getString("fateSchema") == "true") {
                    val raw = container.requestOriginalEffectsPage("/api/listen/together/single/record/new",
                        buildJsonObject { put("partnerUid", target); put("scene", scene) })
                    repository.checkSession(expected)
                    summary["responseSchema"] = shape(raw)
                }
                summary["success"] = JsonPrimitive(true)
                summary["reportVerified"] = JsonPrimitive(true)
                save()
            }
        } catch (error: Throwable) {
            val category = (error as? ApiException)?.kind?.name ?: error.javaClass.simpleName
            summary["success"] = JsonPrimitive(false)
            summary["failedStage"] = JsonPrimitive(stage)
            summary["errorCategory"] = JsonPrimitive(category)
            (error as? ApiException)?.let { summary["errorStatus"] = JsonPrimitive(it.status) }
            save()
            if (error is CancellationException) throw error
            throw AssertionError("Read-only native fate verification failed at $stage ($category)")
        }
    }

    private fun shape(value: JsonElement, depth: Int = 0): JsonElement {
        if (depth > 7) return JsonPrimitive("depth-limit")
        return when (value) {
            JsonNull -> JsonPrimitive("null")
            is JsonObject -> buildJsonObject {
                value.entries.take(60).forEach { (key, entry) ->
                    val field = if (key.matches(Regex("[A-Za-z_][A-Za-z0-9_]{0,79}"))) key else "non-field-key"
                    put(field, shape(entry, depth + 1))
                }
            }
            is JsonArray -> JsonArray(value.take(2).map { shape(it, depth + 1) })
            is JsonPrimitive -> JsonPrimitive(when {
                value.isString -> "string"
                value.booleanOrNull != null -> "boolean"
                value.doubleOrNull != null -> "number"
                else -> "primitive"
            })
        }
    }
}

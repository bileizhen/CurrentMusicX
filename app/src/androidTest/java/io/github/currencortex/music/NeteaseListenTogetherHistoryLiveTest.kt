package io.github.currencortex.music

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import io.github.currencortex.music.data.netease.listen.ListenTogetherInvitation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Explicit real-account reads only. Never joins/leaves a room, changes playback or sends invitations. */
class NeteaseListenTogetherHistoryLiveTest {
    @Test fun realAccountHistoryRankingAndCompletedRoomReportAreReadable() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("listenHistory") == "true")
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(app.packageName == "com.bileizhen.currentmusic")
        val container = app.container
        val evidence = File(requireNotNull(app.getExternalFilesDir(null)), "listen-history-live.json")
        val summary = linkedMapOf<String, JsonElement>(
            "success" to JsonPrimitive(false), "readOnly" to JsonPrimitive(true),
            "historyVerified" to JsonPrimitive(false), "rankingVerified" to JsonPrimitive(false),
            "reportVerified" to JsonPrimitive(false), "reportSongsVerified" to JsonPrimitive(false),
        )
        var stage = "initialization"
        var diagnosticPath: String? = null
        var diagnosticParams = buildJsonObject {}
        fun save() { evidence.writeText(JsonObject(summary).toString()) }
        fun stat(key: String, value: Long?) {
            assertTrue("Server statistics must be absent or nonnegative", value == null || value >= 0)
            summary[key] = value?.let(::JsonPrimitive) ?: JsonNull
        }
        save()
        withTimeout(180_000) {
            container.ready.await(); container.neteaseSessions.ready.await()
            val repository = container.listenTogetherHistory
            val expected = repository.captureSession()
            try {
                stage = "history"
                diagnosticPath = "full/records"
                diagnosticParams = buildJsonObject {
                    put("page", buildJsonObject { put("size", 50); put("cursor", "") }.toString())
                }
                val history = repository.history(expected, size = 50)
                repository.checkSession(expected)
                if (InstrumentationRegistry.getArguments().getString("historySchema") == "true") {
                    val raw = container.requestOriginalEffectsPage("/api/listen/together/full/records", diagnosticParams)
                    repository.checkSession(expected)
                    summary["historyResponseSchema"] = shape(raw)
                    val rawData = raw["data"] as? JsonObject
                    summary["rawHistoryEntries"] = JsonPrimitive((rawData?.get("records") as? JsonArray)?.size ?: -1)
                }
                summary["historyVerified"] = JsonPrimitive(true)
                summary["historyCount"] = JsonPrimitive(history.records.size)
                summary["vip"] = JsonPrimitive(history.vip)
                summary["hasMoreHistory"] = JsonPrimitive(history.hasMore)
                summary["hasMostListen"] = JsonPrimitive(history.mostListen != null)
                InstrumentationRegistry.getArguments().getString("historyMinimum")?.toIntOrNull()?.let { minimum ->
                    assertTrue("Expected real history entries must be present", history.records.size >= minimum)
                }
                history.records.forEach {
                    assertTrue("History durations must be absent or nonnegative", (it.durationMs ?: 0) >= 0)
                    assertTrue("History song counts must be absent or nonnegative", (it.songCount ?: 0) >= 0)
                }
                stat("mostListenDurationMs", history.mostListen?.totalDurationMs)
                stat("mostListenSongCount", history.mostListen?.totalSongCount)
                save()

                if (history.vip && history.hasMore && history.nextCursor.isNotBlank()) {
                    stage = "pagination"
                    val next = repository.history(expected, history.nextCursor)
                    repository.checkSession(expected)
                    val oldIds = history.records.map { it.id }.toSet()
                    val hasNew = next.records.any { it.id !in oldIds }
                    assertTrue("A nonempty next page must advance history", next.records.isEmpty() || hasNew)
                    summary["paginationVerified"] = JsonPrimitive(true)
                    summary["nextPageCount"] = JsonPrimitive(next.records.size)
                    summary["nextPageHasNewRecords"] = JsonPrimitive(hasNew)
                }

                if (history.vip) {
                    stage = "ranking"
                    // Repository performs its own fresh first-page entitlement check.
                    diagnosticPath = null
                    val ranking = repository.ranking(expected)
                    repository.checkSession(expected)
                    ranking.rankings.forEachIndexed { index, entry ->
                        assertTrue("Ranking must preserve server order", entry.rank == index + 1)
                        assertTrue("Ranking durations must be absent or nonnegative", (entry.totalDurationMs ?: 0) >= 0)
                        assertTrue("Ranking song counts must be absent or nonnegative", (entry.totalSongCount ?: 0) >= 0)
                    }
                    summary["rankingVerified"] = JsonPrimitive(true)
                    summary["rankingCount"] = JsonPrimitive(ranking.rankings.size)
                } else {
                    summary["rankingSkipped"] = JsonPrimitive("membershipRequired")
                }
                save()

                val item = history.records.firstOrNull {
                    it.recordRoomType == "listen_together" && ListenTogetherInvitation.validRoomId(it.roomId)
                }
                if (item == null) {
                    summary["reportSkipped"] = JsonPrimitive("noAccessibleTwoPersonRecord")
                } else {
                    stage = "record"
                    diagnosticPath = "record/get"
                    diagnosticParams = buildJsonObject { put("roomId", item.roomId) }
                    val report = repository.record(expected, item.roomId)
                    repository.checkSession(expected)
                    val roomMatches = report.roomId == item.roomId
                    assertTrue("Report must match the requested room", roomMatches)
                    summary["reportVerified"] = JsonPrimitive(true)
                    summary["reportRoomMatches"] = JsonPrimitive(roomMatches)
                    summary["reportHasSelf"] = JsonPrimitive(report.self != null)
                    summary["reportHasPeer"] = JsonPrimitive(report.peer != null)
                    stat("reportDurationMs", report.durationMs)
                    stat("historyDurationMsForReport", item.durationMs)
                    summary["reportDurationUnitsMatchHistory"] = JsonPrimitive(report.durationMs == item.durationMs)
                    if (report.durationMs != null && item.durationMs != null) {
                        assertTrue("Record seconds must normalize to the same millisecond scale as history",
                            kotlin.math.abs(report.durationMs - item.durationMs) < 1000L)
                    }
                    stat("reportSongCount", report.songCount)
                    stat("reportTotalDurationMs", report.totalDurationMs)
                    stat("reportTotalSongCount", report.totalSongCount)
                    save()
                    if (report.showCollectButton && (report.songCount ?: 0) > 0) {
                        stage = "roomSongs"
                        diagnosticPath = "room/songs/list"
                        val songs = repository.roomSongs(expected, item.roomId)
                        repository.checkSession(expected)
                        assertTrue("Report song ids must be valid", songs.all { it > 0 })
                        summary["reportSongsVerified"] = JsonPrimitive(true)
                        summary["reportSongsCount"] = JsonPrimitive(songs.size)
                    } else {
                        summary["reportSongsSkipped"] = JsonPrimitive("noCollectableSongs")
                    }
                }
                repository.checkSession(expected)
                summary["success"] = JsonPrimitive(true)
                summary["completedStage"] = JsonPrimitive(stage)
                save()
            } catch (error: Throwable) {
                summary["success"] = JsonPrimitive(false)
                summary["failedStage"] = JsonPrimitive(stage)
                val category = (error as? ApiException)?.kind?.name ?: error.javaClass.simpleName
                summary["errorCategory"] = JsonPrimitive(category)
                (error as? ApiException)?.let { summary["errorStatus"] = JsonPrimitive(it.status) }
                // A schema failure gets a bounded, values-free structural snapshot.
                // No raw response, cookie, profile, room identifier or user id is saved.
                // Ranking intentionally has no raw fallback: never circumvent its
                // fresh membership check to obtain diagnostic data.
                if ((error as? ApiException)?.kind == ErrorKind.Parse && diagnosticPath != null) {
                    try {
                        repository.checkSession(expected)
                        val raw = container.requestOriginalEffectsPage("/api/listen/together/$diagnosticPath", diagnosticParams)
                        repository.checkSession(expected)
                        summary["responseSchema"] = shape(raw)
                    } catch (_: Throwable) {
                        summary["responseSchemaUnavailable"] = JsonPrimitive(true)
                    }
                }
                save()
                // Sanitized failure text: JUnit output must not dump identifiers or payloads.
                throw AssertionError("Read-only listening-history verification failed at $stage ($category)")
            }
        }
    }

    private fun shape(value: JsonElement, depth: Int = 0): JsonElement {
        if (depth > 7) return JsonPrimitive("depth-limit")
        return when (value) {
            JsonNull -> JsonPrimitive("null")
            is JsonObject -> buildJsonObject {
                value.entries.take(60).forEach { (key, item) ->
                    val field = if (key.matches(Regex("[A-Za-z_][A-Za-z0-9_]{0,79}"))) key else "non-field-key"
                    put(field, shape(item, depth + 1))
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

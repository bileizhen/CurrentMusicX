package io.github.currencortex.music.data.netease.listening

import io.github.currencortex.music.core.netease.NeteaseClientProfile
import io.github.currencortex.music.core.netease.NeteaseSession
import io.github.currencortex.music.core.netease.NeteaseTransport
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.LocalDate

/** Read-only contracts observed in NetEase's official /st/listen-home JavaScript. */
internal class NeteaseListeningRepository(
    private val transport: NeteaseTransport,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun captureSession(): NeteaseSession = transport.sessions.snapshot().also(::checkSession)

    fun checkSession(expected: NeteaseSession) {
        transport.sessions.check(expected)
        if (expected.credential.cookies["MUSIC_U"].isNullOrBlank() || expected.credential.stale ||
            (expected.credential.profile?.uid ?: 0) <= 0) throw ApiException(ErrorKind.NeteaseBindingRequired)
    }

    suspend fun report(expected: NeteaseSession, period: NeteaseListeningPeriod, date: LocalDate): NeteaseListeningReport? {
        val today = Instant.ofEpochMilli(now()).atZone(neteaseListeningZone).toLocalDate()
        val start = period.start(date)
        val current = start == period.start(today)
        require(start <= period.start(today))
        // Official UI offers a rolling three-year window, starting no earlier than launch.
        if (period.next(start).minusDays(1) < today.minusYears(3) ||
            period.next(start).minusDays(1) < LocalDate.of(2020, 9, 18)) throw ApiException(ErrorKind.NotFound)
        checkHistoryAccess(expected, period, start, today)
        val data = request(if (current) "realtime/report" else "report", buildJsonObject {
            put("type", period.wire)
            if (!current) put("endTime", period.requestEndTime(start))
        }, expected) ?: return null
        currentCoroutineContext().ensureActive()
        checkSession(expected)
        val parsed = parseNeteaseListeningReport(data, current) ?: return null
        // Never show another period's data under the user's selected date.
        val returnedStart = Instant.ofEpochMilli(parsed.startTimeMs).atZone(neteaseListeningZone).toLocalDate()
        if (returnedStart != start) parseError()
        return parsed
    }

    suspend fun years(expected: NeteaseSession): List<NeteaseListeningYear>? {
        val data = request("year/report", buildJsonObject {}, expected) ?: return null
        return data.array("yearItems")?.map { value ->
            val item = value as? JsonObject ?: parseError()
            val year = item.number("year")?.takeIf { it in 1900..9999 }?.toInt() ?: parseError()
            NeteaseListeningYear(year, item.duration("playDuration", 1_000), item.number("playNum"))
        }?.sortedByDescending { it.year }
    }

    suspend fun totalDurationMs(expected: NeteaseSession): Long? =
        request("total", buildJsonObject {}, expected)?.duration("totalDuration", 1_000)

    /** Recheck server entitlement before requesting history hidden by the official UI. */
    private suspend fun checkHistoryAccess(expected: NeteaseSession, period: NeteaseListeningPeriod,
        start: LocalDate, today: LocalDate) {
        val freeStart = when (period) {
            NeteaseListeningPeriod.WEEK -> period.start(today).minusWeeks(4)
            NeteaseListeningPeriod.MONTH -> period.start(today).minusMonths(1)
        }
        if (start >= freeStart) return
        val data = requestPath("/api/music-vip-membership/front/vip/info", buildJsonObject {}, expected)
        val entitled = listOf("associator", "redplus").any { kind ->
            (data?.obj(kind)?.number("expireTime") ?: 0) >= now()
        }
        if (!entitled) throw ApiException(ErrorKind.Forbidden, userMessage = "该时段需要网易云会员权限")
    }

    private suspend fun request(path: String, payload: JsonObject, expected: NeteaseSession) =
        requestPath("/api/content/activity/listen/data/$path", payload, expected)

    private suspend fun requestPath(path: String, payload: JsonObject, expected: NeteaseSession): JsonObject? {
        checkSession(expected)
        val body = transport.request(path, payload, session = expected,
            clientProfile = NeteaseClientProfile.ANDROID).body
        currentCoroutineContext().ensureActive()
        checkSession(expected)
        val code = (body["code"] as? JsonPrimitive)?.intOrNull ?: parseError()
        if (code != 200) throw ApiException(when (code) {
            301, 401 -> ErrorKind.NeteaseBindingRequired
            403 -> ErrorKind.Forbidden
            404 -> ErrorKind.NotFound
            429 -> ErrorKind.RateLimited
            else -> ErrorKind.Server
        }, code)
        return when (val data = body["data"]) {
            null, JsonNull -> null
            is JsonObject -> data
            else -> parseError()
        }
    }
}

internal fun parseNeteaseListeningReport(data: JsonObject, current: Boolean): NeteaseListeningReport? {
    // A 200/null or 200/{} response represents a report not generated yet, not zero listening.
    if (data.isEmpty()) return null
    val start = data.number("startTime")?.takeIf { it > 0 } ?: parseError()
    val end = data.number("endTime")?.takeIf { it >= start } ?: parseError()
    val block = data.obj("listenTimeDistributionBlock")
    val today = data.obj("weekTodayListenBlock")
    val wall = data.obj("wallpaperBlock")
    val achievement = block?.obj("achievementTitle")
    val ageBlock = data.obj("topAgeBlock")
    val languageBlock = data.obj("topLanguageBlock")
    val days = block?.array("durationDetails").orEmpty().map { value ->
        val item = value as? JsonObject ?: parseError()
        val date = try { LocalDate.parse(item.text("period")) } catch (_: Exception) { parseError() }
        NeteaseListeningDay(date, item.duration("duration", 60_000), item.duration("podcastDuration", 60_000),
            item.duration("audiobookDuration", 60_000))
    }
    return NeteaseListeningReport(start, end, current, block?.duration("playDuration", 60_000),
        block?.number("listenDays"), days, today?.number("songCount"), today?.urls("coverUrls").orEmpty(),
        wall?.number("songCount"), wall?.array("items").orEmpty().map { song(it as? JsonObject ?: parseError()) },
        wall?.urls("picUrls").orEmpty(),
        ageBlock?.array("sections").orEmpty().mapIndexed { index, value ->
            val section = value as? JsonObject ?: parseError()
            val representative = ageBlock?.array("songItems")?.getOrNull(index) as? JsonObject
            NeteaseListeningAge(section.number("age")?.takeIf { it <= Int.MAX_VALUE }?.toInt(),
                section.number("playSongNum"), (representative ?: section.takeIf { it["songId"] != null })?.let(::song))
        }, languageBlock?.array("sections").orEmpty().mapIndexed { index, value ->
            val section = value as? JsonObject ?: parseError()
            val representative = languageBlock?.array("songItems")?.getOrNull(index) as? JsonObject
            NeteaseListeningLanguage(section.text("language"), section.decimal("percent")?.takeIf { it <= 100 },
                (representative ?: section.takeIf { it["songId"] != null })?.let(::song))
        }, achievement?.text("mainTitle").orEmpty(), achievement?.text("subTitle").orEmpty(),
        block?.array("sections").orEmpty().map { value ->
            val item = value as? JsonObject ?: parseError()
            NeteaseListeningSection(item.text("type"), item.text("value"))
        })
}

private fun song(item: JsonObject) = NeteaseListeningSong(item.number("songId"), item.text("songName"),
    item.array("artists").orEmpty().mapNotNull { (it as? JsonObject)?.text("artistName")?.takeIf(String::isNotBlank) }
        .joinToString(" / "), item.text("picUrl").safeImageUrl(), item.number("playCount"))

private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.number(key: String): Long? {
    val value = get(key)?.takeUnless { it == JsonNull } ?: return null
    return (value as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0 } ?: parseError()
}
private fun JsonObject.decimal(key: String): Double? {
    val value = get(key)?.takeUnless { it == JsonNull } ?: return null
    return (value as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() && it >= 0 } ?: parseError()
}
private fun JsonObject.duration(key: String, multiplier: Long): Long? = decimal(key)?.let {
    val millis = it * multiplier
    if (!millis.isFinite() || millis >= Long.MAX_VALUE.toDouble()) parseError()
    millis.toLong()
}
private fun JsonObject.obj(key: String): JsonObject? {
    val value = get(key)?.takeUnless { it == JsonNull } ?: return null
    return value as? JsonObject ?: parseError()
}
private fun JsonObject.array(key: String): JsonArray? {
    val value = get(key)?.takeUnless { it == JsonNull } ?: return null
    return value as? JsonArray ?: parseError()
}
private fun JsonObject.urls(key: String) = array(key).orEmpty().mapNotNull {
    (it as? JsonPrimitive)?.contentOrNull?.safeImageUrl()?.takeIf(String::isNotBlank)
}
private fun String.safeImageUrl(): String = when {
    startsWith("https://") -> this
    startsWith("http://") -> "https://" + removePrefix("http://")
    startsWith("//") -> "https:$this"
    else -> ""
}
private fun parseError(): Nothing = throw ApiException(ErrorKind.Parse)

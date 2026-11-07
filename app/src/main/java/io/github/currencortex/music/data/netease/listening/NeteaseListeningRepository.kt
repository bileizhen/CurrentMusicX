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

    fun social(expected: NeteaseSession): NeteaseListeningSocialRepository = NeteaseListeningSocialRepository(
        checkSession = { checkSession(expected) },
        request = { path, data -> transport.request(path, data, session = expected,
            clientProfile = NeteaseClientProfile.ANDROID).body })

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
        val parsed = parseNeteaseListeningReport(data, current, period) ?: return null
        // Never show another period's data under the user's selected date.
        val returnedStart = Instant.ofEpochMilli(parsed.startTimeMs).atZone(neteaseListeningZone).toLocalDate()
        if (returnedStart != start) parseError()
        if (period == NeteaseListeningPeriod.WEEK && parsed.weekly != null) {
            var weekly = checkNotNull(parsed.weekly)
            if (!current && weekly.vipSections != null) {
                val vip = optionalRequest("/api/music-vip-membership/front/vip/info", expected)
                weekly = weekly.copy(blackVip = vip?.let { (it.obj("associator")?.number("expireTime") ?: 0) >= now() },
                    superVip = vip?.let { (it.obj("redplus")?.number("expireTime") ?: 0) >= now() })
            }
            if (weekly.friendSongs != null || weekly.friendKeywords != null || weekly.currentFriends != null) {
                val setting = optionalRequest("/api/content/activity/listen/data/user/setting/get", expected)
                val config = optionalRequest("/api/middle/clientcfg/config/list", expected, buildJsonObject {
                    put("moduleName", "activityBench")
                    put("keys", "[\"listen-data-homepage\"]")
                    put("platform", "web")
                })?.obj("activityBench#listen-data-homepage")?.obj("extraConfig")
                weekly = weekly.copy(friendsEnabled = setting?.bool("friendsBlockClose")?.not(),
                    friendSongsEnabled = config?.bool("isFriendsDataFriendsLoveShow"),
                    friendKeywordsEnabled = config?.bool("isFriendsDataFriendsKeywordsShow"),
                    currentFriendsEnabled = config?.bool("isFriendsDataWeekCurrentListen"))
            }
            if (!current) {
                val group = optionalRequest("/api/content/activity/listen/data/libra/group", expected)
                weekly = weekly.copy(wallpaperBottom = group?.bool("wallpaperBottom"))
            }
            currentCoroutineContext().ensureActive()
            checkSession(expected)
            return parsed.copy(weekly = weekly)
        }
        if (period != NeteaseListeningPeriod.MONTH || current || parsed.monthly == null) return parsed
        var monthly = parsed.monthly ?: return parsed
        if (monthly.vip != null) {
            val vip = optionalRequest("/api/music-vip-membership/front/vip/info", expected)
            monthly = monthly.copy(blackVip = vip?.let { (it.obj("associator")?.number("expireTime") ?: 0) >= now() },
                superVip = vip?.let { (it.obj("redplus")?.number("expireTime") ?: 0) >= now() })
        }
        if (monthly.friendSongs != null || monthly.friendKeywords != null) {
            val setting = optionalRequest("/api/content/activity/listen/data/user/setting/get", expected)
            val config = optionalRequest("/api/middle/clientcfg/config/list", expected, buildJsonObject {
                put("moduleName", "activityBench")
                put("keys", "[\"listen-data-homepage\"]")
                put("platform", "web")
            })?.obj("activityBench#listen-data-homepage")?.obj("extraConfig")
            monthly = monthly.copy(friendsEnabled = setting?.bool("friendsBlockClose")?.not(),
                friendSongsEnabled = config?.bool("isFriendsDataFriendsLoveShow"),
                friendKeywordsEnabled = config?.bool("isFriendsDataFriendsKeywordsShow"))
        }
        val group = optionalRequest("/api/content/activity/listen/data/libra/group", expected)
        monthly = monthly.copy(wallpaperBottom = group?.bool("wallpaperBottom"))
        currentCoroutineContext().ensureActive()
        checkSession(expected)
        return parsed.copy(monthly = monthly)
    }

    suspend fun years(expected: NeteaseSession): List<NeteaseListeningYear>? {
        val data = request("year/report", buildJsonObject {}, expected) ?: return null
        return data.array("yearItems")?.map { value ->
            val item = value as? JsonObject ?: parseError()
            val year = item.number("year")?.takeIf { it in 1900..9999 }?.toInt() ?: parseError()
            NeteaseListeningYear(year, item.duration("playDuration", 1_000), item.number("playNum"))
        }?.sortedByDescending { it.year }
    }

    /** endTimeMs is the original report's endTime, NOT requestEndTime(date). */
    suspend fun rank(expected: NeteaseSession, period: NeteaseListeningPeriod, date: LocalDate,
        endTimeMs: Long): NeteaseListeningRank? {
        val today = Instant.ofEpochMilli(now()).atZone(neteaseListeningZone).toLocalDate()
        val start = period.start(date)
        require(start <= period.start(today))
        if (period.next(start).minusDays(1) < today.minusYears(3) ||
            period.next(start).minusDays(1) < LocalDate.of(2020, 9, 18)) throw ApiException(ErrorKind.NotFound)
        val endDate = Instant.ofEpochMilli(endTimeMs).atZone(neteaseListeningZone).toLocalDate()
        require(period.start(endDate) == start)
        checkHistoryAccess(expected, period, start, today)
        val data = request("song/play/rank", buildJsonObject {
            put("type", period.wire)
            put("endTime", endTimeMs)
        }, expected) ?: return null
        if (data.isEmpty()) return null
        if (data.text("type") != period.wire) parseError()
        val returnedStart = data.number("startTime")?.takeIf { it > 0 } ?: parseError()
        val returnedEnd = data.number("endTime")?.takeIf { it >= returnedStart } ?: parseError()
        if (Instant.ofEpochMilli(returnedStart).atZone(neteaseListeningZone).toLocalDate() != start ||
            period.start(Instant.ofEpochMilli(returnedEnd).atZone(neteaseListeningZone).toLocalDate()) != start) parseError()
        val songs = data.array("songItems") ?: return null
        val result = NeteaseListeningRank(period, returnedStart, returnedEnd, data.number("songCount"),
            songs.map { song(it as? JsonObject ?: parseError()) }, data.urls("picUrls"))
        currentCoroutineContext().ensureActive()
        checkSession(expected)
        return result
    }

    /** Official Today page caps the returned recent-song list at 100; timestamp wire unit is seconds. */
    suspend fun todaySongs(expected: NeteaseSession): List<NeteaseListeningSong>? {
        val data = request("today/song/play/rank", buildJsonObject {}, expected) ?: return null
        val result = data.array("songDTOs")?.take(100)?.map { song(it as? JsonObject ?: parseError()) }
        currentCoroutineContext().ensureActive()
        checkSession(expected)
        return result
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

    /** Optional decoration/permission lookup failures hide that feature, never grant access. */
    private suspend fun optionalRequest(path: String, expected: NeteaseSession, payload: JsonObject = buildJsonObject {}): JsonObject? = try {
        requestPath(path, payload, expected)
    } catch (error: ApiException) {
        currentCoroutineContext().ensureActive()
        checkSession(expected)
        if (error.kind == ErrorKind.NeteaseBindingRequired || error.kind == ErrorKind.NeteaseSessionChanged) throw error
        null
    }

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

internal fun parseNeteaseListeningReport(data: JsonObject, current: Boolean,
    period: NeteaseListeningPeriod = NeteaseListeningPeriod.MONTH): NeteaseListeningReport? {
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
        }, monthly = if (period == NeteaseListeningPeriod.MONTH) parseMonthly(data) else null,
        weekly = if (period == NeteaseListeningPeriod.WEEK) parseWeekly(data, current) else null,
        sleep = block?.obj("sleepTdBlock")?.let { sleep ->
            NeteaseListeningSleep(sleep.number("sleepListenDays"), sleep.text("sleepScene"),
                sleep.objects("sleepDurationDetails").map { day ->
                    val date = try { LocalDate.parse(day.text("period")) } catch (_: Exception) { parseError() }
                    NeteaseListeningDay(date, day.duration("duration", 60_000), null, null)
                }, sleep.duration("avgDayPlayDuration", 60_000), sleep.duration("maxDayPlayDuration", 60_000))
        })
}

private fun parseWeekly(data: JsonObject, current: Boolean): NeteaseListeningWeekly? {
    val keys = listOf("topSongBlock", "topArtistBlock", "topStyleBlock", "vipBlock", "djListenDataBlock", "topAgeBlock",
        "topLanguageBlock", "friendsListenWeekBlock", "friendsKeywordWeekBlock", "weekFriendsListenBlock", "wallpaperBlock")
    if (keys.none { data[it] != null && data[it] != JsonNull }) return null
    fun sections(key: String) = data.obj(key)?.objects("sections")?.map { row -> NeteaseListeningHighlightSection(
        row.text("type"), row.text("field"), row.text("text"), row.text("mainText"), row.number("songId"),
        row.text("songName"), row.number("artistId"), row.text("artistName"), row.text("picUrl").safeImageUrl()) }
    fun friend(row: JsonObject) = NeteaseListeningFriend(row.number("userId"), row.text("username"), row.text("userAvatar").safeImageUrl())
    fun friendSong(row: JsonObject) = NeteaseListeningFriendSong(row.number("songId"), row.text("songName"),
        row.text("songPicUrl").safeImageUrl(), friend(row), row.number("playCount"), row.bool("collect"), row.text("artistName"))
    return NeteaseListeningWeekly(isComplete = !current,
        songs = sections("topSongBlock"), artists = sections("topArtistBlock"), vipSections = sections("vipBlock"),
        genres = data.obj("topStyleBlock")?.objects("sections")?.map { NeteaseListeningGenre(it.number("genreId"), it.text("genreName"), it.decimal("percent")) },
        ages = data.obj("topAgeBlock")?.let { block -> block.objects("sections").mapIndexed { index, row ->
            val representative = block.array("songItems")?.getOrNull(index) as? JsonObject
            NeteaseListeningAge(row.number("age")?.takeIf { it <= Int.MAX_VALUE }?.toInt(), row.number("playSongNum"),
                (representative ?: row.takeIf { it["songId"] != null })?.let(::song))
        } },
        languages = data.obj("topLanguageBlock")?.let { block -> block.objects("sections").mapIndexed { index, row ->
            val representative = block.array("songItems")?.getOrNull(index) as? JsonObject
            NeteaseListeningLanguage(row.text("language"), row.decimal("percent")?.takeIf { it <= 100 }, representative?.let(::song))
        } },
        longAudio = data.obj("djListenDataBlock")?.let { audio ->
            listOf("podcast" to "podcast", "audioBook" to "audiobook").map { (type, key) ->
                NeteaseListeningLongAudio(type, null, "", "", null, null,
                    audio.duration("${key}PlayDuration", 60_000), audio.number("${key}EpNum"))
            }
        },
        friendSongs = data.obj("friendsListenWeekBlock")?.objects("items")?.map(::friendSong),
        friendKeywords = data.obj("friendsKeywordWeekBlock")?.objects("items")?.map { row ->
            NeteaseListeningFriendKeyword(row.text("title"), row.text("subTitle"), row.text("enumCode"), row.objects("friendList").map(::friend))
        },
        currentFriends = data.obj("weekFriendsListenBlock")?.objects("friendListenRecords")?.map(::friendSong),
    )
}

private fun parseMonthly(data: JsonObject): NeteaseListeningMonthly? {
    val keys = listOf("topSongBlock", "topArtistBlock", "topStyleBlock", "vipBlock", "djListenDataBlock",
        "friendsListenMonthBlock", "friendsKeyWordMonthBlock", "vipInviteMonthBlock", "wallpaperBlock")
    if (keys.none { data[it] != null && data[it] != JsonNull }) return null
    fun highlight(block: JsonObject?, artist: Boolean = false): NeteaseListeningHighlight? = block?.let {
        NeteaseListeningHighlight(it.number(if (artist) "artisId" else "songId"),
            it.text(if (artist) "artistName" else "songName"),
            it.text(if (artist) "avatarUrl" else "picUrl").safeImageUrl(), it.number("playCount"),
            it.objects("sections").map { row -> NeteaseListeningHighlightSection(
                row.text("type"), row.text("field"), row.text("text"), row.text("mainText"),
                row.number("songId"), row.text("songName"), row.number("artistId"), row.text("artistName"),
                row.text("picUrl").safeImageUrl()) })
    }
    fun friend(row: JsonObject) = NeteaseListeningFriend(row.number("userId"), row.text("username"),
        row.text("userAvatar").safeImageUrl())
    return NeteaseListeningMonthly(
        topSong = highlight(data.obj("topSongBlock")),
        topArtist = highlight(data.obj("topArtistBlock"), artist = true),
        vip = highlight(data.obj("vipBlock")),
        style = data.obj("topStyleBlock")?.let { style -> NeteaseListeningStyle(
            style.number("genreId"), style.text("genreName"), style.number("secondGenreId"), style.text("secondGenreName"),
            style.objects("sections").map { NeteaseListeningGenre(it.number("genreId"), it.text("genreName"), it.decimal("percent")) },
            style.objects("items").map { NeteaseListeningRecommendation(it.text("type"), it.number("resourceId"), it.text("mainText")) }) },
        longAudio = data.obj("djListenDataBlock")?.let { audio ->
            listOf("podcast" to "Podcast", "audioBook" to "Audiobook").map { (type, key) ->
                val totalKey = if (type == "podcast") "podcast" else "audiobook"
                NeteaseListeningLongAudio(type, audio.number("top1${key}Id"), audio.text("top1${key}Name"),
                    audio.text("top1${key}Cover").safeImageUrl(), audio.duration("top1${key}PlayDuration", 60_000),
                    audio.number("top1${key}EpNum"), audio.duration("${totalKey}PlayDuration", 60_000), audio.number("${totalKey}EpNum"))
            }
        },
        friendSongs = data.obj("friendsListenMonthBlock")?.objects("items")?.map { row ->
            NeteaseListeningFriendSong(row.number("songId"), row.text("songName"), row.text("songPicUrl").safeImageUrl(),
                friend(row), row.number("playCount"), row.bool("collect"))
        },
        friendKeywords = data.obj("friendsKeyWordMonthBlock")?.objects("items")?.map { row ->
            NeteaseListeningFriendKeyword(row.text("title"), row.text("subTitle"), row.text("enumCode"), row.objects("friendList").map(::friend))
        },
        sendVipDays = data.obj("vipInviteMonthBlock")?.number("sendVipDay"),
    )
}

private fun song(item: JsonObject) = NeteaseListeningSong(item.number("songId"), item.text("songName"),
    item.array("artists").orEmpty().mapNotNull { (it as? JsonObject)?.text("artistName")?.takeIf(String::isNotBlank) }
        .joinToString(" / "), item.text("picUrl").safeImageUrl(), item.number("playCount"),
    albumName = item.text("albumName"), aliasName = item.text("aliasName"), redStar = item.bool("redStar"),
    lastPlayTimeMs = item.duration("lastPlayTime", 1_000))

private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.bool(key: String): Boolean? {
    val value = get(key)?.takeUnless { it == JsonNull } ?: return null
    return (value as? JsonPrimitive)?.booleanOrNull ?: parseError()
}
private fun JsonObject.objects(key: String): List<JsonObject> = array(key).orEmpty().map { it as? JsonObject ?: parseError() }
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

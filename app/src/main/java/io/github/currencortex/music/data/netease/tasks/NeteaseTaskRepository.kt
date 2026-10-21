package io.github.currencortex.music.data.netease.tasks

import io.github.currencortex.music.core.netease.*
import io.github.currencortex.music.core.network.*
import kotlinx.serialization.json.*

enum class NeteaseTaskGroup(val title: String) { DAILY("日常"), YUNBEI("云贝"), VIP("会员"), MUSICIAN("音乐人") }
enum class NeteaseCheckIn(val title: String) { DAILY("日常"), YUNBEI("云贝"), VIP("黑胶乐签"), MUSICIAN("音乐人") }
data class NeteaseTaskItem(val id: String, val title: String, val detail: String, val progress: String, val destination: String = "")
data class NeteaseTaskPage(val items: List<NeteaseTaskItem> = emptyList(), val summary: String = "",
    val rewardIds: List<String> = emptyList(), val signedToday: Boolean? = null)

/** Native, single-account task queries. Playback completion is never fabricated here. */
class NeteaseTaskRepository(private val transport: NeteaseTransport) {
    val accounts get() = transport.sessions.state
    internal suspend fun owner(): NeteaseSession {
        val owner = transport.sessions.snapshot()
        if (owner.credential.profile == null || owner.credential.stale || owner.credential.cookies["MUSIC_U"].isNullOrBlank())
            throw ApiException(ErrorKind.NeteaseBindingRequired)
        return owner
    }
    internal suspend fun request(path: String, owner: NeteaseSession, data: JsonObject = buildJsonObject {},
        duplicateOkay: Boolean = false): JsonObject {
        val raw = transport.request(path, data, NeteaseEncoding.WEB, session = owner).body
        val code = raw.number("code")?.toInt() ?: throw ApiException(ErrorKind.Parse)
        if (code == 200 || (duplicateOkay && code == -2)) return raw
        if (code in setOf(301, 401)) transport.sessions.markStale(owner)
        throw ApiException(when (code) {
            301, 401 -> ErrorKind.NeteaseBindingRequired; 403 -> ErrorKind.Forbidden
            429, 406 -> ErrorKind.RateLimited; else -> ErrorKind.Server
        }, code, raw.text("message", "msg").takeIf { it.isNotBlank() }?.take(180))
    }
    suspend fun load(group: NeteaseTaskGroup): NeteaseTaskPage {
        val owner = owner()
        val path = when (group) {
            NeteaseTaskGroup.DAILY -> "/api/v1/user/info"
            NeteaseTaskGroup.YUNBEI -> "/api/usertool/task/list/all"
            NeteaseTaskGroup.VIP -> "/api/vipnewcenter/app/level/task/list"
            NeteaseTaskGroup.MUSICIAN -> "/api/nmusician/workbench/mission/cycle/list"
        }
        val page = parsePage(group, request(path, owner))
        val balancePath = when (group) {
            NeteaseTaskGroup.YUNBEI -> "/api/v1/user/info"
            NeteaseTaskGroup.VIP -> "/api/vipnewcenter/app/level/growhpoint/basic"
            else -> null
        }
        val balance = balancePath?.let { appResult { request(it, owner) } }
        transport.sessions.check(owner)
        val raw = (balance as? AppResult.Success)?.value
        val summary = when (group) {
            NeteaseTaskGroup.YUNBEI -> (raw?.get("userPoint") as? JsonObject)?.number("balance")?.let { "可用云贝 $it · ${page.items.size} 项任务" }
            NeteaseTaskGroup.VIP -> ((raw?.get("data") as? JsonObject)?.get("userLevel") as? JsonObject)?.let { level ->
                level.number("growthPoint")?.let { "${level.text("levelName")} · 成长值 $it" }
            }
            else -> null
        }
        return page.copy(summary = summary ?: page.summary)
    }
    internal suspend fun sign(owner: NeteaseSession, kind: NeteaseCheckIn): String {
        val path = when (kind) {
            NeteaseCheckIn.DAILY -> "/api/point/dailyTask"
            NeteaseCheckIn.YUNBEI -> "/api/pointmall/user/sign"
            NeteaseCheckIn.VIP -> "/api/vip-center-bff/task/sign"
            NeteaseCheckIn.MUSICIAN -> "/api/creator/user/access"
        }
        // The legacy Android type=0 endpoint rejects otherwise valid local logins. The supported
        // type=1 check-in is confirmed by pcSign in the official user info response.
        val raw = request(path, owner, buildJsonObject { if (kind == NeteaseCheckIn.DAILY) put("type", 1) }, duplicateOkay = true)
        val message = raw.text("message", "msg")
        if (message.contains("不支持") || message.contains("失败")) throw ApiException(ErrorKind.Server, userMessage = message.take(180))
        if (kind == NeteaseCheckIn.VIP && raw["data"] == JsonPrimitive(false))
            throw ApiException(ErrorKind.Forbidden, userMessage = "网易云未确认乐签成功，请检查会员权益")
        val duplicate = raw.number("code") == -2L || (raw["data"] as? JsonObject)?.get("sign") == JsonPrimitive(false)
        return if (duplicate) "今日已签到" else "签到成功"
    }
    suspend fun claimVipRewards(): String {
        val owner = owner()
        // Re-read the server's claimable IDs immediately before claiming; never manufacture task completion.
        val available = parsePage(NeteaseTaskGroup.VIP, request("/api/vipnewcenter/app/level/task/list", owner)).rewardIds
        if (available.isEmpty()) return "暂无可领取的成长值"
        val receipt = request("/api/vipnewcenter/app/level/task/reward/get", owner,
            buildJsonObject { put("taskIds", available.joinToString(",")) })
        if (receipt["data"] == JsonPrimitive(false))
            throw ApiException(ErrorKind.Server, userMessage = "网易云未确认成长值领取成功，请刷新后重试")
        return "已领取可用成长值"
    }
    suspend fun claimRewards(group: NeteaseTaskGroup): String {
        if (group == NeteaseTaskGroup.VIP) return claimVipRewards()
        val owner = owner()
        val cloud = group == NeteaseTaskGroup.YUNBEI
        require(cloud || group == NeteaseTaskGroup.MUSICIAN)
        val raw = request(if (cloud) "/api/usertool/task/todo/query" else "/api/nmusician/workbench/mission/cycle/list", owner)
        val data = raw["data"]
        val rows = if (cloud) data as? JsonArray else (data as? JsonObject)?.get("taskList") as? JsonArray
        if (rows == null) throw ApiException(ErrorKind.Parse)
        var claimed = 0
        rows.take(100).forEach { element ->
            val row = element as? JsonObject ?: return@forEach
            val ready = if (cloud) row["completed"] == JsonPrimitive(true) else row.number("status") == 2L
            val id = row.number(if (cloud) "userTaskId" else "userMissionId") ?: return@forEach
            val period = row.number("period") ?: return@forEach
            if (!ready || id <= 0 || period < 0) return@forEach
            val deposit = row.number("depositCode")
            if (cloud && deposit == null) return@forEach
            request(if (cloud) "/api/usertool/task/point/receive" else "/api/nmusician/workbench/mission/reward/obtain/new", owner,
                buildJsonObject {
                    put(if (cloud) "userTaskId" else "userMissionId", id.toString()); put("period", period.toString())
                    if (cloud) put("depositCode", deposit.toString())
                }).also {
                    if (it["data"] == JsonPrimitive(false)) throw ApiException(ErrorKind.Server, userMessage = "网易云未确认奖励领取成功，请刷新后重试")
                }
            claimed++
        }
        return if (claimed == 0) "暂无可领取的奖励" else "已领取 $claimed 项任务奖励"
    }
    companion object {
        private fun JsonObject.text(vararg keys: String) = keys.firstNotNullOfOrNull {
            (get(it) as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
        }.orEmpty()
        private fun JsonObject.number(vararg keys: String) = keys.firstNotNullOfOrNull { (get(it) as? JsonPrimitive)?.longOrNull }
        fun parsePage(group: NeteaseTaskGroup, raw: JsonObject): NeteaseTaskPage {
            val root = raw["data"] ?: raw
            val items = mutableListOf<NeteaseTaskItem>()
            val rewards = mutableListOf<String>()
            fun visit(element: JsonElement, depth: Int) {
                if (depth > 8 || items.size >= 100) return
                when (element) {
                    is JsonArray -> element.take(100).forEach { visit(it, depth + 1) }
                    is JsonObject -> {
                        if (group == NeteaseTaskGroup.VIP) {
                            val ids = element["unGetIds"]
                            val values = if (ids is JsonArray) ids.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                                else (ids as? JsonPrimitive)?.contentOrNull?.split(',').orEmpty()
                            rewards += values.filter { it.matches(Regex("[0-9_]{1,80}")) }.take(100)
                        }
                        val title = element.text("taskName", "missionName", "taskTitle").ifBlank {
                            if (element.keys.any { it in setOf("taskId", "missionId", "userMissionId", "missionCode") })
                                element.text("name", "action").takeIf { !it.contains("://") }.orEmpty() else ""
                        }
                        if (title.isNotBlank()) {
                            val current = element.number("currentProgress", "currentCount", "progress", "doneCount")
                            val target = element.number("targetCount", "targetProgress", "totalCount", "needCount", "targetWorth")
                            val progress = element.text("statusDesc", "statusText", "progressDesc", "taskProgressText").ifBlank {
                                when {
                                    element["completed"] == JsonPrimitive(true) -> "已完成"
                                    current != null && target != null && current >= 0 && target > 0 -> "$current / $target"
                                    element.number("progressRate")?.let { it in 0..100 } == true -> "${element.number("progressRate")}%"
                                    else -> "以网易云任务状态为准"
                                }
                            }
                            val id = element.number("userTaskId", "userMissionId")?.takeIf { it > 0 }?.toString()
                                ?: element.text("taskId", "missionId", "id").takeUnless { it.isBlank() || it == "0" } ?: title
                            items += NeteaseTaskItem(id,
                                title.take(100), element.text("taskDesc", "description", "missionDesc", "taskDescription").take(300), progress.take(100),
                                if (element.text("link") == "orpheus://songrcmd") "lib/daily" else "")
                        }
                        element.values.filter { it is JsonArray || it is JsonObject }.forEach { visit(it, depth + 1) }
                    }
                    else -> Unit
                }
            }
            visit(root, 0)
            val objectRoot = root as? JsonObject ?: raw
            val summary = when (group) {
                NeteaseTaskGroup.DAILY -> when ((objectRoot["pcSign"] as? JsonPrimitive)?.booleanOrNull) {
                    true -> "今日签到已完成"; false -> "今日尚未签到"; null -> "每日签到"
                }
                else -> "${items.size} 项任务 · 官方进度"
            }
            return NeteaseTaskPage(items.distinctBy { it.id }, summary, rewards.distinct(),
                if (group == NeteaseTaskGroup.DAILY) (objectRoot["pcSign"] as? JsonPrimitive)?.booleanOrNull else null)
        }
    }
}

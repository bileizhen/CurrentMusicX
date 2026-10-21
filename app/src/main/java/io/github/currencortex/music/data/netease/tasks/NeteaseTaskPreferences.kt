package io.github.currencortex.music.data.netease.tasks

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.ZoneId

data class NeteaseTaskLog(val time: Long, val label: String, val success: Boolean)
data class NeteaseTaskOptions(val automatic: Boolean = false, val signedDay: String = "", val musicianDay: String = "",
    val logs: List<NeteaseTaskLog> = emptyList(), val yunbeiDay: String = "", val vipDay: String = "") {
    fun signedDay(kind: NeteaseCheckIn) = when (kind) {
        NeteaseCheckIn.DAILY -> signedDay; NeteaseCheckIn.YUNBEI -> yunbeiDay
        NeteaseCheckIn.VIP -> vipDay; NeteaseCheckIn.MUSICIAN -> musicianDay
    }
}

/** Preferences and the bounded journal belong to a NetEase UID, never the CurrentMusic account. */
class NeteaseTaskPreferences(private val store: DataStore<Preferences>) {
    private val featureKey = booleanPreferencesKey("netease_tasks_enabled")
    val enabled: Flow<Boolean> = store.data.map { it[featureKey] ?: false }.distinctUntilChanged()
    suspend fun enable(value: Boolean) { store.edit { it[featureKey] = value } }
    private fun key(uid: Long, suffix: String) = stringPreferencesKey("netease_tasks_${uid}_$suffix")
    fun options(uid: Long): Flow<NeteaseTaskOptions> = store.data.map { read(it, uid) }.distinctUntilChanged()
    suspend fun snapshot(uid: Long) = read(store.data.first(), uid)
    private fun read(p: Preferences, uid: Long): NeteaseTaskOptions {
        if (uid <= 0) return NeteaseTaskOptions()
        val logs = p[key(uid, "logs")].orEmpty().lineSequence().mapNotNull { line ->
            val parts = line.split('|', limit = 3)
            if (parts.size != 3) null else parts[0].toLongOrNull()?.let { NeteaseTaskLog(it, parts[2], parts[1] == "1") }
        }.take(20).toList()
        return NeteaseTaskOptions(p[key(uid, "auto")] == "1", p[key(uid, "day")].orEmpty(),
            p[key(uid, "musicianDay")].orEmpty(), logs, p[key(uid, "yunbeiDay")].orEmpty(), p[key(uid, "vipDay")].orEmpty())
    }
    suspend fun automatic(uid: Long, enabled: Boolean) {
        require(uid > 0)
        store.edit { it[key(uid, "auto")] = if (enabled) "1" else "0" }
    }
    suspend fun record(uid: Long, message: String, success: Boolean, day: String? = null, kind: NeteaseCheckIn = NeteaseCheckIn.DAILY,
        now: Long = System.currentTimeMillis()) {
        require(uid > 0)
        store.edit { p ->
            day?.let { p[key(uid, when (kind) {
                NeteaseCheckIn.DAILY -> "day"; NeteaseCheckIn.YUNBEI -> "yunbeiDay"
                NeteaseCheckIn.VIP -> "vipDay"; NeteaseCheckIn.MUSICIAN -> "musicianDay"
            })] = it }
            val safe = message.replace('|', ' ').replace('\n', ' ').replace('\r', ' ').take(200)
            val line = "$now|${if (success) 1 else 0}|$safe"
            p[key(uid, "logs")] = (listOf(line) + p[key(uid, "logs")].orEmpty().lines().filter(String::isNotBlank)).take(20).joinToString("\n")
        }
    }
    companion object {
        fun day(now: Long = System.currentTimeMillis()): String = Instant.ofEpochMilli(now).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().toString()
    }
}

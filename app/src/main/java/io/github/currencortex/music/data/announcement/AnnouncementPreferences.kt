package io.github.currencortex.music.data.announcement

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

data class AnnouncementReadState(val suppressRead: Boolean = false, val read: Set<String> = emptySet()) {
    fun unread(server: String, items: List<Announcement>): List<Announcement> =
        if (!suppressRead) items else items.filter { announcementKey(server, it) !in read }
}

internal fun announcementKey(server: String, item: Announcement): String {
    val identity = if (item.id > 0) item.id.toString() else "${item.createdAt}:${item.title}:${item.body}"
    return MessageDigest.getInstance("SHA-256").digest("$server\n$identity".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

class AnnouncementPreferences(private val store: DataStore<Preferences>) {
    private val suppress = booleanPreferencesKey("announcements.suppressRead")
    private val read = stringSetPreferencesKey("announcements.read")
    private fun decode(value: Preferences) = AnnouncementReadState(value[suppress] ?: false, value[read].orEmpty())
    suspend fun snapshot() = decode(store.data.first())
    suspend fun acknowledge(server: String, items: List<Announcement>, suppressRead: Boolean): AnnouncementReadState {
        val value = store.edit { preferences ->
            preferences[suppress] = suppressRead
            if (suppressRead) preferences[read] = (preferences[read].orEmpty() + items.map { announcementKey(server, it) })
                .toList().takeLast(500).toSet()
        }
        return decode(value)
    }
}

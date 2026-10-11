package io.github.currencortex.music.data.announcement

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test

class AnnouncementPreferencesTest {
    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }
    private val base = "https://one.example/cm/"
    private val notices = listOf(Announcement(id = 1, title = "First"), Announcement(id = 2, title = "Second"))

    @Test fun suppressionSurvivesNewRepositoryAndOnlyNewNoticesReappear() = runBlocking {
        val store = MemoryPreferences()
        AnnouncementPreferences(store).acknowledge(base, notices, true)
        val restored = AnnouncementPreferences(store).snapshot()
        assertTrue(restored.suppressRead)
        assertTrue(restored.unread(base, notices).isEmpty())
        val new = Announcement(id = 3, title = "New")
        assertEquals(listOf(new), restored.unread(base, notices + new))
    }

    @Test fun sameIdsFromAnotherServerStillAppearAndOldEditsDoNotCountAsNew() = runBlocking {
        val preferences = AnnouncementPreferences(MemoryPreferences())
        val read = preferences.acknowledge(base, notices, true)
        assertEquals(notices, read.unread("https://two.example/cm/", notices))
        assertTrue(read.unread(base, notices.map { it.copy(body = "Edited", pinned = true, updatedAt = 20) }).isEmpty())
    }

    @Test fun ordinaryDismissalAndTurningSuppressionOffAllowExistingNotices() = runBlocking {
        val preferences = AnnouncementPreferences(MemoryPreferences())
        assertEquals(notices, preferences.acknowledge(base, notices, false).unread(base, notices))
        preferences.acknowledge(base, notices, true)
        val restored = preferences.acknowledge(base, emptyList(), false)
        assertFalse(restored.suppressRead)
        assertEquals(notices, restored.unread(base, notices))
    }

    @Test fun rememberingNewNoticesDoesNotForgetPreviouslyAcknowledgedNotices() = runBlocking {
        val preferences = AnnouncementPreferences(MemoryPreferences())
        preferences.acknowledge(base, notices, true)
        val new = Announcement(id = 3, title = "New")
        val read = preferences.acknowledge(base, listOf(new), true)
        assertTrue(read.unread(base, notices + new).isEmpty())
    }
}

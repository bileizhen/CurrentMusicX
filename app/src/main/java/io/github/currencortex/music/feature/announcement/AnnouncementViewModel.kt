package io.github.currencortex.music.feature.announcement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.data.announcement.Announcement
import io.github.currencortex.music.data.announcement.AnnouncementReadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AnnouncementViewModel(private val container: AppContainer, private val refreshIntervalMillis: Long = 15 * 60_000L) : ViewModel() {
    private val pending = MutableStateFlow<List<Announcement>>(emptyList())
    val items = pending.asStateFlow()
    private val suppress = MutableStateFlow(false)
    val suppressRead = suppress.asStateFlow()
    private var readState = AnnouncementReadState()
    private var source = ""
    private val foreground = MutableStateFlow(true)

    fun setForeground(active: Boolean) { foreground.value = active }

    init {
        viewModelScope.launch {
            container.ready.await()
            readState = container.announcementPreferences.snapshot()
            suppress.value = readState.suppressRead
            foreground.collectLatest { active ->
                if (active) container.announcements.observe(
                    container.musicSettings.state.map { it.server }.onEach { server ->
                        if (source != server) { source = server; pending.value = emptyList() }
                    }, refreshes = flow { while (true) { delay(refreshIntervalMillis); emit(Unit) } },
                    retryCount = 2, clearBeforeFetch = false, minimumRefreshMillis = refreshIntervalMillis).collect { notices ->
                    pending.value = container.announcements.pendingAutomatic(source, notices, readState)
                }
            }
        }
    }

    fun dismiss(suppressFuture: Boolean) {
        val shown = pending.value
        val server = source
        container.announcements.dismissAutomatic(server, shown)
        readState = readState.copy(suppressRead = suppressFuture)
        suppress.value = suppressFuture
        pending.value = emptyList()
        viewModelScope.launch {
            try {
                readState = container.announcementPreferences.acknowledge(server, shown, suppressFuture)
                suppress.value = readState.suppressRead
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { container.logger.warn("Announcements", "Unable to save announcement preference", error) }
        }
    }
}

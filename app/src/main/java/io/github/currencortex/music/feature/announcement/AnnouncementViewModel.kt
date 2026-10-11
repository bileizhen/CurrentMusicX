package io.github.currencortex.music.feature.announcement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.data.announcement.Announcement
import io.github.currencortex.music.data.announcement.AnnouncementReadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AnnouncementViewModel(private val container: AppContainer) : ViewModel() {
    private val pending = MutableStateFlow<List<Announcement>>(emptyList())
    val items = pending.asStateFlow()
    private val suppress = MutableStateFlow(false)
    val suppressRead = suppress.asStateFlow()
    private var readState = AnnouncementReadState()
    private var source = ""

    init {
        viewModelScope.launch {
            container.ready.await()
            readState = container.announcementPreferences.snapshot()
            suppress.value = readState.suppressRead
            container.announcements.observe(container.musicSettings.state.map { it.server }).collect {
                source = container.musicSettings.state.value.server
                pending.value = readState.unread(source, it)
            }
        }
    }

    fun dismiss(suppressFuture: Boolean) {
        val shown = pending.value
        val server = source
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

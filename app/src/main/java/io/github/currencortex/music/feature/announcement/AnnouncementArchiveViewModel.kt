package io.github.currencortex.music.feature.announcement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.data.announcement.Announcement
import io.github.currencortex.music.data.announcement.AnnouncementRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AnnouncementArchiveState(val items: List<Announcement> = emptyList(),
    val loading: Boolean = true, val error: String? = null)

class AnnouncementArchiveViewModel(repository: AnnouncementRepository, servers: Flow<String>) : ViewModel() {
    private val refreshes = MutableStateFlow(0)
    private val mutableState = MutableStateFlow(AnnouncementArchiveState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            var previousServer: String? = null
            combine(servers.distinctUntilChanged(), refreshes) { server, _ -> server }.collectLatest { server ->
                mutableState.value = AnnouncementArchiveState(
                    items = if (previousServer == server) mutableState.value.items else emptyList())
                previousServer = server
                try {
                    mutableState.value = AnnouncementArchiveState(repository.fetchAll(server), loading = false)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    mutableState.value = mutableState.value.copy(loading = false, error = "公告暂时无法加载，请重试")
                }
            }
        }
    }

    fun refresh() { if (!state.value.loading) refreshes.value++ }
}

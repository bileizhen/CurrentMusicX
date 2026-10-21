package io.github.currencortex.music.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.netease.tasks.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class NeteaseTasksState(val group: NeteaseTaskGroup = NeteaseTaskGroup.DAILY, val loading: Boolean = false,
    val page: NeteaseTaskPage? = null, val error: String? = null)
enum class NeteaseTaskAction { SIGN, CLAIM }

@OptIn(ExperimentalCoroutinesApi::class)
class NeteaseTasksViewModel(private val repository: NeteaseTaskRepository, val automation: NeteaseTaskAutomation,
    private val notice: (String) -> Unit) : ViewModel() {
    val account = repository.accounts
    private val group = MutableStateFlow(NeteaseTaskGroup.DAILY)
    private val refreshes = MutableStateFlow(0)
    private val mutableState = MutableStateFlow(NeteaseTasksState())
    val state = mutableState.asStateFlow()
    val options = account.flatMapLatest { automation.preferences.options(it.profile?.uid ?: 0L) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NeteaseTaskOptions())
    private var acting = false
    private val mutableAction = MutableStateFlow<NeteaseTaskAction?>(null)
    val currentAction = mutableAction.asStateFlow()
    init {
        viewModelScope.launch {
            var previousRevision: Long? = null
            combine(account, group, refreshes) { owner, tab, _ -> owner to tab }.collectLatest { (owner, tab) ->
                val cached = mutableState.value.page.takeIf { previousRevision == owner.revision && mutableState.value.group == tab }
                previousRevision = owner.revision
                mutableState.value = NeteaseTasksState(tab, loading = owner.loggedIn && !owner.stale, page = cached)
                if (!owner.loggedIn || owner.stale) return@collectLatest
                when (val result = appResult { repository.load(tab) }) {
                    is AppResult.Success -> mutableState.value = NeteaseTasksState(tab, page = result.value)
                    is AppResult.Failure -> mutableState.value = NeteaseTasksState(tab, page = cached, error = result.message)
                }
            }
        }
    }
    fun select(value: NeteaseTaskGroup) { group.value = value }
    fun refresh() { if (!state.value.loading) refreshes.value++ }
    fun automatic(enabled: Boolean) {
        val uid = account.value.profile?.uid ?: return
        viewModelScope.launch {
            when (val result = appResult { automation.preferences.automatic(uid, enabled) }) {
                is AppResult.Failure -> notice(result.message)
                else -> Unit
            }
        }
    }
    fun sign(kind: NeteaseCheckIn = NeteaseCheckIn.DAILY) = action(NeteaseTaskAction.SIGN) { automation.sign(kind) }
    fun claim() { val selected = group.value; action(NeteaseTaskAction.CLAIM) { automation.claim(selected) } }
    private fun action(type: NeteaseTaskAction, block: suspend () -> String) {
        if (acting || automation.busy.value) return
        val revision = account.value.revision
        acting = true
        mutableAction.value = type
        viewModelScope.launch {
            try {
                when (val result = appResult(block)) {
                    is AppResult.Success -> if (account.value.revision == revision) { notice(result.value); refreshes.value++ }
                    is AppResult.Failure -> if (account.value.revision == revision) notice(result.message)
                }
            } finally { acting = false; mutableAction.value = null }
        }
    }
}

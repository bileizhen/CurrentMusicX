package io.github.currencortex.music.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.profile.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class LevelsState(val signedIn: Boolean = false, val loading: Boolean = false,
    val rules: LevelRules? = null, val progress: LevelProgress? = null, val error: String? = null,
    val busy: Boolean = false, val trial: LevelTrial? = null, val answers: List<Int> = emptyList(),
    val result: LevelTrialResult? = null) {
    val canSubmit get() = !busy && trial?.validAnswers(answers) == true
    val canStartTrial get() = !busy && !loading && rules != null && progress?.level == rules.trial.level
}

class LevelsViewModel(private val repository: LevelsApi, owners: Flow<RequestSession>,
    private val onProgress: suspend (RequestSession, LevelProgress) -> Unit = { _, _ -> }) : ViewModel() {
    val state = MutableStateFlow(LevelsState())
    private var owner: RequestSession? = null
    private var action: Job? = null
    private var revision = 0L
    init {
        viewModelScope.launch {
            owners.distinctUntilChanged().collect { next ->
                action?.cancel(); revision++; owner = next
                state.value = LevelsState(signedIn = next.token != null)
                if (next.token != null) refresh()
            }
        }
    }
    private fun current(expected: RequestSession, epoch: Long) = owner == expected && revision == epoch
    fun refresh() {
        val expected = owner?.takeIf { it.token != null } ?: return
        if (state.value.busy || state.value.loading) return
        action?.cancel()
        val epoch = ++revision
        state.update { it.copy(loading = true, error = null) }
        action = viewModelScope.launch {
            val result = appResult {
                val rules = state.value.rules ?: repository.rules(expected)
                rules to repository.progress(expected)
            }
            if (!current(expected, epoch)) return@launch
            when (result) {
                is AppResult.Success -> {
                    state.update { it.copy(loading = false, rules = result.value.first, progress = result.value.second) }
                    appResult { onProgress(expected, result.value.second) }
                }
                is AppResult.Failure -> state.update { it.copy(loading = false, error = message(result, false)) }
            }
        }
    }
    suspend fun poll() {
        while (currentCoroutineContext().isActive) { refresh(); delay(30_000) }
    }
    fun startTrial() {
        val expected = owner ?: return
        val rules = state.value.rules ?: return
        if (!state.value.canStartTrial) return
        action?.cancel()
        val epoch = ++revision
        state.update { it.copy(busy = true, error = null, result = null) }
        action = viewModelScope.launch {
            val result = appResult { repository.trial(expected, rules.trial) }
            if (!current(expected, epoch)) return@launch
            when (result) {
                is AppResult.Success -> state.update { it.copy(busy = false, trial = result.value,
                    answers = List(result.value.questions.size) { -1 }) }
                is AppResult.Failure -> {
                    state.update { it.copy(busy = false, error = message(result, true)) }
                    if (result.status == 409 || result.status == 403) refreshAfterTrialError(expected, epoch)
                }
            }
        }
    }
    fun answer(question: Int, option: Int) {
        val value = state.value
        val trial = value.trial ?: return
        if (value.busy || question !in trial.questions.indices || option !in trial.questions[question].options.indices) return
        state.update { it.copy(answers = it.answers.toMutableList().apply { this[question] = option }) }
    }
    fun closeTrial() {
        action?.cancel(); revision++
        state.update { it.copy(trial = null, answers = emptyList(), busy = false, loading = false, error = null) }
        refresh()
    }
    fun submit() {
        val value = state.value
        val expected = owner ?: return
        val trial = value.trial ?: return
        if (!value.canSubmit) return
        action?.cancel()
        val epoch = ++revision
        // Submission owns the action now; a cancelled progress poll cannot clear its loading flag.
        state.update { it.copy(busy = true, loading = false, error = null) }
        action = viewModelScope.launch {
            val result = appResult { repository.submit(expected, trial, value.answers) }
            if (!current(expected, epoch)) return@launch
            when (result) {
                is AppResult.Success -> {
                    state.update { it.copy(busy = false, trial = null, answers = emptyList(), result = result.value,
                        progress = it.progress?.copy(level = result.value.level)) }
                    state.value.progress?.let { appResult { onProgress(expected, it) } }
                    if (current(expected, epoch)) refresh()
                }
                is AppResult.Failure -> {
                    val stale = result.status in setOf(400, 403, 409)
                    state.update { it.copy(busy = false, error = message(result, true),
                        trial = if (stale) null else it.trial, answers = if (stale) emptyList() else it.answers) }
                    if (stale) refreshAfterTrialError(expected, epoch)
                }
            }
        }
    }
    private suspend fun refreshAfterTrialError(expected: RequestSession, epoch: Long) {
        val result = appResult { repository.progress(expected) }
        if (current(expected, epoch) && result is AppResult.Success) {
            state.update { it.copy(progress = result.value) }
            appResult { onProgress(expected, result.value) }
        }
    }
    private fun message(error: AppResult.Failure, trial: Boolean): String = when {
        error.status == 404 -> "当前服务器暂不支持等级体系"
        trial && error.status == 400 -> "题目或答案已失效，请重新进入试炼"
        trial && error.status == 403 -> "尚未达到试炼要求，请刷新等级后重试"
        trial && error.status == 409 -> "已通过试炼，请查看最新等级"
        else -> error.message
    }
}

package io.github.currencortex.music.data.netease.tasks

import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Runs only while the app is in use. No background service, playback loop or simulated listening. */
class NeteaseTaskAutomation(private val repository: NeteaseTaskRepository, val preferences: NeteaseTaskPreferences,
    foreground: StateFlow<Boolean>, scope: CoroutineScope, private val clock: () -> Long = System::currentTimeMillis,
    enabled: Flow<Boolean> = flowOf(true)) {
    private val lock = Mutex()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val attempted = mutableMapOf<Pair<Long, String>, Long>()
    init {
        scope.launch {
            combine(repository.accounts, foreground, enabled) { account, visible, allowed -> account to (visible && allowed) }
                .collectLatest { (account, visible) ->
                    val uid = account.profile?.uid ?: return@collectLatest
                    if (!visible || !account.loggedIn || account.stale) return@collectLatest
                    preferences.options(uid).map { it.automatic }.distinctUntilChanged().collectLatest { enabled ->
                        if (enabled) while (currentCoroutineContext().isActive) {
                            val now = clock()
                            val key = uid to NeteaseTaskPreferences.day(now)
                            if (now - (attempted[key] ?: Long.MIN_VALUE / 2) >= 30 * 60_000L) {
                                attempted.clear(); attempted[key] = now
                                appResult { sign() }
                                appResult { sign(NeteaseCheckIn.YUNBEI) }
                            }
                            delay(60_000)
                        }
                    }
                }
        }
    }
    suspend fun sign(kind: NeteaseCheckIn = NeteaseCheckIn.DAILY): String = lock.withLock {
        mutableBusy.value = true
        try {
            val owner = repository.owner()
            val uid = owner.credential.profile!!.uid
            val day = NeteaseTaskPreferences.day(clock())
            val options = preferences.snapshot(uid)
            if (options.signedDay(kind) == day) return@withLock "今日已签到"
            val prefix = kind.title
            when (val result = appResult { repository.sign(owner, kind) }) {
                is AppResult.Success -> {
                    preferences.record(uid, "$prefix · ${result.value}", true, day, kind, clock())
                    result.value
                }
                is AppResult.Failure -> {
                    preferences.record(uid, "$prefix · ${result.message}", false, now = clock())
                    throw ApiException(result.kind, result.status, result.message)
                }
            }
        } finally { mutableBusy.value = false }
    }
    suspend fun claim(group: NeteaseTaskGroup): String = lock.withLock {
        mutableBusy.value = true
        try {
            val uid = repository.owner().credential.profile!!.uid
            when (val result = appResult { repository.claimRewards(group) }) {
                is AppResult.Success -> {
                    preferences.record(uid, "${group.title} · ${result.value}", true, now = clock())
                    result.value
                }
                is AppResult.Failure -> {
                    preferences.record(uid, "${group.title} · ${result.message}", false, now = clock())
                    throw ApiException(result.kind, result.status, result.message)
                }
            }
        } finally { mutableBusy.value = false }
    }
}

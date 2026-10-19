package io.github.currencortex.music.feature.roaming

import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.core.netease.NeteaseSessionStore
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.roaming.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class RoamingState(val catalog: RoamingCatalog = RoamingCatalog.fallback, val catalogLoading: Boolean = false,
    val catalogError: String? = null, val choice: RoamingChoice = RoamingCatalog.fallback.modes.first(),
    val settings: RoamingSettings = RoamingSettings(), val active: Boolean = false,
    val loading: Boolean = false, val error: String? = null, val waiting: Boolean = false, val ready: Boolean = false)

/** Application-scoped station: leaving its page keeps music playing, leaving the station restores the old queue. */
class PersonalRoamingController(private val repository: PersonalRoamingRepository,
    private val sessions: NeteaseSessionStore, private val player: PlayerController,
    private val preferences: RoamingPreferences, private val scope: CoroutineScope,
    private val sourceIdentity: StateFlow<String>) {
    val state = MutableStateFlow(RoamingState())
    private var generation = 0L
    private var job: Job? = null
    private var catalogJob: Job? = null
    private var station: String? = null
    private var original: QueueSnapshot? = null
    private var waitingRevision: Long? = null
    private var failedChoice: RoamingChoice? = null
    private val seen = LinkedHashSet<Long>()
    init {
        player.onRadioBoundary = ::awaitNext
        scope.launch {
            sessions.ready.await()
            combine(sessions.state.map { it.revision }.distinctUntilChanged(), sourceIdentity) { revision, source -> revision to source }
                .collectLatest {
                    stop(restore = false)
                    state.update { it.copy(ready = false) }
                    val uid = sessions.state.value.profile?.uid ?: 0
                    val saved = preferences.load(uid)
                    val choice = state.value.catalog.choices.find { it.key == saved.selection } ?: RoamingCatalog.fallback.modes.first()
                    state.value = state.value.copy(settings = saved, choice = choice, error = null, ready = true)
                }
        }
        scope.launch {
            combine(player.queue.state.map { Triple(it.radioSession, it.current?.id, it.songs.size - it.index - 1) }.distinctUntilChanged(),
                player.state.map { it.mode }.distinctUntilChanged()) { queue, mode -> queue to mode }.collect { (queue, mode) ->
                val current = station ?: return@collect
                if (queue.first != current || mode != PlayerMode.LOCAL) { stop(restore = false); return@collect }
                if (queue.third <= 1 && state.value.settings.preload && !state.value.loading && state.value.error == null) refill()
            }
        }
    }
    fun loadCatalog() {
        if (catalogJob?.isActive == true) return
        catalogJob = scope.launch {
            val revision = sessions.state.value.revision
            state.update { it.copy(catalogLoading = true, catalogError = null) }
            val result = appResult { repository.catalog() }
            if (revision != sessions.state.value.revision) {
                state.update { it.copy(catalogLoading = false) }; return@launch
            }
            when (result) {
                is AppResult.Success -> state.update { old -> old.copy(catalog = result.value,
                    choice = result.value.choices.find { it.key == if (old.active) old.choice.key else old.settings.selection } ?: old.choice) }
                is AppResult.Failure -> state.update { it.copy(catalogError = result.message) }
            }
            state.update { it.copy(catalogLoading = false) }
        }
    }
    fun start(choice: RoamingChoice = state.value.choice) {
        if (!state.value.ready) return
        if (player.state.value.mode != PlayerMode.LOCAL) {
            state.update { it.copy(error = "请先退出一起听或结束投屏，再开启私人漫游") }; return
        }
        val epoch = ++generation
        job?.cancel(); waitingRevision = null; failedChoice = null
        val revision = sessions.state.value.revision
        val source = sourceIdentity.value
        val commands = player.commandRevision
        val previousQueue = player.queue.state.value
        state.update { it.copy(loading = true, waiting = false, error = null) }
        job = scope.launch {
            try {
                val songs = repository.songs(choice)
                ensureActive()
                if (epoch != generation || revision != sessions.state.value.revision || source != sourceIdentity.value ||
                    player.state.value.mode != PlayerMode.LOCAL || commands != player.commandRevision ||
                    player.queue.state.value.copy(positionMs = previousQueue.positionMs) != previousQueue) {
                    if (epoch == generation) state.update { it.copy(loading = false) }
                    return@launch
                }
                if (songs.isEmpty()) { failedChoice = choice; state.update { it.copy(loading = false, error = "这个模式暂时没有可播放的推荐，试试其他模式") }; return@launch }
                if (station == null) original = previousQueue.copy(positionMs = player.state.value.positionMs)
                val session = UUID.randomUUID().toString()
                waitingRevision = null
                station = session; seen.clear(); seen.addAll(songs.map { it.id })
                state.update { it.copy(choice = choice, active = true, loading = false, waiting = false,
                    settings = it.settings.copy(selection = choice.key)) }
                player.queue.startRadio(songs, session)
                player.load()
                preferences.save(sessions.state.value.profile?.uid ?: 0, state.value.settings)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (epoch == generation) { failedChoice = choice; state.update { it.copy(loading = false, error = error.message ?: "获取漫游推荐失败") } }
            }
        }
    }
    private fun refill() {
        val session = station ?: return
        if (state.value.loading) return
        val epoch = generation
        val choice = state.value.choice
        failedChoice = null
        state.update { it.copy(loading = true, error = null) }
        job = scope.launch {
            try {
                val songs = repository.songs(choice, seen.toList())
                ensureActive()
                if (epoch != generation || station != session || player.queue.state.value.radioSession != session) return@launch
                if (!player.queue.appendRadio(songs, session)) {
                    state.update { it.copy(error = "暂时没有新的推荐，可重试或切换模式") }
                    if (waitingRevision != null) player.pause()
                } else {
                    seen.addAll(songs.map { it.id }); while (seen.size > 200) seen.remove(seen.first())
                    val resume = waitingRevision
                    if (resume != null && resume == player.commandRevision && player.queue.next()) player.load()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (epoch == generation) {
                    state.update { it.copy(error = error.message ?: "补充推荐失败，请重试") }
                    if (waitingRevision != null) player.pause()
                }
            } finally {
                if (epoch == generation) {
                    val retryPending = waitingRevision != null && state.value.error != null
                    waitingRevision = null
                    state.update { it.copy(loading = false, waiting = retryPending) }
                }
            }
        }
    }
    fun awaitNext() {
        if (station == null) return
        waitingRevision = player.commandRevision
        state.update { it.copy(waiting = true) }
        if (!state.value.loading) refill()
    }
    fun retry() {
        failedChoice?.let { start(it); return }
        if (state.value.active) { if (state.value.waiting) awaitNext() else refill() } else start()
    }
    fun updateSettings(settings: RoamingSettings) {
        state.update { it.copy(settings = settings) }
        val uid = sessions.state.value.profile?.uid ?: 0
        scope.launch { preferences.save(uid, settings) }
    }
    fun stop(restore: Boolean = state.value.settings.restoreQueue) {
        ++generation; job?.cancel(); job = null; waitingRevision = null; failedChoice = null
        val owned = station != null && player.queue.state.value.radioSession == station && player.state.value.mode == PlayerMode.LOCAL
        station = null
        state.update { it.copy(active = false, loading = false, waiting = false) }
        if (owned) {
            if (restore && original != null) {
                player.pause(); player.queue.restore(original!!)
                if (player.queue.state.value.current == null) player.clear() else player.load(false)
            } else player.queue.state.value = player.queue.state.value.copy(radioSession = null)
        }
        original = null; seen.clear()
    }
}

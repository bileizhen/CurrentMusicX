package io.github.currencortex.music.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.admin.*
import io.github.currencortex.music.data.profile.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

enum class AdminTab(val label: String) { OVERVIEW("概览"), USERS("用户"), ANNOUNCEMENTS("公告"), DEVICES("设备"), CLIENTS("客户端"), SYSTEM("系统") }
data class AdminIdentity(val server: String, val id: Long?, val revision: Long)
data class AdminState(
    val identity: AdminIdentity? = null, val actor: ProfileUser? = null,
    val tab: AdminTab = AdminTab.OVERVIEW, val loading: Boolean = false, val busy: Boolean = false,
    val error: String? = null, val operationError: String? = null, val message: String? = null,
    val query: String = "", val sort: String = "reg", val offset: Int = 0,
    val overview: JsonObject? = null, val traffic: JsonObject? = null, val trafficError: String? = null,
    val users: AdminUsers? = null, val announcements: List<AdminAnnouncement>? = null,
    val sessions: List<AdminSession>? = null, val clients: JsonObject? = null, val rules: LevelRules? = null,
    val rulesError: String? = null,
) { val authorized get() = actor?.canAdminister == true }

class AdminViewModel(val container: AppContainer) : ViewModel(), AdminActions {
    private val accounts = container.accountRepository
    private val repository = AdminRepository(container.apiClient)
    val state = MutableStateFlow(AdminState())
    private var readJob: Job? = null
    private var writeJob: Job? = null
    fun identity() = AdminIdentity(accounts.server, accounts.state.value.account?.id, accounts.sessionRevision.value)
    private fun session() = RequestSession(accounts.server, accounts.token)
    private fun current(id: AdminIdentity, owner: RequestSession) = id == identity() && owner == session()
    init {
        viewModelScope.launch {
            container.sessionRestored.await()
            combine(accounts.sessionRevision, accounts.state, container.musicSettings.state.map { it.server }.distinctUntilChanged())
                { _, account, _ -> identity() to Triple(account.account?.isAdmin, account.account?.isSuper, account.account?.isOwner) }
                .distinctUntilChanged().collect {
                    readJob?.cancel(); writeJob?.cancel()
                    state.value = AdminState(identity = identity())
                    refresh()
                }
        }
    }
    override fun clearOperationError() { state.update { it.copy(operationError = null) } }
    override fun dismissMessage() { state.update { it.copy(message = null) } }
    override fun select(tab: AdminTab) {
        if (state.value.busy || state.value.tab == tab) return
        state.update { it.copy(tab = tab, error = null, message = null) }; refresh()
    }
    override fun search(query: String, sort: String) {
        if (state.value.busy) return
        state.update { it.copy(query = query.trim(), sort = sort, offset = 0, users = null) }; refresh()
    }
    override fun page(delta: Int) {
        if (state.value.loading || state.value.busy) return
        val offset = (state.value.offset + delta * AdminRepository.PAGE_SIZE).coerceAtLeast(0)
        if (delta > 0 && offset >= (state.value.users?.total ?: 0)) return
        state.update { it.copy(offset = offset, users = null) }; refresh()
    }
    private suspend fun validate(id: AdminIdentity, owner: RequestSession): ProfileUser {
        if (id.id == null || owner.token.isNullOrBlank() || !current(id, owner)) throw ApiException(ErrorKind.Unauthorized)
        val actor = repository.actor(owner)
        if (!current(id, owner) || actor.id != id.id) throw ApiException(ErrorKind.Unauthorized)
        // Refresh the shared role flags so a revoked administrator also loses the profile entry.
        container.authRepository.updateAccount(container.profileRepository.account(actor), owner)
        currentCoroutineContext().ensureActive()
        if (!current(id, owner)) throw ApiException(ErrorKind.Unauthorized)
        if (!actor.canAdminister) throw ApiException(ErrorKind.Forbidden)
        return actor
    }
    override fun refresh() {
        if (state.value.busy) return
        readJob?.cancel()
        val id = identity(); val owner = session()
        if (id.id == null || owner.token.isNullOrBlank()) { state.value = AdminState(identity = id); return }
        readJob = viewModelScope.launch {
            fun publish(change: (AdminState) -> AdminState) {
                ensureActive()
                if (!current(id, owner)) throw CancellationException("Management session changed")
                state.update(change)
            }
            state.update { it.copy(loading = true, error = null) }
            val result = appResult {
                val actor = validate(id, owner)
                if (!current(id, owner)) return@appResult
                publish { it.copy(actor = actor, identity = id) }
                val snapshot = state.value
                when (snapshot.tab) {
                    AdminTab.OVERVIEW, AdminTab.SYSTEM -> coroutineScope {
                        val trafficRequest = if (snapshot.tab == AdminTab.OVERVIEW) async {
                            appResult { repository.read("admin/traffic", owner) }
                        } else null
                        val overview = repository.read("admin/overview", owner)
                        publish { it.copy(overview = overview) }
                        if (trafficRequest != null) when (val traffic = trafficRequest.await()) {
                            is AppResult.Success -> publish { it.copy(traffic = traffic.value, trafficError = null) }
                            is AppResult.Failure -> {
                                if (traffic.kind in setOf(ErrorKind.Unauthorized, ErrorKind.Forbidden)) throw ApiException(traffic.kind)
                                publish { it.copy(traffic = null, trafficError = traffic.message) }
                            }
                        }
                    }
                    AdminTab.USERS -> {
                        val users = repository.users(owner, snapshot.query, snapshot.sort, snapshot.offset)
                        publish { it.copy(users = users) }
                        when (val rules = appResult { LevelsRepository(container.apiClient).rules(owner) }) {
                            is AppResult.Success -> publish { it.copy(rules = rules.value, rulesError = null) }
                            is AppResult.Failure -> publish { it.copy(rules = null, rulesError = rules.message) }
                        }
                    }
                    AdminTab.ANNOUNCEMENTS -> {
                        val rows = repository.announcements(owner).items
                        publish { it.copy(announcements = rows) }
                    }
                    AdminTab.DEVICES -> {
                        val rows = repository.sessions(owner).sessions
                        publish { it.copy(sessions = rows) }
                    }
                    AdminTab.CLIENTS -> {
                        val clients = repository.read("admin/clients", owner)
                        publish { it.copy(clients = clients) }
                    }
                }
            }
            ensureActive()
            if (current(id, owner)) {
                if (result is AppResult.Failure && result.kind in setOf(ErrorKind.Unauthorized, ErrorKind.Forbidden))
                    state.value = AdminState(identity = id, error = "当前账号没有管理权限，或登录已失效")
                else state.update { it.copy(loading = false, error = (result as? AppResult.Failure)?.message) }
            }
        }
    }
    private fun mutate(success: String, done: () -> Unit, action: suspend (ProfileUser, RequestSession) -> Unit) {
        if (state.value.busy || state.value.loading || !state.value.authorized) return
        val id = identity(); val owner = session()
        if (state.value.identity != id) return
        state.update { it.copy(busy = true, operationError = null, message = null) }
        writeJob = viewModelScope.launch {
            val result = appResult { action(validate(id, owner), owner) }
            ensureActive()
            if (!current(id, owner)) return@launch
            when (result) {
                is AppResult.Success -> {
                    state.update { it.copy(busy = false, message = success) }; done(); refresh()
                }
                is AppResult.Failure -> {
                    val uncertain = result.kind in setOf(ErrorKind.Network, ErrorKind.Timeout, ErrorKind.Server)
                    state.update { it.copy(operationError = result.message + if (uncertain) "。操作结果尚未确认，请刷新核对后再试。" else "") }
                    if (result.kind in setOf(ErrorKind.Unauthorized, ErrorKind.Forbidden)) {
                        // A protected target can return 403 while the operator is still an administrator.
                        val actor = appResult { validate(id, owner) }
                        if (current(id, owner) && actor is AppResult.Failure)
                            state.value = AdminState(identity = id, error = "无法确认管理权限，请刷新重试")
                    }
                    if (current(id, owner)) state.update { it.copy(busy = false) }
                }
            }
        }
    }
    override fun userAction(target: AdminUser, action: AdminUserAction, value: String, level: Int?, done: () -> Unit) =
        mutate("用户已更新", done) { actor, owner ->
            if (!action.allowed(actor, target)) throw ApiException(ErrorKind.Forbidden)
            val body = buildJsonObject {
                when (action) {
                    AdminUserAction.KICK -> Unit
                    AdminUserAction.PASSWORD -> { require(value.length in 6..64); put("password", value) }
                    AdminUserAction.TITLE -> { require(value.codePointCount(0, value.length) <= (state.value.overview?.number("titleMax") ?: 16L)); put("title", value.trim()) }
                    AdminUserAction.PROGRESSION -> {
                        val rules = LevelsRepository(container.apiClient).rules(owner)
                        val points = value.toLongOrNull()
                        require(points != null && points in 0..rules.maxPoints && level in rules.levels.map { it.level } + rules.trial.plusLevel)
                        put("points", points); put("level", level)
                    }
                    AdminUserAction.DISABLE -> put("on", !target.disabled)
                    AdminUserAction.ADMIN -> put("on", !target.isAdmin)
                    AdminUserAction.SUPER -> put("on", !target.isSuper)
                    AdminUserAction.OWNER -> put("on", !target.isOwner)
                }
            }
            repository.write("POST", "admin/users/${target.id}/${action.path}", body, owner)
        }
    override fun kick(session: AdminSession, done: () -> Unit) = mutate("该用户的全部设备已下线", done) { actor, owner ->
        if (actor.id == session.user_id) throw ApiException(ErrorKind.Forbidden)
        repository.write("POST", "admin/users/${session.user_id}/kick", buildJsonObject {}, owner)
    }
    override fun announcement(item: AdminAnnouncement?, title: String, body: String, link: String, pinned: Boolean, done: () -> Unit) =
        mutate(if (item == null) "公告已发布" else "公告已保存", done) { _, owner ->
            require(title.trim().length in 1..60)
            require(link.isBlank() || runCatching { java.net.URI(link).let { it.scheme in setOf("https", "http") && !it.host.isNullOrBlank() && it.userInfo == null } }.getOrDefault(false))
            if (item != null && !item.canEdit) throw ApiException(ErrorKind.Forbidden)
            repository.write(if (item == null) "POST" else "PUT", if (item == null) "announcements" else "announcements/${item.id}",
                buildJsonObject { put("title", title.trim()); put("body", body.trim()); put("link", link.trim()); put("pinned", pinned) }, owner)
        }
    override fun announcementAction(item: AdminAnnouncement, action: String, done: () -> Unit) = mutate("公告已更新", done) { _, owner ->
        require(action in setOf("pin", "restore", "remove"))
        if (!item.canEdit) throw ApiException(ErrorKind.Forbidden)
        repository.write(if (action == "remove") "DELETE" else "POST",
            "announcements/${item.id}" + if (action == "remove") "" else "/$action",
            buildJsonObject { if (action == "pin") put("pinned", !item.pinned) }, owner)
    }
    override fun clearCache(done: () -> Unit) = mutate("辅助缓存已清理", done) { _, owner ->
        repository.write("POST", "admin/cache/clear", buildJsonObject { put("scope", "aux") }, owner)
    }
}

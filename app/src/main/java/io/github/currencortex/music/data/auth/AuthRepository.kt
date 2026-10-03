package io.github.currencortex.music.data.auth

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.security.TokenStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Serializable data class UserDto(val id: Long = 0, val username: String = "", val nickname: String = "", val avatar: String = "")
@Serializable class LoginDto(val token: String, val user: UserDto)
class RegistrationInput(val username: String, val password: String, val nickname: String,
    val contact: String, val code: String, val phone: Boolean)
data class Account(val id: Long, val nickname: String, val username: String = "", val avatar: String = "")
data class AccountState(val account: Account? = null, val loading: Boolean = false, val offline: Boolean = false, val error: String? = null)
class AccountRepository(private val store: TokenStore, private val scope: CoroutineScope, private val vault: AccountVault? = null) {
    private val storageMutex = Mutex()
    @Volatile var token: String? = null
        private set
    @Volatile var server: String = ""
    val state = MutableStateFlow(AccountState())
    val sessionRevision = MutableStateFlow(0L)
    val savedAccounts: StateFlow<List<SavedAccount>> = vault?.accounts ?: MutableStateFlow(emptyList())
    suspend fun restoreToken() = storageMutex.withLock {
        withContext(Dispatchers.IO) { token = store.read() }
        if (token != null) vault?.current()?.takeIf { it.server == server }?.let { state.value = AccountState(it.account()) }
    }
    suspend fun save(token: String, user: UserDto, expected: RequestSession? = null) {
        storageMutex.withLock {
            if (expected != null && expected != RequestSession(server, this.token)) throw ApiException(ErrorKind.Unauthorized)
            val changed = token != this.token || state.value.account?.id != user.id
            vault?.remember(server, user, token)
            withContext(Dispatchers.IO) { store.write(token); this@AccountRepository.token = token }
            state.value = AccountState(Account(user.id, user.nickname.ifBlank { user.username }, user.username, user.avatar))
            if (changed) sessionRevision.update { it + 1 }
        }
    }
    suspend fun clear(rememberSaved: Boolean = false) {
        storageMutex.withLock {
            token = null; withContext(Dispatchers.IO) { store.clear() }; vault?.detach(forget = !rememberSaved)
            state.value = AccountState()
            sessionRevision.update { it + 1 }
        }
    }
    suspend fun activate(key: String): SavedAccount = storageMutex.withLock {
        val (entry, credential) = vault?.credential(key) ?: throw ApiException(ErrorKind.Unauthorized)
        withContext(Dispatchers.IO) { store.write(credential) }
        vault.select(key); server = entry.server; token = credential
        state.value = AccountState(entry.account(), loading = true)
        sessionRevision.update { it + 1 }
        entry
    }
    suspend fun removeSaved(key: String) = storageMutex.withLock {
        if (vault?.current()?.key == key && token != null) throw ApiException(ErrorKind.Forbidden)
        vault?.remove(key)
    }
    fun expired(request: RequestSession) {
        if (request.server == server && request.token != null && request.token == token) {
            token = null
            state.value = AccountState(error = ErrorKind.Unauthorized.message)
            sessionRevision.update { it + 1 }
            scope.launch(Dispatchers.IO) { storageMutex.withLock { if (token == null) { store.clear(); vault?.detach(forget = true) } } }
        }
    }
}
class AuthRepository(private val api: ApiClient, val accounts: AccountRepository,
                     private val device: String, private val persistServer: suspend (String) -> Unit = {},
                     private val persistAccount: suspend (Long, String) -> Unit) {
    private val sessionMutex = Mutex()
    suspend fun restore(server: String): AppResult<UserDto?> = sessionMutex.withLock {
        accounts.server = server
        appResult {
            accounts.restoreToken()
            if (accounts.token == null) return@appResult null
            accounts.state.value = accounts.state.value.copy(loading = true)
            when (val result = appResult { api.get<UserDto>("auth/me", authenticated = true) }) {
                is AppResult.Success -> {
                    val user = result.value
                    accounts.save(accounts.token!!, user)
                    persistAccount(user.id, user.nickname)
                    user
                }
                is AppResult.Failure -> {
                    accounts.state.value = accounts.state.value.copy(loading = false, offline = result.kind != ErrorKind.Unauthorized,
                        error = result.kind.message)
                    throw ApiException(result.kind)
                }
            }
        }
    }
    suspend fun login(username: String, password: String): AppResult<Account> = sessionMutex.withLock { appResult {
        val dto = ApiJson.decodeFromJsonElement<LoginDto>(api.request("POST", "auth/login", body = buildJsonObject {
            put("username", username); put("password", password); put("device", device); put("platform", "app")
        }))
        require(dto.token.isNotBlank())
        accounts.save(dto.token, dto.user)
        persistAccount(dto.user.id, dto.user.nickname)
        accounts.state.value.account!!
    } }
    suspend fun switchAccount(key: String): AppResult<UserDto> = sessionMutex.withLock { appResult {
        val selected = accounts.activate(key)
        persistServer(selected.server); persistAccount(selected.id, selected.nickname)
        when (val result = appResult { api.get<UserDto>("auth/me", authenticated = true) }) {
            is AppResult.Success -> { accounts.save(accounts.token!!, result.value); persistAccount(result.value.id, result.value.nickname); result.value }
            is AppResult.Failure -> { accounts.state.value = accounts.state.value.copy(loading = false,
                offline = result.kind != ErrorKind.Unauthorized, error = result.kind.message); throw ApiException(result.kind) }
        }
    } }
    suspend fun updateAccount(user: UserDto, expected: RequestSession) = sessionMutex.withLock {
        accounts.save(expected.token ?: throw ApiException(ErrorKind.Unauthorized), user, expected)
        persistAccount(user.id, user.nickname)
    }
    suspend fun register(input: RegistrationInput): AppResult<Account> = sessionMutex.withLock { appResult {
        val dto = ApiJson.decodeFromJsonElement<LoginDto>(api.request("POST", "auth/register", body = buildJsonObject {
            put("username", input.username.trim()); put("password", input.password); put("nickname", input.nickname.trim().ifBlank { input.username.trim() })
            put(if (input.phone) "phone" else "email", input.contact.trim()); put(if (input.phone) "phoneCode" else "emailCode", input.code.trim())
            put("device", device); put("platform", "app")
        }))
        require(dto.token.isNotBlank()); accounts.save(dto.token, dto.user); persistAccount(dto.user.id, dto.user.nickname)
        accounts.state.value.account!!
    } }
    suspend fun sendRegistrationCode(value: String, phone: Boolean): AppResult<Unit> = appResult<Unit> {
        api.request("POST", if (phone) "auth/phone/code" else "auth/email/code", body = buildJsonObject { put(if (phone) "phone" else "email", value) })
    }
    suspend fun resetServer(server: String) = sessionMutex.withLock {
        accounts.clear(rememberSaved = true); persistAccount(0, ""); accounts.server = server
    }
    suspend fun logout() = sessionMutex.withLock {
        try { api.request("POST", "auth/logout", authenticated = true) }
        finally { accounts.clear(); persistAccount(0, "") }
    }
}

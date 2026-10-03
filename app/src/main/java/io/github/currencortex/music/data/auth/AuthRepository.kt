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
data class Account(val id: Long, val nickname: String)
data class AccountState(val account: Account? = null, val loading: Boolean = false, val offline: Boolean = false, val error: String? = null)
class AccountRepository(private val store: TokenStore, private val scope: CoroutineScope) {
    private val storageMutex = Mutex()
    @Volatile var token: String? = null
        private set
    @Volatile var server: String = ""
    val state = MutableStateFlow(AccountState())
    suspend fun restoreToken() = storageMutex.withLock { withContext(Dispatchers.IO) { token = store.read() } }
    suspend fun save(token: String, user: UserDto) {
        storageMutex.withLock { withContext(Dispatchers.IO) { store.write(token); this@AccountRepository.token = token } }
        state.value = AccountState(Account(user.id, user.nickname.ifBlank { user.username }))
    }
    suspend fun clear() {
        storageMutex.withLock { token = null; withContext(Dispatchers.IO) { store.clear() } }
        state.value = AccountState()
    }
    fun expired(request: RequestSession) {
        if (request.server == server && request.token != null && request.token == token) {
            token = null
            state.value = AccountState(error = ErrorKind.Unauthorized.message)
            scope.launch(Dispatchers.IO) { storageMutex.withLock { if (token == null) store.clear() } }
        }
    }
}
class AuthRepository(private val api: ApiClient, val accounts: AccountRepository,
                     private val device: String, private val persistAccount: suspend (Long, String) -> Unit) {
    private val sessionMutex = Mutex()
    suspend fun restore(server: String): AppResult<UserDto?> = sessionMutex.withLock {
        accounts.server = server
        appResult {
            accounts.restoreToken()
            if (accounts.token == null) return@appResult null
            accounts.state.value = AccountState(loading = true)
            when (val result = appResult { api.get<UserDto>("auth/me", authenticated = true) }) {
                is AppResult.Success -> {
                    val user = result.value
                    accounts.state.value = AccountState(Account(user.id, user.nickname.ifBlank { user.username }))
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
    suspend fun resetServer(server: String) = sessionMutex.withLock {
        accounts.clear(); persistAccount(0, ""); accounts.server = server
    }
    suspend fun logout() = sessionMutex.withLock {
        try { api.request("POST", "auth/logout", authenticated = true) }
        finally { accounts.clear(); persistAccount(0, "") }
    }
}

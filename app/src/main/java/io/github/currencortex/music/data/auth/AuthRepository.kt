package io.github.currencortex.music.data.auth

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.security.TokenStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalSerializationApi::class)
@Serializable data class UserDto(val id: Long = 0, val username: String = "", val nickname: String = "", val avatar: String = "",
    @JsonNames("is_admin") @Serializable(with = ApiBooleanSerializer::class) val isAdmin: Boolean = false,
    @JsonNames("is_super") @Serializable(with = ApiBooleanSerializer::class) val isSuper: Boolean = false,
    val title: String = "", val level: Int? = null, val points: Long? = null,
    @JsonNames("is_owner") @Serializable(with = ApiBooleanSerializer::class) val isOwner: Boolean = false)
@Serializable class LoginDto(val token: String, val user: UserDto)
class RegistrationInput(val username: String, val password: String, val nickname: String,
    val contact: String, val code: String, val phone: Boolean)
data class Account(val id: Long, val nickname: String, val username: String = "", val avatar: String = "",
    val isAdmin: Boolean = false, val isSuper: Boolean = false, val title: String = "", val level: Int? = null, val points: Long? = null,
    val isOwner: Boolean = false)
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
            state.value = AccountState(Account(user.id, user.nickname.ifBlank { user.username }, user.username, user.avatar, user.isAdmin, user.isSuper, user.title, user.level, user.points, user.isOwner))
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
                     private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
                     private val persistAccount: suspend (Long, String) -> Unit) {
    private val sessionMutex = Mutex()
    @Volatile private var loginChallenge: LoginChallenge? = null
    suspend fun validateRestored() = sessionMutex.withLock {
        val expected = RequestSession(accounts.server, accounts.token)
        val credential = expected.token ?: return@withLock
        val result = withTimeoutOrNull(8_000) { appResult { api.get<UserDto>("auth/me", authenticated = true) } }
            ?: AppResult.Failure(ErrorKind.Timeout)
        if (expected != RequestSession(accounts.server, accounts.token)) return@withLock
        when (result) {
            is AppResult.Success -> { accounts.save(credential, result.value, expected); persistAccount(result.value.id, result.value.nickname) }
            is AppResult.Failure -> accounts.state.value = accounts.state.value.copy(loading = false,
                offline = result.kind != ErrorKind.Unauthorized, error = result.kind.message)
        }
    }
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
    suspend fun login(username: String, password: String): AppResult<LoginOutcome> = sessionMutex.withLock { appResult {
        loginChallenge = null
        val owner = RequestSession(accounts.server, accounts.token)
        val revision = accounts.sessionRevision.value
        val response = api.request("POST", "auth/login", body = buildJsonObject {
            put("username", username); put("password", password); put("device", device); put("platform", "app")
        }, expectedSession = RequestSession(owner.server, null), retryConnection = false).jsonObject
        loginOutcome(response, owner, revision)
    } }
    suspend fun sendLoginEmailCode(email: String): AppResult<Unit> = appResult {
        require(email.isNotBlank())
        api.request("POST", "auth/login/email/code", body = buildJsonObject { put("email", email.trim()) },
            expectedSession = api.publicSession(), retryConnection = false)
        Unit
    }
    suspend fun loginWithEmail(email: String, code: String): AppResult<LoginOutcome> = sessionMutex.withLock { appResult {
        loginChallenge = null
        require(email.isNotBlank() && code.isNotBlank())
        val owner = RequestSession(accounts.server, accounts.token)
        val revision = accounts.sessionRevision.value
        val response = api.request("POST", "auth/login/email", body = buildJsonObject {
            put("email", email.trim()); put("code", code.trim()); put("device", device); put("platform", "app")
        }, expectedSession = RequestSession(owner.server, null), retryConnection = false).jsonObject
        loginOutcome(response, owner, revision)
    } }
    private suspend fun loginOutcome(response: JsonObject, owner: RequestSession, revision: Long): LoginOutcome {
        checkLoginOwner(owner, revision)
        val totp = response["requires2FA"]?.jsonPrimitive?.booleanOrNull == true
        val question = response["requiresSecurityQuestion"]?.jsonPrimitive?.booleanOrNull == true
        require(!(totp && question))
        return if (totp || question) {
            val token = response["challengeToken"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val seconds = response["expiresIn"]?.jsonPrimitive?.longOrNull ?: 300L
            val prompt = if (question) response["securityQuestion"]?.jsonPrimitive?.contentOrNull else null
            require(!question || (!prompt.isNullOrBlank() && prompt.length <= 100))
            require(token.isNotBlank() && token.length <= 8192 && seconds > 0)
            val challenge = LoginChallenge(token, owner, revision, clock() + seconds.coerceAtMost(300) * 1000, clock, prompt)
            loginChallenge = challenge
            LoginOutcome.VerificationRequired(challenge)
        } else LoginOutcome.SignedIn(acceptLogin(response, owner, revision))
    }
    suspend fun loginWithPasskey(origin: String, credential: suspend (String) -> String): AppResult<LoginOutcome> =
        sessionMutex.withLock { appResult {
            loginChallenge = null
            val owner = RequestSession(accounts.server, accounts.token)
            val revision = accounts.sessionRevision.value
            val expected = RequestSession(owner.server, null)
            val rpId = NativePasskeyRoute.rpId(owner.server)
            val response = try {
                api.request("POST", "auth/passkeys/login/options", body = buildJsonObject { put("platform", "app") },
                    expectedSession = expected, retryConnection = false, passkeyOrigin = origin).jsonObject
            } catch (e: ApiException) {
                if (e.status == 403 && e.userMessage?.contains("HTTPS 网页入口") == true)
                    throw ApiException(ErrorKind.Forbidden, 403, "当前服务器尚未支持 Android 通行密钥登录，请使用密码或邮箱验证码")
                throw e
            }
            checkLoginOwner(owner, revision)
            val token = response["challengeToken"]!!.jsonPrimitive.content
            val options = response["publicKey"]!!.jsonObject
            require(token.isNotBlank() && token.length <= 8192)
            require(options["rpId"]?.jsonPrimitive?.content == rpId)
            require(options["challenge"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true)
            require(options["userVerification"]?.jsonPrimitive?.content == "required")
            val seconds = response["expiresIn"]?.jsonPrimitive?.longOrNull ?: 300L
            require(seconds > 0)
            val deadline = clock() + seconds.coerceAtMost(300) * 1000
            val assertion = credential(options.toString())
            checkLoginOwner(owner, revision)
            if (clock() >= deadline) throw ApiException(ErrorKind.Unauthorized, userMessage = "通行密钥验证已过期，请重新登录")
            require(assertion.length <= 131072)
            val result = api.request("POST", "auth/passkeys/login/verify", body = buildJsonObject {
                put("challengeToken", token); put("credential", ApiJson.parseToJsonElement(assertion).jsonObject)
                put("device", device); put("platform", "app")
            }, expectedSession = expected, retryConnection = false, passkeyOrigin = origin).jsonObject
            checkLoginOwner(owner, revision)
            LoginOutcome.SignedIn(acceptLogin(result, owner, revision))
        } }
    suspend fun verifyLogin(challenge: LoginChallenge, code: String): AppResult<LoginOutcome.SignedIn> = sessionMutex.withLock { appResult {
        if (loginChallenge !== challenge || challenge.remainingSeconds <= 0)
            throw ApiException(ErrorKind.Unauthorized, userMessage = "登录验证已失效，请重新登录")
        checkLoginOwner(challenge.owner, challenge.revision)
        require(code.isNotBlank() && code.length <= 128)
        val question = challenge.securityQuestion != null
        val response = api.request("POST", if (question) "auth/security-question/login" else "auth/2fa/login", body = buildJsonObject {
            put("challengeToken", challenge.token); put(if (question) "answer" else "code", code.trim())
        }, expectedSession = RequestSession(challenge.owner.server, null), retryConnection = false).jsonObject
        if (loginChallenge !== challenge || challenge.remainingSeconds <= 0)
            throw ApiException(ErrorKind.Unauthorized, userMessage = "登录验证已失效，请重新登录")
        val account = acceptLogin(response, challenge.owner, challenge.revision)
        loginChallenge = null
        LoginOutcome.SignedIn(account)
    } }
    fun discardLoginChallenge(challenge: LoginChallenge) {
        if (loginChallenge === challenge) loginChallenge = null
    }
    private fun checkLoginOwner(owner: RequestSession, revision: Long) {
        if (owner != RequestSession(accounts.server, accounts.token) || revision != accounts.sessionRevision.value)
            throw ApiException(ErrorKind.Unauthorized, userMessage = "账号或服务器已改变，请重新登录")
    }
    private suspend fun acceptLogin(response: JsonObject, owner: RequestSession, revision: Long): Account {
        checkLoginOwner(owner, revision)
        val dto = ApiJson.decodeFromJsonElement<LoginDto>(response)
        require(dto.token.isNotBlank() && dto.user.id > 0)
        accounts.save(dto.token, dto.user, owner)
        persistAccount(dto.user.id, dto.user.nickname)
        return requireNotNull(accounts.state.value.account)
    }
    suspend fun switchAccount(key: String): AppResult<UserDto> = sessionMutex.withLock { appResult {
        loginChallenge = null
        val selected = accounts.activate(key)
        persistServer(selected.server); persistAccount(selected.id, selected.nickname)
        when (val result = appResult { api.get<UserDto>("auth/me", authenticated = true) }) {
            is AppResult.Success -> { accounts.save(accounts.token!!, result.value); persistAccount(result.value.id, result.value.nickname); result.value }
            is AppResult.Failure -> { accounts.state.value = accounts.state.value.copy(loading = false,
                offline = result.kind != ErrorKind.Unauthorized, error = result.kind.message); throw ApiException(result.kind) }
        }
    } }
    suspend fun updateProgress(expected: RequestSession, level: Int, points: Long) = sessionMutex.withLock {
        if (expected != RequestSession(accounts.server, accounts.token)) throw ApiException(ErrorKind.Unauthorized)
        val user = accounts.state.value.account ?: throw ApiException(ErrorKind.Unauthorized)
        if (user.level != level || user.points != points) accounts.save(expected.token ?: throw ApiException(ErrorKind.Unauthorized),
            UserDto(user.id, user.username, user.nickname, user.avatar, user.isAdmin, user.isSuper, user.title, level, points, user.isOwner), expected)
    }
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
    suspend fun sendPasswordResetCode(email: String): AppResult<Unit> = appResult {
        require(email.isNotBlank())
        api.request("POST", "auth/password/code", body = buildJsonObject { put("email", email.trim()) }, retryConnection = false)
        Unit
    }
    suspend fun resetPassword(email: String, code: String, newPassword: String): AppResult<Unit> = appResult {
        require(email.isNotBlank() && code.isNotBlank() && newPassword.length in 6..64)
        // Public recovery never borrows or replaces a different saved account's session.
        api.request("POST", "auth/password/reset", body = buildJsonObject {
            put("email", email.trim()); put("code", code.trim()); put("newPassword", newPassword)
            put("device", device); put("platform", "app")
        }, retryConnection = false)
        Unit
    }
    suspend fun resetServer(server: String) = sessionMutex.withLock {
        loginChallenge = null
        accounts.clear(rememberSaved = true); persistAccount(0, ""); accounts.server = server
    }
    suspend fun logout() = sessionMutex.withLock {
        loginChallenge = null
        try { api.request("POST", "auth/logout", authenticated = true) }
        finally { accounts.clear(); persistAccount(0, "") }
    }
}

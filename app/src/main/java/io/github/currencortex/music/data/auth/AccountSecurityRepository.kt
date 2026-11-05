package io.github.currencortex.music.data.auth

import io.github.currencortex.music.core.network.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable data class LoginProtectionStatus(val enabled: Boolean = false,
    val recoveryCodesRemaining: Int = 0, val securityQuestionEnabled: Boolean = false,
    val securityQuestion: String = "")

/** These objects contain secrets and deliberately have no data-class toString or saved-state representation. */
class SecurityOwner internal constructor(internal val session: RequestSession, internal val revision: Long)
class AuthenticatorEnrollment internal constructor(val secret: String, val uri: String,
    internal val owner: SecurityOwner, private val deadline: Long, private val clock: () -> Long) {
    val remainingSeconds: Long get() = ((deadline - clock()).coerceAtLeast(0) + 999) / 1000
}
class RecoveryCodes(val codes: List<String>)

class AccountSecurityRepository(private val api: ApiClient, private val accounts: AccountRepository,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    fun owner() = SecurityOwner(RequestSession(accounts.server, accounts.token), accounts.sessionRevision.value)
    fun isCurrent(owner: SecurityOwner) = owner.revision == accounts.sessionRevision.value &&
        owner.session == RequestSession(accounts.server, accounts.token) && !owner.session.token.isNullOrBlank()
    private fun checkOwner(owner: SecurityOwner) {
        if (!isCurrent(owner)) throw ApiException(ErrorKind.Unauthorized, userMessage = "账号或服务器已改变，请重新打开安全设置")
    }
    private suspend fun request(owner: SecurityOwner, method: String, path: String, body: JsonObject? = null): JsonElement {
        checkOwner(owner)
        val response = api.request(method, path, body = body, authenticated = true,
            expectedSession = owner.session, retryConnection = false)
        checkOwner(owner)
        return response
    }
    suspend fun status(owner: SecurityOwner): AppResult<LoginProtectionStatus> = appResult {
        ApiJson.decodeFromJsonElement(request(owner, "GET", "auth/2fa/status"))
    }
    suspend fun setup(owner: SecurityOwner, password: String): AppResult<AuthenticatorEnrollment> = appResult {
        require(password.isNotEmpty())
        val response = request(owner, "POST", "auth/2fa/setup", buildJsonObject { put("password", password) }).jsonObject
        val secret = response["secret"]!!.jsonPrimitive.content
        val uri = response["uri"]!!.jsonPrimitive.content
        val seconds = response["expiresIn"]!!.jsonPrimitive.long
        require(secret.matches(Regex("[A-Z2-7]{16,128}")) && uri.startsWith("otpauth://totp/") && uri.length <= 4096 && seconds > 0)
        AuthenticatorEnrollment(secret, uri, owner, clock() + seconds.coerceAtMost(600) * 1000, clock)
    }
    suspend fun enable(enrollment: AuthenticatorEnrollment, code: String): AppResult<RecoveryCodes> = appResult {
        if (enrollment.remainingSeconds <= 0) throw ApiException(ErrorKind.Unauthorized, userMessage = "绑定已过期，请重新开始")
        require(code.matches(Regex("[0-9]{6}")))
        val response = request(enrollment.owner, "POST", "auth/2fa/enable", buildJsonObject { put("code", code) }).jsonObject
        val codes = response["recoveryCodes"]!!.jsonArray.map { it.jsonPrimitive.content }
        require(codes.isNotEmpty() && codes.size <= 20 && codes.all { it.isNotBlank() && it.length <= 128 })
        RecoveryCodes(codes)
    }
    suspend fun disable(owner: SecurityOwner, password: String, code: String): AppResult<Unit> = appResult {
        require(password.isNotEmpty() && code.isNotBlank() && code.length <= 128)
        request(owner, "POST", "auth/2fa/disable", buildJsonObject { put("password", password); put("code", code.trim()) })
        Unit
    }
    suspend fun setQuestion(owner: SecurityOwner, password: String, question: String, answer: String): AppResult<Unit> = appResult {
        require(password.isNotEmpty() && question.trim().length in 4..100 && answer.trim().length in 2..128)
        request(owner, "POST", "auth/security-question", buildJsonObject {
            put("password", password); put("question", question.trim()); put("answer", answer)
        })
        Unit
    }
    suspend fun disableQuestion(owner: SecurityOwner, password: String, answer: String): AppResult<Unit> = appResult {
        require(password.isNotEmpty() && answer.isNotBlank() && answer.length <= 128)
        request(owner, "DELETE", "auth/security-question", buildJsonObject { put("password", password); put("answer", answer) })
        Unit
    }
}

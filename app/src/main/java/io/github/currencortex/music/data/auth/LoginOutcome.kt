package io.github.currencortex.music.data.auth

import io.github.currencortex.music.core.network.RequestSession

sealed interface LoginOutcome {
    class SignedIn(val account: Account) : LoginOutcome
    class VerificationRequired(val challenge: LoginChallenge) : LoginOutcome
}

/** Opaque, memory-only login proof. Never place it in saved state, messages or logs. */
class LoginChallenge internal constructor(internal val token: String, internal val owner: RequestSession,
    internal val revision: Long, private val deadline: Long, private val clock: () -> Long,
    val securityQuestion: String? = null) {
    val remainingSeconds: Long get() = ((deadline - clock()).coerceAtLeast(0) + 999) / 1000
}

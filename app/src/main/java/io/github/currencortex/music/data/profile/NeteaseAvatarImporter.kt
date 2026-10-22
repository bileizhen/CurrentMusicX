package io.github.currencortex.music.data.profile

import io.github.currencortex.music.core.network.RequestSession
import io.github.currencortex.music.data.auth.AccountRepository
import io.github.currencortex.music.data.auth.AuthRepository
import io.github.currencortex.music.data.binding.BindingRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Copies the login avatar into the project account only while its own avatar is still unset. */
class NeteaseAvatarImporter(
    private val accounts: AccountRepository,
    private val auth: AuthRepository,
    private val profiles: ProfileRepository,
    private val binding: BindingRepository,
    private val session: () -> RequestSession,
    private val download: suspend (String) -> ByteArray,
) {
    private val mutex = Mutex()

    suspend fun importAfterLogin(expected: RequestSession) = mutex.withLock {
        val account = accounts.state.value.account ?: return@withLock
        if (expected.token.isNullOrBlank() || account.avatar.isNotBlank()) return@withLock
        val owner = session()
        if (owner.server != expected.server || owner.token != expected.token) return@withLock
        val revision = accounts.sessionRevision.value
        fun allowed() = session() == owner && accounts.sessionRevision.value == revision &&
            accounts.state.value.account?.let { it.id == account.id && it.avatar.isBlank() } == true

        val bound = binding.status().takeIf { it.bound && !it.stale }?.profile ?: return@withLock
        if (bound.avatar.isBlank() || !allowed()) return@withLock
        // Fetch the server's current profile before downloading; the local account can be stale.
        val current = profiles.me()
        if (current.id != account.id || current.avatar.isNotBlank() || !allowed()) return@withLock
        val bytes = download(bound.avatar)
        if (!allowed()) return@withLock
        val user = profiles.avatarIfDefault(bytes, expected, account.id, ::allowed) ?: return@withLock
        if (allowed()) auth.updateAccount(profiles.account(user), expected)
    }
}

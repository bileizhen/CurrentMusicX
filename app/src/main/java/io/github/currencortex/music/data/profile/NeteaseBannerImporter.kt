package io.github.currencortex.music.data.profile

import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.core.network.RequestSession
import io.github.currencortex.music.core.network.appResult
import io.github.currencortex.music.data.auth.AccountRepository
import io.github.currencortex.music.data.binding.BindingRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Copies NetEase's profile background cover into the project account only while its banner is unset. */
class NeteaseBannerImporter(
    private val accounts: AccountRepository,
    private val profiles: ProfileRepository,
    private val binding: BindingRepository,
    private val session: () -> RequestSession,
    private val download: suspend (String) -> ByteArray,
    private val fetchBackgroundUrl: suspend (Long) -> String = { uid -> binding.userBackgroundUrl(uid) },
) {
    private val mutex = Mutex()

    suspend fun importAfterLogin(expected: RequestSession) = mutex.withLock {
        val account = accounts.state.value.account ?: return@withLock
        if (expected.token.isNullOrBlank()) return@withLock
        val owner = session()
        if (owner.server != expected.server || owner.token != expected.token) return@withLock
        val revision = accounts.sessionRevision.value
        fun allowed() = session() == owner && accounts.sessionRevision.value == revision &&
            accounts.state.value.account?.id == account.id

        val bound = binding.status().takeIf { it.bound && !it.stale }?.profile ?: return@withLock
        if (!allowed()) return@withLock
        // Fetch the server's current profile first; custom banners are never overwritten.
        val current = profiles.me()
        if (current.id != account.id || current.bannerUrl.isNotBlank() || !allowed()) return@withLock
        val backgroundUrl = bound.backgroundUrl.ifBlank {
            if (bound.uid > 0) (appResult { fetchBackgroundUrl(bound.uid) } as? AppResult.Success)?.value.orEmpty()
            else ""
        }.trim()
        if (backgroundUrl.isBlank() || !allowed()) return@withLock
        val bytes = download(backgroundUrl)
        if (!allowed()) return@withLock
        profiles.bannerIfDefault(bytes, expected, account.id, ::allowed)
    }
}

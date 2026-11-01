package io.github.currencortex.music.data.binding

import io.github.currencortex.music.core.network.RequestSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URI
import java.util.concurrent.atomic.AtomicLong

class BindingCredential(val uid: Long, val cookie: String) {
    override fun toString() = "BindingCredential([redacted])"
}
data class ProjectBindingSyncState(val syncing: Boolean = false, val synced: Boolean = false, val error: String? = null)

/** Shares the local login only with the captured project-account owner. */
class ProjectBindingSync(
    private val session: () -> RequestSession,
    private val credential: suspend (RequestSession) -> BindingCredential,
    private val upload: suspend (BindingCredential, RequestSession) -> Unit,
) {
    val state = MutableStateFlow(ProjectBindingSyncState())
    private val mutex = Mutex()
    private val generation = AtomicLong()
    @Volatile private var owner: RequestSession? = null
    fun reset() {
        if (owner != session()) {
            generation.incrementAndGet(); owner = null; state.value = ProjectBindingSyncState()
        }
    }
    suspend fun syncAutomatically(expected: RequestSession, retryDelays: List<Long> = listOf(0L, 5_000L, 30_000L, 120_000L)) {
        for (wait in retryDelays) {
            if (expected != session()) return
            if (wait > 0) delay(wait)
            sync(expected)
            if (expected != session() || state.value.synced) return
        }
    }
    suspend fun sync(expected: RequestSession = session()) = mutex.withLock {
        if (expected != session() || (owner == expected && state.value.synced)) return@withLock
        val version = generation.incrementAndGet()
        owner = expected
        fun current() = version == generation.get() && expected == session()
        state.value = ProjectBindingSyncState(syncing = true)
        try {
            check(!expected.token.isNullOrBlank())
            // Never transmit a NetEase login over a clear-text or credential-bearing URL.
            val uri = URI(expected.server)
            check(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.rawUserInfo == null)
            val login = credential(expected)
            check(login.uid > 0 && login.cookie.isNotBlank())
            if (!current()) return@withLock
            upload(login, expected)
            if (current()) state.value = ProjectBindingSyncState(synced = true)
        } catch (cancelled: CancellationException) {
            if (current()) state.value = ProjectBindingSyncState()
            throw cancelled
        } catch (_: Exception) {
            if (current()) state.value = ProjectBindingSyncState(error =
                "本机登录已保留，绑定尚未同步。请确认 CurrentMusic 服务器使用 HTTPS 后重试。")
        }
    }
}

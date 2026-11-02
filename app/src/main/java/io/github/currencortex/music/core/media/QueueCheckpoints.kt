package io.github.currencortex.music.core.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel

/** Keep the latest pending snapshot while the single database writer is busy. */
internal class QueueCheckpoints {
    private val pending = Channel<QueueSnapshot>(Channel.CONFLATED)

    fun offer(snapshot: QueueSnapshot) {
        pending.trySend(snapshot)
    }

    suspend fun writeTo(save: suspend (QueueSnapshot) -> Unit, onFailure: (Exception) -> Unit) {
        var lastSaved: QueueSnapshot? = null
        for (snapshot in pending) {
            if (snapshot == lastSaved) continue
            try {
                save(snapshot)
                lastSaved = snapshot
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onFailure(error)
            }
        }
    }
}

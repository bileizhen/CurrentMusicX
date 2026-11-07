package io.github.currencortex.music.core.network

import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/** Read and close the response before resuming, so cancellation also interrupts a slow body. */
internal suspend fun <T> Call.readResponse(
    connectionFailure: (IOException) -> IOException = { it },
    read: (Response) -> T,
): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWith(Result.failure(connectionFailure(e)))
        }

        override fun onResponse(call: Call, response: Response) {
            if (!continuation.isActive) { response.close(); return }
            val result = runCatching { response.use(read) }
            if (continuation.isActive) continuation.resumeWith(result)
        }
    })
}

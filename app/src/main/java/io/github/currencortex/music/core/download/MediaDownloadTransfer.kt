package io.github.currencortex.music.core.download

import kotlinx.coroutines.*
import okhttp3.*
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Bounded streaming transfer; cancellation also aborts a blocked network read. */
internal class MediaDownloadTransfer(private val http: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).build()) {
    suspend fun fetch(url: String, target: File, limit: Long, progress: suspend (Long, Long) -> Unit) {
        val call = http.newCall(Request.Builder().url(url).header("Accept-Encoding", "identity").build())
        val response = suspendCancellableCoroutine<Response> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (!continuation.isCancelled) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) { continuation.resume(response, onCancellation = { _, value, _ -> value.close() }) }
            })
        }
        // Cancel blocking body reads promptly, including while the remote host stops sending.
        val watcher = CoroutineScope(currentCoroutineContext()).launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try { response.use {
            check(it.isSuccessful) { "下载服务器返回 ${it.code}，请重试" }
            val body = it.body ?: throw IOException("下载内容为空")
            val total = body.contentLength()
            check(total <= limit) { "下载内容超过支持大小" }
            var received = 0L; var lastProgress = 0L
            body.byteStream().use { input -> target.outputStream().use { output ->
                val buffer = ByteArray(65536)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    received += count; check(received <= limit) { "下载内容超过支持大小" }
                    output.write(buffer, 0, count)
                    val now = System.nanoTime()
                    if (now - lastProgress > 300_000_000) { progress(received, total); lastProgress = now }
                }
            } }
            check(received > 0 && (total < 0 || received == total)) { "文件下载不完整，请重试" }
            progress(received, total)
        } } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            throw error
        } finally { watcher.cancel(); response.close() }
    }

}

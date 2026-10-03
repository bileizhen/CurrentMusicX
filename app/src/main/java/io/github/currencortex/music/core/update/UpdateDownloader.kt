// Adapted from XBlocker data/AppUpdates.kt (MIT, Copyright 2026 XBlocker contributors).
package io.github.currencortex.music.core.update

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

fun interface UpdateDownload {
    suspend fun download(release: AppRelease, source: UpdateSource, onProgress: (Long, Long) -> Unit): File
}

/** Clean client, HTTPS-only redirects, bounded files and official GitHub asset SHA-256. */
class UpdateDownloader(client: OkHttpClient, private val directory: File,
    private val urlFor: (AppRelease, UpdateSource) -> String = { release, source -> source.url(release) },
) : UpdateDownload {
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    @OptIn(InternalCoroutinesApi::class)
    override suspend fun download(release: AppRelease, source: UpdateSource, onProgress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        require(release.size in 1..MAX_APK_BYTES && Regex("[a-f0-9]{64}").matches(release.sha256)) { "安装包校验信息无效" }
        check(directory.isDirectory || directory.mkdirs()) { "无法创建更新目录" }
        // The digest, rather than the server filename, is the safe cache identity.
        val target = File(directory, "update-${release.sha256}.apk")
        if (target.isFile && matches(target, release)) {
            onProgress(release.size, release.size)
            return@withContext target
        }
        target.delete()
        val temporary = File(directory, "${target.name}.part")
        temporary.delete()
        var url = urlFor(release, source).toHttpUrl()
        try {
            for (redirect in 0..MAX_REDIRECTS) {
                check(url.scheme == "https" && url.username.isBlank() && url.password.isBlank()) { "下载源必须使用 HTTPS" }
                val call = http.newCall(Request.Builder().url(url)
                    .header("Accept", "application/vnd.android.package-archive, application/octet-stream")
                    .header("User-Agent", "CurrentMusic updater").build())
                val cancel = currentCoroutineContext().job.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { if (it != null) call.cancel() }
                try {
                    call.execute().use { response ->
                        if (response.code in listOf(301, 302, 303, 307, 308)) {
                            check(redirect < MAX_REDIRECTS) { "下载重定向次数过多" }
                            url = response.header("Location")?.let(url::resolve) ?: error("下载重定向地址无效")
                        } else {
                            check(response.isSuccessful) { "下载失败（HTTP ${response.code}）" }
                            val body = response.body ?: error("下载内容为空")
                            check(body.contentLength() <= 0 || body.contentLength() == release.size) { "安装包大小与发布信息不符" }
                            var received = 0L
                            body.byteStream().use { input -> temporary.outputStream().buffered().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    received += count
                                    check(received <= release.size) { "安装包超过预期大小" }
                                    output.write(buffer, 0, count)
                                    onProgress(received, release.size)
                                }
                            } }
                            check(matches(temporary, release)) { "安装包不完整或 SHA-256 校验失败，请重试或切换下载源" }
                            check(temporary.renameTo(target)) { "无法保存更新文件" }
                            return@withContext target
                        }
                    }
                } finally { cancel.dispose() }
            }
            error("下载重定向次数过多")
        } catch (failure: Exception) {
            temporary.delete()
            currentCoroutineContext().ensureActive()
            throw failure
        }
    }

    companion object {
        const val MAX_APK_BYTES = 128L * 1024 * 1024
        private const val MAX_REDIRECTS = 8
        fun matches(file: File, release: AppRelease): Boolean = file.isFile && file.length() == release.size &&
            sha256(file) == release.sha256
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) { val read = input.read(buffer); if (read < 0) break; digest.update(buffer, 0, read) }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}


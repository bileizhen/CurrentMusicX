package io.github.currencortex.music.data.effects

import android.content.Context
import io.github.currencortex.music.core.update.UpdateSource
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal data class NeteaseEffectsInstallState(
    val loading: Boolean = false,
    val message: String? = null,
    val failed: Boolean = false,
    val progress: Int? = null,
    val removing: Boolean = false,
)

data class NeteaseEffectsAssetState(
    val available: Boolean = false,
    val loading: Boolean = false,
    val progress: Int? = null,
    val message: String? = null,
    val failed: Boolean = false,
)

class NeteaseEffectsAssetRepository(
    private val context: Context,
    client: OkHttpClient,
) {
    private val http = client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()
    private val installMutex = Mutex()
    private val mutable = MutableStateFlow(refreshState())
    val state: StateFlow<NeteaseEffectsAssetState> = mutable.asStateFlow()

    suspend fun remove(): Unit = withContext(Dispatchers.IO) {
        installMutex.withLock {
            val installed = directory(context)
            val leftovers = listOf(installed, File(context.filesDir, "${installed.name}.staging"),
                File(context.filesDir, "${installed.name}.retired"), File(context.cacheDir, "${installed.name}.zip.part"))
            try {
                val remaining = leftovers.filterNot(File::deleteRecursively)
                check(remaining.isEmpty()) { "无法删除全部网易云音效素材，请重试" }
            } finally {
                mutable.value = refreshState()
            }
        }
    }

    suspend fun install(source: UpdateSource, onProgress: (received: Long, total: Long) -> Unit = { _, _ -> }) =
        withContext(Dispatchers.IO) { installMutex.withLock {
            mutable.value = NeteaseEffectsAssetState(loading = true, message = "正在下载网易云音效素材包…")
            try {
                val archive = download(source, onProgress)
                try {
                    extract(archive)
                    mutable.value = NeteaseEffectsAssetState(available = true, message = "网易云音效素材已就绪")
                } finally {
                    archive.delete()
                }
            } catch (cancelled: CancellationException) {
                mutable.value = refreshState()
                throw cancelled
            } catch (failure: Exception) {
                mutable.value = NeteaseEffectsAssetState(
                    message = failure.message?.take(180) ?: "网易云音效素材包下载失败，请稍后重试",
                    failed = true,
                )
                throw failure
            }
        } }

    private fun refreshState(): NeteaseEffectsAssetState {
        val marker = File(directory(context), MARKER_NAME)
        val installed = marker.isFile && runCatching { marker.readText().trim() == PACKAGE_SHA256 }.getOrDefault(false) &&
            File(directory(context), "resource-manifest.json").isFile
        return NeteaseEffectsAssetState(available = installed)
    }

    private suspend fun download(source: UpdateSource, onProgress: (received: Long, total: Long) -> Unit): File {
        val archive = File(context.cacheDir, "netease-effects-ui-9.5.30.zip.part")
        val failures = mutableListOf<String>()
        archive.delete()
        for (candidate in source.repositoryFileFallbacks()) {
            try {
                val request = Request.Builder().url(candidate.repositoryFileUrl(PACKAGE_PATH)).build()
                http.newCall(request).execute().use { response ->
                    require(response.isSuccessful) { "HTTP ${response.code}" }
                    val body = response.body ?: throw IOException("下载内容为空")
                    val declared = body.contentLength()
                    require(declared <= 0 || declared == PACKAGE_SIZE) { "文件大小与清单不符" }
                    archive.outputStream().use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var received = 0L
                            var checkpoint = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                received += count
                                require(received <= PACKAGE_SIZE) { "文件超过预期大小" }
                                output.write(buffer, 0, count)
                                if (received - checkpoint >= 512 * 1024 || received == PACKAGE_SIZE) {
                                    checkpoint = received
                                    onProgress(received, PACKAGE_SIZE)
                                }
                            }
                        }
                    }
                }
                require(archive.length() == PACKAGE_SIZE) { "文件大小与清单不符" }
                require(sha256(archive) == PACKAGE_SHA256) { "文件 SHA-256 校验失败" }
                return archive
            } catch (failure: Exception) {
                archive.delete()
                currentCoroutineContext().ensureActive()
                if (failure is CancellationException) throw failure
                failures += "${candidate.label}: ${failure.message?.take(80)}"
            }
        }
        throw IOException("音效素材包下载失败。${failures.joinToString("；")}")
    }

    private fun extract(archive: File) {
        val target = directory(context)
        val staged = File(context.filesDir, "netease-effects-ui-9.5.30.staging")
        val retired = File(context.filesDir, "netease-effects-ui-9.5.30.retired")
        // A previous failed rollback may have left the only good copy in
        // retired. Recover it before preparing a fresh installation.
        if (retired.exists()) {
            if (!target.exists()) {
                check(retired.renameTo(target)) { "此前的音效素材备份尚未恢复：${retired.absolutePath}" }
            } else {
                check(refreshState().available) { "检测到待恢复的音效素材备份，请先检查安装目录" }
                check(retired.deleteRecursively()) { "无法清理旧音效素材备份" }
            }
        }
        check(staged.deleteRecursively()) { "无法清理旧音效素材临时目录" }
        check(staged.mkdirs()) { "无法创建音效素材目录" }
        var entries = 0
        var totalBytes = 0L
        ZipFile(archive).use { zip ->
            val stagedRoot = staged.canonicalFile
            zip.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
                require(++entries <= PACKAGE_ENTRIES) { "素材包文件数量无效" }
                require(entry.size in 1..MAX_ENTRY_SIZE) { "素材文件大小无效：${entry.name}" }
                totalBytes += entry.size
                require(totalBytes <= PACKAGE_UNCOMPRESSED_SIZE) { "素材包总大小无效" }
                val output = File(staged, entry.name)
                check(output.canonicalFile.path.startsWith(stagedRoot.path + File.separator)) { "素材包路径无效" }
                output.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    output.outputStream().use { stream -> input.copyTo(stream) }
                }
            }
        }
        require(entries == PACKAGE_ENTRIES && totalBytes == PACKAGE_UNCOMPRESSED_SIZE) { "素材包内容与清单不符" }
        require(File(staged, "resource-manifest.json").isFile) { "素材包缺少资源清单" }
        File(staged, MARKER_NAME).writeText(PACKAGE_SHA256)

        if (target.isDirectory) check(target.renameTo(retired)) { "无法替换音效素材目录" }
        try {
            check(staged.renameTo(target)) { "无法保存音效素材目录" }
        } catch (failure: Exception) {
            // Preserve the last working package if a rollback also fails.
            if (retired.isDirectory && !retired.renameTo(target)) {
                throw IOException("音效素材安装失败，原有素材备份保留在：${retired.absolutePath}", failure)
            }
            throw failure
        }
        retired.deleteRecursively()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    companion object {
        const val PACKAGE_SIZE = 67_003_812L
        const val PACKAGE_SHA256 = "552539df911bb45e0cb009d82a7a911d8b4ddcbf2b7506ff0b31cbc757c647f8"
        private const val PACKAGE_PATH = "/external/netease-original-effects/9.5.30/netease-effects-ui.zip"
        private const val PACKAGE_ENTRIES = 233
        private const val PACKAGE_UNCOMPRESSED_SIZE = 67_413_872L
        private const val MAX_ENTRY_SIZE = 32L * 1024 * 1024
        private const val MARKER_NAME = ".package-sha256"
        fun directory(context: Context) = File(context.filesDir, "netease-effects-ui-9.5.30")
    }
}

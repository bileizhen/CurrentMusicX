package io.github.currencortex.music.core.media

import android.content.Context
import android.net.Uri
import android.os.Build
import io.github.currencortex.music.core.update.UpdateSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Version-bound native libraries downloaded on demand from the project repository. */
internal object NeteaseEngineLibraries {
    const val DOWNLOAD_SIZE = 4_409_305L
    private const val ENGINE_HASH = "3f8e2eec659080cb8e90bbfc672d327a180e4e0b6c051818e38625c5f01fcbd1"
    private const val RUNTIME_HASH = "3e6d529147ffd66bd31fab85c8902b526abd1c06e43554ce64b97519e255e4b3"
    private const val SMART_HASH = "b4b927b0d494e9e726a7eac3ed0a81777eea3c0543a203879c6bf3898ec20e78"
    private const val SMART_ENTRY = "assets/audioeffect/preinstall/smartae/-110#6#81f3b6dfa9b6052500be01120678bbeb"
    private const val MAX_APK = 400L * 1024 * 1024
    private const val MAX_REMOTE_FILE = 16L * 1024 * 1024
    private const val REPOSITORY_FILES_PATH = "/external/netease-original-effects/9.5.30/arm64-v8a"

    private data class RemoteFile(val name: String, val sha256: String, val size: Long)

    private val moodLibraries = linkedMapOf(
        "libMNN.so" to "be7ad29454cfeecfb30399e32c3f600f963e69c7dd914f0001021fc78435249d",
        "libMNN_Express.so" to "e095c40a22d3dbb83894c74b3735db857e5137079109cab25797864921d4de57",
        "libCore_MNN_JNI.so" to "fec5363885343072c9aaf395a8560b217def8600404fa2ab9c940509215e0bef")
    private val remoteFiles = listOf(
        RemoteFile("libneaudioeffects.so", ENGINE_HASH, 1_446_928),
        RemoteFile("libMNN.so", moodLibraries.getValue("libMNN.so"), 2_167_032),
        RemoteFile("libMNN_Express.so", moodLibraries.getValue("libMNN_Express.so"), 743_640),
        RemoteFile("libCore_MNN_JNI.so", moodLibraries.getValue("libCore_MNN_JNI.so"), 51_232),
        RemoteFile("smartae.ncae", SMART_HASH, 473))

    private var context: Context? = null
    @Volatile private var loaded = false
    @Volatile private var moodLoaded = false
    private var attempted = false

    @Synchronized fun configure(value: Context) { context = value.applicationContext }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun directory(context: Context) = context.getDir("netease-original-9530", Context.MODE_PRIVATE)

    @Synchronized fun moodAvailable(): Boolean {
        val owner = context ?: return false
        return moodLibraries.all { (name, hash) -> verified(File(directory(owner), name), hash) }
    }

    @Synchronized fun moodDefinition(): ByteArray? = runCatching {
        val owner = context ?: return null
        owner.assets.open("netease-original/mood-codec.js").use { bounded(it, 1_048_576) }
    }.getOrNull()

    @Synchronized fun loadMoodLibraries() {
        if (moodLoaded) return
        check(available()) { "请先下载兼容的网易云原版引擎" }
        val owner = checkNotNull(context)
        check(moodAvailable()) { "请重新下载网易云音效引擎以补充原版情绪模型" }
        moodLibraries.keys.forEach { System.load(File(directory(owner), it).absolutePath) }
        moodLoaded = true
    }

    @Synchronized fun smartDefinition(): ByteArray? = runCatching {
        val owner = context ?: return null
        val file = File(directory(owner), "smartae.ncae")
        val bytes = file.takeIf { verified(it, SMART_HASH) }?.readBytes() ?: return null
        io.github.currencortex.music.data.effects.NcaeDecoder.decode(bytes).also { require(it.type == 1) }.bytes
    }.getOrNull()

    @Synchronized fun available(): Boolean {
        if (loaded) return context?.let { File(directory(it), "libneaudioeffects.so").isFile } == true
        if (attempted || "arm64-v8a" !in Build.SUPPORTED_ABIS.orEmpty()) return false
        attempted = true
        try {
            val owner = context ?: return false.also { attempted = false }
            val folder = directory(owner)
            val engine = File(folder, "libneaudioeffects.so")
            if (!verified(engine, ENGINE_HASH)) return false
            val runtime = runtime(owner) ?: return false
            val helper = owner.assets.open("audiofx/arm64-v8a/libcurrentmusic_netease_bridge.so").use { it.readBytes() }
            val bridge = File(folder, "bridge-${digest(helper)}.so")
            if (!bridge.isFile) writeReadOnly(bridge, helper)
            check(verified(bridge, digest(helper)))
            if (runtime.parentFile?.absolutePath == owner.applicationInfo.nativeLibraryDir) {
                System.loadLibrary("c++_shared")
            } else {
                System.load(runtime.absolutePath)
            }
            System.load(engine.absolutePath)
            System.load(bridge.absolutePath)
            loaded = true
        } catch (_: LinkageError) { loaded = false }
        catch (_: Exception) { loaded = false }
        return loaded
    }

    private fun runtime(context: Context): File? {
        val packaged = File(context.applicationInfo.nativeLibraryDir, "libc++_shared.so")
        if (packaged.isFile) return packaged
        return File(directory(context), "libc++_shared.so").takeIf { verified(it, RUNTIME_HASH) }
    }

    suspend fun remove(context: Context): Unit = withContext(Dispatchers.IO) {
        synchronized(this@NeteaseEngineLibraries) {
            attempted = false
            check(directory(context).deleteRecursively()) { "无法删除网易云音效引擎，请重试" }
        }
    }

    private fun verified(file: File, expected: String) = file.isFile && file.length() in 1..MAX_REMOTE_FILE &&
        digest(file.readBytes()) == expected

    private fun writeReadOnly(file: File, bytes: ByteArray) {
        val temporary = File(file.parentFile, "${file.name}.downloading")
        try {
            temporary.setWritable(true, true)
            temporary.writeBytes(bytes)
            check(temporary.setReadOnly()) { "无法保护音效库文件" }
            if (file.isFile) file.delete()
            if (!temporary.renameTo(file)) throw IOException("无法保存音效引擎")
        } finally { temporary.delete() }
    }

    suspend fun installFromRepository(context: Context, client: OkHttpClient,
        source: UpdateSource,
        onProgress: (received: Long, total: Long) -> Unit = { _, _ -> }) = withContext(Dispatchers.IO) {
        require("arm64-v8a" in Build.SUPPORTED_ABIS.orEmpty()) { "当前设备暂不支持此原版引擎" }
        val total = remoteFiles.sumOf { it.size }
        var received = 0L
        val downloaded = remoteFiles.associate { remote ->
            val bytes = download(client, source, remote)
            received += bytes.size.toLong()
            onProgress(received, total)
            remote.name to bytes
        }
        installBytes(context, downloaded)
    }

    private fun download(client: OkHttpClient, source: UpdateSource, remote: RemoteFile): ByteArray {
        val failures = mutableListOf<String>()
        for (candidate in source.repositoryFileFallbacks()) {
            try {
                val request = Request.Builder().url(candidate.repositoryFileUrl("$REPOSITORY_FILES_PATH/${remote.name}")).build()
                client.newCall(request).execute().use { response ->
                    require(response.isSuccessful) { "HTTP ${response.code}" }
                    val body = response.body ?: throw IOException("下载内容为空")
                    val bytes = body.byteStream().use { bounded(it, remote.size.toInt()) }
                    require(digest(bytes) == remote.sha256) { "文件 SHA-256 校验失败" }
                    return bytes
                }
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                failures += "${candidate.label}: ${failure.message?.take(80)}"
            }
        }
        throw IOException("音效文件下载失败：${remote.name}。${failures.joinToString("；")}")
    }

    suspend fun installFromApk(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        require("arm64-v8a" in Build.SUPPORTED_ABIS.orEmpty()) { "当前设备暂不支持此原版引擎" }
        val copy = File.createTempFile("netease-engine-", ".apk", context.cacheDir)
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("无法读取安装包")
            stream.use { input -> copy.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024); var total = 0L
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    total += count; require(total <= MAX_APK) { "安装包超过导入大小限制" }
                    output.write(buffer, 0, count)
                }
            } }
            installFile(context, copy)
        } finally { copy.delete() }
    }

    suspend fun installFromInstalled(context: Context) = withContext(Dispatchers.IO) {
        val source = try { context.packageManager.getApplicationInfo("com.netease.cloudmusic", 0).sourceDir }
        catch (_: Exception) { throw IllegalArgumentException("请先安装兼容的网易云音乐，或选择安装包导入") }
        installFile(context, File(source))
    }

    private fun readLibrary(archive: ZipFile, name: String): ByteArray {
        val entry = archive.getEntry("lib/arm64-v8a/$name") ?: throw IllegalArgumentException("安装包不含兼容的 arm64 音效引擎")
        require(entry.size in 1..MAX_REMOTE_FILE) { "音效引擎文件大小无效" }
        return archive.getInputStream(entry).use { bounded(it, MAX_REMOTE_FILE.toInt()) }
    }

    private fun bounded(input: InputStream, maximum: Int): ByteArray {
        val output = ByteArrayOutputStream(); val scratch = ByteArray(8192)
        while (true) {
            val count = input.read(scratch); if (count < 0) break
            require(output.size().toLong() + count <= maximum) { "音效文件过大" }
            output.write(scratch, 0, count)
        }
        return output.toByteArray()
    }

    @Synchronized private fun installBytes(context: Context, files: Map<String, ByteArray>) {
        configure(context)
        val folder = directory(context)
        val engine = File(folder, "libneaudioeffects.so")
        if (!loaded || !verified(engine, ENGINE_HASH)) writeReadOnly(engine, files.getValue(engine.name))
        moodLibraries.forEach { (name, hash) ->
            val library = File(folder, name)
            if (!moodLoaded || !verified(library, hash)) writeReadOnly(library, files.getValue(name))
        }
        writeReadOnly(File(folder, "smartae.ncae"), files.getValue("smartae.ncae"))
        attempted = false
        check(available()) { "当前系统未能加载原版引擎，已保留本机音效" }
    }

    @Synchronized internal fun installFile(context: Context, apk: File) {
        configure(context)
        var mood: Map<String, ByteArray> = emptyMap()
        val imported = try {
            ZipFile(apk).use { archive ->
                mood = moodLibraries.mapValues { (name, _) -> readLibrary(archive, name) }
                val smart = archive.getEntry(SMART_ENTRY)?.let { entry ->
                    archive.getInputStream(entry).use { bounded(it, 256 * 1024) }
                } ?: throw IllegalArgumentException("安装包缺少兼容的原版智能音效资源")
                mapOf(
                    "libneaudioeffects.so" to readLibrary(archive, "libneaudioeffects.so"),
                    "libc++_shared.so" to readLibrary(archive, "libc++_shared.so"),
                    "smartae.ncae" to smart)
            }
        } catch (failure: IllegalArgumentException) { throw failure }
        catch (_: Exception) { throw IOException("无法读取网易云安装包，请选择完整 APK 文件") }
        val engine = imported.getValue("libneaudioeffects.so")
        val runtime = imported.getValue("libc++_shared.so")
        val smart = imported.getValue("smartae.ncae")
        require(digest(engine) == ENGINE_HASH && digest(runtime) == RUNTIME_HASH && digest(smart) == SMART_HASH) {
            "目前兼容网易云音乐 9.5.30 的 arm64 引擎，此安装包版本尚未适配"
        }
        require(moodLibraries.all { (name, hash) -> digest(checkNotNull(mood[name])) == hash }) {
            "此安装包的原版情绪模型引擎尚未适配"
        }
        val folder = directory(context)
        val importedFiles = mood + mapOf("libneaudioeffects.so" to engine, "libc++_shared.so" to runtime, "smartae.ncae" to smart)
        if (!verified(File(folder, "libc++_shared.so"), RUNTIME_HASH)) {
            writeReadOnly(File(folder, "libc++_shared.so"), runtime)
        }
        installBytes(context, importedFiles)
    }
}

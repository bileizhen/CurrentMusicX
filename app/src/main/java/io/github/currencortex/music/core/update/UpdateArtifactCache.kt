package io.github.currencortex.music.core.update

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.FileTime

/** Reclaimable artifacts in an explicitly supplied app-private cacheDir/updates root only.
 * Never pass a user Downloads directory. No traversal beyond one UUID worker directory. */
class UpdateArtifactCache(root: File, private val maxCompletedFiles: Int = 2,
    private val maxCompletedBytes: Long = DEFAULT_MAX_BYTES,
    private val nowMillis: () -> Long = System::currentTimeMillis) {
    private val root = root.toPath().toAbsolutePath().normalize()
    init { require(maxCompletedFiles >= 1 && maxCompletedBytes > 0) }
    data class PruneResult(val removedFiles: Int, val completedFiles: Int, val completedBytes: Long)

    /** Nonblocking ownership: duplicate writers cannot erase another download's .part file. */
    fun lease(file: File): Lease = synchronized(lock) {
        val supplied = file.toPath().toAbsolutePath()
        val target = supplied.normalize()
        require(supplied == target) { "Update artifact path must not contain traversal" }
        require(target.fileName.toString().matches(APK) && managedParent(target.parent)) { "Not a managed update artifact" }
        checkSafe(target, allowMissing = true)
        val state = states.getOrPut(root) { State() }
        if (!state.active.add(target)) throw IOException("相同安装包正在下载，请稍后重试")
        Lease(target).also { pruneLocked() }
    }

    fun prune(): PruneResult = synchronized(lock) { pruneLocked() }

    inner class Lease internal constructor(private val target: Path) : Closeable {
        private var closed = false
        /** Recheck after mkdirs and before opening a file; links are never accepted. */
        fun ensureSafe() = synchronized(lock) {
            check(!closed)
            checkSafe(target, allowMissing = false)
        }
        /** Protect the last two returned targets across downloader/worker instances in this process.
         * This is cache retention, not an indefinite installer read lease. */
        fun completed() = synchronized(lock) {
            check(!closed)
            checkSafe(target, allowMissing = false)
            check(Files.isRegularFile(target, NOFOLLOW_LINKS)) { "更新文件不存在" }
            Files.setLastModifiedTime(target, FileTime.fromMillis(nowMillis()))
            val recent = states.getValue(root).returned
            recent.remove(target); recent.add(target)
            while (recent.size > 2) recent.remove(recent.first())
            pruneLocked()
        }
        override fun close() = synchronized(lock) {
            if (!closed) {
                closed = true
                states[root]?.active?.remove(target)
                pruneLocked()
            }
        }
    }

    private fun managedParent(parent: Path): Boolean = parent == root ||
        (parent.parent == root && parent.fileName.toString().matches(UUID_DIRECTORY))

    private fun directorySafe(path: Path, allowMissing: Boolean): Boolean =
        !Files.isSymbolicLink(path) && (Files.isDirectory(path, NOFOLLOW_LINKS) ||
            allowMissing && !Files.exists(path, NOFOLLOW_LINKS))

    private fun checkSafe(target: Path, allowMissing: Boolean) {
        check(directorySafe(root, allowMissing) && directorySafe(target.parent, allowMissing)) { "更新缓存目录无效" }
        for (path in listOf(target, target.resolveSibling("${target.fileName}.part"))) {
            check(!Files.exists(path, NOFOLLOW_LINKS) || Files.isRegularFile(path, NOFOLLOW_LINKS)) { "更新缓存文件无效" }
        }
    }

    private fun children(directory: Path): List<Path> = runCatching {
        Files.newDirectoryStream(directory).use { it.toList() }
    }.getOrDefault(emptyList())

    private fun pruneLocked(): PruneResult {
        if (!directorySafe(root, allowMissing = false)) return PruneResult(0, 0, 0)
        val state = states[root]
        val protected = state?.active.orEmpty() + state?.returned.orEmpty()
        val workerDirs = children(root).filter { it.fileName.toString().matches(UUID_DIRECTORY) && directorySafe(it, false) }
        val candidates = (children(root) + workerDirs.flatMap(::children)).filter {
            managedParent(it.parent) && it.fileName.toString().matches(ARTIFACT) && Files.isRegularFile(it, NOFOLLOW_LINKS)
        }
        fun canRemove(path: Path): Boolean {
            if (!directorySafe(root, false) || !directorySafe(path.parent, false) || !Files.isRegularFile(path, NOFOLLOW_LINKS)) return false
            val partial = path.fileName.toString().endsWith(".part")
            val target = if (partial) path.resolveSibling(path.fileName.toString().removeSuffix(".part")) else path
            return if (partial) target !in state?.active.orEmpty() else target !in protected
        }
        fun remove(path: Path): Boolean = canRemove(path) && runCatching { Files.deleteIfExists(path) }.getOrDefault(false)
        var removed = candidates.count { it.fileName.toString().endsWith(".part") && remove(it) }
        val completed = candidates.filter { it.fileName.toString().matches(APK) }.sortedWith(
            compareBy<Path> { runCatching { Files.getLastModifiedTime(it, NOFOLLOW_LINKS).toMillis() }.getOrDefault(Long.MAX_VALUE) }
                .thenBy { it.toString() })
        var count = completed.size
        var bytes = completed.sumOf { runCatching { Files.size(it) }.getOrDefault(0L) }
        for (path in completed) {
            if (count <= maxCompletedFiles && bytes <= maxCompletedBytes) break
            val size = runCatching { Files.size(path) }.getOrDefault(0L)
            if (remove(path)) { removed++; count--; bytes -= size }
        }
        // Delete only confirmed empty worker UUID directories, never the root or a foreign tree.
        for (directory in workerDirs) {
            if (state?.active.orEmpty().any { it.parent == directory }) continue
            if (directorySafe(root, false) && directorySafe(directory, false)) runCatching {
                val empty = Files.newDirectoryStream(directory).use { !it.iterator().hasNext() }
                if (empty) Files.delete(directory) // Fails safely if another file appeared; never recurse.
            }
        }
        return PruneResult(removed, count, bytes)
    }

    companion object {
        const val DEFAULT_MAX_BYTES = 1024L * 1024 * 1024
        private val APK = Regex("update-[a-f0-9]{64}\\.apk")
        private val ARTIFACT = Regex("update-[a-f0-9]{64}\\.apk(?:\\.part)?")
        private val UUID_DIRECTORY = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
        private class State {
            val active = mutableSetOf<Path>()
            val returned = linkedSetOf<Path>()
        }
        private val lock = Any()
        private val states = mutableMapOf<Path, State>()
    }
}

package io.github.currencortex.music.core.storage

import java.io.File
import java.io.OutputStream
import java.util.UUID

/** Picker payloads survive cache cleanup and recreation; only their small IDs enter saved state. */
internal class DocumentExportFiles(private val directory: File) {
    fun stage(write: (OutputStream) -> Unit): String {
        check(directory.mkdirs() || directory.isDirectory) { "无法准备导出文件" }
        // A picker abandoned with its task has no result callback to release its payload.
        val cutoff = System.currentTimeMillis() - 7 * 86_400_000L
        directory.listFiles()?.filter { it.isFile && NAME.matches(it.name) && it.lastModified() < cutoff }
            ?.forEach { it.delete() }
        val id = UUID.randomUUID().toString()
        val file = file(id)
        try {
            file.outputStream().buffered().use(write)
            return id
        } catch (failure: Throwable) {
            file.delete()
            throw failure
        }
    }

    fun file(id: String): File {
        require(ID.matches(id)) { "导出请求无效" }
        return File(directory, "$id.payload")
    }

    fun discard(id: String) { file(id).delete() }

    private companion object {
        val ID = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
        val NAME = Regex("[a-f0-9-]{36}\\.payload")
    }
}

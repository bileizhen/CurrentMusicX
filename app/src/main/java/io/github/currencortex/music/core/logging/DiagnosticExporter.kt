package io.github.currencortex.music.core.logging

import android.content.Context
import android.content.Intent
import android.content.ClipData
import androidx.core.content.FileProvider
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DiagnosticExporter {
    internal suspend fun feedbackZip(
        logger: AppLogger,
        maxZipBytes: Int,
        maxUncompressedBytes: Int,
        includeAppLogs: Boolean = true,
        audioDiagnostics: String? = null,
    ): ByteArray? =
        withContext(Dispatchers.IO) {
            val entries = diagnosticEntries(logger, includeAppLogs, audioDiagnostics)
            if (entries.sumOf { it.second.size } > maxUncompressedBytes) return@withContext null
            val bytes = ByteArrayOutputStream()
            ZipOutputStream(bytes).use { writeEntries(it, entries) }
            bytes.toByteArray().takeIf { it.size <= maxZipBytes }
        }

    suspend fun create(
        context: Context,
        logger: AppLogger,
        includeAppLogs: Boolean = true,
        audioDiagnostics: String? = null,
    ): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "diagnostics").apply { check(mkdirs() || isDirectory) }
        val now = System.currentTimeMillis()
        directory.listFiles()?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }?.forEachIndexed { index, file ->
                if (index >= 4 || now - file.lastModified() > 86_400_000L) file.delete()
            }
        File.createTempFile("diagnostics-", ".zip", directory).apply {
            ZipOutputStream(outputStream().buffered()).use { zip ->
                writeEntries(zip, diagnosticEntries(logger, includeAppLogs, audioDiagnostics))
            }
        }
    }

    private fun diagnosticEntries(logger: AppLogger, includeAppLogs: Boolean, audioDiagnostics: String?): List<Pair<String, ByteArray>> {
        val logs = if (includeAppLogs) logger.read() else "(application logs not included)"
        return listOf(
            "diagnostics.txt" to DiagnosticReport.create(logs, audioDiagnostics).toByteArray(Charsets.UTF_8),
            "logs.txt" to logs.toByteArray(Charsets.UTF_8),
        )
    }

    private fun writeEntries(zip: ZipOutputStream, entries: List<Pair<String, ByteArray>>) {
        for ((name, bytes) in entries) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }

    suspend fun save(context: Context, file: File, destination: Uri) = withContext(Dispatchers.IO) {
        val output = checkNotNull(context.contentResolver.openOutputStream(destination, "wt")) {
            "无法打开保存位置"
        }
        output.use { target -> file.inputStream().use { it.copyTo(target) } }
    }
    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("应用日志", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

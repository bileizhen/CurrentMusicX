package io.github.currencortex.music

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.core.update.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

/** Only synthetic bytes and a synthetic future version; never downloads or installs a real update. */
class UpdateBackgroundWorkerTest {
    private val release = AppRelease("99.0.0", "", "https://github.com/bileizhen/CurrentMusicX/releases", null, null, false, 10, "0".repeat(64))
    private fun worker(download: UpdateDownload): UpdateDownloadWorker {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(context.packageName.endsWith(".verification"))
        return TestListenableWorkerBuilder.from(context, UpdateDownloadWorker::class.java)
            .setInputData(workDataOf("release" to ApiJson.encodeToString(release), "source" to UpdateSource.GITHUB.name))
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    object : UpdateDownloadWorker(appContext, workerParameters) { override fun downloader() = download }
            }).build()
    }
    @Test fun verifiedResultIsPersistedWithoutRequestingInstallation(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val file = File.createTempFile("update-fixture", ".apk", context.cacheDir).apply { writeBytes(ByteArray(10)) }
        try {
            val result = worker(UpdateDownload { _, _, progress -> progress(4, 10); file }).doWork()
            assertTrue(result is ListenableWorker.Result.Success)
            assertEquals(file.absolutePath, (result as ListenableWorker.Result.Success).outputData.getString("file"))
            assertEquals(UpdateSource.GITHUB.name, result.outputData.getString("source"))
        } finally { file.delete() }
    }
    @Test fun exhaustedSourcesPersistFailureAndKeepInstallationDisabled(): Unit = runBlocking {
        var attempts = 0
        val result = worker(UpdateDownload { _, _, _ -> attempts++; throw IOException("Fixture offline") }).doWork()
        assertEquals(UpdateSource.GITHUB.fallbacks().size, attempts)
        assertTrue(result is ListenableWorker.Result.Failure)
        assertTrue((result as ListenableWorker.Result.Failure).outputData.getString("error")!!.contains("所有下载源均失败"))
        assertNull(result.outputData.getString("file"))
    }
}

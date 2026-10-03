package io.github.currencortex.music.core.update

import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateDownloaderTest {
    @get:Rule val temporary = TemporaryFolder()
    private val bytes = ByteArray(32 * 1024) { (it % 251).toByte() }
    private fun release(): AppRelease {
        val source = temporary.newFile().apply { writeBytes(bytes) }
        return AppRelease("1.2.0", "notes", "https://github.com/example/app/releases/tag/v1.2.0",
            "https://github.com/example/app/releases/download/v1.2.0/app.apk", "app.apk", false,
            bytes.size.toLong(), UpdateDownloader.sha256(source))
    }
    private fun secureServer(test: (MockWebServer, OkHttpClient, File) -> Unit) {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory(), false)
            server.start()
            val client = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
            test(server, client, temporary.newFolder())
        }
    }
    @Test fun validHttpsAssetIsVerifiedAndReusedWithoutAnotherRequest() = secureServer { server, client, directory ->
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        val release = release()
        val downloader = UpdateDownloader(client, directory) { _, _ -> server.url("/asset").toString() }
        var progress = 0L
        val saved = runBlocking { downloader.download(release, UpdateSource.GITHUB) { received, _ -> progress = received } }
        assertArrayEquals(bytes, saved.readBytes())
        assertEquals(release.size, progress)
        val cached = runBlocking { downloader.download(release, UpdateSource.GITHUB) { _, _ -> } }
        assertEquals(saved, cached)
        assertEquals(1, server.requestCount)
    }
    @Test fun digestMismatchRejectsAndDeletesPartialAsset() = secureServer { server, client, directory ->
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        val release = release().copy(sha256 = "0".repeat(64))
        val downloader = UpdateDownloader(client, directory) { _, _ -> server.url("/asset").toString() }
        val error = runCatching { runBlocking { downloader.download(release, UpdateSource.GITHUB) { _, _ -> } } }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error!!.message.orEmpty().contains("SHA-256"))
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }
    @Test fun redirectCannotDowngradeToPlainHttp() = secureServer { server, client, directory ->
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "http://localhost:1/asset"))
        val downloader = UpdateDownloader(client, directory) { _, _ -> server.url("/asset").toString() }
        val error = runCatching { runBlocking { downloader.download(release(), UpdateSource.GITHUB) { _, _ -> } } }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error!!.message.orEmpty().contains("HTTPS"))
        assertEquals(1, server.requestCount)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }
}

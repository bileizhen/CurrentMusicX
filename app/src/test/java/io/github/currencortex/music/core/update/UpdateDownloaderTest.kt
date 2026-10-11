package io.github.currencortex.music.core.update

import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
    private val bytes = ByteArray(32 * 1024) { (it % 251).toByte() }.apply {
        byteArrayOf(0x50, 0x4b, 0x03, 0x04).copyInto(this)
    }
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
    @Test fun dedicatedMirrorTriesAnotherDnsAddressAfterConnectionFailure() = secureServer { server, client, directory ->
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        var failures = 0
        val routed = client.newBuilder().dns(object : okhttp3.Dns {
            override fun lookup(hostname: String) = listOf(
                java.net.InetAddress.getByName("127.0.0.2"), java.net.InetAddress.getByName("127.0.0.1"))
        }).eventListener(object : okhttp3.EventListener() {
            override fun connectFailed(call: okhttp3.Call, inetSocketAddress: java.net.InetSocketAddress,
                proxy: java.net.Proxy, protocol: okhttp3.Protocol?, ioe: java.io.IOException) { failures++ }
        }).build()
        val release = release()
        val downloader = UpdateDownloader(routed, directory) { _, _ ->
            server.url("/asset").newBuilder().host("localhost").build().toString()
        }
        val saved = runBlocking { downloader.download(release, UpdateSource.CURRENTMUSIC) { _, _ -> } }
        assertTrue(UpdateDownloader.matches(saved, release))
        assertTrue(failures > 0)
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

    @Test fun twoBrokenMirrorsFallBackToVerifiedOfficialAsset() = secureServer { server, client, directory ->
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) = when (request.path) {
                "/GITHUB" -> MockResponse().setBody(Buffer().write(bytes))
                "/DPIK" -> MockResponse().setHeader("Content-Type", "text/html").setBody("<html>Mirror unavailable</html>")
                else -> MockResponse().setResponseCode(429).setBody("error code: 1027")
            }
        }
        val release = release()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)
        val downloader = UpdateDownloader(client, directory) { _, source -> server.url("/${source.name}").toString() }
        val installer = object : UpdateInstall {
            override fun canInstall() = true
            override suspend fun request(file: File, release: AppRelease) = error("Downloading must not install")
        }
        val transfer = UpdateTransfer(downloader, installer, scope)
        try {
            transfer.selectRelease(release)
            transfer.download(UpdateSource.GEEKERTAO)
            runBlocking { kotlinx.coroutines.withTimeout(10000) {
                transfer.state.first { it.download is UpdateDownloadState.Ready || it.download is UpdateDownloadState.Failed }
            } }
            val state = transfer.state.value
            assertTrue("Fallback must finish with a verified APK: $state", state.download is UpdateDownloadState.Ready)
            assertEquals(UpdateSource.GITHUB, state.activeSource)
            assertEquals(UpdateSource.GEEKERTAO.fallbacks().size, state.attempt)
            assertArrayEquals(bytes, (state.download as UpdateDownloadState.Ready).file.readBytes())
            assertEquals(UpdateSource.GEEKERTAO.fallbacks().map { "/${it.name}" }, UpdateSource.GEEKERTAO.fallbacks().map { server.takeRequest().path })
            assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".part") })
        } finally { scope.cancel() }
    }

    @Test fun incompleteMirroredFileIsRemovedBeforeTryingNextSource() = secureServer { server, client, directory ->
        server.enqueue(MockResponse().setChunkedBody(Buffer().write(bytes.copyOf(4096)), 512))
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        val release = release()
        val downloader = UpdateDownloader(client, directory) { _, source -> server.url("/${source.name}").toString() }
        val first = runCatching { runBlocking { downloader.download(release, UpdateSource.GEEKERTAO) { _, _ -> } } }
        assertTrue(first.exceptionOrNull() is java.io.IOException)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
        val saved = runBlocking { downloader.download(release, UpdateSource.DPIK) { _, _ -> } }
        assertTrue(UpdateDownloader.matches(saved, release))
    }

    @Test fun mislabeledNonApkBodyIsRejectedEvenWithMatchingDigest() = secureServer { server, client, directory ->
        val html = "<html>Security challenge</html>".toByteArray()
        val file = temporary.newFile().apply { writeBytes(html) }
        val release = release().copy(size = html.size.toLong(), sha256 = UpdateDownloader.sha256(file))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/octet-stream").setBody(Buffer().write(html)))
        val downloader = UpdateDownloader(client, directory) { _, _ -> server.url("/asset").toString() }
        val failure = runCatching { runBlocking { downloader.download(release, UpdateSource.DPIK) { _, _ -> } } }.exceptionOrNull()
        assertTrue(failure is java.io.IOException)
        assertTrue(failure!!.message.orEmpty().contains("APK"))
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun tricklingMirrorIsRejectedWhileSlowOfficialDownloadIsAllowed() = secureServer { server, client, directory ->
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        var clock = 0L
        val release = release()
        val downloader = UpdateDownloader(client, directory, nanoTime = { clock += 16_000_000_000L; clock }) { _, _ -> server.url("/asset").toString() }
        val failure = runCatching { runBlocking { downloader.download(release, UpdateSource.GEEKERTAO) { _, _ -> } } }.exceptionOrNull()
        assertTrue(failure is java.io.IOException)
        assertTrue(failure!!.message.orEmpty().contains("速度过慢"))
        assertTrue(directory.listFiles().orEmpty().isEmpty())
        val file = runBlocking { downloader.download(release, UpdateSource.GITHUB) { _, _ -> } }
        assertTrue(UpdateDownloader.matches(file, release))
    }

    @Test fun slowDedicatedMirrorKeepsProgressInsteadOfDiscardingVerifiedBytes() = secureServer { server, client, directory ->
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        var clock = 0L
        val release = release()
        val progress = mutableListOf<Long>()
        val downloader = UpdateDownloader(client, directory, nanoTime = { clock += 16_000_000_000L; clock }) { _, _ -> server.url("/asset").toString() }
        val file = runBlocking { downloader.download(release, UpdateSource.CURRENTMUSIC) { received, _ -> progress += received } }
        assertTrue(UpdateDownloader.matches(file, release))
        assertEquals(release.size, progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> a <= b })
        assertEquals(1, server.requestCount)
    }
}

package io.github.currencortex.music

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import io.github.currencortex.music.core.media.AvatarImageCache
import io.github.currencortex.music.core.network.ApiClient
import io.github.currencortex.music.core.network.RequestSession
import io.github.currencortex.music.data.profile.ProfileRepository
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Upload fixtures use a separate package and local server; never modify the real account. */
class AvatarCacheTest {
    @Test fun relativeAvatarEvictsOldMemoryAndDisk() = verify(absolute = false)
    @Test fun signedAbsoluteAvatarRefreshesWithoutChangingRequestQuery() = verify(absolute = true)

    private fun verify(absolute: Boolean) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        val namespace = "-test-${UUID.randomUUID()}"
        val directory = File(context.cacheDir, "avatar$namespace")
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val client = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
        val server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        val disk = DiskCache.Builder().directory(directory.toOkioPath()).maxSizeBytes(1024 * 1024).build()
        val loader = ImageLoader.Builder(context).components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .diskCache(disk).build()
        try {
            val base = server.url("/cm/").toString()
            val url = server.url(if (absolute) "/image.jpg?signature=keep%2Bme" else "/cm/avatar/7.jpg").toString()
            val avatar = if (absolute) url else "7.jpg"
            val cover = server.url("/cover.jpg").toString()
            val color = AtomicInteger(Color.RED)
            val reads = AtomicInteger()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                    "/cm/profile/avatar" -> { color.set(Color.BLUE); MockResponse().setBody("{}") }
                    "/cm/auth/me" -> MockResponse().setBody("""{"id":7,"avatar":"$avatar"}""")
                    else -> {
                        if (request.requestUrl!!.encodedPath != "/cover.jpg") {
                            reads.incrementAndGet()
                            assertNull(request.getHeader("Authorization"))
                            if (absolute) assertEquals("signature=keep%2Bme", request.requestUrl!!.encodedQuery)
                        }
                        MockResponse().setHeader("Content-Type", "image/png").setHeader("Cache-Control", "max-age=86400")
                            .setBody(Buffer().write(png(color.get())))
                    }
                }
            }
            suspend fun load(data: String, size: Int = 64): SuccessResult {
                val result = loader.execute(ImageRequest.Builder(context).data(data).size(size).allowHardware(false).build())
                assertTrue(result.toString(), result is SuccessResult)
                return result as SuccessResult
            }
            val initial = load(url)
            assertEquals(Color.RED, initial.image.toBitmap().getPixel(0, 0))
            load(url, 128)
            val unrelated = load(cover)
            // Ensure variants with extra decode parameters are removed too.
            val variant = MemoryCache.Key(url, mapOf("fixture-size" to "256"))
            loader.memoryCache!![variant] = loader.memoryCache!![initial.memoryCacheKey!!]!!
            disk.openSnapshot(url).use { assertNotNull(it) }
            val cache = AvatarImageCache(context, namespace) { loader }
            val api = ApiClient({ base }, { "fixture" }, {}, client = client)
            val repo = ProfileRepository(api, cache) { RequestSession(base, "fixture") }
            repo.avatar(byteArrayOf(1, 2, 3), previousAvatar = avatar)
            assertFalse(loader.memoryCache!!.keys.any { it.key == url })
            disk.openSnapshot(url).use { assertNull(it) }
            assertNotNull(loader.memoryCache!![unrelated.memoryCacheKey!!])
            disk.openSnapshot(cover).use { assertNotNull(it) }
            val refreshedUrl = repo.avatarUrl(base, avatar)!!
            assertNotEquals(url, refreshedUrl)
            val before = reads.get()
            assertEquals(Color.BLUE, load(refreshedUrl).image.toBitmap().getPixel(0, 0))
            assertEquals(before + 1, reads.get())
            // Simulate an old request completing late. Persisted generation must still win.
            loader.memoryCache!![initial.memoryCacheKey!!] = MemoryCache.Value(initial.image)
            val restored = ProfileRepository(api, AvatarImageCache(context, namespace) { loader }) { RequestSession(base, "fixture") }
            assertEquals(refreshedUrl, restored.avatarUrl(base, avatar))
            loader.memoryCache!!.clear()
            assertEquals(Color.BLUE, load(restored.avatarUrl(base, avatar)!!).image.toBitmap().getPixel(0, 0))
            assertEquals(before + 1, reads.get()) // new image survives via disk cache
            repo.avatar(byteArrayOf(4), previousAvatar = avatar)
            disk.openSnapshot(refreshedUrl).use { assertNull(it) }
            assertNotEquals(refreshedUrl, repo.avatarUrl(base, avatar))
        } finally {
            loader.shutdown()
            disk.shutdown()
            server.shutdown()
            context.getSharedPreferences("avatar-cache-versions$namespace", 0).edit().clear().commit()
            directory.deleteRecursively()
        }
    }

    private fun png(color: Int): ByteArray = ByteArrayOutputStream().use { output ->
        Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color); compress(Bitmap.CompressFormat.PNG, 100, output); recycle()
        }
        output.toByteArray()
    }
}

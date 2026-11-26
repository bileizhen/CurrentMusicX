package io.github.currencortex.music.data.song

import io.github.currencortex.music.core.network.ApiClient
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.data.qqmusic.QqMusicRepository
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

data class DynamicCoverMedia(
    val videoUrl: String,
    val source: String,
    val squareUrl: String? = null,
    val tallUrl: String? = null,
) {
    /** Pick aspect-matched video stream for immersive vs compact cover frames. */
    fun urlFor(immersive: Boolean): String = if (immersive) tallUrl ?: videoUrl else squareUrl ?: videoUrl
}

/**
 * Resolves looping animated cover artwork across NetEase, QQ Music, and Apple Music editorial
 * motion artwork. All failures or missing streams degrade cleanly to null so the UI retains the
 * static cover image.
 */
class DynamicCoverRepository(
    private val api: ApiClient? = null,
    private val qqMusic: QqMusicRepository? = null,
    httpClient: OkHttpClient = ApiClient.defaultHttpClient(),
    private val allowExternalLookup: Boolean = true,
    private val appleBrowseBaseUrl: String = "https://music.apple.com",
    private val appleAmpApiBaseUrl: String = "https://amp-api.music.apple.com",
    private val now: () -> Long = { System.currentTimeMillis() },
    private val timeoutMs: Long = 8_000L,
) {
    internal val mediaHttpClient: OkHttpClient = httpClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val http = mediaHttpClient

    private data class CacheKey(
        val musicSource: MusicSource,
        val songId: Long,
        val name: String,
        val artists: String,
        val album: String,
        val qqMid: String,
        val qqAlbumMid: String,
        val hasLocalNetease: Boolean,
        val neteaseRevision: Long?,
        val allowExternalLookup: Boolean,
    )

    private data class CacheEntry(
        val media: DynamicCoverMedia?,
        val expiresAt: Long,
    )

    private val cacheLock = Any()
    private val cache = LinkedHashMap<CacheKey, CacheEntry>()
    private val locks = Array(16) { Mutex() }

    private val tokenMutex = Mutex()
    @Volatile private var cachedToken: String? = null
    @Volatile private var tokenExpiresAt: Long = 0L

    /** Resolve dynamic cover media for [song], returning null when unavailable or disabled. */
    suspend fun resolve(song: Song?): DynamicCoverMedia? {
        if (song == null || song.video || song.name.isBlank()) return null
        val key = cacheKey(song)
        return locks[(key.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            synchronized(cacheLock) {
                cache.entries.removeAll { it.value.expiresAt <= now() }
                cache[key]?.let { return@withLock it.media }
            }
            val resolved = withTimeoutOrNull(timeoutMs) {
                when (song.musicSource) {
                    MusicSource.NETEASE -> resolveNetease(song) ?: resolveAppleMusic(song)
                    MusicSource.QQ_MUSIC -> resolveQq(song) ?: resolveAppleMusic(song)
                    MusicSource.APPLE_MUSIC, MusicSource.SPOTIFY, MusicSource.NAVIDROME,
                    MusicSource.LOCAL, MusicSource.EMBY -> resolveAppleMusic(song)
                    MusicSource.SERVICE -> null
                }
            }
            val ttl = if (resolved != null) POSITIVE_TTL_MS else NEGATIVE_TTL_MS
            synchronized(cacheLock) {
                if (cache.size >= MAX_CACHE_ENTRIES) {
                    cache.keys.firstOrNull()?.let(cache::remove)
                }
                cache[key] = CacheEntry(resolved, now() + ttl)
            }
            resolved
        }
    }

    /** Evict any cached stream URL for [song] after a playback failure so it falls back or refreshes cleanly. */
    fun invalidate(song: Song?) {
        if (song == null) return
        synchronized(cacheLock) {
            cache.keys.removeAll { it.musicSource == song.musicSource && it.songId == song.id && it.name == song.name }
        }
    }

    private fun cacheKey(song: Song): CacheKey {
        val neteaseGateway = api?.netease
        return CacheKey(
            musicSource = song.musicSource,
            songId = NeteaseSongActionsRepository.songId(song) ?: song.id,
            name = song.name.trim(),
            artists = song.artists.trim(),
            album = song.album.trim(),
            qqMid = song.qqMusic?.mid.orEmpty(),
            qqAlbumMid = song.qqMusic?.albumMid.orEmpty(),
            hasLocalNetease = api?.hasLocalNeteaseLogin == true,
            neteaseRevision = neteaseGateway?.sessions?.state?.value?.revision,
            allowExternalLookup = allowExternalLookup,
        )
    }

    private suspend fun resolveNetease(song: Song): DynamicCoverMedia? {
        val gateway = api?.netease ?: return null
        if (!api.hasLocalNeteaseLogin) return null
        val id = NeteaseSongActionsRepository.songId(song) ?: return null
        return try {
            val raw = withContext(Dispatchers.IO) {
                gateway.request("GET", "ncm/song/dynamic/cover", mapOf("id" to id.toString()), null)
            } as? JsonObject ?: return null
            val videoUrl = raw.text("videoUrl").validHttpUrl() ?: return null
            DynamicCoverMedia(videoUrl = videoUrl, source = "netease", squareUrl = videoUrl)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun resolveQq(song: Song): DynamicCoverMedia? {
        val repo = qqMusic ?: return null
        return try {
            val videoUrl = repo.dynamicCover(song)?.validHttpUrl() ?: return null
            DynamicCoverMedia(videoUrl = videoUrl, source = "qqmusic", squareUrl = videoUrl)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun resolveAppleMusic(song: Song): DynamicCoverMedia? {
        if (!allowExternalLookup) return null
        val title = cleanQueryTitle(song.name)
        val album = cleanQueryTitle(song.album)
        val primaryArtist = extractPrimaryArtist(song.artists)
        val allArtists = splitArtists(song.artists)
        if (title.isBlank() && album.isBlank()) return null

        val ampBase = appleAmpApiBaseUrl.toHttpUrlOrNull() ?: return null
        return withContext(Dispatchers.IO) {
            var token = ensureAppleToken() ?: return@withContext null
            for (storefront in listOf("cn", "us")) {
                var result = searchAppleEditorialVideo(ampBase, storefront, token, title, album, primaryArtist, allArtists)
                if (result == SearchAttempt.Unauthorized) {
                    invalidateAppleToken(token)
                    token = ensureAppleToken() ?: return@withContext null
                    result = searchAppleEditorialVideo(ampBase, storefront, token, title, album, primaryArtist, allArtists)
                }
                if (result is SearchAttempt.Found) {
                    return@withContext result.media
                }
            }
            null
        }
    }

    private sealed interface SearchAttempt {
        data class Found(val media: DynamicCoverMedia) : SearchAttempt
        data object NotFound : SearchAttempt
        data object Unauthorized : SearchAttempt
    }

    private fun searchAppleEditorialVideo(
        ampBase: HttpUrl,
        storefront: String,
        token: String,
        title: String,
        album: String,
        primaryArtist: String,
        allArtists: List<String>,
    ): SearchAttempt {
        if (album.isNotBlank()) {
            val term = listOf(album, primaryArtist).filter(String::isNotBlank).joinToString(" ")
            val albumResponse = executeAppleSearch(ampBase, storefront, token, term, types = "albums", extendVideo = true)
            if (albumResponse.unauthorized) return SearchAttempt.Unauthorized
            albumResponse.json?.let { root ->
                extractMatchingAlbumVideo(root, album, allArtists)?.let { return SearchAttempt.Found(it) }
            }
        }
        if (title.isNotBlank()) {
            val term = listOf(title, primaryArtist).filter(String::isNotBlank).joinToString(" ")
            val songResponse = executeAppleSearch(ampBase, storefront, token, term, types = "songs", extendVideo = false)
            if (songResponse.unauthorized) return SearchAttempt.Unauthorized
            val resolvedAlbum = songResponse.json?.let { root ->
                extractMatchingSongAlbum(root, title, allArtists)
            }.orEmpty()
            if (resolvedAlbum.isNotBlank() && !sameNormalized(resolvedAlbum, album)) {
                val retryTerm = listOf(resolvedAlbum, primaryArtist).filter(String::isNotBlank).joinToString(" ")
                val albumResponse = executeAppleSearch(ampBase, storefront, token, retryTerm, types = "albums", extendVideo = true)
                if (albumResponse.unauthorized) return SearchAttempt.Unauthorized
                albumResponse.json?.let { root ->
                    extractMatchingAlbumVideo(root, resolvedAlbum, allArtists)?.let { return SearchAttempt.Found(it) }
                }
            }
        }
        return SearchAttempt.NotFound
    }

    private data class AppleSearchResult(val json: JsonObject? = null, val unauthorized: Boolean = false)

    private fun executeAppleSearch(
        ampBase: HttpUrl,
        storefront: String,
        token: String,
        term: String,
        types: String,
        extendVideo: Boolean,
    ): AppleSearchResult {
        val url = ampBase.newBuilder()
            .addPathSegments("v1/catalog/$storefront/search")
            .addQueryParameter("term", term)
            .addQueryParameter("types", types)
            .addQueryParameter("limit", "5")
            .apply { if (extendVideo) addQueryParameter("extend", "editorialVideo") }
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Origin", "https://music.apple.com")
            .header("Referer", "https://music.apple.com/")
            .header("User-Agent", APPLE_USER_AGENT)
            .header("Accept", "application/json")
            .header("Accept-Language", if (storefront == "cn") "zh-CN,zh;q=0.9,en;q=0.8" else "en-US,en;q=0.9")
            .get()
            .build()
        return try {
            http.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) return AppleSearchResult(unauthorized = true)
                if (!response.isSuccessful) return AppleSearchResult()
                val text = response.body?.string().orEmpty()
                val json = ApiJson.parseToJsonElement(text) as? JsonObject
                AppleSearchResult(json = json)
            }
        } catch (_: IOException) {
            AppleSearchResult()
        } catch (_: Exception) {
            AppleSearchResult()
        }
    }

    private fun extractMatchingAlbumVideo(
        root: JsonObject,
        targetAlbum: String,
        targetArtists: List<String>,
    ): DynamicCoverMedia? {
        val items = root.obj("results").obj("albums").array("data").mapNotNull { it as? JsonObject }
        for (item in items) {
            val attrs = item.obj("attributes")
            val candidateAlbum = attrs.text("name")
            val candidateArtist = attrs.text("artistName")
            if (!albumMatches(candidateAlbum, targetAlbum)) continue
            if (targetArtists.isNotEmpty() && !artistsOverlap(candidateArtist, targetArtists)) continue
            val editorialVideo = attrs["editorialVideo"] as? JsonObject ?: continue
            val tallUrl = editorialVideo.obj("motionDetailTall").text("video").validHttpUrl()
                ?: editorialVideo.obj("motionTallVideo3x4").text("video").validHttpUrl()
            val squareUrl = editorialVideo.obj("motionDetailSquare").text("video").validHttpUrl()
                ?: editorialVideo.obj("motionSquareVideo1x1").text("video").validHttpUrl()
            val primary = tallUrl ?: squareUrl ?: continue
            return DynamicCoverMedia(
                videoUrl = primary,
                source = "applemusic",
                squareUrl = squareUrl,
                tallUrl = tallUrl,
            )
        }
        return null
    }

    private fun extractMatchingSongAlbum(
        root: JsonObject,
        targetTitle: String,
        targetArtists: List<String>,
    ): String? {
        val items = root.obj("results").obj("songs").array("data").mapNotNull { it as? JsonObject }
        for (item in items) {
            val attrs = item.obj("attributes")
            val name = attrs.text("name")
            val artist = attrs.text("artistName")
            val album = attrs.text("albumName")
            if (album.isBlank()) continue
            if (!titleMatches(name, targetTitle)) continue
            if (targetArtists.isNotEmpty() && !artistsOverlap(artist, targetArtists)) continue
            return cleanQueryTitle(album)
        }
        return null
    }

    private suspend fun ensureAppleToken(): String? {
        val existing = cachedToken
        if (existing != null && now() < tokenExpiresAt) return existing
        return tokenMutex.withLock {
            val current = cachedToken
            if (current != null && now() < tokenExpiresAt) return@withLock current
            val browseUrl = appleBrowseBaseUrl.toHttpUrlOrNull() ?: return@withLock null
            val fetched = fetchAppleToken(browseUrl) ?: return@withLock null
            cachedToken = fetched.first
            tokenExpiresAt = fetched.second
            fetched.first
        }
    }

    private fun invalidateAppleToken(token: String) {
        if (cachedToken == token) {
            cachedToken = null
            tokenExpiresAt = 0L
        }
    }

    private fun fetchAppleToken(browseBase: HttpUrl): Pair<String, Long>? {
        val pageUrl = browseBase.newBuilder().addPathSegments("us/browse").build()
        val html = executeGetText(pageUrl) ?: return null
        val scriptMatches = SCRIPT_PATH_REGEX.findAll(html).map { it.groupValues[1] }.distinct().toList()
        for (scriptPath in scriptMatches) {
            val scriptUrl = browseBase.resolve(scriptPath) ?: continue
            // Reject cross-origin script tags from untrusted hosts.
            if (scriptUrl.host != browseBase.host || scriptUrl.port != browseBase.port) continue
            val js = executeGetText(scriptUrl, maxBytes = MAX_BUNDLE_BYTES) ?: continue
            val token = JWT_REGEX.find(js)?.value ?: continue
            val expiry = jwtExpiryMs(token) ?: (now() + DEFAULT_TOKEN_TTL_MS)
            return token to expiry
        }
        return null
    }

    private fun executeGetText(url: HttpUrl, maxBytes: Long = 2_000_000L): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", APPLE_USER_AGENT)
            .header("Accept", "*/*")
            .get()
            .build()
        return try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val length = body.contentLength()
                if (length > maxBytes) return null
                body.byteStream().bufferedReader().use { reader ->
                    val buffer = CharArray(8192)
                    val out = StringBuilder()
                    var total = 0L
                    while (true) {
                        val read = reader.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > maxBytes) return null
                        out.append(buffer, 0, read)
                    }
                    out.toString()
                }
            }
        } catch (_: IOException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun jwtExpiryMs(jwt: String): Long? = runCatching {
        val payloadPart = jwt.split('.').getOrNull(1) ?: return null
        val decoded = String(Base64.getUrlDecoder().decode(payloadPart), Charsets.UTF_8)
        val expSec = (ApiJson.parseToJsonElement(decoded) as? JsonObject)?.get("exp")
            ?.let { it as? JsonPrimitive }?.longOrNull ?: return null
        val candidate = expSec * 1_000L - 60_000L
        candidate.takeIf { it > now() }
    }.getOrNull()

    private fun String.validHttpUrl(): String? = trim()
        .takeIf { it.isNotEmpty() }
        ?.let { url -> url.toHttpUrlOrNull()?.takeIf { it.scheme in setOf("http", "https") }?.toString() }

    private fun JsonObject.text(key: String): String =
        (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.obj(key: String): JsonObject =
        get(key) as? JsonObject ?: EMPTY_OBJECT

    private fun JsonObject.array(key: String): JsonArray =
        get(key) as? JsonArray ?: EMPTY_ARRAY

    companion object {
        private const val POSITIVE_TTL_MS = 30 * 60_000L
        private const val NEGATIVE_TTL_MS = 15_000L
        private const val DEFAULT_TOKEN_TTL_MS = 12 * 60 * 60_000L
        private const val MAX_CACHE_ENTRIES = 128
        private const val MAX_BUNDLE_BYTES = 10_000_000L
        private const val APPLE_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val EMPTY_OBJECT = JsonObject(emptyMap())
        private val EMPTY_ARRAY = JsonArray(emptyList())
        private val SCRIPT_PATH_REGEX = Regex("""src=["']([^"']*assets/index~[^"']+\.js)["']""")
        private val JWT_REGEX = Regex("""eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{10,}""")
        private val BRACKETED_SUFFIX_REGEX = Regex("""\s*[\(\[（【][^()\[\]（）【】]*(?:Deluxe|Expanded|Remaster|Edition|Version|Bonus|Live|EP|Single|Explicit|Clean|Audio|版|专辑|精选)[^()\[\]（）【】]*[\)\]）】]""", RegexOption.IGNORE_CASE)
        private val WHITESPACE_PUNCT_REGEX = Regex("""[\s\p{Punct}]+""")

        internal fun cleanQueryTitle(raw: String): String = raw
            .replace(BRACKETED_SUFFIX_REGEX, " ")
            .replace(Regex("""\s+-\s+(?:EP|Single)$""", RegexOption.IGNORE_CASE), "")
            .trim()

        internal fun splitArtists(raw: String): List<String> = raw
            .split('/', '、', ',', '，', '&', ';', '；')
            .map(String::trim)
            .filter(String::isNotBlank)

        internal fun extractPrimaryArtist(raw: String): String = splitArtists(raw).firstOrNull().orEmpty()

        internal fun normalizeCompare(raw: String): String = cleanQueryTitle(raw)
            .lowercase()
            .replace(WHITESPACE_PUNCT_REGEX, "")

        internal fun sameNormalized(a: String, b: String): Boolean {
            val na = normalizeCompare(a)
            val nb = normalizeCompare(b)
            return na.isNotEmpty() && na == nb
        }

        internal fun albumMatches(candidate: String, target: String): Boolean {
            val nc = normalizeCompare(candidate)
            val nt = normalizeCompare(target)
            if (nc.isEmpty() || nt.isEmpty()) return false
            if (nc == nt) return true
            val shorter = minOf(nc.length, nt.length)
            return shorter >= 4 && (nc.startsWith(nt) || nt.startsWith(nc))
        }

        internal fun titleMatches(candidate: String, target: String): Boolean {
            val nc = normalizeCompare(candidate)
            val nt = normalizeCompare(target)
            if (nc.isEmpty() || nt.isEmpty()) return false
            return nc == nt || (minOf(nc.length, nt.length) >= 4 && (nc.contains(nt) || nt.contains(nc)))
        }

        internal fun artistsOverlap(candidateArtist: String, targetArtists: List<String>): Boolean {
            val nc = normalizeCompare(candidateArtist)
            if (nc.isEmpty()) return false
            val candidateParts = splitArtists(candidateArtist).map(::normalizeCompare).filter(String::isNotEmpty)
            return targetArtists.any { target ->
                val nt = normalizeCompare(target)
                nt.isNotEmpty() && (nc.contains(nt) || candidateParts.any { it == nt || it.contains(nt) || nt.contains(it) })
            }
        }
    }
}

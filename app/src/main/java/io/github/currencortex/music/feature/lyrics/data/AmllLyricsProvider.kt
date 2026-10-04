package io.github.currencortex.music.feature.lyrics.data

import io.github.currencortex.music.data.song.*
import io.github.currencortex.music.feature.lyrics.domain.*
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.ttml.TtmlParser
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class AmllLyricsProvider(private val remote: LyricsRemoteDataSource = GithubTtmlDataSource()) : LyricsProvider {
    override suspend fun findLyrics(song: Song): LyricsProviderResult {
        var error: LyricsProviderResult.Failure? = null
        for (key in LyricsMatcher.candidates(song)) {
            when (val result = remote.fetch(key)) {
                is LyricsProviderResult.Ttml -> return result
                is LyricsProviderResult.Failure -> if (result.code != LyricsErrorCode.NO_LYRICS) error = result
                is LyricsProviderResult.Parsed -> return result
            }
        }
        return error ?: LyricsProviderResult.Failure(LyricsErrorCode.NO_LYRICS, "No AMLL entry for the song's platform ids")
    }
}

/** One id-addressed file; no database index, account credentials, or repository download. */
class GithubTtmlDataSource(
    baseUrl: String = "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/refs/heads/main/",
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS).build(),
) : LyricsRemoteDataSource {
    private val base = baseUrl.toHttpUrl()
    override suspend fun fetch(key: LyricsKey): LyricsProviderResult {
        val directory = when (key.source) { MusicSource.NETEASE -> "ncm-lyrics"; MusicSource.QQ_MUSIC -> "qq-lyrics"; MusicSource.APPLE_MUSIC -> "am-lyrics"; MusicSource.SPOTIFY -> "spotify-lyrics" }
        val url = base.newBuilder().addPathSegment(directory).addPathSegment("${key.songId}.ttml").build()
        return suspendCancellableCoroutine { cont ->
            val call = client.newCall(Request.Builder().url(url).header("Accept", "application/ttml+xml, application/xml, text/xml").build())
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resume(LyricsProviderResult.Failure(LyricsErrorCode.NETWORK_ERROR, "AMLL transport: ${e.javaClass.simpleName}"))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = try { response.use { decode(it, key) } }
                    catch (e: IOException) { LyricsProviderResult.Failure(LyricsErrorCode.NETWORK_ERROR, "AMLL body: ${e.javaClass.simpleName}") }
                    if (cont.isActive) cont.resume(result)
                }
            })
        }
    }
    private fun decode(response: Response, key: LyricsKey): LyricsProviderResult {
        if (response.code == 404) return LyricsProviderResult.Failure(LyricsErrorCode.NO_LYRICS, "No AMLL entry for ${key.source}/${key.songId}")
        if (!response.isSuccessful) return LyricsProviderResult.Failure(LyricsErrorCode.NETWORK_ERROR, "AMLL HTTP ${response.code}")
        val body = response.body ?: return LyricsProviderResult.Failure(LyricsErrorCode.NO_LYRICS, "Empty AMLL response")
        if (body.contentLength() > TtmlParser.MAX_BYTES) return LyricsProviderResult.Failure(LyricsErrorCode.PARSE_ERROR, "AMLL document exceeds size limit")
        val source = body.source()
        source.request(TtmlParser.MAX_BYTES.toLong() + 1)
        if (source.buffer.size > TtmlParser.MAX_BYTES) return LyricsProviderResult.Failure(LyricsErrorCode.PARSE_ERROR, "AMLL document exceeds size limit")
        return LyricsProviderResult.Ttml(source.readUtf8(), key)
    }
}

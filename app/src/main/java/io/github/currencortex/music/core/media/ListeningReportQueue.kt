package io.github.currencortex.music.core.media

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Application-owned workers drain final service reports and isolate each platform's failures. */
class ListeningReportQueue(
    private val scope: CoroutineScope,
    private val currentSession: (Song) -> RequestSession,
    currentMusic: suspend (Song, Long?, RequestSession) -> Unit,
    netease: (suspend (Song, Long?, RequestSession) -> Unit)? = null,
    navidrome: suspend (Song, Long?, RequestSession) -> Unit,
    private val onFailure: (String, ErrorKind) -> Unit = { _, _ -> },
) {
    private data class Report(val song: Song, val ms: Long?, val owner: RequestSession)
    private fun worker(name: String, accepts: (Report) -> Boolean,
        send: suspend (Song, Long?, RequestSession) -> Unit): Channel<Report> {
        val channel = Channel<Report>(Channel.UNLIMITED)
        scope.launch {
            for (report in channel) {
                if (!accepts(report)) continue
                val result = appResult { send(report.song, report.ms, report.owner) }
                if (result is AppResult.Failure) onFailure(name, result.kind)
            }
        }
        return channel
    }
    private val current = worker("CurrentMusic", { report ->
        val owner = currentSession(report.song)
        report.owner.token != null && report.owner.server == owner.server && report.owner.token == owner.token
    }, currentMusic)
    private val native = netease?.let { send -> worker("Netease", { report ->
        report.owner.neteaseRevision != null && report.owner.neteaseRevision == currentSession(report.song).neteaseRevision
    }, send) }
    private val privateLibrary = worker("Navidrome", { it.owner == currentSession(it.song) }, navidrome)

    fun report(song: Song, owner: RequestSession, milliseconds: Long? = null) {
        if (song.video || milliseconds != null && milliseconds <= 0) return
        val report = Report(song, milliseconds, owner)
        if (song.musicSource == MusicSource.NAVIDROME) privateLibrary.trySend(report)
        else {
            current.trySend(report)
            if (song.musicSource == MusicSource.NETEASE) native?.trySend(report)
        }
    }
}

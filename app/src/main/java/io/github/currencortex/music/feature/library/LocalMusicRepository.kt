package io.github.currencortex.music.feature.library

import io.github.currencortex.music.data.song.Song
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The local list to render, plus whether the scan could be persisted for the next visit. */
internal data class LocalMusicLoadResult(val songs: List<Song>, val cacheSaved: Boolean = true)

@Serializable
private data class LocalMusicSnapshot(
    val version: Int = SNAPSHOT_VERSION,
    val scope: String,
    val songs: List<Song>,
)

private const val SNAPSHOT_VERSION = 1

/** Persists the last successful scan, including an empty list, for its selected scope. */
internal class LocalMusicRepository(private val file: File) {
    private val mutex = Mutex()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    /** Guarded by [mutex], so page reentry avoids another disk read. */
    private var snapshot: LocalMusicSnapshot? = null
    private var cacheSaved = true

    suspend fun load(
        scope: String,
        refresh: Boolean = false,
        scan: suspend () -> List<Song>,
    ): LocalMusicLoadResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!refresh) {
                snapshot?.takeIf { it.scope == scope }?.let {
                    return@withLock LocalMusicLoadResult(it.songs, cacheSaved)
                }
                val restored = readSnapshot(scope)
                if (restored != null) {
                    snapshot = restored
                    cacheSaved = true
                    return@withLock LocalMusicLoadResult(restored.songs)
                }
            }
            coroutineContext.ensureActive()
            val songs = scan().toList()
            coroutineContext.ensureActive()
            val fresh = LocalMusicSnapshot(scope = scope, songs = songs)
            cacheSaved = try {
                writeSnapshot(fresh)
                true
            } catch (_: IOException) {
                coroutineContext.ensureActive()
                false
            }
            snapshot = fresh
            LocalMusicLoadResult(songs, cacheSaved = cacheSaved)
        }
    }

    /** Returns the persisted snapshot only when it is valid and belongs to [scope]. */
    private fun readSnapshot(scope: String): LocalMusicSnapshot? {
        if (!file.isFile) return null
        return try {
            val decoded = json.decodeFromString(LocalMusicSnapshot.serializer(), file.readText())
            decoded.takeIf { it.version == SNAPSHOT_VERSION && it.scope == scope }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Missing, truncated, corrupt or written by an unsupported version.
            null
        }
    }

    /** Writes through a sibling temp file so a crash never truncates the last good snapshot. */
    private suspend fun writeSnapshot(snapshot: LocalMusicSnapshot) {
        val directory = file.absoluteFile.parentFile
        directory?.mkdirs()
        val temp = File.createTempFile("local-music-", ".tmp", directory)
        try {
            temp.writeText(json.encodeToString(LocalMusicSnapshot.serializer(), snapshot))
            coroutineContext.ensureActive()
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            runCatching { Files.deleteIfExists(temp.toPath()) }
        }
    }
}

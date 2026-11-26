package io.github.currencortex.music.core.media

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
data class CachedTrackLoudness(
    val lufs: Float,
    val peak: Float = 0f,
    val gatedBlocks: Int = 0,
)

@Serializable
private data class TrackLoudnessSnapshot(
    val version: Int = SNAPSHOT_VERSION,
    val entries: Map<String, CachedTrackLoudness> = emptyMap(),
)

private const val SNAPSHOT_VERSION = 1
private const val MAX_ENTRIES = 2000

/**
 * Bounded LRU store persisting measured EBU R128 integrated loudness per track so subsequent plays
 * normalize instantaneously from sample 0 without an initial convergence lag.
 */
internal class TrackLoudnessStore(private val file: File) : java.io.Closeable {
    constructor(context: Context, suffix: String) : this(File(context.filesDir, "track-loudness$suffix.json"))

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val lru = object : LinkedHashMap<String, CachedTrackLoudness>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedTrackLoudness>?): Boolean =
            size > MAX_ENTRIES
    }
    private var loaded = false
    private var dirty = false
    private var flushJob: Job? = null

    init {
        scope.launch { loadFromDisk() }
    }

    fun lookup(trackKey: String): CachedTrackLoudness? {
        if (trackKey.isBlank()) return null
        ensureLoadedSync()
        return synchronized(lock) { lru[trackKey] }
    }

    fun record(trackKey: String, lufs: Float, peak: Float, gatedBlocks: Int) {
        if (trackKey.isBlank() || !lufs.isFinite() || lufs !in -65f..6f || gatedBlocks < 50) return
        ensureLoadedSync()
        val sanitizedPeak = if (peak.isFinite() && peak > 0f) peak.coerceAtMost(4f) else 0f
        val changed = synchronized(lock) {
            val existing = lru[trackKey]
            val shouldReplace = existing == null ||
                (existing.gatedBlocks < 300 && gatedBlocks >= existing.gatedBlocks * 2) ||
                (existing.gatedBlocks < 150 && gatedBlocks >= 150)
            if (!shouldReplace) {
                false
            } else {
                lru[trackKey] = CachedTrackLoudness(lufs, sanitizedPeak, gatedBlocks)
                dirty = true
                true
            }
        }
        if (changed) scheduleFlush()
    }

    internal suspend fun flushNow() {
        val snapshot = synchronized(lock) {
            if (!dirty) return
            dirty = false
            TrackLoudnessSnapshot(entries = lru.toMap())
        }
        writeSnapshot(snapshot)
    }

    private fun scheduleFlush() {
        synchronized(lock) {
            if (flushJob?.isActive == true) return
            flushJob = scope.launch {
                delay(1_500)
                flushNow()
            }
        }
    }

    private fun ensureLoadedSync() {
        if (loaded) return
        loadFromDisk()
    }

    private fun loadFromDisk() {
        synchronized(lock) {
            if (loaded) return
            loaded = true
            if (!file.isFile) return
            runCatching {
                val parsed = json.decodeFromString(TrackLoudnessSnapshot.serializer(), file.readText())
                if (parsed.version == SNAPSHOT_VERSION) {
                    parsed.entries.entries.take(MAX_ENTRIES).forEach { (k, v) ->
                        if (k.isNotBlank() && v.lufs.isFinite() && v.lufs in -65f..6f) {
                            lru[k] = v
                        }
                    }
                }
            }
        }
    }

    private fun writeSnapshot(snapshot: TrackLoudnessSnapshot) {
        try {
            val directory = file.absoluteFile.parentFile
            directory?.mkdirs()
            val temp = File.createTempFile("track-loudness-", ".tmp", directory)
            try {
                temp.writeText(json.encodeToString(TrackLoudnessSnapshot.serializer(), snapshot))
                Files.move(
                    temp.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } finally {
                runCatching { Files.deleteIfExists(temp.toPath()) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Disk cache writes are best-effort; never interrupt playback.
        }
    }

    override fun close() {
        scope.cancel()
    }
}

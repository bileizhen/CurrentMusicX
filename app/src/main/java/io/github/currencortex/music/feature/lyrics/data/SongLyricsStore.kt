// Per-song imported/matched lyric priority follows Folia Major, AGPL-3.0.
// Reference: chthollyphile/folia-major aecd330b720eb8fc728e6b10ebcec261b144d683.
package io.github.currencortex.music.feature.lyrics.data

import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

@Serializable data class LyricsOrigin(val label: String, val provider: String = "", val reference: String = "")
@Serializable data class SavedLyrics(val document: LyricsDocument, val origin: LyricsOrigin)
@Serializable data class LyricsSongSettings(val offsetMs: Long = 0,
    val importedOrigin: LyricsOrigin? = null, val matchedOrigin: LyricsOrigin? = null,
    val useImported: Boolean = false, val contentRevision: Long = 0) {
    val activeOrigin: LyricsOrigin? get() = if (useImported) importedOrigin ?: matchedOrigin else matchedOrigin
}

/** Durable user choices, separate from disposable provider caches. Positive offset delays lyrics. */
class SongLyricsStore(private val directory: File) {
    @Serializable private data class Entry(val settings: LyricsSongSettings = LyricsSongSettings(),
        val imported: String? = null, val matched: String? = null)
    @Serializable private data class Index(val version: Int = 1, val entries: Map<String, Entry> = emptyMap())

    private val mutex = Mutex()
    private var blockedReason: String? = null
    private var previousIndex: ByteArray? = null
    /** Recovery is visible to the management UI; unrecoverable indexes block writes, never erase data. */
    var recoveryMessage: String? = null
        private set
    private var entries: Map<String, Entry> = readIndex()
    private val mutable = MutableStateFlow(entries.mapValues { it.value.settings })
    val state: StateFlow<Map<String, LyricsSongSettings>> = mutable.asStateFlow()

    fun settings(song: Song): LyricsSongSettings = state.value[identity(song)] ?: LyricsSongSettings()

    suspend fun active(song: Song): SavedLyrics? = mutex.withLock { withContext(Dispatchers.IO) {
        val entry = entries[identity(song)] ?: return@withContext null
        val name = if (entry.settings.useImported) entry.imported ?: entry.matched else entry.matched
        name?.let { readSaved(it) }
    } }

    suspend fun setOffset(song: Song, value: Long) = mutate(song) { previous ->
        previous.copy(settings = previous.settings.copy(offsetMs = value.coerceIn(-60_000, 60_000)))
    }

    suspend fun saveImported(song: Song, saved: SavedLyrics, offsetMs: Long? = null) = save(song, saved, true, offsetMs)
    suspend fun saveMatched(song: Song, saved: SavedLyrics) = save(song, saved, false, null)

    suspend fun selectImported(song: Song, enabled: Boolean) = mutate(song) { previous ->
        require(!enabled || previous.imported != null) { "尚未导入这首歌的歌词" }
        if (previous.settings.useImported == enabled) previous else previous.copy(settings = previous.settings.copy(
            useImported = enabled, contentRevision = nextRevision(previous)))
    }

    /** Restore provider lookup while retaining the user's independent timing correction. */
    suspend fun reset(song: Song) = mutate(song) { previous ->
        if (previous.imported == null && previous.matched == null) previous else Entry(previous.settings.copy(
            importedOrigin = null, matchedOrigin = null, useImported = false, contentRevision = nextRevision(previous)))
    }

    private suspend fun save(song: Song, saved: SavedLyrics, imported: Boolean, offsetMs: Long?) = mutex.withLock {
        withContext(Dispatchers.IO) {
            checkWritable()
            validateOrigin(saved.origin)
            val clean = saved.copy(document = saved.document.withoutEstimatedTiming())
            LyricsFileCodec.validate(clean.document)
            val bytes = json.encodeToString(clean).toByteArray(Charsets.UTF_8)
            require(bytes.size <= LyricsFileCodec.MAX_BYTES) { "歌词文件过大，最多支持 2 MB" }
            val key = identity(song)
            require(key.length <= 1024) { "歌曲标识过长" }
            require(entries.containsKey(key) || entries.size < MAX_SONGS) { "已保存的单曲歌词设置达到 1000 首上限" }
            val previous = entries[key] ?: Entry()
            ensureDirectory()
            val name = "${UUID.randomUUID()}.json"
            val target = File(directory, name)
            var committed = false
            try {
                atomicWrite(target, bytes)
                currentCoroutineContext().ensureActive()
                val options = previous.settings.copy(
                    offsetMs = offsetMs?.coerceIn(-60_000, 60_000) ?: previous.settings.offsetMs,
                    importedOrigin = if (imported) clean.origin else previous.settings.importedOrigin,
                    matchedOrigin = if (imported) previous.settings.matchedOrigin else clean.origin,
                    useImported = imported,
                    contentRevision = nextRevision(previous))
                val next = if (imported) previous.copy(settings = options, imported = name)
                    else previous.copy(settings = options, matched = name)
                commit(key, next)
                committed = true
            } finally { if (!committed) target.delete() }
        }
    }

    private suspend fun mutate(song: Song, transform: (Entry) -> Entry) = mutex.withLock {
        withContext(Dispatchers.IO) {
            checkWritable()
            val key = identity(song)
            require(key.length <= 1024) { "歌曲标识过长" }
            val previous = entries[key] ?: Entry()
            val next = transform(previous)
            if (next == previous) return@withContext
            require(entries.containsKey(key) || entries.size < MAX_SONGS) { "已保存的单曲歌词设置达到 1000 首上限" }
            currentCoroutineContext().ensureActive()
            commit(key, next)
        }
    }

    /** Unique document files plus an atomic index make the index the only commit point. */
    private suspend fun commit(key: String, next: Entry) = withContext(NonCancellable) {
        ensureDirectory()
        val updated = entries + (key to next)
        val bytes = json.encodeToString(Index(entries = updated)).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_INDEX_BYTES) { "歌词设置索引过大" }
        previousIndex?.let { atomicWrite(File(directory, "index.backup.json"), it) }
        atomicWrite(File(directory, "index.json"), bytes)
        val previousEntries = entries
        entries = updated
        previousIndex = bytes
        mutable.value = updated.mapValues { it.value.settings }
        // Keep both generations' documents: a valid backup must remain usable after replacement.
        val retained = (updated.values + previousEntries.values).flatMap { listOfNotNull(it.imported, it.matched) }.toSet()
        runCatching {
            directory.listFiles()?.filter { it.isFile && FILE_NAME.matches(it.name) && it.name !in retained && !Files.isSymbolicLink(it.toPath()) }
                ?.forEach { it.delete() }
        }
    }

    private fun readIndex(): Map<String, Entry> {
        val primary = File(directory, "index.json")
        val backup = File(directory, "index.backup.json")
        val primaryPresent = Files.exists(primary.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)
        val backupPresent = Files.exists(backup.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)
        if (!primaryPresent && !backupPresent) {
            if (directory.listFiles()?.any { it.name.startsWith("index") && ".corrupt-" in it.name } == true) {
                blockedReason = "单曲歌词索引仅剩损坏文件，原文件已保留；为防止覆盖，暂时无法修改歌词设置"
                recoveryMessage = blockedReason
            }
            return emptyMap()
        }
        val first = if (primaryPresent) runCatching { decodeIndex(primary) } else null
        val second = if (backupPresent) runCatching { decodeIndex(backup) } else null
        if (first?.isSuccess == true) {
            previousIndex = first.getOrThrow().first
            if (second?.isFailure == true) {
                recoveryMessage = "歌词设置备份损坏，原文件已保留；当前设置仍可读取"
                if (!preserveDamaged(backup)) blockedReason = "无法保留损坏的歌词备份，暂时无法修改歌词设置"
            }
            return first.getOrThrow().second
        }
        if (second?.isSuccess == true) {
            previousIndex = second.getOrThrow().first
            recoveryMessage = "歌词设置索引无法读取，已从上一次保存的备份恢复；损坏原文件已保留"
            if (primaryPresent && !preserveDamaged(primary)) blockedReason = "无法保留损坏的歌词索引，已恢复的设置仅可读取"
            return second.getOrThrow().second
        }
        // Do not reinterpret an unreadable existing library as an empty writable library.
        if (primaryPresent) preserveDamaged(primary)
        if (backupPresent) preserveDamaged(backup)
        blockedReason = "已保存的单曲歌词索引及备份无法读取，原文件已保留；为防止覆盖，暂时无法修改歌词设置"
        recoveryMessage = blockedReason
        return emptyMap()
    }

    private fun decodeIndex(file: File): Pair<ByteArray, Map<String, Entry>> {
        val bytes = boundedRead(file, MAX_INDEX_BYTES)
        val index = json.decodeFromString<Index>(bytes.toString(Charsets.UTF_8))
        require(index.version == 1 && index.entries.size <= MAX_SONGS) { "不支持的歌词设置索引" }
        index.entries.forEach { (key, value) ->
            require(key.length <= 1024 && value.settings.offsetMs in -60_000L..60_000L && value.settings.contentRevision >= 0)
            listOfNotNull(value.imported, value.matched).forEach { name ->
                require(FILE_NAME.matches(name))
                val document = File(directory, name)
                require(document.isFile && !Files.isSymbolicLink(document.toPath()) && document.length() <= LyricsFileCodec.MAX_BYTES)
            }
            listOfNotNull(value.settings.importedOrigin, value.settings.matchedOrigin).forEach(::validateOrigin)
        }
        return bytes to index.entries.toMap()
    }

    private fun preserveDamaged(file: File): Boolean = runCatching {
        // Move only this exact directory entry; no recursive traversal or symlink following.
        val target = File(directory, "${file.nameWithoutExtension}.corrupt-${UUID.randomUUID()}.json")
        require(file.parentFile.canonicalFile == directory.canonicalFile && target.parentFile.canonicalFile == directory.canonicalFile)
        try { Files.move(file.toPath(), target.toPath(), ATOMIC_MOVE) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(file.toPath(), target.toPath()) }
    }.isSuccess

    private fun boundedRead(file: File, maximum: Long): ByteArray {
        require(file.isFile && !Files.isSymbolicLink(file.toPath()) && file.length() <= maximum) { "保存的歌词文件无法读取或超过大小限制" }
        file.inputStream().use { input ->
            val result = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val size = input.read(buffer)
                if (size < 0) break
                require(result.size().toLong() + size <= maximum) { "保存的歌词文件超过大小限制" }
                result.write(buffer, 0, size)
            }
            return result.toByteArray()
        }
    }

    private fun readSaved(name: String): SavedLyrics {
        require(FILE_NAME.matches(name))
        val file = File(directory, name)
        val result = json.decodeFromString<SavedLyrics>(boundedRead(file, LyricsFileCodec.MAX_BYTES.toLong()).toString(Charsets.UTF_8))
        validateOrigin(result.origin)
        val clean = result.copy(document = result.document.withoutEstimatedTiming())
        LyricsFileCodec.validate(clean.document)
        return clean
    }

    private fun ensureDirectory() { check(directory.isDirectory || directory.mkdirs()) { "无法保存单曲歌词" } }
    private fun checkWritable() { check(blockedReason == null) { blockedReason.orEmpty() } }
    private fun nextRevision(entry: Entry): Long {
        check(entry.settings.contentRevision < Long.MAX_VALUE) { "歌词设置版本过大" }
        return entry.settings.contentRevision + 1
    }

    private fun atomicWrite(destination: File, bytes: ByteArray) {
        val temporary = File.createTempFile("lyrics-", ".tmp", directory)
        try {
            temporary.outputStream().use { it.write(bytes); it.fd.sync() }
            try { Files.move(temporary.toPath(), destination.toPath(), REPLACE_EXISTING, ATOMIC_MOVE) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary.toPath(), destination.toPath(), REPLACE_EXISTING) }
        } finally { temporary.delete() }
    }

    companion object {
        const val MAX_SONGS = 1000
        private const val MAX_INDEX_BYTES = 8 * 1024 * 1024L
        private val FILE_NAME = Regex("[0-9a-f-]{36}\\.json")
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun identity(song: Song): String {
            if (song.musicSource == MusicSource.NAVIDROME) {
                val reference = song.navidrome
                val raw = "${reference?.identity.orEmpty()}\n${reference?.id ?: song.id}"
                return "navidrome:" + MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it.toInt() and 255) }
            }
            val external = when (song.musicSource) {
                MusicSource.NETEASE -> song.externalIds.neteaseId
                MusicSource.QQ_MUSIC -> song.externalIds.qqMusicId
                MusicSource.APPLE_MUSIC -> song.externalIds.appleMusicId
                MusicSource.SPOTIFY -> song.externalIds.spotifyId
                MusicSource.NAVIDROME -> null
            }
            return "${song.musicSource.name.lowercase(Locale.ROOT)}:${external?.takeIf { it.isNotBlank() } ?: song.id}"
        }
        private fun validateOrigin(origin: LyricsOrigin) {
            require(origin.label.length <= 256 && origin.provider.length <= 128 && origin.reference.length <= 2048) { "歌词来源信息过长" }
        }
    }
}

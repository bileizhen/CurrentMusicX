package io.github.currencortex.music.feature.playlistimport

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.netease.playlistimport.*
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.library.scanLocalMusic
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class PlaylistImportMode(val label: String) { LINKS("链接导入"), TEXT("文字导入"), IMAGES("图片导入"), LOCAL("本地导入") }

data class PlaylistImportState(val mode: PlaylistImportMode = PlaylistImportMode.LINKS,
    val links: List<String> = listOf(""), val text: String = "", val importStar: Boolean = false,
    val localSongs: List<Song> = emptyList(), val selected: Set<String> = emptySet(),
    val images: List<PlaylistImportImage> = emptyList(), val imagesLoading: Boolean = false,
    val targets: List<PlaylistImportTarget> = emptyList(), val targetId: Long? = null,
    val targetsLoading: Boolean = false, val targetsLoaded: Boolean = false, val targetsError: String? = null,
    val scanning: Boolean = false, val scanned: Boolean = false,
    val busy: Boolean = false, val task: PlaylistImportTask? = null, val result: PlaylistImportStatus? = null,
    val message: String? = null, val error: String? = null)

class PlaylistImportViewModel(private val repository: NeteasePlaylistImportRepository,
    private val onImported: () -> Unit) : ViewModel() {
    val account = repository.accounts
    private val mutable = MutableStateFlow(PlaylistImportState())
    val state = mutable.asStateFlow()
    private var requestJob: Job? = null
    private var scanJob: Job? = null
    private var imageJob: Job? = null
    private var targetsJob: Job? = null
    private var completedTask: Long? = null
    init { viewModelScope.launch {
        var previous: Long? = null
        account.collect { owner ->
            if (previous != null && previous != owner.revision) {
                requestJob?.cancel(); requestJob = null; completedTask = null
                imageJob?.cancel(); targetsJob?.cancel()
                mutable.update { it.copy(task = null, result = null, busy = false, message = null, error = null,
                    images = emptyList(), imagesLoading = false, targets = emptyList(), targetId = null,
                    targetsLoading = false, targetsLoaded = false, targetsError = null) }
            }
            previous = owner.revision
            if (owner.loggedIn && !owner.stale && state.value.mode == PlaylistImportMode.IMAGES && !state.value.targetsLoaded) loadTargets()
        }
    } }
    private fun edit(change: (PlaylistImportState) -> PlaylistImportState) {
        if (!state.value.busy && !state.value.imagesLoading && state.value.task == null) mutable.update { change(it).copy(error = null) }
    }
    fun mode(value: PlaylistImportMode) {
        edit { it.copy(mode = value) }
        if (state.value.mode == PlaylistImportMode.IMAGES && !state.value.targetsLoaded) loadTargets()
    }
    fun link(index: Int, text: String) = edit { it.copy(links = it.links.mapIndexed { i, old -> if (i == index) text else old }) }
    fun addLink() = edit { if (it.links.size < 10) it.copy(links = it.links + "") else it }
    fun removeLink(index: Int) = edit { if (it.links.size > 1) it.copy(links = it.links.filterIndexed { i, _ -> i != index }) else it }
    fun text(value: String) = edit { it.copy(text = value) }
    fun importStar(value: Boolean) = edit { it.copy(importStar = value) }
    fun select(song: Song) = edit { it.copy(selected = if (song.localUri in it.selected) it.selected - song.localUri else it.selected + song.localUri) }
    fun selectAll() = edit { it.copy(selected = if (it.selected.size == it.localSongs.size) emptySet() else it.localSongs.map(Song::localUri).toSet()) }
    fun localPermissionLost() {
        scanJob?.cancel()
        mutable.update { it.copy(localSongs = emptyList(), selected = emptySet(), scanned = false, scanning = false) }
    }
    fun target(id: Long) = edit { if (it.targets.any { target -> target.id == id }) it.copy(targetId = id) else it }
    fun removeImage(index: Int) = edit { it.copy(images = it.images.filterIndexed { i, _ -> i != index }) }
    fun loadTargets() {
        if (state.value.targetsLoading || state.value.task != null || !account.value.loggedIn || account.value.stale) return
        val revision = account.value.revision
        mutable.update { it.copy(targetsLoading = true, targetsError = null) }
        targetsJob = viewModelScope.launch {
            try {
                val targets = repository.targets()
                if (account.value.revision == revision) mutable.update { it.copy(targets = targets, targetsLoaded = true,
                    targetId = it.targetId?.takeIf { id -> targets.any { target -> target.id == id } }) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (account.value.revision == revision) mutable.update { it.copy(targetsError = failureMessage(error)) } }
            finally { if (account.value.revision == revision) mutable.update { it.copy(targetsLoading = false) } }
        }
    }
    fun readImages(context: Context, uris: List<Uri>) {
        if (uris.isEmpty() || state.value.busy || state.value.imagesLoading || state.value.task != null) return
        if (uris.size > 9) { mutable.update { it.copy(error = "最多选择 9 张图片，请重新选择") }; return }
        val revision = account.value.revision
        mutable.update { it.copy(imagesLoading = true, error = null) }
        imageJob = viewModelScope.launch {
            try {
                val images = withContext(Dispatchers.IO) {
                    val resolver = context.applicationContext.contentResolver
                    uris.distinct().mapIndexed { index, uri ->
                        ensureActive()
                        val mime = resolver.getType(uri).orEmpty()
                        require(mime.startsWith("image/")) { "请选择有效的图片文件" }
                        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                            if (it.moveToFirst()) it.getString(0) else null
                        }?.take(160) ?: "playlist-${index + 1}.jpg"
                        val bytes = requireNotNull(resolver.openInputStream(uri)) { "无法读取所选图片" }.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(32 * 1024)
                            val limit = 8 * 1024 * 1024
                            while (output.size() <= limit) {
                                ensureActive()
                                val read = input.read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
                                if (read < 0) break
                                if (read > 0) output.write(buffer, 0, read)
                            }
                            require(output.size() in 1..limit) { "每张图片必须小于或等于 8 MB，且不能为空" }
                            output.toByteArray()
                        }
                        PlaylistImportImage(bytes, name, mime)
                    }
                }
                if (account.value.revision == revision) mutable.update { it.copy(images = images) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (account.value.revision == revision) mutable.update { it.copy(error = failureMessage(error)) } }
            finally { if (account.value.revision == revision) mutable.update { it.copy(imagesLoading = false) } }
        }
    }
    fun scan(context: Context) {
        if (state.value.scanning || state.value.busy || state.value.task != null) return
        mutable.update { it.copy(scanning = true, error = null) }
        scanJob = viewModelScope.launch {
            try {
                val songs = withContext(Dispatchers.IO) { scanLocalMusic(context.applicationContext) }
                mutable.update { it.copy(localSongs = songs, selected = it.selected.intersect(songs.map(Song::localUri).toSet()), scanned = true) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(error = "无法扫描本地音乐，请检查音频权限后重试") } }
            finally { mutable.update { it.copy(scanning = false) } }
        }
    }
    fun submit() {
        val input = state.value
        if (input.busy || input.scanning || input.imagesLoading || input.task != null) return
        if (!account.value.loggedIn || account.value.stale) return
        val valid = when (input.mode) {
            PlaylistImportMode.LINKS -> input.links.any(String::isNotBlank)
            PlaylistImportMode.TEXT -> input.text.isNotBlank()
            PlaylistImportMode.LOCAL -> input.selected.isNotEmpty()
            PlaylistImportMode.IMAGES -> input.images.isNotEmpty() && input.targetId != null && input.targets.any { it.id == input.targetId }
        }
        if (!valid) { mutable.update { it.copy(error = when (input.mode) {
            PlaylistImportMode.LOCAL -> "请先选择本地歌曲"
            PlaylistImportMode.IMAGES -> "请先选择图片和目标网易云歌单"
            else -> "请先填写导入内容"
        }) }; return }
        val revision = account.value.revision
        mutable.update { it.copy(busy = true, error = null, message = "正在提交导入任务…") }
        var pending: PlaylistImportState? = input
        requestJob = viewModelScope.launch {
            try {
                val task = createTask(requireNotNull(pending).also { pending = null })
                if (account.value.revision != revision) return@launch
                mutable.update { it.copy(task = task, images = emptyList(), message = "任务已提交到网易云，离开页面不会撤回服务端任务。") }
                repeat(60) { attempt ->
                    if (updateStatus(task, revision)) return@launch
                    if (attempt < 59) delay(5_000)
                }
                mutable.update { it.copy(message = "服务端仍在处理，可稍后手动刷新进度。") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (account.value.revision == revision) mutable.update { it.copy(error = failureMessage(error),
                    message = if (it.task != null) "服务端任务已提交，可手动刷新进度。" else null) }
            } finally { if (account.value.revision == revision) mutable.update { it.copy(busy = false) } }
        }
    }
    private suspend fun createTask(input: PlaylistImportState): PlaylistImportTask = when (input.mode) {
        PlaylistImportMode.LINKS -> repository.submitLinks(input.links.filter(String::isNotBlank), input.importStar)
        PlaylistImportMode.TEXT -> repository.submitText(input.text, input.importStar)
        PlaylistImportMode.LOCAL -> repository.submitLocal(input.localSongs.filter { it.localUri in input.selected }, input.importStar)
        PlaylistImportMode.IMAGES -> repository.submitImages(input.images, requireNotNull(input.targetId))
    }
    private suspend fun updateStatus(task: PlaylistImportTask, revision: Long): Boolean {
        val result = repository.status(task)
        currentCoroutineContext().ensureActive()
        if (account.value.revision != revision) throw CancellationException("Account changed")
        mutable.update { it.copy(result = result, error = null, message = result.message.ifBlank {
            if (result.finished) { if (result.successful) "导入完成" else "导入未完成" } else "网易云正在匹配歌曲…"
        }) }
        if (result.finished && completedTask != task.id && (result.successful || result.playlistIds.isNotEmpty())) {
            completedTask = task.id; onImported()
        }
        return result.finished
    }
    fun refresh() {
        val task = state.value.task ?: return
        if (state.value.busy) return
        val revision = account.value.revision
        mutable.update { it.copy(busy = true, error = null) }
        requestJob = viewModelScope.launch {
            try { updateStatus(task, revision) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (account.value.revision == revision) mutable.update { it.copy(error = failureMessage(error)) } }
            finally { if (account.value.revision == revision) mutable.update { it.copy(busy = false) } }
        }
    }
    fun newImport() {
        if (state.value.busy || state.value.result?.finished != true) return
        mutable.update { it.copy(task = null, result = null, message = null, error = null) }
    }
    private fun failureMessage(error: Exception) = (error as? ApiException)?.let { it.userMessage ?: it.kind.message }
        ?: error.message?.take(180) ?: "操作失败，请稍后重试"
}

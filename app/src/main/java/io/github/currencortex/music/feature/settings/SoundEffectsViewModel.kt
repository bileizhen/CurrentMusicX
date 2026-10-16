package io.github.currencortex.music.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.data.effects.NeteaseSoundEffect
import io.github.currencortex.music.data.effects.NativeEffectTile
import io.github.currencortex.music.data.effects.parseNativeEffectsPage
import io.github.currencortex.music.data.settings.EqualizerPreset
import io.github.currencortex.music.core.media.originalEffectUnavailableReason
import io.github.currencortex.music.core.media.SoundEffectNoticePhase
import io.github.currencortex.music.core.media.SoundEffectNoticeResult
import io.github.currencortex.music.core.media.SoundEffectNoticeTier
import io.github.currencortex.music.core.media.originalEffectNoticeResult
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*

data class SoundCatalogState(val loading: Boolean = true, val items: List<NeteaseSoundEffect> = emptyList(),
    val error: String? = null)
data class OriginalEngineImportState(val loading: Boolean = false, val message: String? = null, val failed: Boolean = false)
data class NativeEffectsPageState(val loading: Boolean = true, val banner: List<NativeEffectTile> = emptyList(),
    val featured: List<NativeEffectTile> = emptyList(), val error: String? = null)
enum class SoundEffectKind { AI_MASTER, SPEAKER, MOOD, SMART, ORIGINAL, LOCAL_SMART, MANUAL }
data class SoundEffectSelection(val kind: SoundEffectKind = SoundEffectKind.MOOD,
    val name: String = "AI 情绪调音师", val id: Long? = null)

class SoundEffectsViewModel(private val container: AppContainer) : ViewModel() {
    internal val originalPageApi by lazy { OriginalEffectsPageApi(container, this) }
    internal val originalPageAccount = container.neteaseSessions.state
    val preferences = container.soundEffectsSettings.state
    val tuning = container.smartTuning.state
    val manual = container.equalizerSettings.state
    val runtime = container.equalizer.state
    val playback = container.playerController.state
    val queue = container.playbackQueue.state
    private val auditionController = container.createEffectsAudition(viewModelScope)
    val audition = auditionController.state
    val masterDetail = MutableStateFlow<io.github.currencortex.music.data.effects.OriginalAiMasterMaterial?>(null)
    val masterDetailSongId = MutableStateFlow<Long?>(null)
    val masterDetailLoading = MutableStateFlow(false)
    val masterDetailError = MutableStateFlow<String?>(null)
    private var detailRequest: Job? = null
    val original = container.originalSoundEffects.state
    val originalSmart = container.originalSmartEffects.state
    val originalMood = container.originalMoodEffects.state
    val originalAiMaster = container.originalAiMasterEffects.state
    val originalSpeaker = container.originalSpeakerEffects.state
    val catalog = MutableStateFlow(SoundCatalogState())
    val page = MutableStateFlow(NativeEffectsPageState())
    val more = MutableStateFlow(NativeEffectsPageState())
    val tileLoading = MutableStateFlow<Long?>(null)
    val actionError = MutableStateFlow<String?>(null)
    val originalImport = MutableStateFlow(OriginalEngineImportState())
    val lastSelection = MutableStateFlow(SoundEffectSelection())
    private var feedbackRequest: Job? = null
    private var feedbackToken: Long? = null
    private var userSelected = false
    private var request: Job? = null
    private var importRequest: Job? = null
    private var localRequest: Job? = null
    private var pageRequest: Job? = null
    private var moreRequest: Job? = null
    private var moreVisited = false
    private var tileRequest: Job? = null
    private var tileGeneration = 0L

    init {
        viewModelScope.launch {
            combine(originalMood, originalSmart, original, preferences, manual) { mood, smart, single, local, eq ->
                when {
                    mood.requestedEnabled -> SoundEffectSelection()
                    smart.requestedEnabled -> SoundEffectSelection(SoundEffectKind.SMART, "网易云智能音效")
                    single.requestedId != null || single.selectedId != null -> SoundEffectSelection(SoundEffectKind.ORIGINAL,
                        single.requestedName ?: single.selectedName.orEmpty(), single.requestedId ?: single.selectedId)
                    local.smartEnabled -> SoundEffectSelection(SoundEffectKind.LOCAL_SMART, "本机智能调音")
                    eq.enabled -> SoundEffectSelection(SoundEffectKind.MANUAL, "均衡器 · ${eq.preset.label}")
                    else -> null
                }
            }.collect { if (!userSelected && it != null) lastSelection.value = it }
        }
        viewModelScope.launch {
            combine(originalAiMaster, originalSpeaker) { master, speaker -> when {
                speaker.requestedEnabled -> SoundEffectSelection(SoundEffectKind.SPEAKER, "外放音效")
                master.requestedEnabled -> SoundEffectSelection(SoundEffectKind.AI_MASTER, "AI 调音大师")
                else -> null
            } }.collect { if (!userSelected && it != null) lastSelection.value = it }
        }
        viewModelScope.launch {
            container.neteaseSessions.state.map { it.revision }.distinctUntilChanged().collect {
                if (userSelected) clearFeedback()
                cancelTile()
                moreRequest?.cancel()
                more.value = NativeEffectsPageState()
                page.value = NativeEffectsPageState()
                refreshCatalog(); refreshPage()
                if (moreVisited) refreshMore()
            }
        }
    }
    private fun clearFeedback() {
        feedbackRequest?.cancel(); feedbackRequest = null
        feedbackToken?.let(container.soundEffectNotices::clear); feedbackToken = null
    }
    private fun notice(name: String, membership: Int, results: Flow<SoundEffectNoticeResult?>) {
        clearFeedback()
        val token = container.soundEffectNotices.begin(name, SoundEffectNoticeTier.fromMembership(membership))
        feedbackToken = token
        feedbackRequest = viewModelScope.launch {
            val result = withTimeoutOrNull(35_000) { results.filterNotNull().first() }
                ?: SoundEffectNoticeResult(SoundEffectNoticePhase.WAITING, "仍在准备，可在音效卡片查看状态")
            container.soundEffectNotices.complete(token, result)
        }
    }
    private fun failureNotice(name: String, membership: Int, message: String) {
        notice(name, membership, flowOf(SoundEffectNoticeResult(SoundEffectNoticePhase.FAILED, message)))
    }
    private fun originalMembership(effect: NeteaseSoundEffect): Int =
        (page.value.featured + page.value.banner + more.value.featured).firstOrNull { it.id == effect.id }?.membership
            ?: effect.memberType
    fun notifyUnavailable(effect: NeteaseSoundEffect) {
        originalUnavailableReason(effect)?.let { failureNotice(effect.name, originalMembership(effect), it) }
    }
    private fun localNotice(ready: Boolean, active: Boolean, error: String?, supported: Boolean): SoundEffectNoticeResult? = when {
        error != null -> SoundEffectNoticeResult(SoundEffectNoticePhase.FAILED, error)
        !supported -> SoundEffectNoticeResult(SoundEffectNoticePhase.FAILED, "此设备暂不支持本机均衡器")
        !ready -> null
        else -> SoundEffectNoticeResult(SoundEffectNoticePhase.READY, if (active) "音效已生效" else "已开启，播放后生效")
    }
    fun refreshMore() {
        moreVisited = true
        moreRequest?.cancel()
        moreRequest = viewModelScope.launch {
            more.value = more.value.copy(loading = true, error = null)
            try {
                val items = parseNativeEffectsPage(container.requestOriginalEffectsPage("/api/usertool/sound/queryAllSound",
                    buildJsonObject { put("exposeType", 3) }))
                more.value = NativeEffectsPageState(loading = false, featured = items)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { more.value = more.value.copy(loading = false, error = "暂时无法获取官方精选音效") }
        }
    }
    fun refreshPage() {
        pageRequest?.cancel()
        pageRequest = viewModelScope.launch {
            page.value = page.value.copy(loading = true, error = null)
            supervisorScope {
                suspend fun read(type: Int): List<NativeEffectTile> = parseNativeEffectsPage(
                    container.requestOriginalEffectsPage("/api/usertool/sound/queryAllSound", buildJsonObject { put("exposeType", type) }))
                val banner = async { runCatching { read(1) }.onFailure {
                    if (it is CancellationException) throw it
                } }
                val featured = async { runCatching { read(2) }.onFailure {
                    if (it is CancellationException) throw it
                } }
                val b = banner.await(); val f = featured.await()
                page.value = NativeEffectsPageState(false, b.getOrDefault(page.value.banner), f.getOrDefault(page.value.featured),
                    if (b.isFailure || f.isFailure) "部分音效暂时无法加载" else null)
            }
        }
    }
    /** Presentation metadata never supplies a preset; resolve an authoritative material first. */
    private fun cancelTile() { auditionController.stop(); ++tileGeneration; tileRequest?.cancel(); tileRequest = null; tileLoading.value = null }
    fun useTile(tile: NativeEffectTile) {
        actionError.value = null
        if (playback.value.mode == io.github.currencortex.music.core.media.PlayerMode.CAST) return
        when (tile.id) {
            104L -> { originalAiMasterEnabled(true); return }
            105L -> { originalSpeakerEnabled(true); return }
            106L -> { originalMoodEnabled(true); return }
        }
        if (tile.smart) originalSmartEnabled(true)
        else resolveAndUseOriginal(tile.id, tile.name, tile.membership)
    }
    private fun resolveAndUseOriginal(id: Long, name: String, membership: Int) {
        if (playback.value.mode == io.github.currencortex.music.core.media.PlayerMode.CAST) return
        cancelTile()
        container.originalSoundEffects.cancelPendingSelection()
        actionError.value = null
        userSelected = true
        val generation = tileGeneration
        tileLoading.value = id
        notice(name, membership, flow { kotlinx.coroutines.awaitCancellation() })
        tileRequest = viewModelScope.launch {
            try {
                // Catalog/presentation metadata can be stale. Explicit use always reads the
                // current Android material and authorization fields, without stopping old DSP.
                val effect = container.neteaseSoundEffects.resolve(id) ?: error("此音效尚无兼容的原版素材")
                currentCoroutineContext().ensureActive()
                if (generation != tileGeneration) return@launch
                originalEffectUnavailableReason(effect, original.value.copy(unsupported = original.value.unsupported - id))?.let { error(it) }
                if (playback.value.mode == io.github.currencortex.music.core.media.PlayerMode.CAST) return@launch
                // Applying must not cancel this successful resolver or fetch the same ID twice.
                tileRequest = null
                tileLoading.value = null
                applyResolvedOriginal(effect)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (generation == tileGeneration) {
                    val message = failure.message?.take(180) ?: "音效启用失败"
                    actionError.value = message; failureNotice(name, membership, message)
                }
            }
            finally { if (generation == tileGeneration) { tileRequest = null; tileLoading.value = null } }
        }
    }
    fun refreshCatalog() {
        request?.cancel()
        request = viewModelScope.launch {
            catalog.value = catalog.value.copy(loading = true, error = null)
            try { catalog.value = SoundCatalogState(loading = false, items = container.neteaseSoundEffects.catalog()) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { catalog.value = catalog.value.copy(loading = false, error = "暂时无法获取网易云音效，请稍后重试") }
        }
    }
    fun smartEnabled(enabled: Boolean) {
        cancelTile()
        userSelected = true
        if (enabled) lastSelection.value = SoundEffectSelection(SoundEffectKind.LOCAL_SMART, "本机智能调音")
        localRequest?.cancel()
        if (enabled) {
            container.originalSoundEffects.clear()
            container.originalSmartEffects.disable()
            container.originalMoodEffects.disable(); disableAdditional()
        }
        localRequest = viewModelScope.launch { container.soundEffectsSettings.setSmartEnabled(enabled) }
        if (enabled) notice("本机智能调音", 0, combine(preferences, runtime) { local, engine ->
            localNotice(local.smartEnabled, engine.active, engine.error, engine.supported)
        }) else clearFeedback()
    }
    fun useOriginal(effect: NeteaseSoundEffect) = resolveAndUseOriginal(effect.id, effect.name, originalMembership(effect))
    private fun applyResolvedOriginal(effect: NeteaseSoundEffect) {
        lastSelection.value = SoundEffectSelection(SoundEffectKind.ORIGINAL, effect.name, effect.id)
        localRequest?.cancel()
        // The controller's beforeSelection callback switches other modes only after the
        // new preset has downloaded, passed membership checks and decoded successfully.
        container.originalSoundEffects.select(effect)
        notice(effect.name, originalMembership(effect), original.map { value -> when {
            value.error != null -> SoundEffectNoticeResult(SoundEffectNoticePhase.FAILED, value.error)
            value.loading || value.permissionPending -> null
            value.selectedId == effect.id -> SoundEffectNoticeResult(SoundEffectNoticePhase.READY,
                if (value.processing) "音效已生效" else "已开启，播放后生效")
            value.requestedId == effect.id && value.unavailableReason != null ->
                SoundEffectNoticeResult(SoundEffectNoticePhase.FAILED, value.unavailableReason)
            else -> null
        } })
    }
    fun clearOriginal() { cancelTile(); container.originalSoundEffects.clear() }
    fun retryOriginal() {
        cancelTile()
        localRequest?.cancel()
        container.originalMoodEffects.disable(); disableAdditional()
        container.originalSmartEffects.disable()
        container.originalSoundEffects.retry()
    }
    fun originalSmartEnabled(enabled: Boolean) {
        cancelTile()
        userSelected = true
        if (enabled) lastSelection.value = SoundEffectSelection(SoundEffectKind.SMART, "网易云智能音效")
        localRequest?.cancel()
        if (enabled) {
            container.originalSoundEffects.clear()
            container.originalMoodEffects.disable(); disableAdditional()
        }
        container.originalSmartEffects.setEnabled(enabled)
        if (enabled) notice("网易云智能音效", 3, originalSmart.map {
            originalEffectNoticeResult(it.loading, it.enabled, it.requestedEnabled, it.processing, it.message)
        }) else clearFeedback()
    }
    fun retryOriginalSmart() {
        cancelTile()
        localRequest?.cancel()
        container.originalMoodEffects.disable(); disableAdditional()
        container.originalSoundEffects.clear()
        if (originalSmart.value.requestedEnabled) container.originalSmartEffects.refresh()
        else originalSmartEnabled(true)
    }
    fun originalMoodEnabled(enabled: Boolean) {
        if (enabled) disableAdditional()
        cancelTile()
        userSelected = true
        if (enabled) lastSelection.value = SoundEffectSelection()
        localRequest?.cancel()
        if (enabled) {
            container.originalSoundEffects.clear()
            container.originalSmartEffects.disable()
        }
        container.originalMoodEffects.setEnabled(enabled)
        if (enabled) notice("AI 情绪调音师", 4, originalMood.map {
            originalEffectNoticeResult(it.loading, it.enabled, it.requestedEnabled, it.processing, it.message)
        }) else clearFeedback()
    }
    fun retryOriginalMood() {
        disableAdditional()
        cancelTile()
        localRequest?.cancel()
        container.originalSoundEffects.clear()
        container.originalSmartEffects.disable()
        if (originalMood.value.requestedEnabled) container.originalMoodEffects.refresh()
        else originalMoodEnabled(true)
    }
    fun loadMasterDetail() {
        detailRequest?.cancel()
        masterDetail.value = null
        masterDetailSongId.value = null
        masterDetailError.value = null
        val song = playback.value.song ?: queue.value.current ?: return
        if (song.video || song.musicSource != io.github.currencortex.music.data.song.MusicSource.NETEASE) return
        detailRequest = viewModelScope.launch {
            masterDetailLoading.value = true
            try { masterDetail.value = container.originalAiMasterRepository.prepare(song.id); masterDetailSongId.value = song.id }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { masterDetailError.value = failure.message?.take(160) ?: "暂时无法获取此歌曲的原版调音分析" }
            finally { masterDetailLoading.value = false }
        }
    }
    fun saveMasterDiy(selected: Set<String>) {
        auditionController.stop()
        container.originalAiMasterEffects.setDiy(selected)
    }
    fun previewSpeaker(scene: io.github.currencortex.music.data.effects.OriginalSpeakerScene) = auditionController.speaker(scene)
    fun previewMaster(processed: Boolean) = auditionController.master(playback.value.song ?: queue.value.current,
        processed, originalAiMaster.value.selectedDiy)
    fun stopPreview() = auditionController.stop()
    fun togglePlayback() { auditionController.stop(resume = false); container.playerController.toggle() }
    private fun disableAdditional() {
        container.originalAiMasterEffects.disable()
        container.originalSpeakerEffects.disable()
    }
    fun originalAiMasterEnabled(enabled: Boolean) {
        if (enabled && playback.value.mode == io.github.currencortex.music.core.media.PlayerMode.CAST) return
        cancelTile(); localRequest?.cancel(); userSelected = true
        if (enabled) {
            lastSelection.value = SoundEffectSelection(SoundEffectKind.AI_MASTER, "AI 调音大师")
            container.originalSpeakerEffects.disable()
            container.originalMoodEffects.disable()
            container.originalSmartEffects.disable()
            container.originalSoundEffects.clear()
        }
        container.originalAiMasterEffects.setEnabled(enabled)
        if (enabled) notice("AI 调音大师", 4, originalAiMaster.map {
            originalEffectNoticeResult(it.loading, it.enabled, it.requestedEnabled, it.processing, it.message)
        }) else clearFeedback()
    }
    fun originalSpeakerEnabled(enabled: Boolean) {
        if (enabled && playback.value.mode == io.github.currencortex.music.core.media.PlayerMode.CAST) return
        cancelTile(); localRequest?.cancel(); userSelected = true
        if (enabled) {
            lastSelection.value = SoundEffectSelection(SoundEffectKind.SPEAKER, "外放音效")
            container.originalAiMasterEffects.disable()
            container.originalMoodEffects.disable()
            container.originalSmartEffects.disable()
            container.originalSoundEffects.clear()
        }
        container.originalSpeakerEffects.setEnabled(enabled)
        if (enabled) notice("外放音效", 4, originalSpeaker.map {
            originalEffectNoticeResult(it.loading, it.enabled, it.requestedEnabled, it.processing, it.message)
        }) else clearFeedback()
    }
    fun speakerScene(scene: io.github.currencortex.music.data.effects.OriginalSpeakerScene) {
        if (playback.value.mode == io.github.currencortex.music.core.media.PlayerMode.CAST) return
        container.originalSpeakerEffects.setScene(scene)
    }
    fun originalUnavailableReason(effect: NeteaseSoundEffect): String? =
        originalEffectUnavailableReason(effect, original.value)
    fun importOriginal(uri: android.net.Uri) = importEngine { container.importOriginalApk(uri) }
    fun importInstalledOriginal() = importEngine { container.importInstalledOriginal() }
    private fun importEngine(action: suspend () -> Unit) {
        if (importRequest?.isActive == true) return
        importRequest = viewModelScope.launch {
            originalImport.value = OriginalEngineImportState(loading = true, message = "正在校验并导入音效引擎…")
            try {
                action()
                container.refreshOriginalEngine()
                originalImport.value = OriginalEngineImportState(message = "音效引擎已导入，可以选择原版音效")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                originalImport.value = OriginalEngineImportState(message = failure.message?.take(180)
                    ?: "音效引擎导入失败，请选择兼容的网易云音乐安装包", failed = true)
            }
        }
    }
    fun useLocal(preset: EqualizerPreset) {
        cancelTile()
        userSelected = true
        lastSelection.value = SoundEffectSelection(SoundEffectKind.MANUAL, "均衡器 · ${preset.label}")
        localRequest?.cancel()
        container.originalMoodEffects.disable(); disableAdditional()
        container.originalSmartEffects.disable()
        container.originalSoundEffects.clear()
        val selected = manual.value.copy(enabled = true, preset = preset, gains = preset.gains)
        container.smartTuning.previewManual(selected)
        container.equalizer.configure(selected)
        localRequest = viewModelScope.launch {
            container.soundEffectsSettings.setSmartEnabled(false)
            container.equalizerSettings.select(preset)
            container.equalizerSettings.setEnabled(true)
        }
        notice("均衡器 · ${preset.label}", 0, combine(manual, runtime) { eq, engine ->
            localNotice(eq.enabled && eq.preset == preset, engine.active, engine.error, engine.supported)
        })
    }
    /** Cancels pending original downloads too, while retaining the user's manual curve. */
    fun turnOff() {
        clearFeedback()
        cancelTile()
        userSelected = true
        localRequest?.cancel()
        container.originalMoodEffects.disable(); disableAdditional()
        container.originalSmartEffects.disable()
        container.originalSoundEffects.clear()
        val disabled = manual.value.copy(enabled = false)
        container.smartTuning.previewManual(disabled)
        container.equalizer.configure(disabled)
        localRequest = viewModelScope.launch {
            container.soundEffectsSettings.setSmartEnabled(false)
            container.equalizerSettings.setEnabled(false)
        }
    }
    fun resumeEffect() {
        cancelTile()
        when (lastSelection.value.kind) {
            SoundEffectKind.AI_MASTER -> originalAiMasterEnabled(true)
            SoundEffectKind.SPEAKER -> originalSpeakerEnabled(true)
            SoundEffectKind.MOOD -> originalMoodEnabled(true)
            SoundEffectKind.SMART -> originalSmartEnabled(true)
            SoundEffectKind.LOCAL_SMART -> smartEnabled(true)
            SoundEffectKind.ORIGINAL -> catalog.value.items.firstOrNull { it.id == lastSelection.value.id }?.let(::useOriginal)
            SoundEffectKind.MANUAL -> {
                userSelected = true
                localRequest?.cancel()
                container.originalMoodEffects.disable(); disableAdditional()
                container.originalSmartEffects.disable()
                container.originalSoundEffects.clear()
                val selected = manual.value.copy(enabled = true)
                container.smartTuning.previewManual(selected)
                container.equalizer.configure(selected)
                localRequest = viewModelScope.launch {
                    container.soundEffectsSettings.setSmartEnabled(false)
                    container.equalizerSettings.setEnabled(true)
                }
            }
        }
    }
    override fun onCleared() {
        if (feedbackRequest?.isActive == true) clearFeedback()
        auditionController.close()
        detailRequest?.cancel()
        container.smartTuning.clearManualPreview()
    }
}

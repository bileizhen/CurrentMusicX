package io.github.currencortex.music.core.update

import io.github.currencortex.music.core.logging.LogSink
import io.github.currencortex.music.data.update.UpdateSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

data class UpdatePromptState(val release: AppRelease, val channel: UpdateChannel,
    val saving: Boolean = false, val error: String? = null)

class UpdateService(
    private val checker: ReleaseChecker,
    val settings: UpdateSettingsRepository,
    private val logger: LogSink,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = mutableState
    private val mutableDialogVisible = MutableStateFlow(false)
    val dialogVisible: StateFlow<Boolean> = mutableDialogVisible
    private val mutablePrompt = MutableStateFlow<UpdatePromptState?>(null)
    val prompt: StateFlow<UpdatePromptState?> = mutablePrompt
    private var checkedChannel = settings.state.value.channel
    private var checkedOnLaunch = false
    private var promptAllowed = true

    suspend fun checkOnLaunch() {
        if (checkedOnLaunch) return
        checkedOnLaunch = true
        try { check()
        } catch (cancelled: CancellationException) {
            checkedOnLaunch = false
            throw cancelled
        }
    }

    fun dismissDialog() {
        mutableDialogVisible.value = false
        if (mutableState.value == UpdateState.Checking) promptAllowed = false
    }

    fun dismissPrompt() {
        if (mutablePrompt.value?.saving == true) return
        mutablePrompt.value = null
        if (mutableState.value == UpdateState.Checking) promptAllowed = false
    }

    fun viewPrompt() {
        val prompt = mutablePrompt.value ?: return
        if (prompt.saving) return
        checkedChannel = prompt.channel
        mutableState.value = UpdateState.Available(prompt.release)
        mutablePrompt.value = null
        mutableDialogVisible.value = true
    }

    fun reopenDownload() { mutablePrompt.value = null; mutableDialogVisible.value = true }

    suspend fun ignoreCurrentRelease() {
        val prompt = mutablePrompt.value
        if (prompt?.saving == true) return
        val release = prompt?.release ?: (mutableState.value as? UpdateState.Available)?.release ?: return
        val channel = prompt?.channel ?: checkedChannel
        if (prompt != null) mutablePrompt.value = prompt.copy(saving = true, error = null)
        try {
            settings.ignoreRelease(channel, release.version)
            if (prompt == null) dismissDialog()
            else if (mutablePrompt.value?.release == release && mutablePrompt.value?.channel == channel) mutablePrompt.value = null
        } catch (cancelled: CancellationException) {
            if (prompt != null && mutablePrompt.value?.release == release && mutablePrompt.value?.channel == channel)
                mutablePrompt.value = prompt
            throw cancelled
        } catch (error: Exception) {
            if (prompt != null) {
                if (mutablePrompt.value?.release == release && mutablePrompt.value?.channel == channel)
                    mutablePrompt.value = prompt.copy(error = "无法保存忽略设置，请重试")
            } else mutableState.value = UpdateState.Failed("无法保存忽略设置：${error.message.orEmpty()}")
            logger.warn("Update", "Unable to save ignored release", error)
        }
    }

    suspend fun present() {
        mutablePrompt.value = null
        if (mutableState.value is UpdateState.Available && checkedChannel == settings.snapshot().channel) {
            mutableDialogVisible.value = true
        } else check(manual = true)
    }

    suspend fun check(manual: Boolean = false) {
        if (manual) { mutablePrompt.value = null; mutableDialogVisible.value = true }
        if (!mutex.tryLock()) return
        promptAllowed = true
        mutableState.value = UpdateState.Checking
        try {
            val channel = settings.snapshot().channel
            checkedChannel = channel
            val release = withContext(Dispatchers.IO) {
                logger.info("Update", "Checking GitHub releases on $channel channel")
                checker.check(channel).also {
                    logger.info("Update", if (it == null) "Already up to date" else "New release found: ${it.version}")
                }
            }
            mutableState.value = if (release == null) UpdateState.UpToDate else UpdateState.Available(release)
            val current = settings.snapshot()
            if (current.channel != channel) {
                mutableState.value = UpdateState.Idle
                mutableDialogVisible.value = false
                mutablePrompt.value = null
                return
            }
            if (!manual) mutablePrompt.value = if (release != null && promptAllowed && !mutableDialogVisible.value &&
                current.ignoredVersions[channel] != release.version) UpdatePromptState(release, channel) else null
        } catch (cancelled: CancellationException) {
            mutableState.value = UpdateState.Idle
            mutableDialogVisible.value = false
            mutablePrompt.value = null
            throw cancelled
        } catch (error: Exception) {
            withContext(Dispatchers.IO) { logger.warn("Update", "Update check failed", error) }
            mutableState.value = UpdateState.Failed(error.message ?: error::class.java.simpleName)
            mutablePrompt.value = null
        } finally {
            mutex.unlock()
        }
    }
}

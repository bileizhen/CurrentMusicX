package io.github.currencortex.music.core.update

import io.github.currencortex.music.core.logging.LogSink
import io.github.currencortex.music.data.update.UpdateSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

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

    suspend fun ignoreCurrentRelease() {
        val release = (mutableState.value as? UpdateState.Available)?.release ?: return
        try {
            settings.ignoreRelease(checkedChannel, release.version)
            dismissDialog()
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) {
            mutableState.value = UpdateState.Failed("无法保存忽略设置：${error.message.orEmpty()}")
            logger.warn("Update", "Unable to save ignored release", error)
        }
    }

    suspend fun present() {
        if (mutableState.value is UpdateState.Available && checkedChannel == settings.snapshot().channel) {
            mutableDialogVisible.value = true
        } else check(manual = true)
    }

    suspend fun check(manual: Boolean = false) {
        if (manual) mutableDialogVisible.value = true
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
                return
            }
            if (!manual && release != null && promptAllowed &&
                current.ignoredVersions[channel] != release.version) {
                mutableDialogVisible.value = true
            }
        } catch (cancelled: CancellationException) {
            mutableState.value = UpdateState.Idle
            mutableDialogVisible.value = false
            throw cancelled
        } catch (error: Exception) {
            withContext(Dispatchers.IO) { logger.warn("Update", "Update check failed", error) }
            mutableState.value = UpdateState.Failed(error.message ?: error::class.java.simpleName)
        } finally {
            mutex.unlock()
        }
    }
}

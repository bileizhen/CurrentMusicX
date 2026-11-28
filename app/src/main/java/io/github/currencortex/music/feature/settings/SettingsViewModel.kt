package io.github.currencortex.music.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.CustomBackgroundStore
import io.github.currencortex.music.data.settings.SettingsRepository
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val rootBackgrounds: CustomBackgroundStore? = null,
) : ViewModel() {
    val settings = repository.state
    fun edit(change: (AppearanceSettings) -> AppearanceSettings) {
        viewModelScope.launch { repository.edit(change) }
    }
    /** Selecting a different face keeps imports available in the font library. */
    suspend fun selectFont(name: String) { repository.edit { it.copy(customFont = name) } }

    fun rootBackgroundFile(name: String): File? = rootBackgrounds?.file(name)

    suspend fun importRootBackground(uri: Uri) {
        requireNotNull(rootBackgrounds) { "无法保存背景图片" }.import(uri)
    }

    suspend fun clearRootBackground() {
        rootBackgrounds?.clear() ?: repository.edit { it.copy(rootBackground = "") }
    }
}

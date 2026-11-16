package io.github.currencortex.music.feature.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.media.NeteaseEngineLibraries
import io.github.currencortex.music.core.update.UpdateSource
import io.github.currencortex.music.data.effects.NeteaseEffectsAssetRepository
import io.github.currencortex.music.ui.component.musicScrollPadding
import io.github.currencortex.music.ui.component.nativeui.*
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator

@Composable
internal fun NeteaseEffectsPackageScreen(vm: SoundEffectsViewModel, onBack: () -> Unit) {
    val engineReady by vm.originalEffectsEngineReady.collectAsStateWithLifecycle()
    val assets by vm.originalEffectsAssets.collectAsStateWithLifecycle()
    val transfer by vm.originalImport.collectAsStateWithLifecycle()
    val settings by vm.updateSettings.collectAsStateWithLifecycle()
    val supported = "arm64-v8a" in Build.SUPPORTED_ABIS
    val installed = engineReady && assets.available
    val hasComponents = engineReady || assets.available
    val bytes = (if (engineReady) 0L else NeteaseEngineLibraries.DOWNLOAD_SIZE) +
        (if (assets.available) 0L else NeteaseEffectsAssetRepository.PACKAGE_SIZE)
    val downloadSize = String.format(LocalLocale.current.platformLocale, "%.1f MB", bytes / 1_000_000.0)
    val source = settings.effectiveDownloadSource
    var showImport by rememberSaveable { mutableStateOf(false) }
    var confirmRemoval by rememberSaveable { mutableStateOf(false) }
    val selectApk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importOriginal)
    }

    SettingsUiScope {
        SettingsScrollLayout(Modifier.fillMaxSize().testTag("netease_effects_package"), topBar = {
            SettingsTopBar("网易云音效", onBack = onBack)
        }) { topInset ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = musicScrollPadding(
                PaddingValues(start = 16.dp, end = 16.dp, top = topInset, bottom = 24.dp)),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    SettingsGroup {
                        SettingsPreference("网易云音效包", when {
                            transfer.removing -> "正在移除"
                            installed -> "已安装"
                            !supported -> "仅支持 ARM64 设备"
                            engineReady -> "引擎已安装，待下载界面素材"
                            assets.available -> "界面素材已安装，待下载引擎"
                            else -> "大师音效、智能音效及原版音效"
                        })
                        if (supported || transfer.loading || transfer.message != null) {
                            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (transfer.loading) {
                                    val progress = transfer.progress
                                    if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                                    else LinearProgressIndicator(progress = progress / 100f, modifier = Modifier.fillMaxWidth())
                                    SettingsHint(transfer.message.orEmpty() + (progress?.let { " $it%" } ?: ""),
                                        Modifier.testTag("sound_engine_import_status"))
                                } else if (!installed && supported) {
                                    GlassActionButton(
                                        if (transfer.failed) "重试下载 · $downloadSize" else "下载 · $downloadSize",
                                        vm::downloadOriginalEffects, Modifier.testTag("sound_download_engine"), primary = true)
                                }
                                if (!transfer.loading) transfer.message?.let { SettingsHint(it, error = transfer.failed) }
                            }
                        }
                    }
                }
                if (!installed && supported) item {
                    SettingsGroup {
                        SettingsPreference("下载源", if (source == UpdateSource.CURRENTMUSIC)
                            "跟随「检查更新」 · 专用镜像不提供音效包，优先尝试 GitHub"
                        else "跟随「检查更新」 · ${source.label}")
                    }
                }
                if (!engineReady && supported) item {
                    SettingsGroup {
                        SettingsPreference("其他安装方式", "导入兼容的网易云引擎", enabled = !transfer.loading,
                            onClick = { showImport = !showImport })
                        if (showImport) {
                            SettingsDivider()
                            SettingsPreference("从已安装的网易云导入", enabled = !transfer.loading,
                                onClick = vm::importInstalledOriginal)
                            SettingsDivider()
                            SettingsPreference("选择网易云安装包", enabled = !transfer.loading, onClick = {
                                selectApk.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
                            })
                        }
                    }
                }
                if (hasComponents && !transfer.loading) item {
                    SettingsGroup {
                        if (confirmRemoval) {
                            SettingsPreference("移除网易云音效包？", "删除已下载的引擎和界面素材，并关闭网易云音效。")
                            Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassActionButton("取消", { confirmRemoval = false }, Modifier.weight(1f))
                                GlassActionButton("确认移除", {
                                    confirmRemoval = false
                                    showImport = false
                                    vm.removeOriginalEffects()
                                }, Modifier.weight(1f).testTag("confirm_remove_netease_effects"), destructive = true)
                            }
                        } else {
                            SettingsPreference("移除音效包", modifier = Modifier.testTag("remove_netease_effects"),
                                onClick = { confirmRemoval = true })
                        }
                    }
                }
            }
        }
    }
}

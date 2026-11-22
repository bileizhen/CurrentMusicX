package io.github.currencortex.music.feature.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.BuildConfig
import io.github.currencortex.music.core.config.*
import io.github.currencortex.music.core.storage.StorageUsage
import io.github.currencortex.music.core.update.UpdateState
import io.github.currencortex.music.feature.settings.StorageBar
import io.github.currencortex.music.feature.settings.StorageViewModel
import io.github.currencortex.music.feature.settings.formatStorageSize
import io.github.currencortex.music.feature.settings.storageBarColors
import io.github.currencortex.music.feature.settings.storageBarSegments
import io.github.currencortex.music.ui.component.HeroTextMotion
import io.github.currencortex.music.ui.component.LocalMusicPageActive
import io.github.currencortex.music.ui.component.PageContainerOrigin
import io.github.currencortex.music.ui.component.heroTextMotionElement
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.component.miuix.effect.BgEffectBackground
import io.github.currencortex.music.ui.util.openExternalLink
import io.github.currencortex.music.ui.util.showMusicMessage
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
fun AboutScreen(onBack: () -> Unit, onOpenDocument: (LegalDocument) -> Unit, enableBlur: Boolean,
    storageVm: StorageViewModel, onStorage: () -> Unit,
    onOpenMember: (AboutMember, String) -> Unit, onSupport: () -> Unit = {},
    updateState: UpdateState = UpdateState.Idle, onCheckUpdates: (PageContainerOrigin?) -> Unit = {},
    accountName: String? = null,
    developerMode: Boolean = false, onDeveloperModeUnlocked: () -> Unit = {}) {
    val context = LocalContext.current
    val state = remember { AboutUiState() }
    val storage by storageVm.state.collectAsStateWithLifecycle()
    // Re-measure whenever the page becomes active again, e.g. after clearing the cache page.
    val active = LocalMusicPageActive.current
    LaunchedEffect(active) { if (active) storageVm.refresh() }
    AboutScreenMiuix(
        state = state,
        actions = AboutScreenActions(onBack) { link ->
            when {
                // Tapping 版本 copies its info silently: the row now also counts taps toward
                // unlocking developer mode, so a toast on every tap would only get in the way.
                link == "currentmusic:version" -> {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("version",
                        "${AppMetadata.APP_NAME} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n${BuildConfig.APPLICATION_ID}"))
                }
                link.startsWith("currentmusic:") -> onOpenDocument(LegalDocument.valueOf(link.substringAfter(':')))
                else -> openExternalLink(context, link)
            }
        },
        enableBlur = enableBlur,
        storageUsage = storage.usage,
        onStorage = onStorage,
        onOpenMember = onOpenMember,
        onSupport = onSupport,
        updateState = updateState,
        onCheckUpdates = onCheckUpdates,
        accountName = accountName,
        developerMode = developerMode,
        onDeveloperModeUnlocked = onDeveloperModeUnlocked,
    )
}

@Immutable
data class AboutLink(val fullText: String, val url: String)

@Immutable
class AboutUiState {
    val title: String = "关于"
    val appName: String = AppMetadata.APP_NAME
    val versionName: String = "v${BuildConfig.VERSION_NAME}"
    val links = listOf(
        AboutLink("GitHub", "https://github.com/bileizhen/CurrentMusicX"),
        AboutLink("QQ 群", "https://qm.qq.com/q/LZx3ulz3oG"),
        AboutLink("开源许可", "currentmusic:LICENSE"),
        AboutLink("第三方声明", "currentmusic:NOTICES"),
        AboutLink("隐私", "currentmusic:PRIVACY"),
    ) +
        listOfNotNull(
            AppMetadata.WEBSITE_URL.takeIf { it.isNotBlank() }?.let { AboutLink("网站", it) },
            AppMetadata.PRIVACY_URL.takeIf { it.isNotBlank() }?.let { AboutLink("在线隐私政策", it) },
        )
}

@Immutable
data class AboutScreenActions(val onBack: () -> Unit, val onOpenLink: (String) -> Unit)

@Composable
fun AboutScreenMiuix(state: AboutUiState, actions: AboutScreenActions, enableBlur: Boolean,
    storageUsage: StorageUsage, onStorage: () -> Unit,
    onOpenMember: (AboutMember, String) -> Unit, onSupport: () -> Unit = {},
    updateState: UpdateState = UpdateState.Idle, onCheckUpdates: (PageContainerOrigin?) -> Unit = {},
    accountName: String? = null,
    developerMode: Boolean = false, onDeveloperModeUnlocked: () -> Unit = {}) {
    val colors = COUITheme.colorScheme
    val context = LocalContext.current
    // Five taps on the version row unlock the developer options; the counter resets once it fires.
    var versionTaps by remember { mutableIntStateOf(0) }
    val projectLinks = state.links.filter { it.fullText in setOf("GitHub", "QQ 群", "网站") }
    val legalLinks = state.links.filterNot { it.fullText == "在线隐私政策" || it.fullText in setOf("GitHub", "QQ 群", "网站") }
    SettingsPage(state.title, "about_screen", actions.onBack, largeTitle = true) {
        item("hero") {
            AboutHero(state, updateState, onCheckUpdates)
        }
        item("summary") {
            Card(Modifier.fillMaxWidth()) {
                CardListGroup {
                    item {
                        // The signed-in account's login name (not its display nickname), like the server identifies it.
                        BasicComponent(title = "用户名", endActions = {
                            AboutValue(accountName?.trim()?.takeIf { it.isNotBlank() } ?: state.appName)
                        })
                    }
                    item(showDivider = false) {
                        ArrowPreference(
                            title = "存储空间",
                            endActions = {
                                AboutValue(formatStorageSize(storageUsage.total))
                            },
                            bottomAction = { AboutStorageBar(storageUsage) },
                            onClick = onStorage,
                            modifier = Modifier.testTag("about_storage"),
                        )
                    }
                }
            }
        }
        item("details") {
            PreferenceCategory(title = "详细信息")
            Card(Modifier.fillMaxWidth()) {
                CardListGroup {
                    item {
                        BasicComponent(
                            title = "版本",
                            endActions = { AboutValue("${state.versionName} (${BuildConfig.VERSION_CODE})") },
                            onClick = {
                                if (!developerMode) {
                                    versionTaps++
                                    if (versionTaps >= 5) {
                                        versionTaps = 0
                                        onDeveloperModeUnlocked()
                                        context.showMusicMessage("开发者模式已开启")
                                    }
                                }
                                actions.onOpenLink("currentmusic:version")
                            },
                        )
                    }
                    item {
                        BasicComponent(title = "设计语言", endActions = { AboutValue("ColorOS 17 · COUI 17") })
                    }
                    item(showDivider = false) {
                        BasicComponent(title = "平台", endActions = { AboutValue("Android") })
                    }
                }
            }
        }
        if (projectLinks.isNotEmpty()) {
            item("project") {
                PreferenceCategory(title = "项目")
                Card(Modifier.fillMaxWidth()) {
                    CardListGroup {
                        projectLinks.distinctBy { it.url }.forEachIndexed { index, link ->
                            item(showDivider = index != projectLinks.lastIndex) {
                                ArrowPreference(
                                    title = link.fullText,
                                    endActions = {
                                        AboutValue(when (link.fullText) {
                                            "GitHub" -> "GitHub"
                                            "QQ 群" -> "QQ"
                                            else -> "网站"
                                        })
                                    },
                                    onClick = { actions.onOpenLink(link.url) },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (legalLinks.isNotEmpty()) {
            item("legal") {
                PreferenceCategory(title = "许可")
                Card(Modifier.fillMaxWidth()) {
                    CardListGroup {
                        legalLinks.distinctBy { it.url }.forEachIndexed { index, link ->
                            item(showDivider = index != legalLinks.lastIndex) {
                                ArrowPreference(
                                    title = link.fullText,
                                    onClick = { actions.onOpenLink(link.url) },
                                )
                            }
                        }
                    }
                }
            }
        }
        item("credits") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsListHeader("主要贡献者",
                    marginStartType = io.github.suqi8.coui.kmp.preference.PreferenceCategoryMarginStartType.Small) {
                    Text(AboutCredits.all.size.toString(),
                        Modifier.clip(CircleShape).background(colors.cardBackground)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                        fontSize = 12.sp, color = colors.labelSecondaryVariant)
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ContributorWall(AboutCredits.all,
                            onSelect = { credit -> onOpenMember(credit.member, credit.group) },
                            modifier = Modifier.testTag("about_contributor_wall"))
                        Text("拖动浏览 · 双指缩放 · 点击头像查看详情",
                            Modifier.padding(horizontal = 4.dp), fontSize = 12.sp, lineHeight = 18.sp,
                            color = colors.labelSecondaryVariant)
                    }
                }
            }
        }
        item("credit_wordmark") {
            Card(Modifier.fillMaxWidth()) {
                ContributorNameWordmark(AboutCredits.all.map { it.member.name },
                    Modifier.padding(18.dp).testTag("about_contributor_wordmark"))
            }
        }
    }
}

@Composable
private fun AboutHero(state: AboutUiState, updateState: UpdateState,
    onCheckUpdates: (PageContainerOrigin?) -> Unit) {
    // The hero doubles as the update entry: it says what the last check found, and tapping it
    // opens the usual update page (which re-checks and shows notes or download progress).
    val checking = updateState == UpdateState.Checking
    val label = when (updateState) {
        UpdateState.Checking -> "正在检查…"
        UpdateState.UpToDate -> "已是最新版本"
        is UpdateState.Available -> "发现新版本 ${updateState.release.version}"
        else -> "检查更新"
    }
    // The update page expands out of this card, and the two texts fly there on a parabola.
    var heroOrigin by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val heroLayer = rememberGraphicsLayer()
    val heroMotion = remember { HeroTextMotion() }
    Box(
        Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(24.dp)).testTag("about_hero")
            .onGloballyPositioned { heroOrigin = it }
            .drawWithContent {
                heroLayer.record { this@drawWithContent.drawContent() }
                drawLayer(heroLayer)
            },
    ) {
        BgEffectBackground(
            dynamicBackground = true,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    state.appName,
                    modifier = heroTextMotionElement(heroMotion, 0),
                    color = Color.White,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                // The build code lives in 详细信息; the hero shows just the bold version number.
                Text(
                    state.versionName.removePrefix("v"),
                    modifier = heroTextMotionElement(heroMotion, 1),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(
                    text = label,
                    onClick = {
                        onCheckUpdates(heroOrigin?.takeIf { it.isAttached }?.let { coordinates ->
                            PageContainerOrigin(
                                coordinates = coordinates,
                                color = Color(0xFF0A0A0A),
                                title = state.appName,
                                summary = state.versionName.removePrefix("v"),
                                sourceLayer = heroLayer,
                                heroTextMotion = heroMotion,
                            )
                        })
                    },
                    enabled = !checking,
                    modifier = Modifier.padding(top = 8.dp).testTag("about_check_updates"),
                    minHeight = 40.dp,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    colors = ButtonDefaults.textButtonColorsTransparent(textColor = Color.White),
                )
            }
        }
    }
}

@Composable
private fun AboutValue(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 12.dp),
        fontSize = 14.sp,
        color = COUITheme.colorScheme.labelTertiary,
        textAlign = TextAlign.End,
        maxLines = 1,
    )
}

@Composable
private fun AboutStorageBar(usage: StorageUsage) {
    StorageBar(storageBarSegments(usage), storageBarColors, height = 8.dp)
}

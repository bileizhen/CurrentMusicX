package io.github.currencortex.music.feature.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.BuildConfig
import io.github.currencortex.music.R
import io.github.currencortex.music.core.config.*
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.PageNavigationPreference
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.util.openExternalLink
import io.github.currencortex.music.ui.util.showMusicMessage
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutScreen(onBack: () -> Unit, onOpenDocument: (LegalDocument) -> Unit, enableBlur: Boolean,
    onOpenMember: (AboutMember, String) -> Unit, onSupport: () -> Unit = {}) {
    val context = LocalContext.current
    val state = remember { AboutUiState() }
    AboutScreenMiuix(state, AboutScreenActions(onBack) { link ->
        when {
            link == "currentmusic:version" -> {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("version",
                    "${AppMetadata.APP_NAME} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n${BuildConfig.APPLICATION_ID}"))
                context.showMusicMessage("版本信息已复制")
            }
            link.startsWith("currentmusic:") -> onOpenDocument(LegalDocument.valueOf(link.substringAfter(':')))
            else -> openExternalLink(context, link)
        }
    }, enableBlur, onOpenMember, onSupport)
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
    onOpenMember: (AboutMember, String) -> Unit, onSupport: () -> Unit = {}) {
    val colors = MiuixTheme.colorScheme
    SettingsPage(state.title, "about_screen", actions.onBack) {
        item("brand") {
            Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.drawable.ic_launcher_foreground), "应用图标",
                    Modifier.size(88.dp).testTag("about_logo"), colorFilter = ColorFilter.tint(colors.onBackground))
                Text(state.appName, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(state.versionName, Modifier.testTag("about_version").clickable { actions.onOpenLink("currentmusic:version") }
                    .padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 14.sp, color = colors.onSurfaceVariantSummary)
            }
        }
        item("project") { SettingsSection("项目", MusicIcons.Info) {
            PageNavigationPreference("支持开发者", "自愿赞助项目开发与维护",
                Modifier.testTag("about_open_support"), MusicIcons.Heart, onSupport)
            SettingsDivider()
            state.links.filterNot { it.fullText == "在线隐私政策" }.distinctBy { it.url }.forEachIndexed { index, link ->
                if (index > 0) SettingsDivider()
                PageNavigationPreference(link.fullText, onClick = { actions.onOpenLink(link.url) })
            }
        } }
        item("credits") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsInfoHeader("主要贡献者", MusicIcons.Heart, horizontalInset = 0.dp) {
                    Text(AboutCredits.all.size.toString(),
                        Modifier.clip(CircleShape).background(colors.surfaceContainer)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                        fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                }
                SettingsGroup {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ContributorWall(AboutCredits.all,
                            onSelect = { credit -> onOpenMember(credit.member, credit.group) },
                            modifier = Modifier.testTag("about_contributor_wall"))
                        Text("拖动浏览 · 双指缩放 · 点击头像查看详情",
                            Modifier.padding(horizontal = 4.dp), fontSize = 12.sp, lineHeight = 18.sp,
                            color = colors.onSurfaceVariantSummary)
                    }
                }
            }
        }
        item("credit_wordmark") {
            SettingsGroup {
                ContributorNameWordmark(AboutCredits.all.map { it.member.name },
                    Modifier.padding(18.dp).testTag("about_contributor_wordmark"))
            }
        }
    }
}

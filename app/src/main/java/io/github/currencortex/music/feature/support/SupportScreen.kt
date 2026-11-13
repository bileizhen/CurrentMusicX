package io.github.currencortex.music.feature.support

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.util.showMusicError
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SupportScreen(onBack: () -> Unit, afdianUrl: String = SupportConfig.afdianUrl,
    onOfficialDonation: () -> Unit = {}) {
    val context = LocalContext.current
    val appIcon = remember(context) {
        context.packageManager.getApplicationIcon(context.applicationInfo).toBitmap(192, 192).asImageBitmap()
    }
    val gate = remember { SupportClickGate(SystemClock::elapsedRealtime) }
    SettingsPage("支持开发者", "support_screen", onBack) {
        item("brand") {
            SettingsGroup {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(appIcon, "CurrentMusic 图标", Modifier.size(64.dp))
                    Text("CurrentMusic", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Text("CurrentMusic 由独立开发者持续维护。如果你喜欢这款音乐播放器，欢迎通过自愿赞助支持项目的开发与维护。",
                        fontSize = 15.sp, lineHeight = 23.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Text("开发者 · bileizhen", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
        item("official") {
            SettingsSection("官方捐赠", MusicIcons.Heart) {
                SettingsPreference("支持 CurrentMusic 官方", "登录后扫码捐赠、登记交易单号并查看本人记录",
                    Modifier.testTag("support_official_donation"), onClick = onOfficialDonation,
                    trailing = { Icon(MusicIcons.Forward, null, Modifier.size(16.dp)) })
            }
        }
        item("afdian") {
            SettingsSection("爱发电", MusicIcons.Heart) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("通过爱发电支持 CurrentMusic 的持续开发。", fontSize = 15.sp, lineHeight = 22.sp)
                    val available = supportHttpsUrl(afdianUrl) != null
                    GlassActionButton(if (available) "前往爱发电" else "暂未开放", {
                        if (gate.acquire()) when (openSupportLink(context, afdianUrl)) {
                            SupportLinkResult.INVALID -> context.showMusicError("链接无效")
                            SupportLinkResult.NO_APP -> context.showMusicError("未找到可以打开链接的应用")
                            SupportLinkResult.OPENED -> Unit
                        }
                    }, Modifier.testTag("support_afdian"), primary = true, enabled = available)
                }
            }
        }
        item("privacy") {
            SettingsHint("所有赞助均为自愿行为，不影响 CurrentMusic 的正常使用。赞助不会解锁额外的软件功能或会员权益。")
            SettingsHint("爱发电无需登录。官方捐赠登记需要登录，提交的金额、交易单号和账号信息仅供站长核对。支付由对应平台处理，应用不会自动读取支付记录或判断支付是否完成。")
        }
    }
}

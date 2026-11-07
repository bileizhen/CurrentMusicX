package io.github.currencortex.music.feature.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.data.admin.*
import io.github.currencortex.music.data.profile.userLevelLabel
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField

@Composable internal fun AdminDialogContent(dialog: AdminDialog, state: AdminState, vm: AdminViewModel,
    select: (AdminDialog) -> Unit, close: () -> Unit) {
    val enabled = !state.busy && !state.loading
    when (dialog) {
        is AdminDialog.User -> {
            val target = dialog.user
            val action = dialog.action
            if (action == null) MusicDialog("管理 ${target.name}", close) {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    AdminUserAction.entries.filter { it.allowed(requireNotNull(state.actor), target) }.forEach {
                        MusicTextAction(userActionLabel(it, target), { select(dialog.copy(action = it)) },
                            enabled = enabled && (it != AdminUserAction.PROGRESSION || state.rules != null))
                    }
                }
                MusicDialogButton("取消", close, enabled = enabled)
            } else key(target.id, action) {
                var value by remember { mutableStateOf(when (action) {
                    AdminUserAction.TITLE -> target.title
                    AdminUserAction.PROGRESSION -> target.points.toString()
                    else -> ""
                }) }
                var level by remember { mutableStateOf(target.level) }
                val titleMax = state.overview?.number("titleMax") ?: 16L
                val valid = when (action) {
                    AdminUserAction.PASSWORD -> value.length in 6..64
                    AdminUserAction.TITLE -> value.codePointCount(0, value.length) <= titleMax
                    AdminUserAction.PROGRESSION -> state.rules?.let { rules ->
                        value.toLongOrNull()?.let { it in 0..rules.maxPoints } == true &&
                            level in rules.levels.map { it.level } + rules.trial.plusLevel
                    } == true
                    else -> true
                }
                MusicDialog(userActionLabel(action, target), close) {
                    Text("目标：${target.name}（@${target.username}，ID ${target.id}）")
                    when (action) {
                        AdminUserAction.PASSWORD -> Text("新密码需为 6–64 位，重置后该用户需要重新登录。")
                        AdminUserAction.TITLE -> Text("最多 $titleMax 字，留空可清除头衔。")
                        AdminUserAction.PROGRESSION -> Text("累计积分范围：0–${state.rules?.maxPoints ?: 0}")
                        AdminUserAction.KICK -> Text("该用户所有设备的登录会话都会失效。")
                        AdminUserAction.DISABLE -> Text(if (target.disabled) "允许该用户重新登录。" else "禁用后该用户无法登录，已有会话将失效。")
                        else -> Text("确认更改该用户的管理权限？修改后将立即生效。")
                    }
                    if (action in setOf(AdminUserAction.PASSWORD, AdminUserAction.TITLE, AdminUserAction.PROGRESSION)) {
                        TextField(value, { value = it }, singleLine = true, enabled = enabled,
                            modifier = Modifier.fillMaxWidth().testTag("admin_action_value"),
                            visualTransformation = if (action == AdminUserAction.PASSWORD) PasswordVisualTransformation() else VisualTransformation.None,
                            keyboardOptions = KeyboardOptions(keyboardType = when (action) {
                                AdminUserAction.PASSWORD -> KeyboardType.Password
                                AdminUserAction.PROGRESSION -> KeyboardType.Number
                                else -> KeyboardType.Text
                            }))
                    }
                    if (action == AdminUserAction.PROGRESSION) Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                        state.rules?.let { rules -> (rules.levels.map { it.level } + rules.trial.plusLevel).distinct().forEach { option ->
                            MusicDialogChoiceRow(userLevelLabel(option) ?: "等级 $option", level == option, { if (enabled) level = option })
                        } }
                    }
                    state.error?.let { SettingsHint(it, error = true) }
                    MusicDialogButton(if (state.busy) "正在提交…" else "确认${userActionLabel(action, target)}", {
                        vm.userAction(target, action, value, level, close)
                    }, enabled = enabled && valid, modifier = Modifier.testTag("admin_confirm_action"))
                    MusicDialogButton("取消", close, enabled = enabled)
                }
            }
        }
        is AdminDialog.Announcement -> key(dialog.item?.id) {
            val item = dialog.item
            var title by remember { mutableStateOf(item?.title.orEmpty()) }
            var body by remember { mutableStateOf(item?.body.orEmpty()) }
            var link by remember { mutableStateOf(item?.link.orEmpty()) }
            var pinned by remember { mutableStateOf(item?.pinned ?: false) }
            val validLink = link.isBlank() || runCatching { java.net.URI(link.trim()).let {
                it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() && it.userInfo == null
            } }.getOrDefault(false)
            MusicDialog(if (item == null) "发布公告" else "编辑公告", close) {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("标题（1–60 字）")
                    TextField(title, { title = it }, enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("正文（支持 Markdown）")
                    TextField(body, { body = it }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp))
                    Text("详情链接（可选，http / https）")
                    TextField(link, { link = it }, enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (!validLink) SettingsHint("请输入有效的 http / https 链接", error = true)
                    SettingsSwitch("置顶", pinned, { if (enabled) pinned = it })
                    Text("确认后公告将在客户端生效${if (item?.removed == true) "；此公告已移除，编辑后仍需恢复才会展示" else ""}。")
                }
                state.error?.let { SettingsHint(it, error = true) }
                MusicDialogButton(if (state.busy) "正在提交…" else if (item == null) "确认发布" else "确认保存", {
                    vm.announcement(item, title, body, link.trim(), pinned, close)
                }, enabled = enabled && title.trim().length in 1..60 && validLink)
                MusicDialogButton("取消", close, enabled = enabled)
            }
        }
        is AdminDialog.AnnouncementAction -> ConfirmAdminAction(dialog.label,
            "${dialog.item.title}\n" + when (dialog.action) {
                "remove" -> "移除后客户端不再展示，可在面板中恢复。"
                "restore" -> "恢复后客户端将重新展示该公告。"
                else -> "将更新公告的置顶状态。"
            }, state, close) { vm.announcementAction(dialog.item, dialog.action, close) }
        is AdminDialog.Kick -> ConfirmAdminAction("强制下线", "确认让 @${dialog.session.username} 的全部设备下线？", state, close) {
            vm.kick(dialog.session, close)
        }
        AdminDialog.Cache -> ConfirmAdminAction("清理辅助缓存", "将清理歌词、评论、详情等缓存，不影响音质档位探测缓存。", state, close) {
            vm.clearCache(close)
        }
    }
}

@Composable private fun ConfirmAdminAction(title: String, description: String, state: AdminState, close: () -> Unit, confirm: () -> Unit) {
    MusicDialog(title, close) {
        Text(description)
        state.error?.let { SettingsHint(it, error = true) }
        MusicDialogButton(if (state.busy) "正在提交…" else "确认$title", confirm, enabled = !state.busy && !state.loading)
        MusicDialogButton("取消", close, enabled = !state.busy)
    }
}
private fun userActionLabel(action: AdminUserAction, target: AdminUser): String = when (action) {
    AdminUserAction.DISABLE -> if (target.disabled) "解除禁用" else "禁用账号"
    AdminUserAction.ADMIN -> if (target.isAdmin) "取消管理员" else "设为管理员"
    AdminUserAction.SUPER -> if (target.isSuper) "取消超级管理员" else "设为超级管理员"
    AdminUserAction.OWNER -> if (target.isOwner) "取消站长" else "设为站长"
    else -> action.label
}

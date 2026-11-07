package io.github.currencortex.music.feature.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.admin.*
import io.github.currencortex.music.data.profile.userLevelLabel
import io.github.currencortex.music.feature.update.MarkdownText
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun AdminDetail(destination: AdminDestination, state: AdminState, actions: AdminActions,
    open: (AdminDestination) -> Unit, confirm: (AdminConfirmation) -> Unit, onBack: () -> Unit) {
    key(destination) {
        when (destination) {
            is AdminDestination.User -> AdminUserDetail(destination.user, state, open, confirm, onBack)
            is AdminDestination.EditUser -> AdminUserEditor(destination, state, actions, { open(AdminDestination.User(destination.user)) }, onBack)
            is AdminDestination.Announcement -> AdminAnnouncementEditor(destination.item, state, actions, confirm, onBack)
        }
    }
}

@Composable private fun AdminUserDetail(initial: AdminUser, state: AdminState, open: (AdminDestination) -> Unit,
    confirm: (AdminConfirmation) -> Unit, onBack: () -> Unit) {
    val user = state.users?.users?.find { it.id == initial.id } ?: initial
    MusicSubpage("用户管理", "admin_user_detail", onBack, modifier = Modifier.background(MiuixTheme.colorScheme.background)) { inset ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = inset + 12.dp,
            bottom = LocalMusicBottomInset.current + 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AdminCard(user.name, "@${user.username} · ID ${user.id}") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { AdminBadge(adminRole(user)); if (user.disabled) AdminBadge("已禁用", true) }
                AdminMetric("头衔", user.title.ifBlank { "未设置" }); AdminMetric("等级与积分", "${userLevelLabel(user.level)} · ${adminCount(user.points)} 积分")
                AdminMetric("在线设备", "${user.sessions} 台")
            }
            if (user.email.isNotBlank() || user.phone.isNotBlank()) AdminExpandable("联系方式", "查看邮箱与手机号", "admin_user_contact") {
                if (user.email.isNotBlank()) AdminMetric("邮箱", user.email)
                if (user.phone.isNotBlank()) AdminMetric("手机号", user.phone)
            }
            val allowed = AdminUserAction.entries.filter { state.actor?.let { actor -> it.allowed(actor, user) } == true }
            if (allowed.isEmpty()) SettingsHint(if (state.actor?.id == user.id) "当前账号。请在账号与安全中修改自己的登录信息。" else "当前角色无法管理此账号。")
            listOf("资料与成长" to listOf(AdminUserAction.TITLE, AdminUserAction.PROGRESSION),
                "管理权限" to listOf(AdminUserAction.ADMIN, AdminUserAction.SUPER, AdminUserAction.OWNER),
                "账号安全" to listOf(AdminUserAction.PASSWORD, AdminUserAction.KICK, AdminUserAction.DISABLE)).forEach { (title, all) ->
                val rows = all.filter { it in allowed }
                if (rows.isNotEmpty()) {
                    SettingsListHeader(title)
                    SettingsGroup { rows.forEachIndexed { index, action ->
                        if (index > 0) SettingsDivider(52.dp)
                        val edit = action in setOf(AdminUserAction.TITLE, AdminUserAction.PROGRESSION, AdminUserAction.PASSWORD)
                        AdminActionRow(userActionLabel(action, user), userActionSummary(action, user),
                            if (action == AdminUserAction.PROGRESSION) MusicIcons.Check else MusicIcons.Account,
                            "admin_action_${action.name.lowercase()}", enabled = !state.loading && !state.busy,
                            dangerous = action in setOf(AdminUserAction.DISABLE, AdminUserAction.KICK)) {
                            if (edit) open(AdminDestination.EditUser(user, action)) else confirm(AdminConfirmation.User(user, action))
                        }
                    } }
                }
            }
        }
    }
}

@Composable private fun AdminUserEditor(destination: AdminDestination.EditUser, state: AdminState, actions: AdminActions,
    onBack: () -> Unit, done: () -> Unit) {
    val target = destination.user; val action = destination.action
    val initial = when (action) { AdminUserAction.TITLE -> target.title; AdminUserAction.PROGRESSION -> target.points.toString(); else -> "" }
    var value by remember { mutableStateOf(initial) }
    var passwordAgain by remember { mutableStateOf("") }
    var level by remember { mutableStateOf(target.level) }
    val titleMax = state.overview?.number("titleMax") ?: 16L
    val validation = when (action) {
        AdminUserAction.PASSWORD -> when {
            value.length !in 6..64 -> "新密码需为 6–64 位"
            value != passwordAgain -> "两次输入的密码需要一致"
            else -> null
        }
        AdminUserAction.TITLE -> if (value.codePointCount(0, value.length) > titleMax) "头衔最多 $titleMax 字" else null
        AdminUserAction.PROGRESSION -> state.rules?.let { rules -> when {
            value.toLongOrNull()?.let { it in 0..rules.maxPoints } != true -> "请输入 0–${rules.maxPoints} 之间的整数"
            level !in rules.levels.map { it.level } + rules.trial.plusLevel -> "请选择等级"
            else -> null
        } } ?: "等级规则暂不可用，请重试"
        else -> null
    }
    AdminEditorFrame(userActionLabel(action, target), "admin_user_editor", state, onBack,
        dirty = value != initial || passwordAgain.isNotEmpty() || level != target.level, valid = validation == null,
        saveLabel = if (action == AdminUserAction.PASSWORD) "确认重置密码" else "保存修改",
        save = { actions.userAction(target, action, value, level, done) }) {
        AdminCard(target.name, "@${target.username} · ID ${target.id}") { Text(userActionSummary(action, target), fontSize = 13.sp) }
        SettingsGroup { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AdminField(if (action == AdminUserAction.PASSWORD) "新密码" else if (action == AdminUserAction.PROGRESSION) "累计积分" else "头衔",
                value, { value = it; actions.clearOperationError() }, "admin_action_value", enabled = !state.busy,
                password = action == AdminUserAction.PASSWORD, number = action == AdminUserAction.PROGRESSION)
            if (action == AdminUserAction.TITLE) Text("最多 $titleMax 字，留空可清除头衔", fontSize = 12.sp)
            if (action == AdminUserAction.PASSWORD) AdminField("再次输入新密码", passwordAgain, { passwordAgain = it; actions.clearOperationError() },
                "admin_password_again", enabled = !state.busy, password = true)
            if (validation != null) SettingsHint(validation, error = value.isNotEmpty())
        } }
        if (action == AdminUserAction.PROGRESSION) {
            if (state.rules == null) AdminNotice(state.rulesError ?: "无法加载等级规则", error = true, retry = actions::refresh)
            else {
                SettingsListHeader("等级")
                SettingsGroup {
                    (state.rules.levels.map { it.level } + state.rules.trial.plusLevel).distinct().forEach { option ->
                        MusicDialogChoiceRow(userLevelLabel(option) ?: "等级 $option", level == option, { if (!state.busy) level = option })
                    }
                }
            }
        }
    }
}

@Composable private fun AdminAnnouncementEditor(item: AdminAnnouncement?, state: AdminState, actions: AdminActions,
    confirm: (AdminConfirmation) -> Unit, onBack: () -> Unit) {
    var title by remember { mutableStateOf(item?.title.orEmpty()) }
    var body by remember { mutableStateOf(item?.body.orEmpty()) }
    var link by remember { mutableStateOf(item?.link.orEmpty()) }
    var pinned by remember { mutableStateOf(item?.pinned ?: false) }
    var preview by remember { mutableStateOf(item != null) }
    val editable = item == null || item.canEdit
    val dirty = title != item?.title.orEmpty() || body != item?.body.orEmpty() || link != item?.link.orEmpty() || pinned != (item?.pinned ?: false)
    val validLink = link.isBlank() || runCatching { java.net.URI(link.trim()).let {
        it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() && it.userInfo == null
    } }.getOrDefault(false)
    AdminEditorFrame(if (item == null) "发布公告" else "公告详情", "admin_announcement_editor", state, onBack, dirty && editable,
        valid = title.trim().length in 1..60 && validLink && (dirty || item == null),
        saveLabel = if (item == null) "确认发布" else "保存修改", showSave = editable,
        save = { actions.announcement(item, title, body, link.trim(), pinned, onBack) }) {
        if (editable) SettingsSegmentedControl(listOf("编辑", "预览"), if (preview) 1 else 0, { preview = it == 1 }, tag = "admin_announcement_mode")
        if (preview || !editable) {
            AdminCard(title.ifBlank { "公告标题" }, item?.let { "${it.author} · ${adminTime(it.createdAt)}" } ?: "发布前预览") {
                if (pinned) AdminBadge("置顶")
                if (item?.removed == true) AdminBadge("已移除", true)
                if (body.isNotBlank()) MarkdownText(body, compact = true, onLinkClick = {}) else Text("正文预览将在这里显示", fontSize = 13.sp)
                if (link.isNotBlank()) Text(link, fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
            }
        } else {
            SettingsGroup { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AdminField("标题 · ${title.length}/60", title, { title = it; actions.clearOperationError() }, "admin_announcement_title", enabled = !state.busy)
                if (title.length > 60) SettingsHint("标题最多 60 字", error = true)
                AdminField("正文 · 支持 Markdown", body, { body = it; actions.clearOperationError() }, "admin_announcement_body", enabled = !state.busy, multiline = true)
                AdminField("详情链接（可选）", link, { link = it; actions.clearOperationError() }, "admin_announcement_link", enabled = !state.busy)
                if (!validLink) SettingsHint("请输入有效的 http / https 链接", error = true)
                SettingsSwitch("置顶公告", pinned, { if (!state.busy) pinned = it })
            } }
        }
        if (item != null && editable) {
            SettingsListHeader("公告状态")
            SettingsGroup {
                if (item.removed) AdminActionRow("恢复公告", "恢复后客户端将重新展示", MusicIcons.Update, "admin_announcement_restore", enabled = !state.busy && !dirty) {
                    confirm(AdminConfirmation.Announcement(item, "restore", "恢复公告"))
                } else {
                    AdminActionRow(if (item.pinned) "取消置顶" else "置顶公告", "调整公告展示顺序", MusicIcons.Sort, "admin_announcement_pin", enabled = !state.busy && !dirty) {
                        confirm(AdminConfirmation.Announcement(item, "pin", if (item.pinned) "取消置顶" else "置顶公告"))
                    }
                    SettingsDivider(52.dp)
                    AdminActionRow("移除公告", "客户端不再展示，可随时恢复", MusicIcons.Close, "admin_announcement_remove", enabled = !state.busy && !dirty, dangerous = true) {
                        confirm(AdminConfirmation.Announcement(item, "remove", "移除公告"))
                    }
                }
            }
            if (dirty) SettingsHint("请先保存修改，再调整公告状态。")
        }
        SettingsHint(if (!editable) "此公告由其他管理员发布，当前账号仅可查看。" else if (item?.removed == true) "此公告已移除，保存后仍需恢复才会展示。" else "保存后，用户将看到更新后的公告。")
    }
}

@Composable private fun AdminField(label: String, value: String, change: (String) -> Unit, tag: String,
    enabled: Boolean, password: Boolean = false, number: Boolean = false, multiline: Boolean = false) {
    Text(label, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    TextField(value, change, enabled = enabled, singleLine = !multiline,
        modifier = Modifier.fillMaxWidth().then(if (multiline) Modifier.heightIn(min = 180.dp) else Modifier).testTag(tag),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else if (number) KeyboardType.Number else KeyboardType.Text))
}

@Composable private fun AdminEditorFrame(title: String, tag: String, state: AdminState, onBack: () -> Unit, dirty: Boolean,
    valid: Boolean, saveLabel: String, showSave: Boolean = true, save: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    var discard by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    fun leave() { if (!state.busy) { focus.clearFocus(); if (dirty) discard = true else onBack() } }
    BackHandler { leave() }
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    MusicSubpage(title, tag, ::leave, modifier = Modifier.background(MiuixTheme.colorScheme.background)) { inset ->
        Column(Modifier.fillMaxSize().padding(top = inset).imePadding()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
            state.operationError?.let { AdminNotice(it, error = true) }
            if (showSave) GlassActionButton(if (state.busy) "正在提交…" else saveLabel, { focus.clearFocus(); save() },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).testTag("admin_editor_save"),
                primary = true, enabled = valid && !state.busy && !state.loading)
            Spacer(Modifier.height(if (keyboardVisible) 8.dp else LocalMusicBottomInset.current + 12.dp))
        }
    }
    if (discard) SettingsUiScope { MusicDialog("放弃未保存的修改？", { discard = false }) {
        Text("离开后本次修改不会保存。")
        MusicDialogActions {
            MusicDialogButton("继续编辑", { discard = false }, Modifier.weight(1f))
            MusicDialogButton("放弃修改", { discard = false; onBack() }, destructive = true, modifier = Modifier.weight(1f).testTag("admin_discard"))
        }
    } }
}

@Composable internal fun AdminConfirmDialog(confirmation: AdminConfirmation, state: AdminState, actions: AdminActions, close: () -> Unit, done: () -> Unit) {
    val title: String
    val description: String
    val submit: () -> Unit
    when (confirmation) {
        is AdminConfirmation.User -> {
            title = userActionLabel(confirmation.action, confirmation.user)
            description = "${confirmation.user.name}（@${confirmation.user.username}，ID ${confirmation.user.id}）\n${userActionSummary(confirmation.action, confirmation.user)}"
            submit = { actions.userAction(confirmation.user, confirmation.action, "", null, done) }
        }
        is AdminConfirmation.Announcement -> {
            title = confirmation.label; description = "${confirmation.item.title}\n" + if (confirmation.action == "remove") "客户端将不再展示这条公告，可在面板中恢复。" else "将更新该公告的展示状态。"
            submit = { actions.announcementAction(confirmation.item, confirmation.action, done) }
        }
        is AdminConfirmation.Kick -> {
            title = "全部设备下线"; description = "确认撤销 @${confirmation.session.username} 的所有登录会话？该用户需要重新登录。"
            submit = { actions.kick(confirmation.session, done) }
        }
        AdminConfirmation.Cache -> {
            title = "清理辅助缓存"; description = "清理歌词、评论和详情缓存，音质档位探测缓存会保留。"
            submit = { actions.clearCache(done) }
        }
    }
    SettingsUiScope { MusicDialog(title, close) {
        Text(description)
        state.operationError?.let { SettingsHint(it, error = true) }
        MusicDialogActions {
            MusicDialogButton("取消", close, Modifier.weight(1f), enabled = !state.busy)
            MusicDialogButton(if (state.busy) "提交中…" else "确认", submit, Modifier.weight(1f).testTag("admin_confirm_action"),
                enabled = !state.busy && !state.loading, destructive = true)
        }
    } }
}
internal fun userActionLabel(action: AdminUserAction, target: AdminUser): String = when (action) {
    AdminUserAction.DISABLE -> if (target.disabled) "解除禁用" else "禁用账号"
    AdminUserAction.ADMIN -> if (target.isAdmin) "取消管理员" else "设为管理员"
    AdminUserAction.SUPER -> if (target.isSuper) "取消超级管理员" else "设为超级管理员"
    AdminUserAction.OWNER -> if (target.isOwner) "取消站长" else "设为站长"
    else -> action.label
}
private fun userActionSummary(action: AdminUserAction, target: AdminUser) = when (action) {
    AdminUserAction.TITLE -> "显示在用户身份旁，留空可以清除"
    AdminUserAction.PROGRESSION -> "调整累计积分和当前等级"
    AdminUserAction.PASSWORD -> "重置后该用户需要重新登录"
    AdminUserAction.KICK -> "撤销该用户的全部登录会话"
    AdminUserAction.DISABLE -> if (target.disabled) "允许该用户重新登录" else "禁止登录，并撤销已有会话"
    AdminUserAction.OWNER -> if (target.isOwner) "取消站长后回落为超级管理员" else "授予站长权限，修改后立即生效"
    else -> "调整管理权限，修改后立即生效"
}

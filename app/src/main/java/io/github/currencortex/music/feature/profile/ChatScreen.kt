package io.github.currencortex.music.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import io.github.currencortex.music.data.profile.ChatMessage
import io.github.currencortex.music.data.profile.ProfileUser
import io.github.currencortex.music.ui.component.*
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable internal fun ChatScreen(vm: ChatViewModel, navigate: (String) -> Unit, active: Boolean) {
    val state by vm.state.collectAsStateWithLifecycle()
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val nearBottom by remember { derivedStateOf { list.firstVisibleItemIndex <= 1 } }
    var membersOpen by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ChatMessage?>(null) }
    var confirming by remember { mutableStateOf<ChatMessage?>(null) }
    var preview by remember { mutableStateOf<String?>(null) }
    val bottom = LocalMusicComposerBottomInset.current
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(60.dp) }
    var composerHeight by remember { mutableStateOf(64.dp) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.upload(context.contentResolver, it) }
    }
    LaunchedEffect(vm, lifecycle, active) {
        if (active) lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.observe() }
    }
    LaunchedEffect(account.account?.id) { selected = null; confirming = null; preview = null }
    LaunchedEffect(active) { if (!active) { keyboard?.hide(); membersOpen = false; selected = null; confirming = null; preview = null } }
    LaunchedEffect(state.messages.lastOrNull()?.id) { if (nearBottom) list.animateScrollToItem(0) }
    LaunchedEffect(state.scrollRequest) { if (state.scrollRequest > 0) list.animateScrollToItem(0) }
    Box(Modifier.fillMaxSize().padding(bottom = bottom).testTag("discover_chat")) {
        Box(Modifier.fillMaxSize()) {
            // The messages scroll under the fades; the fixed controls stay sharp above them.
            LazyColumn(Modifier.fillMaxSize()
                .testTag("chat_messages"), state = list, reverseLayout = true,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = headerHeight + 16.dp, bottom = composerHeight + 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                items(state.messages.asReversed(), key = { it.id }) { message ->
                    ChatMessageRow(message, mine = message.userId == account.account?.id, vm,
                        onProfile = { id -> keyboard?.hide(); navigate("user/profile/$id") },
                        onImage = { preview = it }, onMenu = { selected = message })
                }
                if (state.hasOlder) item(key = "older") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        MusicTextAction(if (state.loadingOlder) "正在加载…" else "加载更早的消息", vm::loadOlder,
                            Modifier.testTag("chat_older"), enabled = !state.loadingOlder)
                    }
                }
            }
            if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp))
            else if (state.loaded && state.messages.isEmpty()) Text("还没有消息，来聊聊音乐吧", Modifier.align(Alignment.Center),
                fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .45f))
            if (!nearBottom && state.messages.isNotEmpty()) MusicTextAction("回到最新消息", { scope.launch { list.animateScrollToItem(0) } },
                Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = composerHeight + 8.dp)
                    .background(MiuixTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp)))
        }
        ChatEdgeFade(atTop = true,
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(headerHeight + 24.dp).testTag("chat_header_fade"))
        ChatEdgeFade(atTop = false,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(composerHeight + 24.dp).testTag("chat_composer_fade"))
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().pointerInput(Unit) { detectTapGestures {} }.onSizeChanged {
            headerHeight = with(density) { it.height.toDp() }
        }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).testTag("chat_header"),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("公共聊天室", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (state.connected) "实时消息" else if (state.loaded) "正在连接…" else "与大家聊聊音乐",
                        fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .45f))
                }
                Row(Modifier.clip(CircleShape).background(MiuixTheme.colorScheme.surfaceContainer)
                    .clickable(role = Role.Button) { membersOpen = true }.heightIn(min = 40.dp)
                    .padding(horizontal = 12.dp).testTag("chat_members"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(MusicIcons.Account, null, Modifier.size(16.dp), tint = MiuixTheme.colorScheme.primary)
                    Text("成员 ${state.members.size}", fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
                }
            }
            if (state.error != null) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.error.orEmpty(), Modifier.weight(1f), fontSize = 12.sp)
                MusicTextAction("重试", vm::retry, Modifier.testTag("chat_retry"))
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().pointerInput(Unit) { detectTapGestures {} }.onSizeChanged {
            composerHeight = with(density) { it.height.toDp() }
        }) {
            state.actionError?.let { Text(it, Modifier.padding(horizontal = 20.dp, vertical = 6.dp), fontSize = 12.sp,
                color = MiuixTheme.colorScheme.error) }
            if (state.image.isNotEmpty()) Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(vm.repository.imageUrl(vm.container.audioSession().server, state.image), "待发送图片",
                    Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                MusicTextAction("移除图片", vm::removeImage, enabled = !state.sending)
            }
            if (account.account == null) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("登录 CurrentMusic 后即可聊天", Modifier.weight(1f), fontSize = 13.sp)
                MusicTextAction("去登录", { navigate("user/login") }, Modifier.testTag("chat_login"))
            } else {
                val fragment = state.draft.substringAfterLast('@', "").takeIf {
                    state.draft.contains('@') && it.length <= 12 && it.none(Char::isWhitespace)
                }
                if (fragment != null) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.members.filter { it.nickname.contains(fragment, ignoreCase = true) }.take(3).forEach { member ->
                        MusicTextAction("@${member.nickname}", { vm.mention(member) }, Modifier.weight(1f))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).testTag("chat_composer"),
                    verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChatComposerAction(if (state.uploading) "正在处理图片" else "选择图片", MusicIcons.Image, {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, Modifier.testTag("chat_pick_image"), enabled = !state.uploading && !state.sending, busy = state.uploading)
                    val colors = MiuixTheme.colorScheme
                    BasicTextField(state.draft, vm::draft, Modifier.weight(1f).heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainer).testTag("chat_input")
                            .semantics { contentDescription = "聊天消息" }.padding(horizontal = 14.dp, vertical = 13.dp),
                            textStyle = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, color = colors.onSurface),
                            cursorBrush = SolidColor(colors.primary), maxLines = 5,
                            decorationBox = { inner -> Box {
                                if (state.draft.isEmpty()) Text("聊聊正在听的音乐…", Modifier.clearAndSetSemantics {},
                                    fontSize = 14.sp, lineHeight = 21.sp, color = colors.onSurface.copy(alpha = .4f))
                                inner()
                            } })
                    ChatComposerAction(if (state.sending) "发送中" else "发送", MusicIcons.ArrowUp, vm::send,
                        Modifier.testTag("chat_send"), primary = true, busy = state.sending,
                        enabled = !state.sending && !state.uploading && (state.draft.isNotBlank() || state.image.isNotBlank()))
                }
            }
        }
    }
    if (membersOpen) MusicDialog("聊天室成员", { membersOpen = false }) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.members, key = { it.id }) { member ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UserAvatar(member, vm.container, 32.dp, reserveOverlay = false)
                    Column(Modifier.weight(1f).clickable { membersOpen = false; navigate("user/profile/${member.id}") }) {
                        Text(member.nickname.ifBlank { member.username }, fontSize = 14.sp)
                        UserIdentityDetails(member.title, member.ipRegion)
                    }
                    if (account.account != null) MusicTextAction("@", { vm.mention(member); membersOpen = false }, Modifier.testTag("chat_mention_${member.id}"))
                }
            }
        }
        TextButton("关闭", { membersOpen = false })
    }
    selected?.let { message -> MusicDialog("消息操作", { selected = null }) {
        if (message.content.isNotEmpty()) TextButton("复制文字", { clipboard.setText(AnnotatedString(message.content)); selected = null })
        if (message.userId == account.account?.id) TextButton("删除消息", { selected = null; confirming = message }, Modifier.testTag("chat_delete"))
        TextButton("关闭", { selected = null })
    } }
    confirming?.let { message -> MusicDialog("删除这条消息？", { confirming = null }) {
        Text("删除后其他成员也将无法查看。", fontSize = 13.sp)
        TextButton("确认删除", { vm.delete(message); confirming = null }, Modifier.testTag("chat_confirm_delete"))
        TextButton("取消", { confirming = null })
    } }
    preview?.let { image -> MusicDialog("聊天图片", { preview = null }) {
        AsyncImage(image, "聊天图片", Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 420.dp), contentScale = ContentScale.Fit)
        TextButton("关闭", { preview = null })
    } }
}

@Composable private fun ChatComposerAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, primary: Boolean = false, busy: Boolean = false) {
    val colors = MiuixTheme.colorScheme
    Box(modifier.size(48.dp).clip(CircleShape)
        .background(if (primary && enabled) colors.primary else colors.surfaceContainer)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp).semantics { contentDescription = label })
        else Icon(icon, label, Modifier.size(22.dp), tint = when {
            !enabled -> colors.onSurface.copy(alpha = .25f)
            primary -> colors.onPrimary
            else -> colors.onSurface.copy(alpha = .65f)
        })
    }
}

@Composable private fun ChatMessageRow(message: ChatMessage, mine: Boolean, vm: ChatViewModel,
    onProfile: (Long) -> Unit, onImage: (String) -> Unit, onMenu: () -> Unit) {
    val user = message.user ?: ProfileUser(id = message.userId, nickname = "已注销用户")
    val color = MiuixTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Avatar, gap, and a small opposite gutter; long messages retain their reading width.
        val messageWidth = (maxWidth - 36.dp - 10.dp - 12.dp).coerceIn(0.dp, 360.dp)
        Row(Modifier.fillMaxWidth().testTag("chat_message_${message.id}"),
            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
            if (!mine) Box(Modifier.padding(end = 10.dp).testTag("chat_avatar_${message.id}")
                .clickable(enabled = user.id > 0) { onProfile(user.id) }) {
                UserAvatar(user, vm.container, 36.dp, reserveOverlay = false)
            }
            Column(Modifier.widthIn(max = messageWidth), horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(user.nickname.ifBlank { user.username }, Modifier.weight(1f, fill = false).clickable(enabled = user.id > 0) { onProfile(user.id) },
                        fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    UserRoleBadge(user.role)
                    Box(Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onMenu)
                        .testTag("chat_menu_${message.id}"), contentAlignment = Alignment.Center) {
                        Icon(MusicIcons.More, "消息操作", Modifier.size(18.dp).rotate(90f), tint = color.onSurface.copy(alpha = .4f))
                    }
                }
                UserIdentityDetails(user.title, user.ipRegion)
                Column(Modifier.testTag("chat_bubble_${message.id}")
                    .clip(RoundedCornerShape(if (mine) 18.dp else 6.dp, if (mine) 6.dp else 18.dp, 18.dp, 18.dp))
                    .background(if (mine) color.primary.copy(alpha = .10f) else color.surfaceContainer).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (message.content.isNotEmpty()) SelectionContainer {
                        Text(message.content, Modifier.testTag("chat_body_${message.id}"), fontSize = 14.sp, lineHeight = 21.sp)
                    }
                    vm.repository.imageUrl(vm.container.audioSession().server, message.image)?.let { url ->
                        AsyncImage(url, "聊天图片", Modifier.widthIn(max = 230.dp).fillMaxWidth().heightIn(min = 120.dp, max = 240.dp)
                            .clip(RoundedCornerShape(10.dp)).clickable { onImage(url) }, contentScale = ContentScale.Fit)
                    }
                }
                if (message.createdAt > 0) Text(remember(message.createdAt) {
                    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(message.createdAt * 1000))
                }, Modifier.padding(horizontal = 12.dp).testTag("chat_time_${message.id}"),
                    fontSize = 10.sp, color = color.onSurface.copy(alpha = .4f))
            }
            if (mine) Box(Modifier.padding(start = 10.dp).testTag("chat_avatar_${message.id}")
                .clickable(enabled = user.id > 0) { onProfile(user.id) }) {
                UserAvatar(user, vm.container, 36.dp, reserveOverlay = false)
            }
        }
    }
}

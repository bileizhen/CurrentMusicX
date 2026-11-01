package io.github.currencortex.music.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.luminance
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.component.liquid.rememberCombinedBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.LocalContentColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
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
import io.github.currencortex.music.data.profile.validMusicAttachments
import io.github.currencortex.music.feature.update.MarkdownText
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
    var referenceOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ChatMessage?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var menuAnchor by remember { mutableStateOf<Rect?>(null) }
    var attachmentAnchor by remember { mutableStateOf<Rect?>(null) }
    var confirming by remember { mutableStateOf<ChatMessage?>(null) }
    var preview by remember { mutableStateOf<String?>(null) }
    val bottom = LocalMusicComposerBottomInset.current
    val density = LocalDensity.current
    val messagesBackdrop = rememberGlassBackdrop()
    val baseBackdrop = LocalGlassBackdrop.current
    val controlsBackdrop = if (baseBackdrop != null && messagesBackdrop != null)
        rememberCombinedBackdrop(baseBackdrop, messagesBackdrop) else messagesBackdrop ?: baseBackdrop
    var headerHeight by remember { mutableStateOf(60.dp) }
    var composerHeight by remember { mutableStateOf(64.dp) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.upload(context.contentResolver, it) }
    }
    LaunchedEffect(vm, lifecycle, active) {
        if (active) lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.observe() }
    }
    LaunchedEffect(account.account?.id) { selected = null; menuOpen = false; confirming = null; preview = null; referenceOpen = false }
    LaunchedEffect(active) { if (!active) { keyboard?.hide(); membersOpen = false; menuOpen = false; referenceOpen = false; selected = null; confirming = null; preview = null } }
    LaunchedEffect(state.messages.lastOrNull()?.id) { if (nearBottom) list.animateScrollToItem(0) }
    LaunchedEffect(state.scrollRequest) { if (state.scrollRequest > 0) list.animateScrollToItem(0) }
    val menuItems = selected?.let { message -> buildList {
        if (message.content.isNotEmpty()) add(GlassMenuItem("chat_copy", "复制文字", MusicIcons.Copy,
            onClick = { clipboard.setText(AnnotatedString(message.content)) }))
        if (message.userId == account.account?.id) add(GlassMenuItem("chat_delete", "删除消息", MusicIcons.Trash,
            danger = true, onClick = { confirming = message }))
    } } ?: listOf(
        GlassMenuItem("chat_pick_image", "选择图片", MusicIcons.Image, enabled = !state.uploading && !state.sending,
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }),
        GlassMenuItem("chat_attach_current", "引用音乐", MusicIcons.Headphones, enabled = !state.sending,
            onClick = { referenceOpen = true }))
    SettingsUiScope {
    CompositionLocalProvider(LocalContentColor provides MiuixTheme.colorScheme.onSurface) {
    Box(Modifier.fillMaxSize().padding(bottom = bottom).testTag("discover_chat")) {
    GlassMenu(menuOpen, { menuOpen = it }, menuItems, menuAnchor, controlsBackdrop,
        GlassMenuTags("chat_menu_panel", "chat_menu_dismiss", "chat_menu_action"), menuWidth = null, content = {
        Box(Modifier.fillMaxSize().then(if (messagesBackdrop != null) Modifier.layerBackdrop(messagesBackdrop) else Modifier)) {
            // The messages scroll under the fades; the fixed controls stay sharp above them.
            LazyColumn(Modifier.fillMaxSize()
                .testTag("chat_messages"), state = list, reverseLayout = true,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = headerHeight + 16.dp, bottom = composerHeight + 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(state.messages.asReversed(), key = { it.id }) { message ->
                    ChatMessageRow(message, mine = message.userId == account.account?.id, vm,
                        onProfile = { id -> keyboard?.hide(); navigate("user/profile/$id") },
                        onImage = { preview = it }, onMenu = { anchor -> selected = message; menuAnchor = anchor; menuOpen = true }, onCard = { card ->
                            if (card.type == "song") {
                                val song = io.github.currencortex.music.data.song.Song(card.id, card.name, card.sub, cover = card.pic)
                                val player = vm.container.playerController
                                if (player.external != null) player.external?.request(song) else player.playList(listOf(song), 0)
                            } else navigate("lib/${if (card.type == "playlist") "ncmplaylist" else card.type}/${card.id}")
                        })
                }
                if (state.loading && state.messages.isEmpty()) item(key = "loading") { ChatLoadingMessages() }
                if (state.hasOlder) item(key = "older") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        MusicTextAction(if (state.loadingOlder) "正在加载…" else "加载更早的消息", vm::loadOlder,
                            Modifier.testTag("chat_older"), enabled = !state.loadingOlder)
                    }
                }
            }
        }
        CompositionLocalProvider(LocalGlassBackdrop provides controlsBackdrop) {
        if (!state.loading && state.loaded && state.messages.isEmpty()) ChatEmptyState(Modifier.align(Alignment.Center))
        if (!nearBottom && state.messages.isNotEmpty()) GlassActionButton("回到最新消息",
            { scope.launch { list.animateScrollToItem(0) } },
            Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = composerHeight + 8.dp).testTag("chat_latest"),
            fillWidth = false, minHeight = 36.dp)
        ChatEdgeFade(atTop = true,
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(headerHeight + 24.dp).testTag("chat_header_fade"))
        ChatEdgeFade(atTop = false,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(composerHeight + 24.dp).testTag("chat_composer_fade"))
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().pointerInput(Unit) { detectTapGestures {} }.onSizeChanged {
            headerHeight = with(density) { it.height.toDp() }
        }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("chat_header"),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("公共聊天室", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(5.dp).background(if (state.connected) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.onSurfaceVariantSummary, CircleShape))
                        Text(if (state.connected) "实时消息" else if (state.loaded) "正在连接…" else "与大家聊聊音乐",
                            fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
                GlassActionButton("成员 ${state.members.size}", { membersOpen = true },
                    Modifier.testTag("chat_members"), fillWidth = false)

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
            state.attachments.forEach { card ->
                SettingsGroup(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("chat_attachment_${card.type}_${card.id}")) {
                    SettingsPreference(card.name, "引用 · ${card.sub.ifBlank { "音乐内容" }}",
                        leading = { MusicCover(card.pic, Modifier.size(32.dp), cornerRadius = 8.dp) }, trailing = {
                            GlassIconButton({ vm.removeAttachment(card) }, "移除引用", enabled = !state.sending,
                                modifier = Modifier.testTag("chat_remove_attachment_${card.type}_${card.id}"), surface = false) {
                                Icon(MusicIcons.Close, null, Modifier.size(18.dp))
                            }
                        })
                }
            }
            if (state.image.isNotEmpty()) SettingsGroup(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                SettingsPreference("待发送图片", leading = {
                    AsyncImage(vm.repository.imageUrl(vm.container.audioSession().server, state.image), "待发送图片",
                        Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                }, trailing = {
                    GlassIconButton(vm::removeImage, "移除图片", enabled = !state.sending, surface = false) {
                        Icon(MusicIcons.Close, null, Modifier.size(18.dp))
                    }
                })
            }
            if (account.account == null) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("登录 CurrentMusic 后即可聊天", Modifier.weight(1f), fontSize = 13.sp)
                MusicTextAction("去登录", { navigate("user/login") }, Modifier.testTag("chat_login"))
            } else {
                val fragment = state.draft.substringAfterLast('@', "").takeIf {
                    state.draft.contains('@') && it.length <= 12 && it.none(Char::isWhitespace)
                }
                if (fragment != null) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (state.suggestions + state.members.filter { it.nickname.contains(fragment, ignoreCase = true) }).distinctBy { it.id }.take(3).forEach { member ->
                        MusicTextAction("@${member.nickname}", { vm.mention(member) }, Modifier.weight(1f))
                    }
                }
                if (state.draft.contains("@CurrentMusicAI")) GlassActionButton("猜你想问", {
                    vm.draft("@CurrentMusicAI " + if (state.image.isNotEmpty()) "这张图里有什么？" else "介绍一下这首歌的创作背景")
                }, Modifier.padding(start = 16.dp), fillWidth = false, minHeight = 36.dp)
                GlassTextComposer(state.draft, vm::draft,
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("chat_composer"),
                    fieldModifier = Modifier.testTag("chat_input").semantics { contentDescription = "聊天消息" },
                    placeholder = "聊聊正在听的音乐…", leading = {
                        GlassIconButton({ selected = null; menuAnchor = attachmentAnchor; menuOpen = !menuOpen }, "添加内容",
                            Modifier.testTag("chat_add_content"), enabled = !state.sending && !state.uploading,
                            opensMenu = true, onBoundsChanged = { attachmentAnchor = it }, surface = false) {
                            if (state.uploading) CircularProgressIndicator(Modifier.size(18.dp))
                            else Icon(MusicIcons.Add, null, Modifier.size(22.dp))
                        }
                    }, trailing = {
                        ChatSendAction(vm::send, Modifier.testTag("chat_send"), busy = state.sending,
                            enabled = !state.sending && !state.uploading && (state.draft.isNotBlank() || state.image.isNotBlank() || state.attachments.isNotEmpty()))
                    })
            }
        }
        }
    }, toolbar = {})
    }
    if (referenceOpen) {
        var query by remember { mutableStateOf("") }
        var type by remember { mutableStateOf("song") }
        MusicChoiceDialog("引用内容", { referenceOpen = false }) {
            MusicTextAction("引用当前歌曲", { vm.attachCurrent(); referenceOpen = false })
            SettingsSegmentedControl(listOf("歌曲", "专辑", "歌手", "歌单"),
                listOf("song", "album", "artist", "playlist").indexOf(type), { index ->
                    type = listOf("song", "album", "artist", "playlist")[index]; vm.searchReferences(query, type)
                }, tag = "chat_reference_category")
            TextField(query, { query = it; vm.searchReferences(query, type) }, label = "搜索引用内容", modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                if (state.referenceBusy) item { LoadingTextList(2, leading = true) }
                else if (query.isNotBlank() && state.references.isEmpty()) item { Text("没有找到相关内容", fontSize = 13.sp) }
                items(state.references) { card -> MusicAttachmentCard(card) { vm.attach(card); referenceOpen = false } }
            }
            MusicDialogButton("关闭", { referenceOpen = false })
        }
    }
    if (membersOpen) MusicDialog("聊天室成员", { membersOpen = false }) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.members, key = { it.id }) { member ->
                SettingsGroup {
                    SettingsPreference(member.nickname.ifBlank { member.username },
                        listOf(member.title, member.ipRegion.takeIf { it.isNotBlank() }?.let { "IP属地：$it" }.orEmpty())
                            .filter(String::isNotBlank).joinToString(" · "),
                        onClick = { membersOpen = false; navigate("user/profile/${member.id}") },
                        leading = { UserAvatar(member, vm.container, 36.dp, reserveOverlay = false) }, trailing = {
                            if (account.account != null) GlassIconButton({ vm.mention(member); membersOpen = false },
                                "提及 ${member.nickname}", Modifier.testTag("chat_mention_${member.id}"), surface = false) {
                                Text("@", fontSize = 19.sp, color = MiuixTheme.colorScheme.primary)
                            }
                        })
                }
            }
        }
        MusicDialogButton("关闭", { membersOpen = false })
    }
    confirming?.let { message -> MusicDialog("删除这条消息？", { confirming = null }) {
        Text("删除后其他成员也将无法查看。", fontSize = 13.sp)
        MusicDialogButton("确认删除", { vm.delete(message); confirming = null }, Modifier.testTag("chat_confirm_delete"), destructive = true)
        MusicDialogButton("取消", { confirming = null })
    } }
    preview?.let { image -> MusicDialog("聊天图片", { preview = null }) {
        AsyncImage(image, "聊天图片", Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 420.dp), contentScale = ContentScale.Fit)
        MusicDialogButton("关闭", { preview = null })
    } }
    }
    }
}

@Composable private fun ChatMessageRow(message: ChatMessage, mine: Boolean, vm: ChatViewModel,
    onProfile: (Long) -> Unit, onImage: (String) -> Unit, onMenu: (Rect) -> Unit,
    onCard: (io.github.currencortex.music.data.profile.PostAttachment) -> Unit) {
    val user = message.user ?: ProfileUser(id = message.userId, nickname = "已注销用户")
    val color = MiuixTheme.colorScheme
    var actionBounds by remember(message.id) { mutableStateOf<Rect?>(null) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Avatar, gap, and a small opposite gutter; long messages retain their reading width.
        val messageWidth = (maxWidth - 36.dp - 10.dp - 12.dp).coerceIn(0.dp, 360.dp)
        Row(Modifier.fillMaxWidth().testTag("chat_message_${message.id}"),
            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
            if (!mine) Box(Modifier.padding(end = 10.dp).testTag("chat_avatar_${message.id}")
                .combinedClickable(enabled = user.id > 0, onClick = { onProfile(user.id) }, onLongClick = { vm.mention(user) })) {
                UserAvatar(user, vm.container, 36.dp, reserveOverlay = false)
            }
            Column(Modifier.widthIn(max = messageWidth), horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(user.nickname.ifBlank { user.username }, Modifier.weight(1f, fill = false).clickable(enabled = user.id > 0) { onProfile(user.id) },
                        fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    UserRoleBadge(user.role)
                    if (user.isBot) Text("AI", fontSize = 10.sp, color = color.primary)
                    GlassIconButton({ actionBounds?.let(onMenu) }, "消息操作", Modifier.testTag("chat_menu_${message.id}"),
                        size = 32.dp, opensMenu = true, surface = false, onBoundsChanged = { actionBounds = it }) {
                        Icon(MusicIcons.More, null, Modifier.size(17.dp).rotate(90f), tint = color.onSurfaceVariantSummary)
                    }
                }
                Column(Modifier.testTag("chat_bubble_${message.id}")
                    .clip(RoundedCornerShape(if (mine) 18.dp else 6.dp, if (mine) 6.dp else 18.dp, 18.dp, 18.dp))
                    .background(if (mine) color.primary.copy(alpha = if (color.onSurface.luminance() > .5f) .16f else .09f) else color.surfaceContainer).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (message.content.isNotEmpty()) SelectionContainer {
                        MarkdownText(message.content, Modifier.testTag("chat_body_${message.id}"), compact = true, allowImages = false)
                    }
                    if (message.streaming) Text(if (message.content.isEmpty()) "正在思考…" else "正在回答…",
                        fontSize = 11.sp, color = color.onSurfaceVariantSummary)
                    validMusicAttachments(message.atts).forEach { card -> MusicAttachmentCard(card) { onCard(card) } }
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
                .combinedClickable(enabled = user.id > 0, onClick = { onProfile(user.id) }, onLongClick = { vm.mention(user) })) {
                UserAvatar(user, vm.container, 36.dp, reserveOverlay = false)
            }
        }
    }
}

package io.github.currencortex.music.feature.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.currencortex.music.data.profile.SquareUser
import io.github.currencortex.music.data.profile.listeningDuration
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.util.collectAsPageState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun DiscoverUsersPage(vm: DiscoverViewModel, navigate: (String) -> Unit, active: Boolean) {
    val state by vm.state.collectAsPageState()
    val latest by rememberUpdatedState(state)
    val bottom = LocalMusicBottomInset.current
    val owner = LocalLifecycleOwner.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var sorting by rememberSaveable { mutableStateOf(false) }
    fun search() { focus.clearFocus(); keyboard?.hide(); scope.launch { list.scrollToItem(0); vm.submit() } }
    LaunchedEffect(vm, owner, active) {
        if (active) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.pollStats() }
    }
    LaunchedEffect(active) { if (!active) { focus.clearFocus(); keyboard?.hide(); sorting = false } }
    LaunchedEffect(state.submitted, state.sort, state.listening) { list.scrollToItem(0) }
    LaunchedEffect(list, active) {
        if (active) snapshotFlow {
            val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            Triple(last, list.layoutInfo.totalItemsCount, latest.users.size)
        }.distinctUntilChanged().collect { (last, total, _) ->
            val current = latest
            if (current.loaded && current.users.isNotEmpty() && !current.loading && current.error == null &&
                current.hasMore && total > 0 && last >= total - 3) vm.submit(more = true)
        }
    }
    Column(Modifier.fillMaxSize().testTag("discover_users")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            UserSearchField(state.query, vm::query, ::search, {
                focus.clearFocus(); keyboard?.hide(); scope.launch { list.scrollToItem(0); vm.clearQuery() }
            })
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                UserFilterChip(state.listening) { focus.clearFocus(); keyboard?.hide(); vm.filter(!state.listening) }
                Spacer(Modifier.weight(1f))
                Row(Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) {
                    focus.clearFocus(); keyboard?.hide(); sorting = true
                }.padding(start = 12.dp, end = 4.dp).testTag("discover_sort"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(userSortOptions.first { it.first == state.sort }.second, fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = .65f))
                    Icon(MusicIcons.Sort, "排序方式", Modifier.size(17.dp), tint = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
                }
            }
        }
        MusicPullToRefresh(state.loading && !state.loadingMore, vm::refresh, Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize().testTag("discover_users_list"), state = list,
                contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 20.dp + bottom),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "summary") {
                    Text(when {
                        !state.loaded && state.loading -> "正在查找用户…"
                        state.submitted.isNotEmpty() -> "“${state.submitted}” · ${state.total} 位用户"
                        state.listening -> "${state.total} 位正在听歌的用户"
                        else -> "${state.stats.users} 位用户 · ${state.stats.listening} 人正在听歌"
                    }, Modifier.padding(start = 2.dp, bottom = 4.dp), fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .45f))
                }
                if (state.loading && state.users.isEmpty()) items(4, key = { "placeholder-$it" }) { UserRowPlaceholder() }
                if (state.loaded && !state.loading && state.users.isEmpty() && state.error == null) item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Person, null, Modifier.size(32.dp), tint = MiuixTheme.colorScheme.onSurface.copy(alpha = .28f))
                        Text(if (state.listening) "当前没有正在听歌的用户" else "没有匹配的用户", fontSize = 14.sp)
                        Text(if (state.submitted.isNotEmpty()) "换个关键词，或清除搜索试试" else "稍后刷新，看看谁来了", fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
                        if (state.submitted.isNotEmpty()) MusicTextAction("清除搜索", vm::clearQuery)
                        else if (state.listening) MusicTextAction("查看全部用户", { vm.filter(false) })
                    }
                }
                items(state.users, key = { "user-${it.id}" }) { user -> UserDirectoryRow(user, vm) { navigate("user/profile/${user.id}") } }
                state.error?.let { error -> item(key = "error") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
                        MusicTextAction(if (state.failedMore) "重试加载更多" else "重新加载", vm::retry, Modifier.testTag("discover_retry"))
                    }
                } }
                if (state.users.isNotEmpty() && state.error == null) item(key = "pagination") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        when {
                            state.loadingMore -> CircularProgressIndicator(Modifier.size(18.dp))
                            state.hasMore -> MusicTextAction("加载更多", { vm.submit(more = true) }, Modifier.testTag("discover_more"), enabled = !state.loading)
                            else -> Text("已显示全部 ${state.users.size} 位用户", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .35f))
                        }
                    }
                }
            }
        }
    }
    if (sorting) MusicDialog("用户排序", { sorting = false }) {
        Column(Modifier.selectableGroup()) {
            userSortOptions.forEach { (key, label) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
                    .selectable(state.sort == key, role = Role.RadioButton) { sorting = false; vm.sort(key) }
                    .padding(horizontal = 12.dp).testTag("discover_sort_$key"), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), fontSize = 15.sp,
                        color = if (state.sort == key) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
                    if (state.sort == key) Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = MiuixTheme.colorScheme.primary)
                }
            }
        }
        MusicDialogButton("取消", { sorting = false })
    }
}

@Composable private fun UserSearchField(query: String, change: (String) -> Unit, search: () -> Unit, clear: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val colors = MiuixTheme.colorScheme
    val outline by animateColorAsState(if (focused) colors.primary.copy(alpha = .4f) else Color.Transparent, tween(160), label = "user search focus")
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(15.dp))
        .background(colors.surfaceContainer).border(1.dp, outline, RoundedCornerShape(15.dp)).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Search, null, Modifier.size(19.dp), tint = colors.onSurface.copy(alpha = .4f))
        BasicTextField(query, change, Modifier.weight(1f).padding(vertical = 12.dp).testTag("discover_query")
            .semantics { contentDescription = "搜索昵称或简介" }, singleLine = true, interactionSource = interactions,
            textStyle = TextStyle(fontSize = 14.sp, color = colors.onSurface), cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }),
            decorationBox = { inner -> Box { if (query.isEmpty()) Text("搜索昵称或简介", Modifier.clearAndSetSemantics {}, fontSize = 14.sp, color = colors.onSurface.copy(alpha = .4f)); inner() } })
        if (query.isNotEmpty()) Box(Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Button, onClick = clear)
            .testTag("discover_clear"), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Close, "清除搜索", Modifier.size(16.dp), tint = colors.onSurface.copy(alpha = .5f))
        }
        MusicTextAction("搜索", search, Modifier.testTag("discover_submit"))
    }
}

@Composable private fun UserFilterChip(selected: Boolean, click: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val background by animateColorAsState(if (selected) colors.primary.copy(alpha = .1f) else Color.Transparent, tween(180), label = "listener filter")
    Row(Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(background)
        .toggleable(selected, role = Role.Checkbox, onValueChange = { click() }).padding(horizontal = 10.dp).testTag("discover_listening"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(MusicIcons.Headphones, null, Modifier.size(16.dp), tint = if (selected) colors.primary else colors.onSurface.copy(alpha = .5f))
        Text("正在听歌", Modifier.semantics { contentDescription = "仅显示正在听歌" }, fontSize = 12.sp,
            color = if (selected) colors.primary else colors.onSurface.copy(alpha = .65f))
        if (selected) Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = colors.primary)
    }
}

@Composable private fun UserDirectoryRow(user: SquareUser, vm: DiscoverViewModel, open: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .985f else 1f, tween(120), label = "user row press")
    Row(Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(18.dp))
        .background(MiuixTheme.colorScheme.surface).clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = open)
        .testTag("discover_user_${user.id}").heightIn(min = 84.dp).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        UserAvatar(user.profileUser(), vm.container, 44.dp, reserveOverlay = false)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(user.nickname, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                UserRoleBadge(user.role)
            }
            UserIdentityDetails(user.title, user.ipRegion)
            if (user.bio.isNotBlank()) Text(user.bio, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = .55f))
            Text("听歌 ${user.days} 天 · ${listeningDuration(user.listenMs)}", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = .4f))
            user.current?.let { current ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(13.dp), tint = MiuixTheme.colorScheme.primary)
                    Text("正在听：${current.name}", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MiuixTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable private fun UserRowPlaceholder() {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MiuixTheme.colorScheme.surface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MusicPlaceholder(Modifier.size(44.dp).clip(CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MusicPlaceholder(Modifier.fillMaxWidth(.45f).height(16.dp))
            MusicPlaceholder(Modifier.fillMaxWidth(.8f).height(12.dp))
            MusicPlaceholder(Modifier.fillMaxWidth(.55f).height(10.dp))
        }
    }
}

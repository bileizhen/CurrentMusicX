package io.github.currencortex.music.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.data.profile.*
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.auth.AuthViewModel
import io.github.currencortex.music.feature.auth.LoginScreen
import io.github.currencortex.music.feature.library.LibraryLinks
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.*
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable fun UserAvatar(user: ProfileUser, container: AppContainer, size: androidx.compose.ui.unit.Dp = 64.dp,
    decoration: String = user.decoration) {
    val preferences by container.musicSettings.state.collectAsStateWithLifecycle()
    val scales by container.profileRepository.scales.collectAsStateWithLifecycle()
    val versions by container.profileRepository.avatarVersions.collectAsStateWithLifecycle()
    val repository = container.profileRepository
    var avatarUrl = repository.avatarUrl(preferences.server, user.avatar)
    if (!user.avatar.startsWith("http") && versions[user.id] != null)
        avatarUrl = avatarUrl?.toHttpUrlOrNull()?.newBuilder()?.setQueryParameter("v", versions[user.id].toString())?.build()?.toString()
    DecoratedAvatar(avatarUrl, repository.decorationUrl(preferences.server, decoration),
        scale = scales[decoration] ?: 1.0, size = size, onImageError = {
            // Report the failure category only; image URLs can contain signed credentials.
            container.logger.warn("Image", "User image failed: ${it.javaClass.simpleName}", null)
        })
}

@Composable fun MeScreen(vm: ProfileViewModel, auth: AuthViewModel, navigate: (String) -> Unit, onSettings: () -> Unit,
    play: (List<Song>, Int) -> Unit) {
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    if (account.account == null) Column {
        TextButton("设置", onClick = onSettings)
        LoginScreen(auth)
    } else ProfileScreen(vm, navigate, play, onSettings = onSettings)
}

@Composable fun ProfileScreen(vm: ProfileViewModel, navigate: (String) -> Unit, play: (List<Song>, Int) -> Unit,
    onBack: (() -> Unit)? = null, onSettings: (() -> Unit)? = null) {
    // Lazy content can be evaluated after a refresh starts. Capture one immutable
    // snapshot so its user, statistics and lists always belong to the same result.
    val state = vm.state.collectAsStateWithLifecycle().value
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { vm.avatar.value = uri; vm.dialog.value = "avatar" }
    }
    val user = state.profile?.user
    val own = user != null && user.id == account.account?.id
    LazyColumn(Modifier.fillMaxSize().testTag("profile_screen"), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row {
            onBack?.let { TextButton("返回", onClick = it) }
            onSettings?.let { TextButton("设置", onClick = it) }
            TextButton("刷新", onClick = vm::reload, enabled = !state.loading)
        } }
        if (state.loading) item { Text("正在加载用户资料…") }
        state.error?.let { item { Text(it); TextButton("重试", onClick = vm::reload) } }
        if (user != null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(user, vm.container, 44.dp)
                    Column(Modifier.weight(1f)) {
                        Text(user.nickname.ifBlank { user.username }, fontSize = 26.sp)
                        Text(user.bio.ifBlank { "还没有填写简介" })
                    }
                }
            }
            item { val stat = state.profile!!.stat
                LazyRow(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    items(listOf("点赞" to "${stat.likes}", "收藏" to "${stat.favs}", "歌单" to "${stat.playlists}",
                        "听歌天数" to "${stat.playDays}", "听歌时长" to listeningDuration(stat.listenMs))) { (label, value) ->
                        Column { Text(value); Text(label) }
                    }
                }
            }
            if (own) {
                item { LibraryLinks(navigate) }
                item { Row {
                    TextButton("编辑资料", onClick = { vm.dialog.value = "edit" }, enabled = !busy, modifier = Modifier.testTag("edit_profile"))
                    TextButton("更换头像", onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy)
                } }
                item { TextButton("头像挂件", onClick = { navigate("user/decorations") }, modifier = Modifier.testTag("open_decorations")) }
                item { TextButton("网易云账号", onClick = { navigate("user/binding") }, modifier = Modifier.testTag("open_binding")) }
                item { TextButton("账户管理", onClick = { navigate("user/accounts") }, modifier = Modifier.testTag("open_accounts")) }
                item { TextButton("修改密码", onClick = { vm.dialog.value = "password" }, enabled = !busy) }
                item { TextButton("退出登录", onClick = { vm.dialog.value = "logout" }, enabled = !busy) }
            }
            state.profile?.current?.let { song -> item { Text("当前在听"); Row(verticalAlignment = Alignment.CenterVertically) {
                MusicCover(song.pic, Modifier.size(48.dp)); Column { TextButton(song.name, onClick = { play(listOf(song.toDomain()), 0) }); Text(song.artists) }
            } } }
            val recent = state.profile!!.recent.map { it.toDomain() }
            if (recent.isNotEmpty()) item { Text("最近听过") }
            itemsIndexed(recent, key = { _, song -> "recent-${song.id}" }) { index, song ->
                SongRow(SongRowUi(song), { play(recent, index) }, { vm.container.playerController.add(song, true) }, { vm.container.playerController.add(song) })
            }
            val lists = state.profile!!.playlists
            if (lists.isNotEmpty()) item { Text(if (own) "我的歌单" else "公开歌单") }
            items(lists, key = { "playlist-${it.id}" }) { list ->
                Row(Modifier.fillMaxWidth().clickable { navigate("lib/playlist/${list.id}") }.padding(8.dp)) {
                    MusicCover(list.cover, Modifier.size(48.dp)); Column(Modifier.padding(12.dp)) {
                        Text(list.name); Text("${list.count} 首" + if (list.source == "ncm") " · 网易云" else "")
                    }
                }
            }
        }
        state.sectionErrors.forEach { item { Text(it) } }
    }
}

@Composable fun ProfileDialogs(vm: ProfileViewModel, auth: AuthViewModel) {
    val dialog by vm.dialog.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val avatar by vm.avatar.collectAsStateWithLifecycle()
    val context = LocalContext.current
    fun close() { if (!busy) { vm.dialog.value = null; vm.avatar.value = null } }
    when (dialog) {
        "edit" -> state.profile?.user?.let { user ->
            var nickname by rememberSaveable(user.id) { mutableStateOf(user.nickname) }
            var bio by rememberSaveable(user.id) { mutableStateOf(user.bio) }
            var visible by rememberSaveable(user.id) { mutableStateOf(user.visible) }
            MusicDialog("编辑资料", ::close) {
                Text("昵称"); TextField(nickname, { nickname = it }, singleLine = true, modifier = Modifier.testTag("profile_nickname"))
                Text("简介"); TextField(bio, { bio = it }, modifier = Modifier.testTag("profile_bio"))
                visible?.let { current -> SettingsSwitch("在用户广场公开资料", current, { visible = it }) }
                TextButton("保存", onClick = { vm.update(nickname, bio, visible) }, enabled = nickname.isNotBlank() && !busy,
                    modifier = Modifier.testTag("save_profile"))
            }
        }
        "avatar" -> MusicDialog("更换头像", ::close) {
            AsyncImage(avatar, contentDescription = "新头像预览", modifier = Modifier.size(120.dp))
            Text("上传后将替换当前头像。")
            TextButton("确认上传", onClick = { vm.upload(context.contentResolver) }, enabled = !busy, modifier = Modifier.testTag("confirm_avatar_upload"))
        }
        "password" -> {
            var old by remember { mutableStateOf("") }; var new by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }
            MusicDialog("修改密码", ::close) {
                Text("当前密码"); TextField(old, { old = it }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                Text("新密码（至少 6 位）"); TextField(new, { new = it }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                Text("再次输入新密码"); TextField(confirm, { confirm = it }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                TextButton("更新密码", enabled = old.isNotEmpty() && new.length >= 6 && new == confirm && !busy, onClick = {
                    vm.password(old, new); old = ""; new = ""; confirm = ""
                })
            }
        }
        "logout" -> MusicDialog("退出当前账号？", ::close) {
            Text("会移除本机保存的当前账号凭据，其他已保存账号保留。")
            TextButton("确认退出", onClick = { vm.container.playerController.pause(); vm.dialog.value = null; auth.logout() })
        }
    }
}

@Composable fun DiscoverScreen(vm: DiscoverViewModel, navigate: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(vm, owner) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.pollStats() } }
    LazyColumn(Modifier.fillMaxSize().testTag("discover_screen"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("发现", fontSize = 30.sp); Text("${state.stats.users} 位用户 · ${state.stats.listening} 人正在听歌") }
        item { TextField(state.query, vm::query, singleLine = true, modifier = Modifier.testTag("discover_query")); TextButton("搜索用户", onClick = { vm.submit() }, modifier = Modifier.testTag("discover_submit")) }
        item { SettingsSwitch("仅显示正在听歌", state.listening, vm::filter) }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(listOf("reg" to "最新注册", "reg_asc" to "最早注册", "name" to "昵称", "days" to "听歌天数", "listen" to "听歌时长", "likes" to "点赞数")) { (key, label) ->
                TextButton((if (state.sort == key) "✓ " else "") + label, onClick = { vm.sort(key) })
            }
        } }
        item { TextButton("刷新用户", onClick = { vm.submit() }, enabled = !state.loading) }
        if (state.loading) item { Text("正在加载用户…") }
        state.error?.let { item { Text(it); TextButton("重试", onClick = { vm.submit() }) } }
        if (!state.loading && state.users.isEmpty() && state.error == null) item { Text(if (state.listening) "当前没有正在听歌的用户" else "没有匹配的用户") }
        items(state.users, key = { it.id }) { user ->
            Row(Modifier.fillMaxWidth().clickable { navigate("user/profile/${user.id}") }.testTag("discover_user_${user.id}"), verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(user.profileUser(), vm.container, 32.dp)
                Column(Modifier.weight(1f)) {
                    Text(user.nickname); Text(user.bio); Text("${user.days} 天 · ${listeningDuration(user.listenMs)}")
                    user.current?.let { Text("正在听：${it.name}") }
                }
            }
        }
        if (state.users.size < state.total) item { TextButton("加载更多", onClick = { vm.submit(true) }, enabled = !state.loading) }
    }
}

@Composable fun DecorationScreen(vm: DecorationViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    val catalog = state.catalog
    val user = ProfileUser(id = account.account?.id ?: 0L, avatar = account.account?.avatar.orEmpty())
    Column(Modifier.fillMaxSize().padding(20.dp).testTag("decoration_screen"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton("返回", onClick = onBack); Text("头像挂件", fontSize = 28.sp)
        if (state.loading) Text("正在加载挂件…")
        state.error?.let { Text(it); TextButton("重试", onClick = vm::reload) }
        if (catalog != null) {
            if (!catalog.unlocked) Text("累计 ${listeningDuration(catalog.listenMs)}；解锁需 ${listeningDuration(catalog.minListenMs)}")
            TextField(state.query, vm::query, singleLine = true, modifier = Modifier.testTag("decoration_query"))
            Text("搜索名称或 ID，可预览挂件。")
            TextButton("取消佩戴", onClick = { vm.set("") }, enabled = catalog.current.isNotEmpty() && !busy)
            LazyVerticalGrid(GridCells.Adaptive(110.dp), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(catalog.decorations.filter { it.name.contains(state.query, true) || it.id.contains(state.query, true) }, key = { it.id }) { item ->
                    Column(Modifier.clickable { vm.preview(item) }.testTag("decoration_${item.id}"), horizontalAlignment = Alignment.CenterHorizontally) {
                        UserAvatar(user, vm.container, 38.dp, item.id)
                        Text((if (catalog.current == item.id) "✓ " else "") + item.name)
                    }
                }
            }
            state.preview?.let { item -> MusicDialog("挂件预览：${item.name}", { if (!busy) vm.preview(null) }) {
                UserAvatar(user, vm.container, 64.dp, item.id)
                TextButton("佩戴", onClick = { vm.set(item.id) }, enabled = catalog.unlocked && !busy, modifier = Modifier.testTag("wear_decoration"))
                if (!catalog.unlocked) Text("尚未达到服务器解锁条件")
            } }
        }
        val message by vm.message.collectAsStateWithLifecycle()
        message?.let { Text(it) }
    }
}

@Composable fun AccountScreen(container: AppContainer, auth: AuthViewModel, onBack: () -> Unit, add: () -> Unit) {
    val accounts by container.accountRepository.savedAccounts.collectAsStateWithLifecycle()
    val account by container.accountRepository.state.collectAsStateWithLifecycle()
    val state by auth.state.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<io.github.currencortex.music.data.auth.SavedAccount?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp).testTag("account_screen"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton("返回", onClick = onBack); Text("账户管理", fontSize = 28.sp); TextButton("添加账号", onClick = add, enabled = !state.loading) }
        items(accounts, key = { it.key }) { saved ->
            val active = saved.id == account.account?.id && saved.server == container.accountRepository.server
            Column {
                Text(saved.nickname.ifBlank { saved.username }); Text(saved.server)
                Row {
                    TextButton(if (active) "当前账号" else "切换", enabled = !active && !state.loading, onClick = { auth.switch(saved.key) }, modifier = Modifier.testTag("switch_account_${saved.id}"))
                    TextButton("移除保存", enabled = !active && !state.loading, onClick = { removing = saved })
                }
            }
        }
        state.message?.let { item { Text(it) } }
        if (state.loading) item { Text("正在验证账号…") }
    }
    removing?.let { saved -> MusicDialog("移除已保存账号？", { removing = null }) {
        Text(saved.nickname.ifBlank { saved.username }); Text("仅移除本机凭据。")
        TextButton("确认移除", onClick = { removing = null; auth.removeSaved(saved.key) })
    } }
}

package io.github.currencortex.music.feature.playlistimport

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun PlaylistImportScreen(vm: PlaylistImportViewModel, onBack: () -> Unit,
    onLogin: () -> Unit, onPlaylist: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) }
    var permissionError by remember { mutableStateOf<String?>(null) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        permissionError = if (granted) null else "未获得音频权限，可在系统设置中开启。"
        if (granted) vm.scan(context.applicationContext)
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.readImages(context.applicationContext, uris)
    }
    DisposableEffect(lifecycle, permission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                if (allowed && !granted) vm.localPermissionLost()
                allowed = granted
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val colors = MiuixTheme.colorScheme
    val connected = account.loggedIn && !account.stale
    val editable = connected && !state.busy && !state.imagesLoading && state.task == null
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val ready = editable && !state.scanning && when (state.mode) {
        PlaylistImportMode.LINKS -> state.links.any(String::isNotBlank)
        PlaylistImportMode.TEXT -> state.text.isNotBlank()
        PlaylistImportMode.IMAGES -> state.images.isNotEmpty() && state.targetId != null && !state.targetsLoading
        PlaylistImportMode.LOCAL -> state.selected.isNotEmpty()
    }
    MiuixTheme(colors = colors, textStyles = MiuixTheme.textStyles) {
    Column(Modifier.fillMaxSize().background(colors.background).imePadding().testTag("playlist_import")) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 22.dp, top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(colors.surfaceContainer)
                .clickable(role = Role.Button, onClick = onBack).testTag("playlist_import_back"), contentAlignment = Alignment.Center) {
                Icon(MusicIcons.Back, "返回", Modifier.size(22.dp))
            }
            Text("导入歌单", fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        }
        if (state.task == null) {
            Row(Modifier.fillMaxWidth().testTag("playlist_import_actions").background(colors.background)
                .padding(horizontal = 22.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (connected) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("网易云 · ${account.profile?.nickname.orEmpty()}", fontSize = 11.sp,
                        color = colors.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (state.mode != PlaylistImportMode.IMAGES) Row(Modifier.fillMaxWidth().heightIn(min = 32.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("加入「我喜欢」", Modifier.weight(1f), fontSize = 13.sp,
                            color = colors.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Switch(state.importStar, vm::importStar, enabled = editable, modifier = Modifier.testTag("playlist_import_star"))
                    } else Text("保存到所选歌单", fontSize = 13.sp, color = colors.onSurfaceVariantSummary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box((if (connected) Modifier.width(132.dp) else Modifier.fillMaxWidth()).height(46.dp).clip(RoundedCornerShape(15.dp))
                    .background(if (connected && !ready) colors.primary.copy(alpha = .14f) else colors.primary)
                    .clickable(enabled = if (connected) ready else true, role = Role.Button) {
                        focus.clearFocus(); keyboard?.hide(); if (connected) vm.submit() else onLogin()
                    }.testTag(if (connected) "playlist_import_submit" else "playlist_import_login"), contentAlignment = Alignment.Center) {
                    Text(if (!connected) "登录网易云后导入" else if (state.busy) "正在提交…" else "开始导入",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = if (connected && !ready) colors.primary.copy(alpha = .5f) else colors.onPrimary)
                }
            }
        }
        Row(Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 22.dp)
            .background(colors.surfaceContainer, RoundedCornerShape(18.dp)).padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PlaylistImportMode.entries.forEach { tab ->
                val selected = state.mode == tab
                Row(Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(13.dp))
                    .background(if (selected) colors.primary else Color.Transparent)
                    .selectable(selected, enabled = editable, role = Role.Tab, onClick = { focus.clearFocus(); keyboard?.hide(); vm.mode(tab) })
                    .testTag("playlist_import_tab_${tab.name}"), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                    Text(tab.label.removeSuffix("导入"), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        color = if (selected) colors.onPrimary else colors.onSurfaceVariantSummary)
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp).testTag("playlist_import_list"),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 8.dp, bottom = LocalMusicBottomInset.current + 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!connected) item {
                Text(if (account.stale) "网易云登录已过期，请重新登录后导入。" else "登录网易云后即可导入歌单。",
                    fontSize = 14.sp, color = colors.onSurfaceVariantSummary)
            }
            run {
                if (state.task == null) {
                    item { PlaylistImportHero(state.mode) }
                    when (state.mode) {
                        PlaylistImportMode.LINKS -> {
                            item { ImportStep(1, "准备分享链接") }
                            item { PlaylistImportGuide() }
                            item { Spacer(Modifier.height(4.dp)); ImportStep(2, "添加分享链接") }
                            itemsIndexed(state.links) { index, link ->
                                ImportLinkField(link, { vm.link(index, it) }, "链接 ${index + 1}", editable,
                                    onPaste = { clipboard.getText()?.text?.takeIf(String::isNotBlank)?.let { vm.link(index, it) } },
                                    onRemove = if (state.links.size > 1) ({ vm.removeLink(index) }) else null,
                                    modifier = Modifier.fillMaxWidth(), tag = "playlist_import_link_$index")
                            }
                            if (state.links.size < 10) item {
                                ImportTextAction("＋ 添加其他链接", vm::addLink, editable, Modifier.testTag("playlist_import_add_link"))
                            }
                            item { Text("最多 10 个链接，支持歌单、个人主页和文章。", fontSize = 12.sp, color = colors.onSurfaceVariantSummary) }
                        }
                        PlaylistImportMode.TEXT -> item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ImportStep(1, "粘贴歌曲文字")
                                Text("每行一首，使用“歌名 - 歌手”更容易识别。", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                                BasicTextField(state.text, vm::text, enabled = editable,
                                    textStyle = TextStyle(fontSize = 15.sp, color = colors.onBackground), cursorBrush = SolidColor(colors.primary),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp).clip(RoundedCornerShape(20.dp))
                                        .background(colors.surfaceContainer).padding(18.dp).testTag("playlist_import_text"),
                                    decorationBox = { inner -> if (state.text.isEmpty()) Text("歌名 - 歌手\n歌名 - 歌手", fontSize = 15.sp, color = colors.onSurfaceVariantSummary); inner() })
                                Text("支持多首歌曲，网易云会自动匹配。", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                            }
                        }
                        PlaylistImportMode.IMAGES -> {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ImportStep(1, "选择歌单图片")
                                    Text("选择歌单截图或歌曲列表图片，最多 9 张，每张不超过 8 MB。",
                                        fontSize = 14.sp, color = colors.onSurfaceVariantSummary)
                                    Text("点击开始导入后，会将所选图片上传到网易云识别，并将歌曲加入你选择的歌单。",
                                        fontSize = 14.sp, color = colors.onSurfaceVariantSummary)
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (state.imagesLoading) "正在读取图片…" else "已选择 ${state.images.size} / 9 张",
                                            Modifier.weight(1f).testTag("playlist_import_image_count"), fontSize = 14.sp)
                                        MusicTextAction(if (state.images.isEmpty()) "选择图片" else "重新选择",
                                            { imagePicker.launch(arrayOf("image/*")) },
                                            Modifier.testTag("playlist_import_pick_images"), enabled = editable)
                                    }
                                }
                            }
                            itemsIndexed(state.images) { index, image ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("${index + 1}. ${image.fileName}", Modifier.weight(1f), fontSize = 14.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    MusicTextAction("删除", { vm.removeImage(index) },
                                        Modifier.testTag("playlist_import_remove_image_$index"), enabled = editable)
                                }
                            }
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.weight(1f)) { ImportStep(2, "选择目标歌单") }
                                        MusicTextAction(if (state.targetsLoading) "读取中…" else "刷新", vm::loadTargets,
                                            Modifier.testTag("playlist_import_reload_targets"), enabled = editable && !state.targetsLoading)
                                    }
                                    Text("请选择一个你可编辑的网易云歌单。", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                                    state.targetsError?.let { Text(it, fontSize = 13.sp, color = colors.error) }
                                    if (state.targetsLoaded && state.targets.isEmpty()) Text("暂无可编辑的歌单，请先在网易云创建歌单，再返回刷新。",
                                        fontSize = 14.sp, modifier = Modifier.testTag("playlist_import_targets_empty"))
                                    if (state.targets.isNotEmpty()) LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp).selectableGroup()
                                        .testTag("playlist_import_targets")) {
                                        items(state.targets, key = { it.id }) { target ->
                                            val selected = state.targetId == target.id
                                            Row(Modifier.fillMaxWidth().selectable(selected, enabled = editable && !state.targetsLoading,
                                                role = Role.RadioButton, onClick = { vm.target(target.id) })
                                                .testTag("playlist_import_target_${target.id}").padding(vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Text(target.name, Modifier.weight(1f), fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                Text(if (selected) "✓" else "○", color = if (selected) colors.primary else colors.onSurfaceVariantSummary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        PlaylistImportMode.LOCAL -> {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ImportStep(1, "选择本地歌曲")
                                    Text("扫描后选择要匹配的歌曲。开始导入时仅发送所选歌曲信息，不上传音频文件。",
                                        fontSize = 14.sp, color = colors.onSurfaceVariantSummary)
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        MusicTextAction(if (state.scanning) "扫描中…" else if (state.scanned) "重新扫描" else "扫描本地音乐", {
                                            permissionError = null
                                            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
                                                vm.scan(context.applicationContext) else request.launch(permission)
                                        }, Modifier.weight(1f).testTag("playlist_import_scan"), enabled = editable && !state.scanning)
                                        if (state.localSongs.isNotEmpty()) MusicTextAction(if (state.selected.size == state.localSongs.size) "取消全选" else "全选",
                                            vm::selectAll, enabled = editable)
                                    }
                                    permissionError?.let { Text(it, fontSize = 13.sp, color = colors.error) }
                                    if (state.scanned) Text("已选择 ${state.selected.size} / ${state.localSongs.size} 首", fontSize = 13.sp,
                                        modifier = Modifier.testTag("playlist_import_local_count"))
                                    if (state.scanned && state.localSongs.isEmpty()) Text("没有找到本地音乐。", fontSize = 14.sp)
                                }
                            }
                            if (state.localSongs.isNotEmpty()) item {
                                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp).testTag("playlist_import_local_songs")) {
                                    items(state.localSongs, key = { it.localUri }) { song ->
                                        val selected = song.localUri in state.selected
                                        Row(Modifier.fillMaxWidth().selectable(selected, enabled = editable, role = Role.Checkbox, onClick = { vm.select(song) })
                                            .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Box(Modifier.size(28.dp).clip(CircleShape).background(if (selected) colors.primary else colors.surfaceContainer), contentAlignment = Alignment.Center) {
                                                Text(if (selected) "✓" else "", color = colors.onPrimary, fontSize = 16.sp)
                                            }
                                            Column(Modifier.weight(1f)) {
                                                Text(song.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(song.artists, fontSize = 12.sp, color = colors.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (state.task != null) item {
                    Card { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (state.result?.finished == true) "导入结果" else "导入处理中",
                            fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        state.message?.let { Text(it, fontSize = 14.sp, modifier = Modifier.testTag("playlist_import_status")) }
                        Text("任务已提交到网易云，离开页面不会撤回任务。", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                        if (state.result?.finished != true) MusicDialogButton(if (state.busy) "正在查询进度…" else "刷新进度", vm::refresh,
                            Modifier.testTag("playlist_import_refresh"), enabled = !state.busy)
                        state.result?.playlistIds.orEmpty().forEachIndexed { index, id ->
                            MusicDestinationRow("打开导入歌单${if (state.result?.playlistIds?.size == 1) "" else " ${index + 1}"}", { onPlaylist(id) },
                                Modifier.testTag("playlist_import_result_$id"))
                        }
                        if (state.result?.finished == true) MusicDialogButton("继续导入", vm::newImport, enabled = !state.busy)
                    } }
                }
                state.error?.let { error -> item { Text(error, fontSize = 14.sp, color = colors.error, modifier = Modifier.testTag("playlist_import_error")) } }
            }
        }

    }
    }
}

@Composable private fun ImportTextAction(label: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.primary.copy(alpha = if (enabled) 1f else .45f),
        modifier = modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick).heightIn(min = 40.dp).wrapContentHeight())
}

@Composable private fun ImportLinkField(value: String, onValue: (String) -> Unit, label: String, enabled: Boolean,
    onPaste: () -> Unit, onRemove: (() -> Unit)?, modifier: Modifier, tag: String) {
    val colors = MiuixTheme.colorScheme
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(colors.surfaceContainer)
        .border(1.dp, colors.onSurfaceVariantSummary.copy(alpha = .08f), RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(label, Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariantSummary)
            ImportTextAction("粘贴", onPaste, enabled)
            onRemove?.let { ImportTextAction("删除", it, enabled, Modifier.testTag("${tag}_remove")) }
        }
        BasicTextField(value, onValue, enabled = enabled, singleLine = true,
            modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp).testTag(tag),
            textStyle = TextStyle(color = colors.onBackground, fontSize = 15.sp), cursorBrush = SolidColor(colors.primary),
            decorationBox = { inner -> if (value.isEmpty()) Text("粘贴链接或整段分享内容", fontSize = 14.sp,
                color = colors.onSurfaceVariantSummary.copy(alpha = .65f), maxLines = 1, overflow = TextOverflow.Ellipsis); inner() })
    }
}

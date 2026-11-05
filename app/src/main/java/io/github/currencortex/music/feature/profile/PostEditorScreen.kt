package io.github.currencortex.music.feature.profile

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.data.profile.PostAttachment
import io.github.currencortex.music.data.song.NeteaseSongActionsRepository
import io.github.currencortex.music.feature.update.MarkdownText
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun PostEditorScreen(vm: PostsViewModel, state: PostsState) {
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(state.draft, TextRange(state.draft.length))) }
    var preview by rememberSaveable { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    val songFlow = remember(vm) { vm.container.playerController.state.map { it.song }.distinctUntilChanged() }
    val song by songFlow.collectAsStateWithLifecycle(initialValue = null)
    val current = song?.let { s -> NeteaseSongActionsRepository.songId(s)?.let { PostAttachment("song", it, s.name, s.artists, s.cover) } }
    val colors = MiuixTheme.colorScheme
    val appDensity = LocalDensity.current
    fun close() { if (!state.busy) { if (state.picker) vm.picker(false) else vm.editor(false) } }
    Dialog(::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
        dismissOnBackPress = !state.busy, dismissOnClickOutside = false)) {
        CompositionLocalProvider(LocalGlassBackdrop provides null, LocalMusicBottomInset provides 0.dp, LocalDensity provides appDensity) {
            val focus = LocalFocusManager.current
            MusicSubpage(if (state.picker) "添加音乐" else "发布动态", "post_editor", onBack = ::close,
                backTag = "post_editor_close", modifier = Modifier.background(colors.background).imePadding(), actions = {
                    if (state.picker) GlassActionButton("完成", { vm.picker(false) }, Modifier.testTag("post_picker_done"),
                        primary = true, fillWidth = false)
                    else GlassActionButton(if (state.busy) "发布中" else "发布", { focus.clearFocus(); vm.publish() },
                        Modifier.testTag("post_publish"), primary = true, fillWidth = false,
                        enabled = !state.busy && state.draft.isNotBlank())
                }) { topInset ->
                Column(Modifier.fillMaxSize().padding(top = topInset)) {
                    if (state.picker) {
                        PostAttachmentPicker(vm, state, current, Modifier.weight(1f))
                    } else {
                        SettingsSegmentedControl(listOf("编辑", "预览"), if (preview) 1 else 0, {
                            focus.clearFocus(); preview = it == 1
                        }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), tag = "post_editor_mode", enabled = !state.busy)
                        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("post_editor_content"),
                            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            item {
                                SettingsGroup {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (preview) "正文预览" else "写下此刻", Modifier.weight(1f),
                                            fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                        Text("${text.text.length} 字", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                                    }
                                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).heightIn(min = 176.dp)) {
                                        if (preview) {
                                            if (text.text.isBlank()) Text("还没有内容", color = colors.onSurfaceVariantSummary)
                                            else MarkdownText(text.text, Modifier.testTag("post_preview"), compact = true)
                                        } else BasicTextField(text, { text = it; vm.draft(it.text) },
                                            Modifier.fillMaxWidth().heightIn(min = 176.dp).focusRequester(requester)
                                                .semantics { contentDescription = "帖子正文" }.testTag("post_draft"), enabled = !state.busy,
                                            textStyle = TextStyle(fontSize = 16.sp, lineHeight = 25.sp, color = colors.onSurface),
                                            cursorBrush = SolidColor(colors.primary), decorationBox = { input ->
                                                Box { if (text.text.isEmpty()) Text("最近在听什么？", fontSize = 16.sp,
                                                    lineHeight = 25.sp, color = colors.onSurfaceVariantSummary); input() }
                                            })
                                    }
                                    if (!preview) {
                                        SettingsDivider()
                                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            PostMarkdownTool.entries.forEach { tool ->
                                                GlassIconButton({ text = applyPostMarkdown(text, tool); vm.draft(text.text); requester.requestFocus() },
                                                    tool.label, Modifier.testTag("post_tool_${tool.name.lowercase()}"), enabled = !state.busy, surface = false) {
                                                    Text(tool.glyph, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("分享音乐", Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                        Text("${state.attachments.size}/5", Modifier.testTag("post_attachment_count"),
                                            fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                                    }
                                    SettingsGroup {
                                        state.attachments.forEach { card ->
                                            PostAttachmentTile(card, Modifier.padding(horizontal = 12.dp).padding(top = 12.dp)) {
                                                GlassIconButton({ vm.removeAttachment(card) }, "移除${card.name}",
                                                    Modifier.testTag("post_remove_${card.type}_${card.id}"), enabled = !state.busy, surface = false) {
                                                    Icon(MusicIcons.Close, null, Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                        SettingsPreference("添加音乐", if (state.attachments.isEmpty()) "单曲、专辑、歌手或歌单" else "还可添加 ${5 - state.attachments.size} 项",
                                            Modifier.testTag("post_add_attachment"), enabled = !state.busy && state.attachments.size < 5,
                                            onClick = { focus.clearFocus(); vm.picker(true) },
                                            leading = { Icon(MusicIcons.Add, null, Modifier.size(22.dp), tint = colors.primary) },
                                            trailing = { Icon(MusicIcons.Forward, null, Modifier.size(16.dp), tint = colors.onSurfaceVariantSummary) })
                                        if (state.attachments.isEmpty() && current != null) {
                                            SettingsDivider(50.dp)
                                            SettingsPreference("附带当前歌曲", current.name, Modifier.testTag("post_attach_current"),
                                                enabled = !state.busy, onClick = { vm.attach(current) },
                                                leading = { MusicCover(current.pic, Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))) })
                                        }
                                    }
                                    SettingsHint("关闭后保留草稿")
                                }
                            }
                        }
                        state.message?.let { SettingsHint(it, Modifier.testTag("post_editor_message"), error = true) }
                    }
                }
            }
        }
    }
}

@Composable private fun PostAttachmentPicker(vm: PostsViewModel, state: PostsState, current: PostAttachment?, modifier: Modifier) {
    val focus = LocalFocusManager.current
    val types = listOf("song", "album", "artist", "playlist")
    Column(modifier.fillMaxWidth()) {
        SettingsSegmentedControl(listOf("单曲", "专辑", "歌手", "歌单"), types.indexOf(state.attachmentType),
            { vm.attachmentType(types[it]) }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), tag = "post_attachment_type")
        MusicSearchBar(state.attachmentQuery, { focus.clearFocus(); vm.searchAttachments() },
            Modifier.fillMaxWidth().padding(16.dp), onInput = vm::attachmentQuery,
            placeholder = "搜索${postAttachmentLabel(state.attachmentType)}", inputTag = "post_attachment_query",
            submitTag = "post_attachment_search", onCancel = { vm.attachmentQuery("") })
        Text("已添加 ${state.attachments.size}/5 · 点击条目添加或移除", Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        LazyColumn(Modifier.weight(1f).testTag("post_attachment_results"), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!state.attachmentSearched && current != null) item { Text("正在播放", fontSize = 13.sp) }
            if (!state.attachmentSearched && current != null) item { AttachmentChoice(current, state, vm) }
            if (!state.attachmentSearched) item { Text("输入关键词，搜索想分享的音乐内容。", fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
            items(state.attachmentResults, key = { "${it.type}:${it.id}" }) { AttachmentChoice(it, state, vm) }
            if (state.attachmentLoading) item { LoadingTextList(3, leading = true) }
            if (state.attachmentSearched && !state.attachmentLoading && state.attachmentError == null && state.attachmentResults.isEmpty()) item {
                Text("没有找到相关内容，换个关键词试试。", Modifier.padding(vertical = 20.dp), fontSize = 14.sp)
            }
            state.attachmentError?.let { error -> item {
                Text(error, fontSize = 13.sp)
                MusicTextAction("重试", vm::retryAttachments, Modifier.testTag("post_attachment_retry"))
            } }
            if (state.attachmentMore && !state.attachmentLoading && state.attachmentError == null) item {
                MusicTextAction("加载更多", { vm.searchAttachments(true) }, Modifier.testTag("post_attachment_more"))
            }
        }
        state.message?.let { Text(it, Modifier.padding(horizontal = 20.dp), fontSize = 13.sp, color = MiuixTheme.colorScheme.primary) }
    }
}

@Composable private fun AttachmentChoice(card: PostAttachment, state: PostsState, vm: PostsViewModel) {
    val selected = state.attachments.any { it.type == card.type && it.id == card.id }
    val enabled = selected || state.attachments.size < 5
    PostAttachmentTile(card, Modifier.testTag("post_pick_${card.type}_${card.id}").semantics { this.selected = selected },
        onClick = { if (selected) vm.removeAttachment(card) else vm.attach(card) }, enabled = enabled) {
        Icon(if (selected) MusicIcons.Check else MusicIcons.Add,
            if (selected) "已添加" else if (enabled) "添加" else "最多添加 5 项", Modifier.size(22.dp),
            tint = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface.copy(alpha = if (enabled) .6f else .25f))
    }
}

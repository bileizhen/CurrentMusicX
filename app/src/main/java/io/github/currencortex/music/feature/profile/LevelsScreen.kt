package io.github.currencortex.music.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.currencortex.music.data.profile.*
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun LevelsScreen(vm: LevelsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm, lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.poll() } }
    LevelsPage(state, onBack, vm::refresh, vm::startTrial, vm::answer, vm::submit, vm::closeTrial)
}

@Composable internal fun LevelsPage(state: LevelsState, onBack: () -> Unit, refresh: () -> Unit,
    startTrial: () -> Unit, answer: (Int, Int) -> Unit, submit: () -> Unit, closeTrial: () -> Unit) {
    val trial = state.trial
    val list = rememberLazyListState()
    LaunchedEffect(trial, state.signedIn) { list.scrollToItem(0) }
    BackHandler(trial != null) { closeTrial() }
    MusicSubpage(if (trial == null) "等级与成长" else "等级试炼", "levels_screen",
        onBack = { if (trial != null) closeTrial() else onBack() }, actions = {
            if (trial == null && state.signedIn) MusicTextAction(if (state.loading) "更新中" else "刷新", refresh,
                enabled = !state.loading && !state.busy)
        }) { topInset ->
        LazyColumn(Modifier.fillMaxSize().testTag("levels_list"), state = list, contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = topInset + 12.dp, bottom = LocalMusicBottomInset.current + 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!state.signedIn) item { SettingsHint("登录 CurrentMusic 后查看等级与成长进度") }
            if (state.loading && state.progress == null) item { SettingsHint("正在获取等级…") }
            state.error?.let { error -> item { SettingsGroup {
                SettingsHint(error, error = true)
                if (trial == null) MusicTextAction("重试", refresh, Modifier.padding(horizontal = 16.dp),
                    enabled = !state.loading && !state.busy)
            } } }
            if (trial != null) {
                item { SettingsGroup { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("已完成 ${state.answers.count { it >= 0 }} / ${trial.questions.size} 题", fontWeight = FontWeight.SemiBold)
                    state.rules?.trial?.let { Text("满分 ${it.maxScore} 分，${it.passScore} 分及格。提交后可查看成绩。", fontSize = 13.sp) }
                } } }
                itemsIndexed(trial.questions) { index, question -> SettingsGroup {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${index + 1}. ${question.prompt}", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        question.options.forEachIndexed { option, text ->
                            val selected = state.answers.getOrNull(index) == option
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(selected, enabled = !state.busy, role = Role.RadioButton, onClick = { answer(index, option) })
                                .testTag("trial_answer_${index}_$option").padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(if (selected) "●" else "○", color = MiuixTheme.colorScheme.primary)
                                Text(text, Modifier.weight(1f), fontSize = 14.sp)
                            }
                        }
                    }
                } }
                item { MusicTextAction(if (state.busy) "正在提交…" else "提交答案", submit,
                    Modifier.fillMaxWidth().testTag("submit_level_trial"), enabled = state.canSubmit) }
            } else {
                val progress = state.progress
                val rules = state.rules
                if (progress != null) item { SettingsGroup {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(userLevelLabel(progress.level).orEmpty(), Modifier.testTag("current_user_level"),
                            fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.primary)
                        Text("累计 ${progress.points} 积分", Modifier.testTag("current_user_points"), fontSize = 17.sp)
                        rules?.let {
                            val next = it.next(progress)
                            Text(if (progress.level == it.trial.plusLevel) "已通过等级试炼"
                                else if (next == null) "已达到常规最高等级，可参加等级试炼"
                                else "距 ${userLevelLabel(next.level)} 还需 ${(next.points - progress.points).coerceAtLeast(0)} 积分",
                                fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }
                } }
                state.result?.let { result -> item { SettingsGroup {
                    SettingsHint("试炼得分 ${result.score} · ${if (result.passed) "已通过" else "未通过，可再次挑战"}",
                        modifier = Modifier.testTag("level_trial_result"))
                } } }
                if (rules != null && progress != null) {
                    item { SettingsListHeader("今日成长") }
                    item { SettingsGroup { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("每日最多 ${rules.dailyMaximum} 分", fontWeight = FontWeight.Medium)
                        Text("按 ${if (rules.timeZone == "Asia/Shanghai") "北京时间" else rules.timeZone} 零点换日，同一账号各设备共享额度。", fontSize = 12.sp)
                        if (rules.pointSources.any { it.id == "listen" })
                            Text("有效收听满 ${rules.listenSeconds} 秒后计分，同一歌曲每天计一次。积分同步后更新。", fontSize = 12.sp)
                    } } }
                    items(rules.pointSources, key = { "points-${it.id}" }) { source -> SettingsGroup {
                        SettingsPreference(pointSourceLabel(source.id),
                            "每次 ${source.points} 分 · 今日 ${progress.today[source.id] ?: 0} / ${source.dailyLimit} 次")
                    } }
                    item { SettingsListHeader("等级规则") }
                    item { SettingsGroup { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        rules.levels.sortedBy { it.level }.forEach { Text("${userLevelLabel(it.level)} · ${it.points} 积分", fontSize = 14.sp) }
                    } } }
                    item { SettingsGroup { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${userLevelLabel(rules.trial.plusLevel)} 试炼", fontWeight = FontWeight.Medium)
                        Text("达到 ${userLevelLabel(rules.trial.level)} 后参加，共 ${rules.trial.questionCount} 题，${rules.trial.passScore} 分及格。", fontSize = 13.sp)
                        MusicTextAction(if (state.busy) "正在加载题目…" else "开始试炼", startTrial,
                            Modifier.testTag("start_level_trial"), enabled = state.canStartTrial)
                    } } }
                }
            }
        }
    }
}

private fun pointSourceLabel(id: String) = when (id) {
    "visit" -> "每日访问"
    "listen" -> "听歌"
    "post" -> "发布帖子"
    "comment" -> "评论他人的帖子"
    else -> id
}

package io.github.currencortex.music.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.AppContainer
import top.yukonga.miuix.kmp.basic.*

@Composable fun MusicHomeScreen(container: AppContainer, onSearch: () -> Unit, onSettings: () -> Unit) {
    val account by container.accountRepository.state.collectAsStateWithLifecycle()
    val queue by container.playbackQueue.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row { Text("CurrentMusic", fontSize = 30.sp, modifier = Modifier.weight(1f)); TextButton("设置", onClick = onSettings) }
        Text(account.account?.let { "欢迎，${it.nickname}" } ?: "音乐，从这里开始", fontSize = 22.sp)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("寻找下一首喜欢的歌")
                TextButton("搜索音乐", onClick = onSearch)
            }
        }
        if (queue.songs.isNotEmpty()) {
            Text("播放队列 · ${queue.songs.size} 首")
            Text(queue.current?.name.orEmpty())
        }
        account.error?.let { Text(it) }
    }
}

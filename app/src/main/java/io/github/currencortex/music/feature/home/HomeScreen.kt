package io.github.currencortex.music.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun HomeScreen() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("CurrentMusic", fontSize = 30.sp)
        Text("音乐，从这里开始")
        Card {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("欢迎使用 CurrentMusic", fontSize = 20.sp)
                Text("使用搜索寻找歌曲，登录你的 CurrentMusic 账户。")
            }
        }
    }
}

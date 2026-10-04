package io.github.currencortex.music.feature.lyrics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.*
import io.github.currencortex.music.feature.lyrics.domain.LyricsInterlude

@Composable internal fun LyricsInterludeView(interval: LyricsInterlude, position: State<Long>, modifier: Modifier) {
    Row(modifier.testTag("lyrics_interlude"), verticalAlignment = Alignment.CenterVertically) {
        BasicText(interval.label, style = TextStyle(color = Color.White.copy(alpha = .65f), fontSize = 13.sp))
        Canvas(Modifier.padding(start = 10.dp).size(48.dp, 20.dp)) {
            val end = interval.endTimeMs
            val progress = if (end != null) ((position.value - interval.startTimeMs).toDouble() / (end - interval.startTimeMs).coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
                else ((position.value - interval.startTimeMs).coerceAtLeast(0) % 3000) / 3000f
            repeat(3) { index ->
                drawCircle(Color.White.copy(alpha = if (progress * 3 > index) .8f else .25f),
                    radius = 3.dp.toPx(), center = Offset(size.width * (index + .5f) / 3, size.height / 2))
            }
        }
    }
}

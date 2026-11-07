package io.github.currencortex.music.feature.listening

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import java.time.LocalDate

@Composable internal fun ListeningSummaryPager(report: ListeningDisplay, period: ListeningPeriod,
    range: ListeningRange, selectedDay: LocalDate, today: LocalDate, onDay: (LocalDate) -> Unit,
    normal: @Composable () -> Unit) {
    val sleep = report.sleep?.takeIf { period == ListeningPeriod.MONTH && it.listenDays > 0 }
    if (sleep == null) { normal(); return }
    val s = LocalFootprintScale.current
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    LaunchedEffect(range.start) { pager.scrollToPage(0) }
    Column(Modifier.background(FootprintSurface,RoundedCornerShape((20*s).dp)),horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(pager, Modifier.fillMaxWidth().testTag("listening_summary_pager"),
            verticalAlignment = Alignment.Top, pageSpacing = (10*s).dp) { page ->
            if (page == 0) normal() else ListeningSleepCard(sleep, range, selectedDay, today, onDay)
        }
        Row(Modifier.padding(bottom=(23*s).dp), horizontalArrangement=Arrangement.spacedBy((8.667f*s).dp)) {
            repeat(2) { page -> Box(Modifier.size((7*s).dp).background(
                if(page == pager.currentPage) (if(page==0) FootprintRed else Color(0xFF1356B2)) else FootprintPale, CircleShape)
                .clickable { scope.launch { pager.animateScrollToPage(page) } }.testTag("listening_summary_page_$page")) }
        }
    }
}

@Composable private fun ListeningSleepCard(sleep: ListeningSleepDisplay, range: ListeningRange,
    selectedDay: LocalDate, today: LocalDate, onDay: (LocalDate) -> Unit) {
    val s = LocalFootprintScale.current
    FootprintCard(Modifier.testTag("listening_sleep")) {
      Box(Modifier.fillMaxWidth()) {
        AsyncImage("https://sg.music.163.com/st/listen-home/static/media/sleepBgLight.0bdc2643.png",null,
            Modifier.align(Alignment.TopEnd).size((199*s).dp,(162*s).dp),contentScale=ContentScale.FillBounds)
       Column {
        FootprintText("${chineseMonth(range.start.monthValue)}月助眠记录",Modifier.padding(start=(16*s).dp,top=(16*s).dp),
            size=34f, color=FootprintPale, weight=FontWeight.SemiBold)
        Column(Modifier.fillMaxWidth().padding(top=(18*s).dp,end=(24*s).dp),horizontalAlignment=Alignment.End) {
            Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy((5*s).dp)) {
                FootprintText("陪伴入眠",Modifier.padding(bottom=(5*s).dp),size=30f,weight=FontWeight.SemiBold)
                FootprintText(sleep.listenDays.toString(),size=51f,numeric=true,weight=FontWeight.Bold)
                FootprintText("天",Modifier.padding(bottom=(5*s).dp),size=30f,weight=FontWeight.SemiBold)
            }
            FootprintText(if(sleep.complete && sleep.scene.isNotBlank()) "${sleep.scene}是你本月常用入眠音"
                else if((sleep.days[today] ?: 0)>0) "今日已打卡" else "今日未打卡",size=14f)
        }
        Spacer(Modifier.height((22*s).dp))
        ListeningMonthCalendar(range,sleep.days,selectedDay,today,onDay,sleepMode=true)
        if((sleep.averageDurationMs ?: 0)>0 && (sleep.maximumDurationMs ?: 0)>0) {
            Row(Modifier.fillMaxWidth().padding(vertical=(24*s).dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                listOf("日均陪伴" to sleep.averageDurationMs!!,"最长陪伴" to sleep.maximumDurationMs!!).forEach { (label,ms) ->
                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy((6*s).dp)) {
                        FootprintDuration(ms,numberSize=30f,unitSize=16f)
                        FootprintText(label,size=14f,color=Color(0xFF1356B2),weight=FontWeight.SemiBold)
                    }
                }
            }
        } else Spacer(Modifier.height((24*s).dp))
       }
      }
    }
}

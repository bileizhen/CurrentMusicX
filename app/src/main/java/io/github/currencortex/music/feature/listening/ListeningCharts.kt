package io.github.currencortex.music.feature.listening

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import java.time.LocalDate

/** Matches the official strictly-greater scale thresholds, in minutes. */
internal fun footprintChartScale(maxMinutes: Double): Double = when {
    maxMinutes < 5 -> 5.0
    maxMinutes < 15 -> 20.0
    maxMinutes < 30 -> 30.0
    maxMinutes < 60 -> 60.0
    maxMinutes < 90 -> 90.0
    maxMinutes < 120 -> 120.0
    maxMinutes < 180 -> 180.0
    maxMinutes < 360 -> 360.0
    maxMinutes < 480 -> 480.0
    maxMinutes < 720 -> 720.0
    maxMinutes < 1080 -> 1080.0
    else -> 1440.0
}
private fun tick(minutes: Double): String = if (minutes >= 60 && minutes % 60 == 0.0) "${(minutes/60).toInt()}小时"
    else "${if(minutes % 1 == 0.0) minutes.toInt().toString() else minutes.toString()}分钟"

@Composable internal fun ListeningWeekChart(range: ListeningRange, days: Map<LocalDate,Long>, selectedDay: LocalDate,
    today: LocalDate, onDay: (LocalDate)->Unit, breakdowns: Map<LocalDate,ListeningDayBreakdown> = emptyMap()) {
    val s=LocalFootprintScale.current
    val upper=footprintChartScale((days.values.maxOrNull() ?: 0)/60000.0)
    val maximumDay=days.maxByOrNull { it.value }?.takeIf { it.value>0 }?.key
    var popup by remember(range.start) { mutableStateOf<LocalDate?>(if(today in range.start..range.end && days.containsKey(today)) today else days.maxByOrNull { it.value }?.key?.takeIf { days[it]!!>0 }) }
    val light=rememberListeningBarLight(popup==today && today in range.start..range.end && (days[today] ?: 0)>=3600000)
    Box(Modifier.fillMaxWidth().height((204.667f*s).dp).testTag("listening_week_chart")) {
        Column(Modifier.fillMaxWidth().height((183.333f*s).dp),verticalArrangement=Arrangement.SpaceBetween) {
            listOf(upper,upper/2,0.0).forEach { value -> Row(Modifier.fillMaxWidth().padding(start=(12*s).dp,end=(14.667f*s).dp),verticalAlignment=Alignment.CenterVertically) {
                FootprintText(tick(value),Modifier.width((37.666f*s).dp),size=9.333f,color=FootprintMuted,numeric=true,lineHeight=10f,maxLines=1)
                Box(Modifier.weight(1f).height((.667f*s).dp).background(FootprintInk.copy(alpha=.06f)))
            } }
        }
        Row(Modifier.align(Alignment.TopEnd).padding(end=(23*s).dp).width((254*s).dp).height((178.333f*s).dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Bottom) {
            range.dates.forEach { date ->
                val ms=if(date<=today) days[date] ?: 0L else 0L
                val height=if(ms<=0) 0f else ((ms/60000.0/upper*173.333).toFloat()).coerceIn(32f,183.333f)
                val animated by animateFloatAsState(height,tween(500),label="duration height")
                Box(Modifier.width((32*s).dp).fillMaxHeight().zIndex(if(popup==date) 1f else 0f).clickable(enabled=date<=today && days.containsKey(date),role=Role.Button) { popup=if(popup==date)null else date;onDay(date) }
                    .testTag("listening_bar_$date").semantics { contentDescription="${date.monthValue}月${date.dayOfMonth}日，${compactDuration(ms)}" }) {
                    if(ms>0) ListeningDurationBar(FootprintRed,ms>=3600000,if(date==today)light else null,
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height((animated*s).dp))
                    else Box(Modifier.align(Alignment.BottomCenter).size((32*s).dp))
                    if(popup==date) {
                        Box(Modifier.align(Alignment.BottomCenter).offset(y=(-animated*s).dp)
                            .width((1*s).dp).height((8*s).dp).background(FootprintInk.copy(alpha=.25f)))
                        Box(Modifier.align(Alignment.BottomCenter).offset(y=(-(animated+8)*s).dp).wrapContentSize(unbounded=true)) {
                            ChartPopup(date,ms,today,breakdowns[date],isTop=date==maximumDay)
                        }
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(end=(14.667f*s).dp).width((270.667f*s).dp).height((16*s).dp),horizontalArrangement=Arrangement.SpaceAround) {
            range.dates.forEach { date -> Box(Modifier.width((27*s).dp),contentAlignment=Alignment.Center) {
                if(date==today) Box(Modifier.size((16*s).dp).background(FootprintRed,CircleShape),contentAlignment=Alignment.Center) { FootprintText("今",size=10f,color=Color.White,weight=FontWeight.Bold) }
                else FootprintText(listOf("日","一","二","三","四","五","六")[date.dayOfWeek.value%7],size=9f,color=FootprintMuted)
            } }
        }
    }
}

@Composable private fun ChartPopup(date: LocalDate, ms: Long, today:LocalDate, breakdown: ListeningDayBreakdown?, modifier: Modifier=Modifier, sleepMode: Boolean = false, isTop: Boolean = false) {
    val ink=FootprintInk
    val s=LocalFootprintScale.current
    Column(modifier.dropShadow(RoundedCornerShape((10*s).dp),Shadow(radius=(8*s).dp,
            offset=DpOffset(0.dp,(2*s).dp),color=Color.Black.copy(alpha=.08f)))
        .background(FootprintSurface,RoundedCornerShape((10*s).dp))
        .border((.5f*s).dp,FootprintInk.copy(alpha=.1f),RoundedCornerShape((10*s).dp))
        .padding(start=(9*s).dp,end=(9*s).dp,top=((if(date==today)5f else 7f)*s).dp,bottom=(5*s).dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(((if(date==today)1.5f else 3.333f)*s).dp)) {
            if(date==today) Canvas(Modifier.size((9.833f*s).dp)) {
                drawArc(ink,180f,180f,false,topLeft=Offset(size.width*.15f,0f),size=androidx.compose.ui.geometry.Size(size.width*.7f,size.height*.85f),style=Stroke(size.width*.15f))
                drawLine(ink,Offset(size.width*.15f,size.height*.5f),Offset(size.width*.15f,size.height*.9f),size.width*.2f,StrokeCap.Round)
                drawLine(ink,Offset(size.width*.85f,size.height*.5f),Offset(size.width*.85f,size.height*.9f),size.width*.2f,StrokeCap.Round)
            } else if(isTop) coil3.compose.AsyncImage("https://sg.music.163.com/st/listen-home/static/media/top_listen_icon.4fe84421.png", "TOP",
                Modifier.size((18.303f*s).dp,(7.333f*s).dp))
            FootprintText(if(date==today)"今日" else date.toString().replace('-', '.'),size=8f,numeric=date!=today,
                weight=FontWeight.SemiBold,lineHeight=if(date==today)11.667f else 7.333f,maxLines=1)
        }
        Spacer(Modifier.height(((if(date==today).666f else 3.333f)*s).dp))
        val values = if (breakdown==null) listOf((if(sleepMode) "助眠" else "听歌") to ms) else
            listOf("听歌" to (breakdown.musicMs ?: 0), "听书" to (breakdown.audiobookMs ?: 0), "播客" to (breakdown.podcastMs ?: 0)).filter { it.second > 0 }.ifEmpty { listOf("听歌" to 0L) }
        values.forEach { (label,duration) -> Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy((3.333f*s).dp)) {
            FootprintText(label,Modifier.padding(bottom=(.667f*s).dp),size=8f,color=FootprintInk.copy(alpha=.7f))
            Row(Modifier.semantics(mergeDescendants=true) { contentDescription=compactDuration(duration) },verticalAlignment=Alignment.CenterVertically) {
                val minutes=duration.coerceAtLeast(0)/60000
                val parts=buildList {
                    if(minutes>=60) add(minutes/60 to "小时")
                    if(minutes<60 || minutes%60!=0L) add(minutes%60 to if(minutes==0L) "分钟" else "分")
                }
                parts.forEach { (value,unit) ->
                    FootprintText(value.toString(),size=12f,weight=FontWeight.Bold,numeric=true,lineHeight=13.333f,maxLines=1)
                    FootprintText(unit,Modifier.padding(horizontal=(1*s).dp),size=10f,weight=FontWeight.SemiBold,lineHeight=15f,maxLines=1)
                }
            }
        } }
    }
}

@Composable internal fun ListeningMonthCalendar(range:ListeningRange,days:Map<LocalDate,Long>,selectedDay:LocalDate,
    today:LocalDate,onDay:(LocalDate)->Unit,breakdowns: Map<LocalDate,ListeningDayBreakdown> = emptyMap(), sleepMode: Boolean = false) {
    val s=LocalFootprintScale.current
    val ink=FootprintInk
    var popup by remember(range.start) { mutableStateOf<LocalDate?>(null) }
    val offset=range.start.dayOfWeek.value%7
    val dates=List<LocalDate?>(offset){null}+range.dates
    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
        dates.chunked(7).forEach { week -> Row(Modifier.width((302*s).dp),horizontalArrangement=Arrangement.SpaceBetween) {
            (week+List<LocalDate?>(7-week.size){null}).forEach { date ->
                val ms=if(date!=null && date<=today) days[date] ?: 0L else 0L
                Box(Modifier.size((43*s).dp).zIndex(if(date!=null && popup==date)1f else 0f),contentAlignment=Alignment.Center) {
                    if(date!=null) {
                        Box(Modifier.size((36*s).dp).clip(CircleShape)
                            .background(if(ms>=3600000) Brush.verticalGradient(if(sleepMode) listOf(Color(0xFF1356B2),Color(0xFF9ABBED)) else listOf(Color(0xFFFF2222),Color(0xFFFF8080))) else Brush.verticalGradient(List(2) { if(ms>0) (if(sleepMode) FootprintSoftBlue else FootprintSoftRed) else Color.Transparent }))
                            .clickable(enabled=date<=today && days.containsKey(date),role=Role.Button) {popup=if(popup==date)null else date;onDay(date)}
                            .testTag("listening_day_$date").semantics {contentDescription="${date.monthValue}月${date.dayOfMonth}日，${compactDuration(ms)}"},contentAlignment=Alignment.Center) {
                            if(ms==0L && date<=today) Canvas(Modifier.fillMaxSize()) { drawCircle(ink.copy(alpha=.08f),radius=size.width/2-.5f,style=Stroke(1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(),2.dp.toPx())))) }
                            FootprintText(if(date==today)"今" else date.dayOfMonth.toString(),size=14f,numeric=date!=today,weight=FontWeight.SemiBold,
                                color=if(ms>=3600000)Color.White else if(ms>0) (if(sleepMode) Color(0xFF1356B2) else FootprintRed) else FootprintMuted)
                        }
                        if(popup==date) ChartPopup(date,ms,today,breakdowns[date],Modifier.offset(y=(-42*s).dp).wrapContentSize(unbounded=true),sleepMode)
                    }
                }
            }
        } }
        Row(Modifier.width((298.667f*s).dp).padding(top=(9.667f*s).dp),horizontalArrangement=Arrangement.SpaceAround) {
            listOf("日","一","二","三","四","五","六").forEach { FootprintText(it,size=9f,color=FootprintMuted) }
        }
    }
}

@Composable internal fun ListeningAchievement(fact:ListeningFact) {
    val s=LocalFootprintScale.current
    val image=mapOf(
        "云村超级乐迷" to "super_fan_pic.fc4c65dd.png", "云村发烧友" to "heavy_enthusiast_pic.9b987c66.png",
        "听歌全勤奖" to "perfect_attendance_pic.20bac56c.png", "云村失踪人口" to "missing_person_pic.1fadb1e8.png",
        "自律达人" to "self_discipline_pic.bffd0768.png", "云村夜猫子" to "night_owl_pic.b2ebde54.png",
        "红心收藏家" to "heart_collector_pic.80b6f112.png", "音乐漫游者" to "music_wander_pic.ff8943da.png",
        "黑胶大师" to "vinyl_master_pic.5e50336d.png")[fact.value]
    Column(Modifier.fillMaxWidth().height((167.667f*s).dp).padding(top=(8.667f*s).dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy((8.667f*s).dp)) {
        if(image!=null) coil3.compose.AsyncImage("https://sg.music.163.com/st/listen-home/static/media/$image",fact.value,Modifier.size((167*s).dp,(136.333f*s).dp))
        else FootprintText(fact.value,size=23f,weight=FontWeight.Bold)
        if(fact.detail.isNotBlank()) FootprintText(fact.detail,size=15f,lineHeight=15f,maxLines=1,color=FootprintInk.copy(alpha=.8f),weight=FontWeight.SemiBold)
    }
}

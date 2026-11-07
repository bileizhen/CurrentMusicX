package io.github.currencortex.music.feature.listening

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import io.github.currencortex.music.R
import io.github.currencortex.music.ui.component.MusicOverlayDialog
import io.github.currencortex.music.ui.component.MusicDialogButton
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.nativeui.SettingsFloatingHeader
import io.github.currencortex.music.ui.component.liquid.rememberCombinedBackdrop
import io.github.currencortex.music.ui.component.nativeui.GlassMenu
import io.github.currencortex.music.ui.component.nativeui.GlassMenuTags
import io.github.currencortex.music.ui.component.nativeui.GlassPopupText
import io.github.currencortex.music.ui.component.nativeui.GlassIconButton
import io.github.currencortex.music.ui.component.nativeui.SettingsSegmentedControl
import io.github.currencortex.music.ui.component.nativeui.LocalGlassBackdrop
import io.github.currencortex.music.ui.component.nativeui.rememberGlassBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import io.github.currencortex.music.ui.component.LocalMusicBottomInset
import java.time.LocalDate

internal val FootprintDark: Boolean @Composable get() = io.github.currencortex.music.ui.theme.isInDarkTheme()
internal val FootprintInk: Color @Composable get() = if (FootprintDark) MiuixTheme.colorScheme.onSurface else Color(0xFF283248)
internal val FootprintMuted: Color @Composable get() = if (FootprintDark) MiuixTheme.colorScheme.onSurfaceVariantSummary else Color(0xFF9296A2)
internal val FootprintRed = Color(0xFFFF3A3A)
internal val FootprintPale: Color @Composable get() = if (FootprintDark) FootprintInk.copy(alpha = .30f) else Color(0xFFE4E5E9)
internal val FootprintSurface: Color @Composable get() = if (FootprintDark) MiuixTheme.colorScheme.surfaceContainer else Color.White
internal val FootprintInset: Color @Composable get() = if (FootprintDark) MiuixTheme.colorScheme.surfaceContainerHigh else Color(0xFFF0F0F2)
internal val FootprintSoftRed: Color @Composable get() = if (FootprintDark) Color(0xFF633039) else Color(0xFFFFDDDD)
internal val FootprintSoftBlue: Color @Composable get() = if (FootprintDark) Color(0xFF253E61) else Color(0xFFDCE8FA)
internal val LocalFootprintScale = staticCompositionLocalOf { 1f }
internal val FootprintBackground: Color @Composable get() = if (FootprintDark) MiuixTheme.colorScheme.background else Color(0xFFF8F7F9)
private val Outfit = FontFamily(Font(R.font.listening_outfit, FontWeight.Normal), Font(R.font.listening_outfit_semibold, FontWeight.SemiBold), Font(R.font.listening_outfit_bold, FontWeight.Bold), Font(R.font.listening_outfit_black, FontWeight.Black))
private val MiSans = FontFamily(Font(R.font.listening_misans_semibold, FontWeight.SemiBold), Font(R.font.listening_misans_bold, FontWeight.Bold))
private val MiSansSmall = FontFamily(Font(R.font.listening_misans_semibold, FontWeight.SemiBold))

@Composable internal fun FootprintText(text: String, modifier: Modifier = Modifier, size: Float = 14f,
    color: Color = FootprintInk, weight: FontWeight = FontWeight.Normal, numeric: Boolean = false,
    maxLines: Int = Int.MAX_VALUE, brush: Brush? = null, fontFamily: FontFamily? = null, lineHeight: Float? = null) {
    val s = LocalFootprintScale.current
    // CSS uses tight line boxes; Android otherwise retains font ascent/descent padding
    // even when lineHeight is smaller, shifting every following chart row.
    val textModifier = if ((size >= 30f && (maxLines == 1 || maxLines == Int.MAX_VALUE) || lineHeight != null && maxLines == 1) && !text.contains('\n')) modifier.height(((lineHeight ?: size)*s).dp).wrapContentHeight(Alignment.CenterVertically,unbounded=true) else modifier
    BasicText(text, textModifier, style = TextStyle(color = color, fontSize = (size * s).sp,
        lineHeight = ((lineHeight ?: (size * if(size >= 30) 1f else 1.18f)) * s).sp, fontWeight = weight,
        fontFamily = fontFamily ?: if (numeric) Outfit else if(weight >= FontWeight.SemiBold) (if(size <= 16f) MiSansSmall else MiSans) else FontFamily.SansSerif,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None, LineHeightStyle.Mode.Tight),
        platformStyle = PlatformTextStyle(includeFontPadding = false)).let { if(brush==null) it else it.copy(brush=brush) }, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable internal fun FootprintCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(FootprintSurface, RoundedCornerShape((20 * LocalFootprintScale.current).dp)), content = content)
}

@Composable internal fun FootprintCover(cover: String, title: String, modifier: Modifier) {
    Box(modifier.background(FootprintPale), contentAlignment = Alignment.Center) {
        if (cover.isNotBlank()) AsyncImage(cover, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else FootprintText("♪", size = 20f, color = FootprintMuted)
    }
}

@Composable internal fun ListeningPage(source: ListeningSource, period: ListeningPeriod, range: ListeningRange,
    today: LocalDate, selectedDay: LocalDate, report: ListeningDisplay?, day: ListeningDisplay?, loading: Boolean,
    error: String?, signedIn: Boolean, dayLoading: Boolean, dayError: String?,
    onPeriod: (ListeningPeriod) -> Unit, onMove: (Long) -> Unit,
    onDay: (LocalDate) -> Unit, onRetry: () -> Unit, onLogin: () -> Unit, onBack: () -> Unit,
    onMonthlyAction: (ListeningMonthlyAction) -> Unit = {}, onOpenCloudSongs: (Boolean) -> Unit = {}) {
    val ink = FootprintInk
    var rules by remember { mutableStateOf(false) }
    var songs by remember { mutableStateOf(false) }
    var durationInfo by remember(source,period,range.start) { mutableStateOf(false) }
    var durationInfoAnchor by remember { mutableStateOf<Rect?>(null) }
    val list = rememberLazyListState()
    val controlBackdrop = rememberGlassBackdrop()
    val bodyBackdrop = rememberGlassBackdrop()
    val headerBackdrop = if(controlBackdrop != null && bodyBackdrop != null)
        rememberCombinedBackdrop(controlBackdrop, bodyBackdrop) else controlBackdrop

    val activity = LocalActivity.current
    val dark = FootprintDark
    DisposableEffect(activity, dark) {
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = !dark
        onDispose { previous?.let { controller?.isAppearanceLightStatusBars = it } }
    }
    LaunchedEffect(source, period, range.start) { list.scrollToItem(0) }
    Box(Modifier.fillMaxSize().testTag("listening_screen")) {
        Box(Modifier.matchParentSize()
            .then(if (controlBackdrop != null) Modifier.layerBackdrop(controlBackdrop) else Modifier)
            .background(FootprintBackground))
        GlassMenu(durationInfo, { durationInfo=it }, emptyList(), durationInfoAnchor, headerBackdrop,
            tags=GlassMenuTags("listening_duration_info_popup","listening_duration_info_dismiss"),
            menuWidth=280.dp, bottomInset=LocalMusicBottomInset.current,
            description=GlassPopupText("收听时长", if(source==ListeningSource.NETEASE)
                "记录你收听歌曲、播客和有声书的总时长，包含多端播放数据，每日记录上限为24小时，颜色越深代表收听时长越长。"
                else "记录你在本机实际播放音乐的时长，暂停、缓冲和拖动进度不计入。颜色越深代表收听时长越长。"),
            content={
        BoxWithConstraints(Modifier.widthIn(max = 600.dp).fillMaxSize().align(Alignment.TopCenter).navigationBarsPadding()) {
            val s = maxWidth.value / 375f
            CompositionLocalProvider(LocalFootprintScale provides s, LocalGlassBackdrop provides controlBackdrop) {
                val density = LocalDensity.current
                val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val collapseTarget by remember { derivedStateOf { if (list.firstVisibleItemIndex > 0) 1f else
                    (list.firstVisibleItemScrollOffset / with(density) { (6.667f * s).dp.toPx() }).coerceIn(0f, 1f) } }
                val collapsed by animateFloatAsState(collapseTarget,
                    tween(250, easing=FastOutSlowInEasing), label="listening header collapse")
                LazyColumn(Modifier.fillMaxSize().testTag("listening_list")
                    .then(if(bodyBackdrop != null) Modifier.layerBackdrop(bodyBackdrop) else Modifier), state = list,
                    contentPadding = PaddingValues(start = (20*s).dp, end = (20*s).dp, top = statusInset + (70*s).dp + 44.dp + (if (period == ListeningPeriod.YEAR) 10 else 20).dp, bottom = (36*s).dp + LocalMusicBottomInset.current),
                    verticalArrangement = Arrangement.spacedBy((20*s).dp)) {
                    if (period != ListeningPeriod.YEAR) item("range") {
                        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                            PeriodArrow(false, true, { onMove(-1) }, Modifier.testTag("listening_previous"))
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                FootprintText("${range.start.monthValue}.${range.start.dayOfMonth} - ${range.end.monthValue}.${range.end.dayOfMonth}", size=16f, numeric=true,lineHeight=16f,maxLines=1)
                            }
                            PeriodArrow(true, range.end < today, { onMove(1) }, Modifier.testTag("listening_next"))
                        }
                    }
                    if (source == ListeningSource.NETEASE && !signedIn) item("login") { FootprintCard {
                        Column(Modifier.padding((24*s).dp), verticalArrangement = Arrangement.spacedBy((16*s).dp)) {
                            FootprintText("绑定网易云账号，查看你的听歌足迹", size=16f)
                            FootprintAction("绑定网易云", onLogin, Modifier.testTag("listening_login"))
                        }
                    } } else {
                        if (error != null) item("error") { FootprintCard { Column(Modifier.padding((20*s).dp)) {
                            FootprintText(error, color=FootprintMuted)
                            FootprintAction("重试", onRetry, Modifier.testTag("listening_retry"))
                        } } }
                        if (loading && report == null) item("loading") { FootprintCard { FootprintText("正在收集听歌足迹…", Modifier.padding((28*s).dp), color=FootprintMuted) } }
                        if (report != null) {
                            if (period == ListeningPeriod.YEAR) item("years") { ListeningYearCards(report, range.start.year, source == ListeningSource.NETEASE) }
                            else {
                                val badge = report.facts.firstOrNull { it.title == "听歌印记" }
                                if (badge != null && (source == ListeningSource.LOCAL || range.end < today)) item("badge") {
                                    Box(Modifier.layout { measurable,constraints ->
                                        val child=measurable.measure(constraints)
                                        val overlap=(20*s).dp.roundToPx()
                                        layout(child.width,(child.height-overlap).coerceAtLeast(0)) { child.placeRelative(0,-overlap) }
                                    }) { ListeningAchievement(badge) }
                                }
                                item("summary") { ListeningSummaryPager(report,period,range,selectedDay,today,onDay) { FootprintCard {
                                    Box(Modifier.fillMaxWidth().padding(start=(16*s).dp, top=(16*s).dp, end=(16*s).dp)) {
                                        FootprintText(if (period == ListeningPeriod.WEEK) "本周收听时长"
                                            else "${chineseMonth(range.start.monthValue)}月收听时长", size=34f, color=FootprintPale, weight=FontWeight.SemiBold)
                                        ListeningDurationInfo({ durationInfo=true }, { durationInfoAnchor=it }, Modifier.align(Alignment.TopEnd))
                                    }
                                    Column(Modifier.fillMaxWidth().padding(top=(18*s).dp,end=(24*s).dp), horizontalAlignment=Alignment.End,
                                        verticalArrangement=Arrangement.spacedBy((4*s).dp)) {
                                        if (report.totalMs == null) FootprintText("报告尚未生成", Modifier.testTag("listening_total"), size=25f, weight=FontWeight.Bold)
                                        else FootprintDuration(report.totalMs, Modifier.testTag("listening_total"))
                                        report.listenedDays?.let { FootprintText("已听 $it/${range.dates.size} 天",size=14f) }
                                    }
                                    if (period == ListeningPeriod.WEEK) {
                                        Spacer(Modifier.height((50*s).dp))
                                        ListeningWeekChart(range, report.days, selectedDay, today, onDay, report.dayBreakdowns)
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible=source==ListeningSource.LOCAL || (selectedDay==today && today in range.start..range.end),
                                            enter=androidx.compose.animation.expandVertically(tween(160,easing=LinearEasing)),
                                            exit=androidx.compose.animation.shrinkVertically(tween(160,easing=LinearEasing))) {
                                            val shownDay = if(source==ListeningSource.NETEASE) cloudListeningDay(report,today,today) else day
                                        Column(Modifier.padding(start=(20*s).dp,end=(20*s).dp,top=(24*s).dp,bottom=0.dp), verticalArrangement=Arrangement.spacedBy((10*s).dp)) {
                                            if (source==ListeningSource.NETEASE || selectedDay==today) Image(painterResource(R.drawable.listening_today_title),"今日收听",Modifier.size((65*s).dp,(15*s).dp),colorFilter=androidx.compose.ui.graphics.ColorFilter.tint(FootprintInk))
                                            else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy((3*s).dp)) {
                                                Canvas(Modifier.size((12*s).dp)) {
                                                    drawArc(ink,180f,180f,false,topLeft=Offset(size.width*.16f,size.height*.1f),size=androidx.compose.ui.geometry.Size(size.width*.68f,size.height*.8f),style=androidx.compose.ui.graphics.drawscope.Stroke(size.width*.12f))
                                                    drawLine(ink,Offset(size.width*.15f,size.height*.5f),Offset(size.width*.15f,size.height*.9f),size.width*.2f,androidx.compose.ui.graphics.StrokeCap.Round)
                                                    drawLine(ink,Offset(size.width*.85f,size.height*.5f),Offset(size.width*.85f,size.height*.9f),size.width*.2f,androidx.compose.ui.graphics.StrokeCap.Round)
                                                }
                                                FootprintText(if (selectedDay == today) "今日收听" else "${selectedDay.monthValue}.${selectedDay.dayOfMonth} 收听",size=13f,weight=FontWeight.SemiBold)
                                            }
                                            if (dayError != null) FootprintText(dayError,size=12f,color=FootprintMuted)
                                            else if (dayLoading && shownDay == null) FootprintText("正在读取…",size=12f,color=FootprintMuted)
                                            else if (shownDay?.songCount != null) Row(Modifier.clickable(enabled=source==ListeningSource.NETEASE && shownDay.songCount>0) { onOpenCloudSongs(true) },verticalAlignment=Alignment.Bottom) {
                                                FootprintText("${shownDay.songCount}",size=30f,numeric=true,weight=FontWeight.Bold)
                                                FootprintText("首歌曲",Modifier.padding(bottom=(3*s).dp),size=16f)
                                            } else FootprintText("暂无歌曲明细",size=16f,color=FootprintMuted)
                                            if (shownDay?.tracks?.isNotEmpty() == true) Row(Modifier.fillMaxWidth().clickable(enabled=source==ListeningSource.NETEASE) { onOpenCloudSongs(true) },horizontalArrangement=Arrangement.spacedBy((5*s).dp)) {
                                                shownDay.tracks.take(5).forEach { FootprintCover(it.cover,it.title,Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape((5*s).dp))) }
                                                Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape((5*s).dp)).background(FootprintInset).clickable { if(source==ListeningSource.NETEASE) onOpenCloudSongs(true) else songs=true },contentAlignment=Alignment.Center) { FootprintText("•••",color=FootprintMuted,size=15f) }
                                            }
                                        }
                                        }
                                        if(source==ListeningSource.NETEASE && report.summarySections.size>=2)
                                            ListeningSummarySections(report.summarySections,Modifier.padding(top=(24*s).dp,bottom=(24*s).dp))
                                        else Spacer(Modifier.height((24*s).dp))
                                    } else {
                                        Spacer(Modifier.height((22*s).dp))
                                        ListeningMonthCalendar(range,report.days,selectedDay,today,onDay,report.dayBreakdowns)
                                        val facts = report.facts.filterNot { it.title.startsWith("听歌年代") || it.title.startsWith("听歌语言") || it.title == "听歌印记" }
                                        if(source == ListeningSource.NETEASE) Box(Modifier.padding(top=((if(report.summarySections.size>=2) 24 else 0)*s).dp,bottom=(24*s).dp)) { ListeningSummarySections(report.summarySections) }
                                        else if (facts.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top=(24*s).dp,bottom=(24*s).dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                                            facts.take(2).forEach { fact -> Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy((5*s).dp)) {
                                                FootprintText(fact.value,size=25f,numeric=true,weight=FontWeight.Bold)
                                                FootprintText(fact.title,size=13f,color=FootprintRed,weight=FontWeight.SemiBold)
                                            } }
                                        } else Spacer(Modifier.height((24*s).dp))
                                    }
                                } } }
                                if(period == ListeningPeriod.MONTH && report.monthly != null) item("details") { ListeningMonthlyCards(report,range,onMonthlyAction,onRank={onOpenCloudSongs(false)}) }
                                else if(period == ListeningPeriod.WEEK && report.weekly != null) item("details") { ListeningWeeklyCards(report,range,onMonthlyAction,onRank={onOpenCloudSongs(false)}) }
                                else if (report.coverUrls.isNotEmpty() || report.tracks.any { it.cover.isNotBlank() } || report.facts.any { it.title.startsWith("听歌年代") || it.title.startsWith("听歌语言") }) item("details") { ListeningDetailCards(report,period,range,onMonthlyAction) }
                                if(report.monthly == null && report.weekly == null) item("note") { Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy((8*s).dp)) {
                                    FootprintText(if (today in range.start..range.end) "完整数据收集中..." else "这一段音乐时光",size=16f,weight=FontWeight.Bold,fontFamily=FontFamily.SansSerif)
                                    FootprintText(if (source == ListeningSource.NETEASE && today in range.start..range.end)
                                        (if(period==ListeningPeriod.WEEK) "周日下午3点更新" else "每月1日更新") else "已记录的收听足迹",size=12f,color=FootprintMuted)
                                    if(source==ListeningSource.LOCAL) FootprintText("本机记录 · ${report.note}",Modifier.padding(top=(20*s).dp),size=11f,color=FootprintMuted)
                                } }
                            }
                        }
                    }
                }
                // The official segmented control moves into the fixed title as the page scrolls.
                CompositionLocalProvider(LocalGlassBackdrop provides headerBackdrop) {
                SettingsFloatingHeader(background=FootprintBackground, toolbarHeight=(54*s).dp) {
                Box(Modifier.fillMaxWidth().statusBarsPadding().height((54*s).dp)) {
                    GlassIconButton(onBack, "返回", Modifier.align(Alignment.CenterStart)
                        .padding(start=12.dp).testTag("navigate_back")) {
                        Icon(MusicIcons.Back, null, tint=MiuixTheme.colorScheme.onBackground)
                    }
                    Column(Modifier.align(Alignment.Center).alpha(1f-collapsed).testTag("listening_title"),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy((4*s).dp)) {
                        FootprintText(if(source==ListeningSource.NETEASE) "云村听歌足迹" else "本机听歌足迹",size=17f,weight=FontWeight.Bold)
                        FootprintText(report?.takeIf { source==ListeningSource.LOCAL || signedIn }?.cumulativeMs?.let { "总时长${compactDuration(it)}" } ?: source.label,size=11f,color=FootprintMuted)
                    }
                    GlassIconButton({ rules=true }, "听歌足迹规则", Modifier.align(Alignment.CenterEnd)
                        .padding(end=12.dp).testTag("listening_rules")) {
                        Icon(MusicIcons.File, null, tint=MiuixTheme.colorScheme.onBackground)
                    }
                }
                }
                FootprintTabs(period,onPeriod,Modifier.align(Alignment.TopCenter).padding(top=statusInset + (70*s).dp*(1f-collapsed) + (((54*s).dp-44.dp)/2)*collapsed).width(((335f-175f*collapsed)*s).dp))
                }
                MusicOverlayDialog(show=rules, title="听歌足迹规则", onDismissRequest={rules=false},
                    summary=if(source==ListeningSource.LOCAL)
                        "统计本机实际音乐播放时间，暂停、缓冲、拖动进度与 MV 不计入。旧版本保留的每日时长会迁移，歌曲与时段明细从升级后记录；本机足迹不随账号切换。"
                    else "按北京时间、周日为一周起点统计。云端时长包含音乐、播客和有声书。完整周报通常于周日下午3点更新，月报于每月1日更新，年度数据随年度报告发布。历史可用范围与账号权限以网易云为准。") {
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        if(source==ListeningSource.NETEASE) MusicDialogButton("查看网易云完整规则", {
                            rules=false
                            onMonthlyAction(ListeningMonthlyAction.Url("https://y.music.163.com/g/yida/2aa33ff7660a4fb9967250ecafa34535"))
                        })
                        MusicDialogButton("知道了", { rules=false }, Modifier.testTag("listening_rules_dismiss"))
                    }
                }
                if(songs) FootprintDialog("收听歌曲",{songs=false}) { day?.tracks.orEmpty().forEach { FootprintText(it.title.ifBlank{"歌曲"},Modifier.padding(vertical=6.dp),size=14f) } }

            }
        }
        }, toolbar={})
    }
}

@Composable private fun FootprintTabs(period:ListeningPeriod, select:(ListeningPeriod)->Unit,modifier:Modifier) {
    SettingsSegmentedControl(ListeningPeriod.entries.map { it.label }, period.ordinal,
        { select(ListeningPeriod.entries[it]) }, modifier,
        tag="listening_period", role=Role.Tab)
}

@Composable private fun ListeningDurationInfo(open: () -> Unit, onBounds: (Rect) -> Unit, modifier: Modifier = Modifier) {
    GlassIconButton(open, "收听时长说明", modifier.testTag("listening_duration_info"), size=32.dp,
        opensMenu=true, onBoundsChanged=onBounds) {
        Icon(MusicIcons.Info, null, Modifier.size(20.dp), tint=MiuixTheme.colorScheme.onBackground)
    }
}

@Composable private fun PeriodArrow(next:Boolean,enabled:Boolean,click:()->Unit,modifier:Modifier) {
    Box(Modifier.width((49.667f*LocalFootprintScale.current).dp).height(44.dp), contentAlignment=Alignment.Center) {
        GlassIconButton(click, if(next) "下一周期" else "上一周期", modifier=modifier, enabled=enabled) {
            Icon(MusicIcons.Forward, null, Modifier.size(20.dp).rotate(if(next) 0f else 180f),
                tint=MiuixTheme.colorScheme.onBackground.copy(alpha=if(enabled) 1f else .38f))
        }
    }
}

@Composable internal fun FootprintDuration(ms:Long,modifier:Modifier=Modifier,numberSize:Float=51f,unitSize:Float=30f) {
    val minutes=ms.coerceAtLeast(0)/60000
    Row(modifier.semantics(mergeDescendants=true){contentDescription=compactDuration(ms)},verticalAlignment=Alignment.Bottom) {
        if(minutes>=60){ FootprintText("${minutes/60}",size=numberSize,weight=FontWeight.Bold,numeric=true);FootprintText("小时",Modifier.padding(start=((unitSize/10)*LocalFootprintScale.current).dp, end=((unitSize/10)*LocalFootprintScale.current).dp, bottom=((unitSize/6)*LocalFootprintScale.current).dp),size=unitSize,weight=FontWeight.SemiBold) }
        if (minutes < 60 || minutes % 60 != 0L) {
            FootprintText("${minutes%60}",size=numberSize,weight=FontWeight.Bold,numeric=true)
            FootprintText(if(minutes==0L)"分钟" else "分",Modifier.padding(start=((unitSize/10)*LocalFootprintScale.current).dp, end=((unitSize/10)*LocalFootprintScale.current).dp, bottom=((unitSize/6)*LocalFootprintScale.current).dp),size=unitSize,weight=FontWeight.SemiBold)
        }
    }
}
internal fun compactDuration(ms:Long):String { val m=ms.coerceAtLeast(0)/60000;return if(m>=60)"${m/60}小时${m%60}分" else "${m}分" }
internal fun chineseMonth(month:Int)=listOf("一","二","三","四","五","六","七","八","九","十","十一","十二")[month-1]

@Composable private fun FootprintAction(label:String,click:()->Unit,modifier:Modifier=Modifier) {
    Box(modifier.padding(vertical=8.dp).clip(CircleShape).background(FootprintRed).clickable(onClick=click).padding(horizontal=20.dp,vertical=10.dp),contentAlignment=Alignment.Center){FootprintText(label,color=Color.White,weight=FontWeight.SemiBold)}
}
@Composable internal fun FootprintDialog(title:String,dismiss:()->Unit,content:@Composable ColumnScope.()->Unit) {
    Dialog(dismiss) {
        BoxWithConstraints {
            FootprintCard(Modifier.heightIn(max = maxHeight * .85f)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FootprintText(title, size = 20f, weight = FontWeight.Bold)
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                        .testTag("listening_dialog_content"), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
                    FootprintAction("知道了", dismiss, Modifier.testTag("listening_dialog_dismiss"))
                }
            }
        }
    }
}

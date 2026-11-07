package io.github.currencortex.music.feature.listening

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.nativeui.GlassIconButton
import io.github.currencortex.music.ui.component.nativeui.LocalGlassBackdrop
import io.github.currencortex.music.ui.component.nativeui.rememberGlassBackdrop
import io.github.currencortex.music.ui.component.nativeui.SettingsFloatingHeader
import io.github.currencortex.music.ui.component.liquid.rememberCombinedBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberPlatformOverscrollFactory
import androidx.compose.foundation.withoutVisualEffect
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.core.network.appResult
import io.github.currencortex.music.data.netease.listening.*
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val WallListBackground: Color @Composable get() = if (FootprintDark) FootprintBackground else Color(0xFFF2F3F7)

/** Keep the complete ranking response: its count and wall are independent of its Top 20. */
@Composable internal fun ListeningCloudSongs(repository: NeteaseListeningRepository, period: ListeningPeriod,
    date: LocalDate, endTimeMs: Long?, today: Boolean, dismiss: () -> Unit,
    play: (ListeningMonthlyAction.Song) -> Unit,
    playRank: (List<NeteaseListeningSong>,Int) -> Unit, currentSongId: Long?, playing: Boolean) {
    var retry by remember { mutableIntStateOf(0) }
    var songs by remember { mutableStateOf<List<NeteaseListeningSong>?>(null) }
    var rank by remember { mutableStateOf<NeteaseListeningRank?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(repository,period,date,endTimeMs,today,retry,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        loading=true
        error=null
        when(val result=appResult {
            val session=repository.captureSession()
            if(today) repository.todaySongs(session) to null else {
                val response=repository.rank(session,
                    if(period==ListeningPeriod.MONTH) NeteaseListeningPeriod.MONTH else NeteaseListeningPeriod.WEEK,
                    date,requireNotNull(endTimeMs) { "报告尚未生成" })
                response?.songs to response
            }
        }) {
            is AppResult.Success -> { songs=result.value.first; rank=result.value.second }
            is AppResult.Failure -> error=result.message
        }
        loading=false
        awaitCancellation()
        }
    }
    Dialog(dismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(WallListBackground)) {
            CompositionLocalProvider(LocalFootprintScale provides (maxWidth.value/375f)) {
                if(today) ListeningTodaySongs(songs.orEmpty(),loading,error,{retry++},dismiss,play,currentSongId,playing)
                else ListeningWallDetail(rank,period,date,loading,error,{retry++},dismiss,playRank,currentSongId,playing)
            }
        }
    }
}

/** Full-bleed wall, export controls and the ranked list share one native scroll surface. */
@Composable internal fun ListeningWallDetail(rank: NeteaseListeningRank?, period: ListeningPeriod,
    date: LocalDate, loading: Boolean = false, error: String? = null, retry: () -> Unit = {},
    dismiss: () -> Unit = {}, play: (List<NeteaseListeningSong>,Int) -> Unit = {_,_->}, currentSongId: Long? = null, playing:Boolean=false) {
    val s=LocalFootprintScale.current
    val sheetBackground=WallListBackground
    val state=rememberLazyListState()
    val wallBackdrop=rememberGlassBackdrop()
    val listBackdrop=rememberGlassBackdrop()
    val topBackdrop=if(wallBackdrop!=null && listBackdrop!=null)
        rememberCombinedBackdrop(wallBackdrop,listBackdrop) else wallBackdrop
    // LeiTheme installs Miuix's translating overscroll factory. This full-bleed
    // page needs Android's WebView-like stretch, which keeps its top edge covered.
    val platformOverscroll=rememberPlatformOverscrollFactory()
    val overscroll=remember(platformOverscroll) { platformOverscroll.createOverscrollEffect() }
    val scrollEvents=remember(overscroll) { overscroll.withoutVisualEffect() }
    val title=if(period==ListeningPeriod.MONTH) monthName(date.monthValue) else "本周"
    val covers=rank?.coverUrls.orEmpty().filter(String::isNotBlank).ifEmpty {
        rank?.songs.orEmpty().map { it.coverUrl }.filter(String::isNotBlank)
    }
    val songs=rank?.songs.orEmpty()
    val range=listeningRange(period,date)
    val start=rank?.startTimeMs?.takeIf { it>0 }?.let { Instant.ofEpochMilli(it).atZone(neteaseListeningZone).toLocalDate() } ?: range.start
    val end=rank?.endTimeMs?.takeIf { it>0 }?.let { Instant.ofEpochMilli(it).atZone(neteaseListeningZone).toLocalDate() } ?: range.end
    val timeline="${start.monthValue}.${start.dayOfMonth}-${end.monthValue}.${end.dayOfMonth}"
    val exportInfo=LocalListeningWallExportInfo.current.copy(timeline=timeline,songTitles=songs.map { it.name })
    val headerSolid by remember { derivedStateOf {
        state.firstVisibleItemIndex>0 || state.firstVisibleItemScrollOffset>0
    } }
    ListeningSongSystemBars(headerSolid && !FootprintDark)
    Box(Modifier.fillMaxSize().testTag("listening_cloud_songs")) {
        // WebView stretches the entire document at its edge, including its absolutely
        // positioned cover wall and fixed navigation. Render the effect once around
        // the document; only the list supplies scroll/fling events. Normal scrolling
        // moves the foreground, while the wall retains its independent 60s motion.
        Box(Modifier.fillMaxSize().overscroll(overscroll)) {
        Box(Modifier.fillMaxSize().then(if(wallBackdrop!=null) Modifier.layerBackdrop(wallBackdrop) else Modifier).background(Color.Black)) {
            Box(Modifier.fillMaxSize()) {
            if(covers.isNotEmpty()) ListeningMovingWall(covers.take(20),copies=3)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                Color(0x05121212),Color(0x54121212),Color(0xD9070707),Color(0xF2121212)))))
            Box(Modifier.fillMaxWidth().offset(y=(-79.667f*s).dp).height((607.667f*s).dp)
                .background(Brush.verticalGradient(0f to Color(0xFF171717),.223f to Color(0x66141414),.446f to Color(0x05121212),1f to Color(0x05121212))))
            }
            // A short/empty rank still owns the entire area below its rounded sheet.
            Canvas(Modifier.fillMaxSize()) {
                val heading=state.layoutInfo.visibleItemsInfo.firstOrNull { it.key=="rank_heading" }
                val top=if(state.firstVisibleItemIndex>1) 0f else heading?.let { it.offset+(10*s).dp.toPx() }
                if(top!=null && top<size.height) drawRect(sheetBackground,Offset(0f,top.coerceAtLeast(0f)),
                    androidx.compose.ui.geometry.Size(size.width,size.height-top.coerceAtLeast(0f)))
            }
        }
        CompositionLocalProvider(LocalGlassBackdrop provides wallBackdrop) {
        LazyColumn(state=state,overscrollEffect=scrollEvents,modifier=Modifier.fillMaxSize().testTag("listening_wall_scroll")
            .then(if(listBackdrop!=null) Modifier.layerBackdrop(listBackdrop) else Modifier)) {
            item(key="hero") {
                Column(Modifier.fillMaxWidth().padding(top=(366.667f*s).dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    FootprintText("${title}收听",size=16f,lineHeight=16f,maxLines=1,color=Color.White,weight=FontWeight.SemiBold)
                    Row(Modifier.padding(top=(13.333f*s).dp),verticalAlignment=Alignment.Bottom) {
                        FootprintText(rank?.songCount?.toString() ?: "—",Modifier.testTag("listening_wall_total"),
                            size=50f,lineHeight=40f,maxLines=1,numeric=true,weight=FontWeight.Bold,color=Color.White)
                        FootprintText("首",Modifier.padding(start=(2.667f*s).dp),size=33.333f,lineHeight=33.333f,
                            maxLines=1,color=Color.White,weight=FontWeight.SemiBold)
                    }
                    ListeningWallExportButtons(covers,title,rank?.songCount?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
                        modifier=Modifier.padding(top=(28.333f*s).dp),overlay=true,info=exportInfo)
                    Spacer(Modifier.height((25*s).dp))
                }
            }
            item(key="rank_heading") {
                Column(Modifier.fillMaxWidth().background(WallListBackground,RoundedCornerShape(topStart=(10*s).dp,topEnd=(10*s).dp))
                    .padding(top=(20*s).dp,bottom=(14.667f*s).dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=(14*s).dp),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.SpaceBetween) {
                        FootprintText(if(rank==null) "收听排行" else "我的${title}Top${songs.size}",size=16f,weight=FontWeight.SemiBold)
                        FootprintText(timeline,size=16f,numeric=true)
                    }
                    ListeningSongLoadState(loading,error,songs.isEmpty(),retry)
                }
            }
            itemsIndexed(songs,key={index,song -> "${song.id}:$index"}) { index,song ->
                ListeningRankSongRow(index,song,song.id!=null && song.id==currentSongId,playing) {
                    val playable=songs.filter { it.id!=null }
                    play(playable,songs.take(index).count { it.id!=null })
                }
            }
            item(key="bottom") { Spacer(Modifier.fillMaxWidth().background(WallListBackground).navigationBarsPadding().height((50*s).dp)) }
        }
        }
        CompositionLocalProvider(LocalGlassBackdrop provides topBackdrop) {
            ListeningSongsTopBar("唱片墙列表",dismiss,headerSolid)
        }
        }
    }
}

@Composable private fun ListeningRankSongRow(index: Int, song: NeteaseListeningSong, selected: Boolean, playing:Boolean, play: () -> Unit) {
    val s=LocalFootprintScale.current
    Row(Modifier.fillMaxWidth().background(WallListBackground).clickable(enabled=song.id!=null,role=Role.Button) {
        play()
    }.height((64*s).dp).padding(start=(14*s).dp,end=(15*s).dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.width((31.667f*s).dp)) {
            if(selected) ListeningPlayingIcon(playing,Modifier.size((16*s).dp))
            else FootprintText("${index+1}",size=15.333f,numeric=true,weight=FontWeight.Bold,color=FootprintInk.copy(alpha=.8f))
        }
        FootprintCover(song.coverUrl,song.name,Modifier.size((48*s).dp).clip(RoundedCornerShape((8*s).dp)))
        Column(Modifier.weight(1f).padding(start=(9*s).dp,end=(8*s).dp),verticalArrangement=Arrangement.spacedBy((3*s).dp)) {
            FootprintText(song.name,size=16.667f,weight=FontWeight.SemiBold,maxLines=1,color=if(selected) FootprintRed else FootprintInk.copy(alpha=.9f))
            FootprintText(listOf(song.artists,song.albumName).filter(String::isNotBlank).joinToString(" - "),size=12f,color=FootprintMuted,maxLines=1)
        }
        song.playCount?.let { count ->
            val muted = FootprintMuted
            Canvas(Modifier.size((10*s).dp)) {
                val triangle=Path().apply { moveTo(size.width*.2f,size.height*.1f); lineTo(size.width*.9f,size.height*.5f); lineTo(size.width*.2f,size.height*.9f); close() }
                drawPath(triangle,muted,style=Stroke(width=1.dp.toPx()))
            }
            FootprintText("${count}次",Modifier.padding(start=(3*s).dp),size=12f,color=FootprintMuted)
        }
    }
}

@Composable internal fun ListeningTodaySongs(songs: List<NeteaseListeningSong>, loading: Boolean,
    error: String?, retry: () -> Unit, dismiss: () -> Unit,
    play: (ListeningMonthlyAction.Song) -> Unit, currentSongId: Long? = null, playing: Boolean = false) {
    val s=LocalFootprintScale.current
    var info by remember { mutableStateOf(false) }
    ListeningSongSystemBars(!FootprintDark)
    Column(Modifier.fillMaxSize().background(WallListBackground).statusBarsPadding().testTag("listening_today_songs")) {
        ListeningSongsTopBar("今日收听",dismiss,true,insets=false,
            subtitle=LocalDate.now(neteaseListeningZone).format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")))
        ListeningSongLoadState(loading,error,songs.isEmpty(),retry)
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=(18*s).dp,vertical=(17.333f*s).dp)) {
            itemsIndexed(songs,key={i,song -> "${song.id}:$i"}) { _,song ->
                val selected=song.id!=null && song.id==currentSongId && playing
                Row(Modifier.fillMaxWidth().padding(bottom=(20*s).dp).clickable(enabled=song.id!=null,role=Role.Button) {
                    song.id?.let { play(ListeningMonthlyAction.Song(it,song.name,song.artists,song.coverUrl)) }
                },verticalAlignment=Alignment.CenterVertically) {
                    FootprintCover(song.coverUrl,song.name,Modifier.size((40*s).dp).clip(RoundedCornerShape((4*s).dp)))
                    if(selected) ListeningPlayingIcon(true,Modifier.padding(start=(10*s).dp).size((16*s).dp))
                    Column(Modifier.weight(1f).padding(start=(10*s).dp,end=(10*s).dp),verticalArrangement=Arrangement.spacedBy((4*s).dp)) {
                        FootprintText(song.name+song.aliasName.takeIf(String::isNotBlank)?.let { "（$it）" }.orEmpty(),size=16f,maxLines=1,
                            color=if(selected) FootprintRed else FootprintInk.copy(alpha=.9f))
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            if(song.redStar==true) FootprintText("♥",Modifier.padding(end=(3*s).dp),size=9f,color=FootprintRed)
                            FootprintText(song.artists,size=12f,color=FootprintMuted,maxLines=1)
                        }
                    }
                    song.lastPlayTimeMs?.let { FootprintText(Instant.ofEpochMilli(it).atZone(neteaseListeningZone)
                        .format(DateTimeFormatter.ofPattern("HH:mm"))+" 听过",size=10f,color=FootprintMuted.copy(alpha=.6f)) }
                }
            }
            if(!loading && error==null) item {
                Row(Modifier.fillMaxWidth().padding(top=(13.333f*s).dp,bottom=(48.333f*s).dp).navigationBarsPadding(),
                    horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                    FootprintText("仅展示每日最近收听的100首歌曲",size=12f,color=FootprintMuted)
                    FootprintText("ⓘ",Modifier.padding(start=(4*s).dp).clickable { info=true },size=12f,color=FootprintMuted)
                }
            }
        }
    }
    if(info) FootprintDialog("今日收听",{info=false}) {
        FootprintText("展示今天最近收听的歌曲，最多100首。同一首歌曲重复收听时，展示最近一次收听时间。",size=14f)
    }
}

@Composable private fun ListeningPlayingIcon(playing:Boolean,modifier:Modifier) {
    val phase=rememberListeningBarLight(playing)
    Canvas(modifier.semantics { contentDescription=if(playing) "正在播放" else "已暂停" }) {
        repeat(3) { index ->
            val fraction=.3f+.7f*((kotlin.math.sin(((phase?.value ?: 0f)*6.283185f+index*2f)*2f)+1f)/2f)
            drawRoundRect(FootprintRed,Offset(size.width*index/3,size.height*(1-fraction)),
                androidx.compose.ui.geometry.Size(size.width*.18f,size.height*fraction),
                androidx.compose.ui.geometry.CornerRadius(size.width*.09f))
        }
    }
}

@Composable private fun ListeningSongLoadState(loading:Boolean,error:String?,empty:Boolean,retry:()->Unit) {
    val s=LocalFootprintScale.current
    if(loading || error!=null || empty) Column(Modifier.fillMaxWidth().padding((24*s).dp)) {
        FootprintText(if(loading) "正在读取收听歌曲…" else error ?: "暂无收听歌曲",color=FootprintMuted)
        if(error!=null) FootprintText("重试",Modifier.padding(top=(16*s).dp).clickable(onClick=retry),color=FootprintRed)
    }
}

@Composable private fun ListeningSongsTopBar(title:String,dismiss:()->Unit,solid:Boolean,insets:Boolean=true,subtitle:String?=null) {
    val s=LocalFootprintScale.current
    val ink by animateColorAsState(if(solid) FootprintInk else Color.White,tween(250),label="wall header ink")
    val background by animateColorAsState(if(solid) WallListBackground else Color(0xFF171717),tween(250),label="wall header background")
    SettingsFloatingHeader(background=background,toolbarHeight=(54*s).dp) {
        Box(Modifier.fillMaxWidth().then(if(insets) Modifier.statusBarsPadding() else Modifier).height((54*s).dp)) {
            Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally) {
                FootprintText(title,size=if(subtitle==null)16f else 17f,weight=FontWeight.SemiBold,color=ink)
                subtitle?.let { FootprintText(it,Modifier.padding(top=(5*s).dp),size=11f,color=FootprintMuted) }
            }
            GlassIconButton(dismiss,"返回",Modifier.align(Alignment.CenterStart).padding(start=12.dp)
                .testTag("listening_songs_back")) {
                Icon(MusicIcons.Back,null,tint=MiuixTheme.colorScheme.onBackground)
            }
        }
    }
}

@Composable internal fun ListeningSongSystemBars(light:Boolean) {
    val view=LocalView.current
    DisposableEffect(view,light) {
        val window=(view.parent as? DialogWindowProvider)?.window
        val controller=window?.let { WindowCompat.getInsetsController(it,view) }
        val oldStatus=controller?.isAppearanceLightStatusBars
        val oldNavigation=controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars=light
        controller?.isAppearanceLightNavigationBars=light
        onDispose {
            oldStatus?.let { controller?.isAppearanceLightStatusBars=it }
            oldNavigation?.let { controller?.isAppearanceLightNavigationBars=it }
        }
    }
}

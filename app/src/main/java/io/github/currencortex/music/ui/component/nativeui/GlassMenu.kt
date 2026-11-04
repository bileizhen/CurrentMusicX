// SPDX-License-Identifier: GPL-3.0-only
// CommonPopupBox / CommonPopupMenu adapted from chen08209's upstream project popup.dart,
// commit 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7. See THIRD_PARTY_NOTICES.md.
package io.github.currencortex.music.ui.component.nativeui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.*
import io.github.currencortex.music.ui.theme.LocalCustomFont

@Immutable data class GlassMenuItem(
    val id: String, val label: String, val icon: ImageVector? = null,
    val subtitle: String? = null, val checked: Boolean? = null, val toggle: Boolean = false,
    val danger: Boolean = false, val enabled: Boolean = true, val separated: Boolean = false,
    val children: List<GlassMenuItem> = emptyList(), val keepOpen: Boolean = false,
    val popOnSelect: Boolean = false, val contentTag: String? = null,
    val onEnter: () -> Unit = {}, val onClick: () -> Unit = {},
    val leadingContent: (@Composable () -> Unit)? = null, val subMenuMinWidth: Dp? = null,
    val animateSubItems:Boolean=false, val checkable:Boolean=false,
    val onExit: () -> Unit = {},
)
@Immutable data class GlassMenuTags(val panel: String = "glass_menu_panel", val dismiss: String = "glass_menu_dismiss",
    val actionPrefix: String = "glass_menu_action")
@Immutable data class GlassMenuInteraction(val selected: Int, val displacement: Offset, val highlight: Rect)
@Immutable data class GlassMenuWaves(val path: Float, val scale: Float, val opacity: Float)
val GlassMenuInteractionKey = SemanticsPropertyKey<GlassMenuInteraction>("GlassMenuInteraction")
val GlassMenuWavesKey = SemanticsPropertyKey<GlassMenuWaves>("GlassMenuWaves")
val GlassMenuFrameKey = SemanticsPropertyKey<GlassPopupFrame>("GlassMenuFrame")
val GlassMenuDepthKey = SemanticsPropertyKey<Int>("GlassMenuDepth")
val GlassMenuPathKey = SemanticsPropertyKey<List<String>>("GlassMenuPath")
val GlassMenuUnfoldKey = SemanticsPropertyKey<Float>("GlassMenuUnfold")

internal class GlassMenuTrigger {
    var open by mutableStateOf(false)
    var hidden by mutableStateOf(false)
    var layer: GraphicsLayer? by mutableStateOf(null)
    var move: ((Offset) -> Unit)? = null
    var release: ((Offset) -> Unit)? = null
    var cancel: (() -> Unit)? = null
}
internal val LocalGlassMenuTrigger = staticCompositionLocalOf<GlassMenuTrigger?> { null }
private data class MenuStep(val id: String, val top: Float, val itemHeight: Float, val width: Float, val rightOffset: Float = 0f)
private data class MenuLevel(val items: List<GlassMenuItem>, val owner: GlassMenuItem?, val step: MenuStep?, val top: Float,
    val width: Float, val reservedWidth:Float=width)
private data class MenuHit(val index: Int, val bounds: Rect, val action: () -> Unit, val subMenu: Boolean)

/** menuShadows from liquid_glass.dart: one wide, downward shadow; no tight halo. */
@Composable private fun Modifier.popupShadow(pane:Shape,progress:()->Float):Modifier {
    val dark=MiuixTheme.colorScheme.onBackground.luminance()>.5f
    val shadowShape=remember(pane){object:Shape {
        var previous:Outline?=null
        var cached:Outline?=null
        override fun createOutline(size:Size,layoutDirection:LayoutDirection,density:Density):Outline {
            val outline=pane.createOutline(size,layoutDirection,density)
            if(outline!=previous) {
                // A Generic outline preserves the translated pane inside its morph envelope.
                cached=Outline.Generic(when(outline) {
                    is Outline.Rounded->Path().apply{addRoundRect(outline.roundRect)}
                    is Outline.Rectangle->Path().apply{addRect(outline.rect)}
                    is Outline.Generic->outline.path
                })
                previous=outline
            }
            return checkNotNull(cached)
        }
    }}
    return dropShadow(shadowShape) {
        radius=29.dp.toPx();offset=Offset(0f,8.dp.toPx())
        color=Color.Black.copy(alpha=.118f*if(dark)1.5f else 1f)
        alpha=progress().coerceIn(0f,1f)
    }
}

/** LiquidGlassRim.paintLine: an outside hairline, independent of the Gaussian frost. */
@Composable private fun Modifier.popupRim(pane:Shape,progress:()->Float):Modifier {
    val dark=MiuixTheme.colorScheme.onBackground.luminance()>.5f
    val enabled=LocalLiquidGlassEnabled.current
    return if(!enabled)this else drawWithContent {
        drawContent()
        val outline=pane.createOutline(size,layoutDirection,this)
        val geometry=(pane as? GlassOpticalShape)?.opticalGeometry(size,this,layoutDirection)
        val bounds=when(outline) {
            is Outline.Rounded->outline.roundRect.let{Rect(it.left,it.top,it.right,it.bottom)}
            is Outline.Rectangle->outline.rect
            is Outline.Generic->outline.path.getBounds()
        }
        if(bounds.isEmpty)return@drawWithContent
        val round=geometry?.radius?:bounds.size.minDimension/2
        val across=(round/bounds.width).coerceIn(0f,.4f)
        val depth=progress().coerceIn(0f,1f)
        val side=(if(dark).27f else .25f)*depth
        val corner=(if(dark).1f else .15f)*depth
        val flat=(if(dark).02f else .09f)*depth
        val brush=Brush.horizontalGradient(colorStops=arrayOf(
            0f to Color.Black.copy(alpha=side),across/4 to Color.Black.copy(alpha=corner),
            across to Color.Black.copy(alpha=flat),(1-across) to Color.Black.copy(alpha=flat),
            (1-across/4) to Color.Black.copy(alpha=corner),1f to Color.Black.copy(alpha=side)),
            startX=bounds.left,endX=bounds.right)
        val width=.67.dp.toPx()
        val line=if(outline is Outline.Rounded)Outline.Rounded(RoundRect(bounds.inflate(width/2),CornerRadius(round+width/2)))else outline
        drawOutline(line,brush,style=Stroke(width))
    }
}

/** goo.dart's shared pill union; search uses the same outline and optical geometry as popups. */
internal fun glassGooShape(frame: () -> GlassPopupFrame): Shape = PopupShape(frame, Offset.Zero)

private class PopupShape(val frame: () -> GlassPopupFrame, val origin: Offset) : GlassOpticalShape {
    private val blend get()=frame().blend
    private fun distance(rect:Rect,radius:Float,point:Offset):Float {
        val r=min(radius,rect.size.minDimension/2)
        val q=Offset(abs(point.x-rect.center.x)-rect.width/2+r,abs(point.y-rect.center.y)-rect.height/2+r)
        return Offset(max(q.x,0f),max(q.y,0f)).getDistance()+min(max(q.x,q.y),0f)-r
    }
    private fun contains(a:Rect,radius:Float,b:Rect)=listOf(b.topLeft,b.topRight,b.bottomLeft,b.bottomRight)
        .all{distance(a,radius,it)<= -blend}
    override fun opticalGeometry(size:Size,density:Density,direction:LayoutDirection):GlassOpticalGeometry {
        val f=frame()
        val a=f.bounds.translate(-origin);val b=f.droplet.translate(-origin)
        if(contains(a,f.radius,b))return GlassOpticalGeometry(a,f.radius)
        if(contains(b,b.size.minDimension/2,a))return GlassOpticalGeometry(b,b.size.minDimension/2)
        return GlassOpticalGeometry(a,f.radius,b,blend)
    }
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val f=frame(); val a=f.bounds.translate(-origin); val b=f.droplet.translate(-origin)
        // Like gooPairOutline, stop tracing once one pane has absorbed the other.
        if(contains(a,f.radius,b))return Outline.Rounded(RoundRect(a,CornerRadius(f.radius)))
        if(contains(b,b.size.minDimension/2,a))return Outline.Rounded(RoundRect(b,CornerRadius(b.size.minDimension/2)))
        val p=Path().apply { addRoundRect(RoundRect(a, CornerRadius(f.radius))) }
        if(b.width>.1f && b.height>.1f) {
            val other=Path().apply { addOval(b) }
            p.op(p,other,PathOperation.Union)
            if(blend<=.001f)return Outline.Generic(p)
            // goo.dart's _bridge sphere-traces the same smooth minimum as the optical shader.
            // Casting all 128 rays avoids a separate adaptive subdivision cache in Compose.
            fun field(point:Offset):Float {
                val da=distance(a,f.radius,point);val db=distance(b,b.size.minDimension/2,point)
                val h=(.5f+.5f*(db-da)/blend).coerceIn(0f,1f)
                return glassLerp(db,da,h)-blend*h*(1-h)
            }
            val ends=Array(128){Offset.Zero}
            val flows=BooleanArray(128)
            val limit=b.size.maxDimension/2+a.size.maxDimension+blend
            for(i in ends.indices) {
                val angle=i*2*PI/128
                val direction=Offset(cos(angle).toFloat(),sin(angle).toFloat())
                var inside=0f;var ray=0f
                for(iteration in 0..511) {
                    val point=b.center+direction*ray
                    val platter=distance(a,f.radius,point)
                    if(platter< -blend){inside=ray;break}
                    val depth=field(point)
                    if(depth>=0f) {
                        var outside=ray
                        repeat(4) {val middle=(inside+outside)/2
                            if(field(b.center+direction*middle)<0f)inside=middle else outside=middle}
                        break
                    }
                    inside=ray;ray+=max(.5f,min(-depth,platter+blend))
                    if(ray>=limit)break
                }
                ends[i]=b.center+direction*inside
                flows[i]=distance(a,f.radius,ends[i])<blend
            }
            val start=flows.indexOfFirst{!it}.coerceAtLeast(0)
            val bridge=Path();var drawing=false
            for(i in 0..128) {
                val turn=(start+i)%128
                if(flows[turn]) {
                    if(!drawing){bridge.moveTo(b.center.x,b.center.y);drawing=true}
                    bridge.lineTo(ends[turn].x,ends[turn].y)
                }else if(drawing){bridge.close();drawing=false}
            }
            if(drawing)bridge.close()
            p.op(p,bridge,PathOperation.Union)
        }
        return Outline.Generic(p)
    }
}

/** A source-anchored glass popup, with a carried opening press, nested levels and drag selection. */
@Composable fun BoxScope.GlassMenu(expanded: Boolean, onExpandedChange: (Boolean) -> Unit,
    items: List<GlassMenuItem>, anchor: Rect?, backdrop: Backdrop?, tags: GlassMenuTags = GlassMenuTags(),
    onDepthChanged: (Int) -> Unit = {}, onPathChanged: (List<String>) -> Unit = {},
    onInteractionChanged: (Boolean) -> Unit = {}, menuWidth: Dp? = 192.dp, bottomInset:Dp=0.dp, topInset:Dp=0.dp,
    rootOwner: GlassMenuItem? = null, showDividers: Boolean = true, pullPastEdges: Boolean = true,
    flipRootAtEdges: Boolean = false,
    parentWidth: Dp? = null, onParentTransition: (Float) -> Unit = {},
    content:@Composable ()->Unit={}, toolbar: @Composable () -> Unit) {
    val scope=rememberCoroutineScope()
    val trigger=remember { GlassMenuTrigger() }
    val motion=remember { GlassPopupMotion() }
    val density=LocalDensity.current
    val textMeasurer=rememberTextMeasurer(cacheSize=64)
    val font=LocalCustomFont.current
    val labelStyle=remember(font){TextStyle(fontFamily=font,fontSize=15.sp,lineHeight=20.sp)}
    val subtitleStyle=remember(font){TextStyle(fontFamily=font,fontSize=13.sp,lineHeight=18.sp)}
    val contentHeights=remember(density.density,density.fontScale){mutableStateMapOf<List<String>,Int>()}
    val haptics=LocalHapticFeedback.current
    val ink=MiuixTheme.colorScheme.onBackground
    val dimColor=MiuixTheme.colorScheme.surface
    val path=remember { mutableStateListOf<MenuStep>() }
    val retainedPath=remember {mutableStateListOf<MenuStep>()}
    var hasShown by remember {mutableStateOf(false)}
    val unfold=remember { Animatable(if(rootOwner!=null)0f else 1f) }
    val widthProgress=remember {derivedStateOf{(unfold.value/.3f).coerceIn(0f,1f)}}
    var folding by remember { mutableStateOf(false) }
    val reorderingEnabled=remember {derivedStateOf{!folding&&unfold.value==1f&&motion.opening&&motion.clock.value>=1f}}
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var selected by remember { mutableIntStateOf(Int.MIN_VALUE) }
    var highlight by remember { mutableStateOf(Rect.Zero) }
    var landed by remember { mutableStateOf(false) }
    var dwell by remember { mutableStateOf<Job?>(null) }
    val rows=remember { mutableStateMapOf<Pair<Int,Int>,Rect>() }
    var activeCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var activeRestCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var pointerHeld by remember { mutableStateOf(false) }
    val press=rememberElasticPressState()
    val currentOpen by rememberUpdatedState(expanded)
    val changeOpen by rememberUpdatedState(onExpandedChange)
    val frameDensity=density.density
    val maxWidth=with(density){ 280.dp.toPx() }
    val inset=with(density){ 8.dp.toPx() }
    val measuredWidth=remember(items,textMeasurer,labelStyle,subtitleStyle,density.density,density.fontScale) {
        items.maxOfOrNull {item->
            val label=textMeasurer.measure(AnnotatedString(item.label),labelStyle,maxLines=1,softWrap=false).size.width
            val subtitle=item.subtitle?.let{textMeasurer.measure(AnnotatedString(it),subtitleStyle,maxLines=1,softWrap=false).size.width}?:0
            val trailing=item.checked==true||item.children.isNotEmpty()||item.toggle
            with(density){(16.dp+(if(trailing)20.dp else 24.dp)+(if(trailing)24.dp else 0.dp)+
                (if(item.icon!=null)32.dp else 0.dp)+(if(item.leadingContent!=null)56.dp else 0.dp)).toPx()}+max(label,subtitle)
        }?:0f
    }
    val menuWidthPx=menuWidth?.let{with(density){it.toPx()}}?:measuredWidth.coerceIn(with(density){160.dp.toPx()},maxWidth)
    val safeTop=max(WindowInsets.safeDrawing.getTop(density).toFloat(),with(density){topInset.toPx()})
    val safeBottom=max(WindowInsets.safeDrawing.getBottom(density).toFloat(),with(density){bottomInset.toPx()})
    val a=anchor?.translate(-rootOrigin) ?: Rect((rootSize.width-60*frameDensity).coerceAtLeast(0f),
        safeTop+8*frameDensity,(rootSize.width-16*frameDensity).coerceAtLeast(44*frameDensity),safeTop+52*frameDensity)
    val rootWidth=menuWidthPx.coerceAtMost((rootSize.width-32*frameDensity).coerceAtLeast(1f))
    fun resolve(steps:List<MenuStep>)=buildList {
        add(MenuLevel(items,rootOwner,null,0f,rootWidth))
        for(step in steps) {
            val owner=last().items.firstOrNull{it.id==step.id} ?: break
            if(owner.children.isEmpty())break
            val target=max(step.width*1.12f,with(density){owner.subMenuMinWidth?.toPx()?:0f})
                .coerceAtMost(maxWidth).coerceAtMost((rootSize.width-32*frameDensity).coerceAtLeast(1f))
            add(MenuLevel(owner.children,owner,step,max(0f,step.top-inset),target,target))
        }
    }
    val levels=resolve(path)
    val renderedLevels=resolve(retainedPath)
    val liveLevels by rememberUpdatedState(levels)
    val depth=levels.lastIndex
    val topLevel=levels.last()
    val heightLimit=(rootSize.height-safeTop-safeBottom-32*frameDensity).coerceAtLeast(1f)
    fun rowHeight(item: GlassMenuItem,width:Float=rootWidth):Float {
        val trailing=item.checked==true||item.children.isNotEmpty()||item.toggle
        val remaining=(width-with(density){(16.dp+(if(trailing)20.dp else 24.dp)+
            (if(item.toggle)68.dp else if(trailing)24.dp else 0.dp)+
            (if(item.icon!=null)32.dp else 0.dp)+(if(item.leadingContent!=null)56.dp else 0.dp)).toPx()}).roundToInt().coerceAtLeast(1)
        val constraints=Constraints(maxWidth=remaining)
        val label=textMeasurer.measure(AnnotatedString(item.label),labelStyle,maxLines=1,constraints=constraints).size.height
        val subtitle=item.subtitle?.let{textMeasurer.measure(AnnotatedString(it),subtitleStyle,maxLines=2,constraints=constraints).size.height}?:0
        return with(density){24.dp.toPx()}+max((label+subtitle).toFloat(),if(item.toggle)with(density){48.dp.toPx()}else 0f)
    }
    fun contentKey(level:MenuLevel)=retainedPath.take(renderedLevels.indexOf(level)).map{it.id}
    fun levelHeight(level: MenuLevel): Float {
        // Predict only the first layout. Real text/switch metrics own the resting height;
        // multiplying an estimated line height across many rows leaves a large empty tail.
        val content=contentHeights[contentKey(level)]?.toFloat() ?: (
            (if(level.owner!=null)rowHeight(level.owner,level.width)+(if(showDividers)17*frameDensity else 0f) else 0f)+
            level.items.sumOf { (rowHeight(it,level.width)+(if(showDividers&&it.separated)17*frameDensity else 0f)).toDouble() }.toFloat())
        val rootLimit=with(density){16.dp.toPx()+7.5f*(24.dp.toPx()+20.sp.toPx())}
        return (inset*2+content).coerceAtMost(if(level.owner==null)min(heightLimit,rootLimit)else heightLimit)
    }
    val requestedRootHeight=levelHeight(levels.first())
    val bottomEdge=rootSize.height-safeBottom-16*frameDensity
    val spaceBelow=bottomEdge-(a.top-inset)
    val spaceAbove=a.bottom+inset-safeTop
    // Keep the owner row next to its trigger. Clamping a downward menu alone
    // would move that row away from the user's finger when the bottom runs out.
    val rootAbove=flipRootAtEdges&&rootOwner!=null&&requestedRootHeight>spaceBelow&&spaceAbove>spaceBelow
    val rootHeight=if(flipRootAtEdges&&rootOwner!=null)
        min(requestedRootHeight,(if(rootAbove)spaceAbove else spaceBelow).coerceAtLeast(1f))else requestedRootHeight
    val rootTop=(if(rootAbove)a.bottom+inset-rootHeight else a.top-inset)
        .coerceIn(safeTop,(bottomEdge-rootHeight).coerceAtLeast(safeTop))
    val rootRight=a.right.coerceIn(rootWidth+16*frameDensity,(rootSize.width-16*frameDensity).coerceAtLeast(rootWidth+16*frameDensity))
    val rootCard=Rect(rootRight-rootWidth,rootTop,rootRight,rootTop+rootHeight)
    fun opensAbove(level: MenuLevel): Boolean {
        val step=level.step ?: return rootAbove
        val triggerTop=rootTop+step.top
        return flipRootAtEdges&&levelHeight(level)>bottomEdge-(triggerTop-inset)&&
            triggerTop+step.itemHeight+inset-safeTop>bottomEdge-(triggerTop-inset)
    }
    fun cardOf(level: MenuLevel): Rect {
        val step=level.step
        if(step==null)return rootCard
        val triggerTop=rootTop+step.top
        val triggerBottom=triggerTop+step.itemHeight
        val above=opensAbove(level)
        val available=if(above)triggerBottom+inset-safeTop else bottomEdge-triggerTop+inset
        val height=if(flipRootAtEdges)min(levelHeight(level),available.coerceAtLeast(1f))else levelHeight(level)
        val top=(if(above)triggerBottom+inset-height else rootTop+level.top)
            .coerceIn(safeTop,(bottomEdge-height).coerceAtLeast(safeTop))
        val right=rootRight.coerceAtLeast(level.width+16*frameDensity)
            .coerceAtMost(rootSize.width-16*frameDensity)
        return Rect(right-level.width,top,right,top+height)
    }
    fun stepOf(level: MenuLevel): MenuStep? = level.step ?: rootOwner?.let {
        MenuStep(it.id,a.top-rootTop,a.height,parentWidth?.let{with(density){it.toPx()}}?:a.width+2*inset)
    }
    val activeCard=cardOf(topLevel)
    val actualCard=if(depth==0)rootCard else activeCard
    val currentCard by rememberUpdatedState(actualCard)
    val currentAnchor by rememberUpdatedState(a)
    val visible=expanded||motion.shown
    // Async child content belongs to its active branch, not the retained closing layers.
    levels.forEach { level ->
        if (expanded && level.owner != null) key(level.owner.id) {
            val exit by rememberUpdatedState(level.owner.onExit)
            DisposableEffect(level.owner.id) { onDispose { exit() } }
        }
    }
    LaunchedEffect(expanded,rootSize.width>0) {
        if(rootSize.width==0)return@LaunchedEffect
        if(expanded)hasShown=true
        if(rootOwner!=null) {
            if(expanded) {
                motion.settle(true)
                unfold.animateTo(1f,spring(.85f,223.8f));unfold.snapTo(1f)
            } else if(motion.shown) {
                unfold.animateTo(0f,spring(1f,341.5f))
                motion.settle(false)
            }
        } else if(expanded||motion.shown) motion.animate(expanded,currentAnchor,currentCard,frameDensity)
        if(!expanded) { path.clear(); selected=Int.MIN_VALUE; rows.clear() }
    }
    val parentTransition by rememberUpdatedState(onParentTransition)
    LaunchedEffect(rootOwner!=null) {
        if(rootOwner!=null) snapshotFlow { if(path.isEmpty())unfold.value.coerceIn(0f,1f) else 1f }
            .collect { parentTransition(it) }
    }
    SideEffect { trigger.open=expanded; trigger.hidden=visible; onDepthChanged(depth)
        onPathChanged(path.map { it.id }); onInteractionChanged(pointerHeld) }
    fun cancelPress() {
        dwell?.cancel(); dwell=null;selected=Int.MIN_VALUE;landed=false;pointerHeld=false;press.release()
    }
    fun pop() {
        if(path.isEmpty()) { if(rootOwner!=null)changeOpen(false); return }
        if(folding)return
        folding=true
        scope.launch {
            unfold.animateTo(0f,spring(1f,341.5f))
            if(folding) { path.removeAt(path.lastIndex); folding=false;unfold.snapTo(1f) }
        }
    }
    fun toggleTop() {
        if(folding) {
            folding=false
            scope.launch {unfold.animateTo(1f,spring(.85f,223.8f));unfold.snapTo(1f)}
        }else pop()
    }
    fun push(item: GlassMenuItem,index: Int) {
        item.onEnter()
        val rect=rows[depth to index] ?: Rect(inset,inset,topLevel.width-inset,inset+44*frameDensity)
        val step=MenuStep(item.id,activeCard.top-rootTop+rect.top,rect.height,topLevel.width,activeCard.right-rootRight)
        scope.launch {
            // Initialize before publishing the new level: never paint one full-size frame
            // and then jump back to its collapsed header on the following frame.
            unfold.snapTo(0f);folding=false;path.add(step);selected=Int.MIN_VALUE
            retainedPath.clear();retainedPath.addAll(path)
            unfold.animateTo(1f,spring(.85f,223.8f));unfold.snapTo(1f)
        }
    }
    fun choose(item: GlassMenuItem,index: Int) {
        if(!currentOpen||!item.enabled)return
        if(item.children.isNotEmpty()) { push(item,index);return }
        if(item.keepOpen) {
            item.onClick()
            if(item.popOnSelect)pop()
        } else {
            // Keep the picked action behind the closing glass, as the upstream popup route does.
            changeOpen(false)
            scope.launch { delay(500); item.onClick() }
        }
    }
    fun hit(position: Offset): MenuHit? {
        val coords=activeCoordinates?.takeIf{it.isAttached} ?:return null
        // Compose coordinates already include the graphics-layer transform. Inverting it twice
        // would move selection away from a stretching panel.
        val painted=coords.windowToLocal(position)
        val live=liveLevels.last()
        val liveStep=stepOf(live)
        val clip=if(liveStep!=null) {
            val width=glassLerp(liveStep.width,live.width,widthProgress.value)
            val left=live.width-width
            val bounds=Rect(left,0f,live.width,cardOf(live).height)
            val originTop=rootTop+liveStep.top-cardOf(live).top
            val shift=rootRight+liveStep.rightOffset-cardOf(live).right
            glassLerp(Rect(left+inset+shift,originTop,live.width-inset+shift,
                originTop+liveStep.itemHeight),bounds,unfold.value)
        }else Rect(0f,0f,live.width,cardOf(live).height)
        if(!clip.contains(painted))return null
        val row=rows.entries.firstOrNull{it.key.first==depth&&it.value.contains(painted)} ?: return null
        val i=row.key.second
        if(i==-1)return MenuHit(-1,row.value,{toggleTop()},true)
        val item=liveLevels.last().items.getOrNull(i)?.takeIf{it.enabled} ?:return null
        return MenuHit(i,row.value,{choose(item,i)},item.children.isNotEmpty())
    }
    fun track(position: Offset, start: Boolean=false) {
        val coords=activeCoordinates?.takeIf{it.isAttached} ?:return
        val rest=activeRestCoordinates?.takeIf{it.isAttached}?.windowToLocal(position) ?: return
        if(start||!pointerHeld){pointerHeld=true;press.press(rest)} else press.drag(rest,pastEdges=pullPastEdges)
        val row=hit(position)
        val i=row?.index ?:Int.MIN_VALUE
        if(i==selected)return
        dwell?.cancel();landed=false
        if(i!=Int.MIN_VALUE)haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        selected=i
        if(row!=null)highlight=row.bounds
        if(row?.subMenu==true) dwell=scope.launch {
            delay(400)
            val live=hit(position)
            if(currentOpen&&selected==i&&live?.index==i) { live.action();selected=-1;landed=true;haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
        }
    }
    fun release(position: Offset) {
        val action=if(landed)null else hit(position)?.action
        cancelPress();action?.invoke()
    }
    val trackAction=rememberUpdatedState<(Offset,Boolean)->Unit>({position,start->track(position,start)})
    val releaseAction=rememberUpdatedState<(Offset)->Unit>({release(it)})
    val cancelAction=rememberUpdatedState<()->Unit>({cancelPress()})
    val chooseAction=rememberUpdatedState<(GlassMenuItem,Int)->Unit>({item,index->choose(item,index)})
    val headerAction=rememberUpdatedState<()->Unit>({toggleTop()})
    SideEffect { trigger.move={trackAction.value(it,false)};trigger.release={releaseAction.value(it)};trigger.cancel={cancelAction.value()} }
    DisposableEffect(trigger) {onDispose { trigger.move=null;trigger.release=null;trigger.cancel=null;dwell?.cancel() } }
    BackHandler(enabled=visible) {if(currentOpen&&path.isNotEmpty())pop()else if(currentOpen)changeOpen(false)}
    Box(Modifier.matchParentSize().onGloballyPositioned { rootOrigin=it.boundsInWindow().topLeft;rootSize=it.size })
    CompositionLocalProvider(LocalGlassMenuTrigger provides trigger) { content() }
    if(visible) Box(Modifier.matchParentSize().testTag(tags.dismiss)
        .semantics {contentDescription="关闭菜单";onClick {if(path.isNotEmpty())pop()else changeOpen(false);true}}
        .pointerInput(Unit) {
            awaitEachGesture {
                val down=awaitFirstDown(false,PointerEventPass.Initial);down.consume()
                if(path.isNotEmpty())pop()else changeOpen(false)
                do {val e=awaitPointerEvent();e.changes.forEach{it.consume()}}while(e.changes.any{it.pressed})
            }
        })
    CompositionLocalProvider(LocalGlassMenuTrigger provides trigger) { toolbar() }
    if((!visible&&!hasShown)||rootSize.width==0)return
    val frame=remember(motion,a,actualCard,frameDensity){derivedStateOf{motion.frame(a,actualCard,frameDensity)}}
    // Hold the envelope at rest; the animated outline and content only change paint/layers.
    val envelope=Rect(floor(min(a.left,renderedLevels.minOf{cardOf(it).right-it.reservedWidth})-20*frameDensity),
        floor(min(a.top,renderedLevels.minOf{cardOf(it).top})-20*frameDensity),
        ceil(max(a.right,renderedLevels.maxOf{cardOf(it).right})+20*frameDensity),
        ceil(max(a.bottom,renderedLevels.maxOf{cardOf(it).bottom})+20*frameDensity))
    // The optical shader owns its SDF mask. Read the motion in paint instead of recomposing
    // every row (and rebuilding their modifier nodes) for every frame of a return.
    val shape=remember(frame,envelope.topLeft){PopupShape({frame.value},envelope.topLeft)}
    Box(Modifier.offset{IntOffset(envelope.left.roundToInt(),envelope.top.roundToInt())}
        .requiredSize(with(density){envelope.width.toDp()},with(density){envelope.height.toDp()})
        .then(if(!visible)Modifier.clearAndSetSemantics{}else Modifier)
        .layout {measurable,constraints->val child=measurable.measure(constraints)
            layout(child.width,child.height){if(visible)child.place(0,0)}}) {
        Box(Modifier.matchParentSize().graphicsLayer {
            alpha=if(!motion.opening||motion.clock.value<1f)1f else 0f
            val held=press.frame(16.dp.toPx())
            transformOrigin=TransformOrigin((actualCard.center.x-envelope.left)/envelope.width,
                (actualCard.center.y-envelope.top)/envelope.height)
            scaleX=held.scaleX;scaleY=held.scaleY
            translationX=held.pull.x;translationY=held.pull.y
        }.popupShadow(shape){frame.value.progress}.popupRim(shape){frame.value.progress}
            .glassSurface(backdrop,shape,menu=true,menuProgress={frame.value.progress},frost={frame.value.frost},clipToShape=false))
        for((index,level) in renderedLevels.withIndex()) {
            val scroll=rememberScrollState()
            val card=cardOf(level);val active=index==depth
            val retired=index>depth
            val covered=depth-index
            val step=stepOf(level)
            val stepTop=step?.let{rootTop+it.top-card.top}?:0f
            val stepShift=step?.let{rootRight+it.rightOffset-card.right}?:0f
            LaunchedEffect(expanded,active,opensAbove(level),card.height,scroll.maxValue) {
                if(expanded&&active&&opensAbove(level))scroll.scrollTo(scroll.maxValue)
            }
            LaunchedEffect(expanded,active,level.items.map{it.id to it.checked},card.height) {
                if(expanded&&active&&level.owner==null) {
                    val checked=level.items.indexOfFirst{it.checked==true}
                    withFrameNanos{}
                    if(checked>=0) {
                        val before=level.items.take(checked).sumOf{rowHeight(it,level.width).toDouble()}.toFloat()
                        val row=rowHeight(level.items[checked],level.width)
                        val alignment=3f/6.5f
                        scroll.scrollTo((before-(card.height-2*inset-row)*alignment).roundToInt().coerceAtLeast(0))
                    }else scroll.scrollTo(0)
                }
            }
            val paintedWidth=remember(active,step,level.width){derivedStateOf {
                if(active&&step!=null)glassLerp(step.width,level.width,widthProgress.value)else level.width
            }}
            val cardShape=remember(active,step,stepTop,stepShift,level.width,frameDensity){object:Shape{
                override fun createOutline(size:Size,layoutDirection:LayoutDirection,density:Density):Outline {
                    val grow=if(active&&step!=null)unfold.value else 1f
                    val left=size.width-paintedWidth.value
                    val bounds=Rect(left,0f,size.width,size.height)
                    val origin=if(step==null)bounds else
                        Rect(left+inset+stepShift,stepTop,size.width-inset+stepShift,stepTop+step.itemHeight)
                    return Outline.Rounded(RoundRect(glassLerp(origin,bounds,grow),
                        CornerRadius(glassLerp(16*frameDensity,24*frameDensity,grow))))
                }
            }}
            run {
                // The capture stays at the full card width. Shrinking the capture itself
                // reallocates blurred layers and rebuilds Gaussian pipelines on every frame.
                val materialShape=remember(active,step,stepTop,stepShift,level.reservedWidth,frameDensity){object:GlassOpticalShape {
                    override fun opticalGeometry(size:Size,density:Density,direction:LayoutDirection):GlassOpticalGeometry {
                        val width=if(active&&step!=null)glassLerp(step.width,level.reservedWidth,widthProgress.value)else level.reservedWidth
                        val left=size.width-width
                        val bounds=Rect(left,0f,size.width,size.height)
                        val grow=if(active&&step!=null)unfold.value else 1f
                        val origin=if(step==null)bounds else Rect(left+inset+stepShift,stepTop,
                            size.width-inset+stepShift,stepTop+step.itemHeight)
                        return GlassOpticalGeometry(glassLerp(origin,bounds,grow),glassLerp(16*frameDensity,24*frameDensity,grow))
                    }
                    override fun createOutline(size:Size,layoutDirection:LayoutDirection,density:Density):Outline {
                        val g=opticalGeometry(size,density,layoutDirection)
                        return Outline.Rounded(RoundRect(g.pane,CornerRadius(g.radius)))
                    }
                }}
                Box(Modifier.offset{IntOffset((card.right-level.reservedWidth-envelope.left).roundToInt(),(card.top-envelope.top).roundToInt())}
                    .requiredSize(with(density){level.reservedWidth.toDp()},with(density){card.height.toDp()})
                    .graphicsLayer {
                        alpha=if(!retired&&motion.opening&&motion.clock.value>=1f)1f else 0f
                        if(active) {
                            val f=press.frame(16.dp.toPx())
                            val width=if(step!=null)glassLerp(step.width,level.reservedWidth,widthProgress.value)else level.reservedWidth
                            transformOrigin=TransformOrigin(1-width/2/level.reservedWidth,.5f)
                            scaleX=f.scaleX;scaleY=f.scaleY;translationX=f.pull.x;translationY=f.pull.y
                        }else {
                            val pivot=path.getOrNull(index)?.let{it.top-level.top}?:0f
                            transformOrigin=TransformOrigin(1f,pivot/card.height)
                            val scale=max(0f,1-.05f*(covered-1+unfold.value.coerceIn(0f,1f)))
                            scaleX=scale;scaleY=scale
                        }
                    }.popupShadow(materialShape){if(active)unfold.value else max(1f/6f,1-(covered-1+unfold.value.coerceIn(0f,1f))/3)}
                    .popupRim(materialShape){if(active)(unfold.value/.35f).coerceIn(0f,1f)else 1f}
                    .glassSurface(backdrop,materialShape,true,clipToShape=false,
                        // Parent and child share the same frost/tint. Bend/light materialize
                        // with the new card; its Gaussian kernel stays cached through a fold.
                        strength={if(active)(unfold.value/.35f).coerceIn(0f,1f)else 1f},materialStrength={1f}))
            }
            val currentIndex=index
            ElasticPress(Modifier.offset{IntOffset((card.left-envelope.left).roundToInt(),(card.top-envelope.top).roundToInt())}
                .requiredSize(with(density){card.width.toDp()},with(density){card.height.toDp()})
                .layout {measurable,constraints->val child=measurable.measure(constraints)
                    layout(child.width,child.height){if(!retired)child.place(0,0)}}
                .graphicsLayer {
                    val displayFrame=frame.value
                    val contentScale=displayFrame.bounds.width/actualCard.width
                    val pivot=path.getOrNull(index)?.let{it.top-level.top}?:0f
                    transformOrigin=TransformOrigin(1f,pivot/card.height)
                    val settle=unfold.value.coerceIn(0f,1f)
                    val scale=if(active)1f else max(0f,1-.05f*(covered-1+settle))
                    scaleX=scale;scaleY=scale
                    if(!motion.opening||motion.clock.value<1f) {
                        transformOrigin=TransformOrigin(0f,0f)
                        scaleX*=contentScale;scaleY*=contentScale
                        translationX=displayFrame.bounds.left-card.left
                        translationY=displayFrame.bounds.top-card.top
                    }
                    alpha=if(retired)0f else displayFrame.content.coerceIn(0f,1f)
                    this.shape=if(!motion.opening||motion.clock.value<1f) {
                        // A fresh outline is assigned in the layer phase; RenderNode's shape
                        // cache stays current without a composition/layout pass.
                        val rect=Rect(0f,0f,card.width,(displayFrame.bounds.height/contentScale.coerceAtLeast(.001f)).coerceAtLeast(0f))
                        val radius=displayFrame.radius/contentScale.coerceAtLeast(.001f)
                        object:Shape {override fun createOutline(size:Size,layoutDirection:LayoutDirection,density:Density)=
                            Outline.Rounded(RoundRect(rect,CornerRadius(radius)))}
                    }else {
                        val outline=cardShape.createOutline(Size(card.width,card.height),LayoutDirection.Ltr,this)
                        object:Shape{override fun createOutline(size:Size,layoutDirection:LayoutDirection,density:Density)=outline}
                    }
                    clip=!active||!motion.opening||motion.clock.value<1f||(step!=null&&unfold.value<.999f)
                }
                .onGloballyPositioned{if(active)activeRestCoordinates=it}
                .then(if(!active)Modifier.clearAndSetSemantics{}else Modifier)
                .then(if(active)Modifier.testTag(tags.panel).semantics {
                    paneTitle=level.owner?.label ?: "菜单"
                    this[GlassMenuDepthKey]=depth
                    this[GlassMenuPathKey]=path.map{it.id}
                    this[GlassMenuUnfoldKey]=unfold.value
                    this[GlassMenuFrameKey]=frame.value
                    this[GlassMenuWavesKey]=GlassMenuWaves(frame.value.progress,frame.value.bounds.width/actualCard.width,frame.value.content)
                    val f=press.frame(with(density){16.dp.toPx()})
                    this[GlassMenuInteractionKey]=GlassMenuInteraction(selected,f.pull,highlight)
                }else Modifier), enabled=expanded&&active,state=if(active)press else rememberElasticPressState(),observePointer=false) {
                Box(Modifier.fillMaxSize()
                    .onGloballyPositioned {if(active)activeCoordinates=it}
                    .then(if(active)Modifier.pointerInput(depth,expanded) {
                        awaitEachGesture {
                            val down=awaitFirstDown(false,PointerEventPass.Initial)
                            if(!currentOpen||down.isConsumed)return@awaitEachGesture
                            trackAction.value(activeCoordinates!!.localToWindow(down.position),true)
                            var cancelled=false
                            try {
                                do {
                                    val event=awaitPointerEvent(PointerEventPass.Initial)
                                    val change=event.changes.firstOrNull{it.id==down.id}
                                    val released=change?.changedToUp()==true
                                    if(change==null||event.changes.count{it.pressed}>1||change.isConsumed)cancelled=true
                                    val delta=(change?.position?:down.position)-down.position
                                    // Upstream _RowPressRecognizer yields to a scrollable menu's drag.
                                    if(scroll.maxValue>0&&abs(delta.y)>viewConfiguration.touchSlop&&abs(delta.y)>abs(delta.x)) {
                                        cancelled=true;break
                                    }
                                    if(change!=null&&!cancelled)trackAction.value(activeCoordinates!!.localToWindow(change.position),false)
                                    if(scroll.maxValue==0||released||abs(delta.x)>viewConfiguration.touchSlop)
                                        event.changes.forEach{it.consume()}
                                    if(change?.pressed!=true) {
                                        if(released&&!cancelled)releaseAction.value(activeCoordinates!!.localToWindow(change!!.position))
                                        break
                                    }
                                }while(!cancelled)
                            }finally{cancelAction.value()}
                        }
                    }else Modifier.clickable(interactionSource=remember{MutableInteractionSource()},indication=null){pop()})
                ) {
                    val key=contentKey(level)
                    val currentRows=rememberUpdatedState(level.items)
                    val currentOwner=rememberUpdatedState(level.owner)
                    val currentActive=rememberUpdatedState(active)
                    val currentExpanded=rememberUpdatedState(expanded)
                    val currentStepShift=rememberUpdatedState(stepShift)
                    val headerAtBottom=opensAbove(level)
                    // Navigation changes paint/input ownership, not the mounted row tree.
                    // Keep active/expanded reads inside callbacks and draw phases so returning
                    // to the parent does not rebuild every source row in the handoff frame.
                    val content: @Composable ()->Unit = remember(key,ink,frameDensity,density.fontScale,contentHeights,tags.actionPrefix,showDividers,headerAtBottom) { {
                    val owner=currentOwner.value
                    LookaheadScope {
                    Column(Modifier.fillMaxWidth().padding(8.dp)
                        .then(if(owner?.contentTag!=null)Modifier.testTag(owner.contentTag)else Modifier)
                        .verticalScroll(scroll).onSizeChanged {
                            if(contentHeights[key]!=it.height)contentHeights[key]=it.height
                        }) {
                        @Composable fun row(item:GlassMenuItem,i:Int,isHeader:Boolean=false) {
                            if(showDividers&&item.separated&&!isHeader)Box(Modifier.fillMaxWidth().padding(vertical=8.dp).height(1.dp).background(ink.copy(alpha=.1f)))
                            Row(Modifier.fillMaxWidth().onGloballyPositioned {
                                val parent=activeCoordinates
                                if(currentActive.value&&parent?.isAttached==true)rows[currentIndex to i]=parent.localBoundingBoxOf(it,false)
                            }.drawBehind {
                                if(currentActive.value&&selected==i)drawRoundRect(ink.copy(alpha=.12f),cornerRadius=CornerRadius(16.dp.toPx()))
                            }
                                .clickable(enabled=item.enabled,interactionSource=remember{MutableInteractionSource()},indication=null,
                                    role=if(item.toggle)Role.Switch else if(item.checkable)Role.Checkbox else if(item.checked!=null)Role.RadioButton else Role.Button){
                                    if(currentActive.value&&currentExpanded.value) {
                                        if(isHeader)headerAction.value()else chooseAction.value(item,i)
                                    }
                                }
                                .semantics {if(item.toggle||item.checkable)toggleableState=ToggleableState(item.checked==true)else if(item.checked!=null)this.selected=item.checked}
                                .testTag(if(isHeader)"${tags.actionPrefix}_back"else item.id)
                                .padding(start=12.dp,end=if(item.checked==true||item.children.isNotEmpty()||isHeader)8.dp else 12.dp,
                                    top=12.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
                                Row(Modifier.weight(1f).graphicsLayer {
                                    translationX=if(currentActive.value&&step!=null)level.width-paintedWidth.value+
                                        currentStepShift.value*(1f-unfold.value.coerceIn(0f,1f)) else 0f
                                }.drawWithContent {
                                    val shift=if(currentActive.value&&step!=null)level.width-paintedWidth.value+
                                        currentStepShift.value*(1f-unfold.value.coerceIn(0f,1f)) else 0f
                                    clipRect(right=(size.width-shift).coerceAtLeast(0f)){this@drawWithContent.drawContent()}
                                },verticalAlignment=Alignment.CenterVertically) {
                                item.leadingContent?.let {it();Spacer(Modifier.width(10.dp))}
                                item.icon?.let {Icon(it,null,Modifier.size(20.dp),tint=if(item.danger)Color(0xFFFF6464)else ink.copy(alpha=.86f));Spacer(Modifier.width(12.dp))}
                                Column(Modifier.weight(1f)) {
                                    Text(item.label,color=if(item.danger)Color(0xFFFF6464)else ink.copy(alpha=if(item.enabled)1f else .38f),
                                        fontSize=15.sp,lineHeight=20.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                    item.subtitle?.let{Text(it,color=MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        fontSize=13.sp,lineHeight=18.sp,maxLines=2,overflow=TextOverflow.Ellipsis)}
                                }
                                }
                                if(item.toggle&&!isHeader) {
                                    Spacer(Modifier.width(8.dp));GlassSwitch(item.checked==true,null,pressed=currentActive.value&&selected==i)
                                }else if(item.children.isNotEmpty()||isHeader) {
                                    Spacer(Modifier.width(8.dp));Canvas(Modifier.size(16.dp).graphicsLayer {
                                        rotationZ=if(isHeader)(if(headerAtBottom)-90f else 90f)*(if(currentActive.value)unfold.value else 1f)else 0f
                                    }){drawLine(ink.copy(alpha=.7f),Offset(size.width*.4f,size.height*.25f),Offset(size.width*.65f,size.height*.5f),1.3.dp.toPx(),StrokeCap.Round)
                                        drawLine(ink.copy(alpha=.7f),Offset(size.width*.65f,size.height*.5f),Offset(size.width*.4f,size.height*.75f),1.3.dp.toPx(),StrokeCap.Round)}
                                }else if(item.checked==true) {
                                    Spacer(Modifier.width(8.dp));Canvas(Modifier.size(16.dp)) {
                                        val check=Path().apply{moveTo(size.width*.16f,size.height*.5f);lineTo(size.width*.4f,size.height*.72f);lineTo(size.width*.85f,size.height*.25f)}
                                        drawPath(check,ink,style=Stroke(1.5.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
                                    }
                                }
                            }
                        }
                        if(owner!=null&&!headerAtBottom) {
                            row(owner,-1,true)
                            if(showDividers)Box(Modifier.fillMaxWidth().padding(vertical=8.dp).height(1.dp).background(ink.copy(alpha=.1f)))
                        }
                        currentRows.value.forEachIndexed {i,item -> key(item.id){
                            Box(Modifier.then(if(owner?.animateSubItems==true)
                                Modifier.animateBounds(this@LookaheadScope,boundsTransform={_,_->if(reorderingEnabled.value)spring(dampingRatio=1f,stiffness=450f)else snap()})else Modifier)
                                .graphicsLayer {
                                alpha=if(!currentActive.value&&path.getOrNull(index)?.id==item.id)0f
                                    else if(!currentActive.value||owner==null)1f else ((unfold.value-.05f)/.35f).coerceIn(0f,1f)
                            }){row(item,i)}
                        }}
                        if(owner!=null&&headerAtBottom) {
                            if(showDividers)Box(Modifier.fillMaxWidth().padding(vertical=8.dp).height(1.dp).background(ink.copy(alpha=.1f)))
                            row(owner,-1,true)
                        }
                    }
                    }
                    }}
                    content()
                    if(!active)Box(Modifier.matchParentSize().drawBehind {
                        val receded=(.2f*(covered-1+unfold.value.coerceIn(0f,1f))).coerceIn(0f,.4f)
                        drawOutline(cardShape.createOutline(size,layoutDirection,this),dimColor.copy(alpha=receded))
                    })
                }
            }
        }
        Canvas(Modifier.matchParentSize()) {
            val f=frame.value
            val layer=trigger.layer
            if(layer!=null&&f.glyph>0f) {
                val scale=f.droplet.width/a.width
                val center=f.droplet.center-envelope.topLeft
                withTransform({translate(center.x-layer.size.width*scale/2,center.y-layer.size.height*scale/2);scale(scale,scale,Offset.Zero)}) {
                    layer.alpha=f.glyph.coerceIn(0f,1f)
                    drawLayer(layer)
                }
            }
        }
    }
}

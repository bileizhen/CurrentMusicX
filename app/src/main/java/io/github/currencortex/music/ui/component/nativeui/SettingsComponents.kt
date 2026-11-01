// SPDX-License-Identifier: GPL-3.0-only
// Native adaptations of upstream lib/ui/list.dart, metrics.dart, typography.dart and
// scaffold.dart @ 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7. See THIRD_PARTY_NOTICES.md.
package io.github.currencortex.music.ui.component.nativeui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.musicScrollPadding
import io.github.currencortex.music.ui.component.LocalMusicBottomInset
import io.github.currencortex.music.ui.component.liquid.rememberCombinedBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Limits the new preference controls to settings; music/library rows keep their own design. */
internal val LocalSettingsComponents = compositionLocalOf {false}
@Composable internal fun SettingsUiScope(content:@Composable ()->Unit) {
    CompositionLocalProvider(LocalSettingsComponents provides true,content=content)
}

private data class SettingsPicker(val anchor:Rect,val items:List<GlassMenuItem>)
private val LocalSettingsPicker=compositionLocalOf<((Rect,List<GlassMenuItem>)->Unit)?> {null}

/** Controls sample the background; the floating picker samples the composed page beneath it. */
@Composable internal fun SettingsPage(title:String,tag:String,onBack:(()->Unit)?=null,
    subtitle:String?=null,padding:PaddingValues=PaddingValues(16.dp,8.dp,16.dp,32.dp),content:LazyListScope.()->Unit) {
    SettingsUiScope {
        val backdrop=rememberGlassBackdrop()
        val contentBackdrop=rememberGlassBackdrop()
        val pickerBackdrop=if(backdrop!=null&&contentBackdrop!=null)
            rememberCombinedBackdrop(backdrop,contentBackdrop)else backdrop
        var picker by remember {mutableStateOf<SettingsPicker?>(null)}
        var pickerOpen by remember {mutableStateOf(false)}
        Box(Modifier.fillMaxSize().testTag("settings_page_$tag")) {
            Box(Modifier.matchParentSize().then(if(backdrop!=null)Modifier.layerBackdrop(backdrop)else Modifier)
                .background(MiuixTheme.colorScheme.background))
            CompositionLocalProvider(LocalGlassBackdrop provides backdrop,
                LocalSettingsPicker provides {anchor,items->
                    picker=SettingsPicker(anchor,items.map {item->item.copy(onClick={item.onClick();pickerOpen=false})})
                    pickerOpen=true
                }) {
                // The picker is a sibling of this capture. Controls inside it keep reading
                // the inert background, so neither capture can recursively sample itself.
                GlassMenu(pickerOpen,{pickerOpen=it},picker?.items.orEmpty(),picker?.anchor,pickerBackdrop,
                    GlassMenuTags("settings_choice_panel","settings_choice_dismiss","settings_choice"),
                    menuWidth=null,bottomInset=LocalMusicBottomInset.current,content={
                Box(Modifier.widthIn(max=760.dp).fillMaxWidth().align(Alignment.TopCenter).fillMaxHeight()) {
                    val direction=androidx.compose.ui.platform.LocalLayoutDirection.current
                    val top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()+SettingsToolbarHeight
                    LazyColumn(Modifier.fillMaxSize().testTag(tag)
                        .then(if(contentBackdrop!=null)Modifier.layerBackdrop(contentBackdrop)else Modifier),
                        contentPadding=musicScrollPadding(PaddingValues(padding.calculateStartPadding(direction),
                            top+padding.calculateTopPadding(),padding.calculateEndPadding(direction),padding.calculateBottomPadding())),
                        verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        subtitle?.let {item {SettingsHint(it)}}
                        content()
                    }
                    CompositionLocalProvider(LocalGlassBackdrop provides pickerBackdrop) {
                        SettingsFloatingHeader {SettingsTopBar(title,onBack,tag="${tag}_toolbar")}
                    }
                }
                },toolbar={})
            }
        }
    }
}

@Composable internal fun SettingsTopBar(title:String,onBack:(()->Unit)?=null,tag:String="settings_toolbar",
    backTag:String="navigate_back",titleModifier:Modifier=Modifier,
    actions:@Composable RowScope.()->Unit={}) {
    val dispatcher=LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val inherited=LocalGlassBackdrop.current
    val ownBackdrop=if(inherited==null)rememberGlassBackdrop()else null
    val backdrop=inherited?:ownBackdrop
    Box(Modifier.fillMaxWidth().statusBarsPadding()) {
    if(inherited==null)Box(Modifier.matchParentSize().then(if(ownBackdrop!=null)Modifier.layerBackdrop(ownBackdrop)else Modifier)
        .background(MiuixTheme.colorScheme.background))
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
    Row(Modifier.fillMaxWidth().height(SettingsToolbarHeight).padding(horizontal=16.dp).testTag(tag),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        GlassIconButton({if(onBack!=null)onBack()else dispatcher?.onBackPressed()},"返回",
            Modifier.testTag(backTag)) {Icon(MusicIcons.Back,null)}
        Text(title,Modifier.weight(1f).then(titleModifier),fontSize=17.sp,lineHeight=22.sp,fontWeight=FontWeight.SemiBold,
            maxLines=1,overflow=TextOverflow.Ellipsis)
        actions()
    }
    }
    }
}

@Composable internal fun SettingsSegments(labels:List<String>,selected:Int,onSelect:(Int)->Unit) {
    Row(Modifier.fillMaxWidth().semantics {selectableGroup()},horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed {index,label->SettingsChoiceButton(label,index==selected,{onSelect(index)},Modifier.weight(1f))}
    }
}

@Composable internal fun SettingsChoiceButton(label:String,selected:Boolean,onClick:()->Unit,modifier:Modifier=Modifier) {
    GlassActionButton(label,onClick,modifier.semantics {this.selected=selected;role=Role.RadioButton},primary=selected)
}

/** SettingTextCard selection fill, extended with a description for preview modes. */
@Composable internal fun SettingsSelectionCard(title:String,summary:String,selected:Boolean,onClick:()->Unit,modifier:Modifier=Modifier) {
    val colors=MiuixTheme.colorScheme
    val ink=if(selected)colors.onPrimary else colors.onSurface
    ElasticPress(modifier.selectable(selected,role=Role.RadioButton,onClick=onClick)) {
        Column(Modifier.clip(RoundedCornerShape(16.dp)).background(if(selected)colors.primary else colors.onSurface.copy(alpha=.04f))
            .padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(title,Modifier.weight(1f),fontSize=15.sp,lineHeight=20.sp,fontWeight=FontWeight.Medium,color=ink)
                if(selected)Icon(MusicIcons.Check,null,Modifier.size(18.dp),tint=ink)
            }
            Text(summary,fontSize=13.sp,lineHeight=18.sp,color=if(selected)ink.copy(alpha=.8f)else colors.onSurfaceVariantSummary)
        }
    }
}

@Composable internal fun SettingsGroup(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MiuixTheme.colorScheme.surfaceContainer),content=content)
}

/** Same section hierarchy as the appearance page: icon heading, one list, footer outside the card. */
@Composable internal fun SettingsSection(title:String,icon:ImageVector,modifier:Modifier=Modifier,
    footer:String?=null,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        SettingsInfoHeader(title,icon,horizontalInset=0.dp)
        SettingsGroup(content=content)
        footer?.takeIf {it.isNotBlank()}?.let {SettingsHint(it)}
    }
}

@Composable internal fun SettingsDivider(start:androidx.compose.ui.unit.Dp=16.dp) {
    Box(Modifier.fillMaxWidth().padding(start=start,end=16.dp).height(.5.dp)
        .background(MiuixTheme.colorScheme.onSurface.copy(alpha=.08f)))
}

@Composable internal fun SettingsHint(text:String,modifier:Modifier=Modifier,error:Boolean=false) {
    Text(text,modifier.padding(horizontal=16.dp,vertical=8.dp),fontSize=13.sp,lineHeight=18.sp,
        color=if(error)MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

@Composable internal fun SettingsSectionHeader(title:String,action:String?=null,onClick:()->Unit={}) {
    Row(Modifier.fillMaxWidth().padding(start=16.dp,end=8.dp,top=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,Modifier.weight(1f),fontSize=17.sp,lineHeight=22.sp,fontWeight=FontWeight.SemiBold)
        action?.let {GlassActionButton(it,onClick,fillWidth=false,minHeight=36.dp)}
    }
}

/** ListItem's 56dp minimum, 16dp horizontal inset and 17/13sp title/summary scale. */
@Composable internal fun SettingsPreference(title:String,summary:String?=null,modifier:Modifier=Modifier,
    enabled:Boolean=true,onClick:(()->Unit)?=null,role:Role=Role.Button,
    leading:(@Composable ()->Unit)?=null,trailing:(@Composable ()->Unit)?=null,
    footer:(@Composable ()->Unit)?=null) {
    val interactions=remember {MutableInteractionSource()}
    val ink=MiuixTheme.colorScheme.onSurface
    Column(modifier.fillMaxWidth().settingsPressFeedback(interactions).then(if(onClick!=null)Modifier.clickable(enabled=enabled,
        role=role,interactionSource=interactions,indication=null,onClick=onClick)else Modifier)) {
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).padding(horizontal=16.dp,vertical=10.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            leading?.invoke()
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(title,fontSize=17.sp,lineHeight=22.sp,color=ink.copy(alpha=if(enabled)1f else .38f))
                summary?.takeIf {it.isNotBlank()}?.let {Text(it,fontSize=13.sp,lineHeight=18.sp,
                    color=MiuixTheme.colorScheme.onSurfaceVariantSummary.let {it.copy(alpha=it.alpha*(if(enabled)1f else .45f))})}
            }
            trailing?.invoke()
        }
        footer?.let {Box(Modifier.fillMaxWidth().padding(start=16.dp,end=16.dp,bottom=12.dp)){it()}}
    }
}

@Composable internal fun SettingsToggle(title:String,checked:Boolean,onCheckedChange:(Boolean)->Unit,
    modifier:Modifier=Modifier,summary:String?=null,enabled:Boolean=true,leading:(@Composable ()->Unit)?=null) {
    SettingsPreference(title,summary,modifier.semantics {toggleableState=ToggleableState(checked)},enabled,
        onClick={onCheckedChange(!checked)},role=Role.Switch,leading=leading,
        trailing={GlassSwitch(checked,onCheckedChange,modifier=Modifier.graphicsLayer {alpha=if(enabled)1f else .45f}.clearAndSetSemantics{},enabled=enabled)})
}

/** Adapter for preference pages with a footer such as a slider or diagnostic details. */
@Composable internal fun SettingsComponent(title:String?,summary:String?=null,modifier:Modifier=Modifier,
    onClick:(()->Unit)?=null,startAction:(@Composable ()->Unit)?=null,
    endActions:(@Composable RowScope.()->Unit)?=null,bottomAction:(@Composable ()->Unit)?=null,enabled:Boolean=true) {
    SettingsPreference(title.orEmpty(),summary,modifier,enabled,onClick,leading=startAction,
        trailing=endActions?.let {action->{Row(verticalAlignment=Alignment.CenterVertically,content=action)}},footer=bottomAction)
}

@Composable internal fun SettingsChoiceRow(title:String,selected:Boolean,onClick:()->Unit,
    modifier:Modifier=Modifier,summary:String?=null,enabled:Boolean=true,leading:(@Composable ()->Unit)?=null) {
    SettingsPreference(title,summary,modifier.semantics {this.selected=selected},enabled,onClick,Role.RadioButton,leading,
        trailing={SettingsSelectionCheck(selected)})
}

@Immutable internal data class SettingsOption(val id:String,val label:String,val selected:Boolean,
    val summary:String?=null,val onSelect:()->Unit)

/** Options expand from the value; selecting an entry saves once and closes the shared glass menu. */
@Composable internal fun SettingsOptionsRow(title:String,value:String,options:List<SettingsOption>,
    modifier:Modifier=Modifier,summary:String?=null,enabled:Boolean=true) {
    val open=LocalSettingsPicker.current
    var rowBounds by remember {mutableStateOf<Rect?>(null)}
    var valueBounds by remember {mutableStateOf<Rect?>(null)}
    GlassMenuAnchor(enabled,onOpen={val anchor=valueBounds?:rowBounds
            if(anchor!=null)open?.invoke(anchor,options.map {option->GlassMenuItem(option.id,option.label,
                subtitle=option.summary,checked=option.selected,keepOpen=true,onClick=option.onSelect)})}) {openValue,target->
    SettingsPreference(title,summary,modifier.onGloballyPositioned {rowBounds=it.boundsInWindow()},enabled,
        onClick=openValue,
        trailing={Row(target.widthIn(max=196.dp).heightIn(min=40.dp)
            .onGloballyPositioned {valueBounds=it.boundsInWindow()}.padding(horizontal=8.dp),
            horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(value,Modifier.weight(1f,false).widthIn(max=160.dp),fontSize=17.sp,lineHeight=22.sp,maxLines=1,overflow=TextOverflow.Ellipsis,
                color=MiuixTheme.colorScheme.onSurfaceVariantSummary)
            val ink=MiuixTheme.colorScheme.onSurfaceVariantSummary
            Canvas(Modifier.size(16.dp)) {
                val stroke=1.2.dp.toPx()
                drawLine(ink,Offset(size.width*.28f,size.height*.35f),Offset(size.width*.5f,size.height*.15f),stroke,StrokeCap.Round)
                drawLine(ink,Offset(size.width*.5f,size.height*.15f),Offset(size.width*.72f,size.height*.35f),stroke,StrokeCap.Round)
                drawLine(ink,Offset(size.width*.28f,size.height*.65f),Offset(size.width*.5f,size.height*.85f),stroke,StrokeCap.Round)
                drawLine(ink,Offset(size.width*.5f,size.height*.85f),Offset(size.width*.72f,size.height*.65f),stroke,StrokeCap.Round)
            }
        }})
    }
}

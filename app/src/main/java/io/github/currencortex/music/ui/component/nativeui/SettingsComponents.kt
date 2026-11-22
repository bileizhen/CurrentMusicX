// SPDX-License-Identifier: GPL-3.0-only
// Native adaptations of upstream lib/ui/list.dart, metrics.dart, typography.dart and
// scaffold.dart @ 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7. See THIRD_PARTY_NOTICES.md.
package io.github.currencortex.music.ui.component.nativeui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.musicScrollPadding
import io.github.currencortex.music.ui.component.springChainSettingsItems
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.glowScrollOffsetPx
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.TopAppBarGlow
import io.github.suqi8.coui.kmp.blur.isRuntimeShaderSupported
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.ArrowRight
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.preference.PreferenceCategoryMarginStartType
import io.github.suqi8.coui.kmp.preference.OverlaySpinnerPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.effect.rememberSpringChainState
import io.github.suqi8.coui.kmp.effect.springChain
import io.github.suqi8.coui.kmp.squircle.SquircleDefaults
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.utils.overScrollVertical

/** Limits the new preference controls to settings; music/library rows keep their own design. */
internal val LocalSettingsComponents = compositionLocalOf {false}
@Composable internal fun SettingsUiScope(content:@Composable ()->Unit) {
    CompositionLocalProvider(LocalSettingsComponents provides true,content=content)
}

/** Common settings subpage shell with COUI's native top bar and preference list. */
@Composable internal fun SettingsPage(title:String,tag:String,onBack:(()->Unit)?=null,
    subtitle:String?=null,padding:PaddingValues=PaddingValues(16.dp,8.dp,16.dp,32.dp),
    largeTitle:Boolean=false,content:LazyListScope.()->Unit) {
    SettingsUiScope {
        val backdrop=rememberGlassBackdrop()
        Box(Modifier.fillMaxSize().testTag("settings_page_$tag")) {
            // This layer records only the flat background, so content may sample it safely.
            // The scrolling list is recorded separately for the top bar glow; sampling the layer
            // that records the list from inside the list makes hwui's RenderNode tree reference
            // itself, and RenderThread overflows its stack while preparing that tree.
            Box(Modifier.matchParentSize().then(if(backdrop!=null)Modifier.layerBackdrop(backdrop)else Modifier)
                .background(COUITheme.colorScheme.background))
            CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter) {
                    SettingsNativePage(
                        title=title,
                        tag=tag,
                        onBack=onBack,
                        subtitle=subtitle,
                        padding=padding,
                        modifier=Modifier.widthIn(max=760.dp).fillMaxWidth().fillMaxHeight(),
                        largeTitle=largeTitle,
                        content=content,
                    )
                }
            }
        }
    }
}

/**
 * The top-bar sample, recorded the way COUI's page scaffold records it: the window fill
 * under the page, then the content. Sampling a transparent layer makes the downscaled
 * glow blur step into hard blocks behind high-contrast rows.
 */
@Composable private fun rememberGlowBackdrop():LayerBackdrop? {
    if(android.os.Build.VERSION.SDK_INT<33||!LocalView.current.isHardwareAccelerated||
        !isRuntimeShaderSupported())return null
    val background=COUITheme.colorScheme.background
    return rememberLayerBackdrop {drawRect(background);drawContent()}
}

@Composable internal fun SettingsNativePage(title:String,tag:String,onBack:(()->Unit)?=null,
    subtitle:String?=null,padding:PaddingValues=PaddingValues(16.dp,8.dp,16.dp,32.dp),
    modifier:Modifier=Modifier,actions:@Composable RowScope.()->Unit={},verticalSpacing:androidx.compose.ui.unit.Dp=16.dp,
    backTag:String="navigate_back",state:LazyListState?=null,
    largeTitle:Boolean=false,contentModifier:Modifier=Modifier,content:LazyListScope.()->Unit) {
    val dispatcher=LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val direction=LocalLayoutDirection.current
    val defaultState=rememberLazyListState()
    val listState=state ?: defaultState
    val springChainState=rememberSpringChainState(listState)
    // Records the list itself for the top bar glow. It is deliberately not exposed through
    // LocalGlassBackdrop: this layer contains every row, including its glass controls.
    // The pinned small bar needs it too, otherwise scrolled rows disappear under an opaque band.
    // COUI's page scaffold records the window fill under the page for this sample; without it
    // the downscaled glow blur steps visibly where the list is transparent.
    val glowBackdrop=rememberGlowBackdrop()
    val scrollBehavior=COUIScrollBehavior()
    SettingsUiScope {
        Scaffold(
            modifier=modifier.fillMaxSize().testTag("settings_page_$tag"),
            containerColor=COUITheme.colorScheme.background,
            contentWindowInsets=WindowInsets(0,0,0,0),
            topBar={
                val navigationIcon: @Composable () -> Unit = {
                    IconButton(
                        onClick={if(onBack!=null)onBack()else dispatcher?.onBackPressed()},
                        modifier=Modifier.testTag(backTag),
                        minWidth=44.dp,
                        minHeight=44.dp,
                        cornerRadius=22.dp,
                        // No material: the bar is a transparent glow, and a material button without
                        // a backdrop falls back to a flat disc that reads as a defect.
                        material=false,
                    ) { Icon(COUIIcons.Back,contentDescription="返回") }
                }
                // A transparent bar plus frost is COUI 17's material bar; without a backdrop
                // (no runtime shader) keep the opaque bar as the fallback.
                val glow: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? =
                    if (glowBackdrop != null) {{
                        TopAppBarGlow(
                            backdrop=glowBackdrop,
                            scrollOffsetPx=scrollBehavior.glowScrollOffsetPx(),
                            isDark=COUITheme.isDark,
                            baseColor=COUITheme.colorScheme.background,
                            enabled=true,
                        )
                    }} else null
                val barColor=if (glowBackdrop != null) Color.Transparent else COUITheme.colorScheme.background
                if (largeTitle) {
                    TopAppBar(
                        title=title,
                        modifier=Modifier.testTag("${tag}_toolbar"),
                        navigationIcon=navigationIcon,
                        actions=actions,
                        color=barColor,
                        scrollBehavior=scrollBehavior,
                        glow=glow,
                        showDivider=false,
                    )
                } else {
                    SmallTopAppBar(
                        title=title,
                        modifier=Modifier.testTag("${tag}_toolbar"),
                        navigationIcon=navigationIcon,
                        actions=actions,
                        color=barColor,
                        scrollBehavior=scrollBehavior,
                        glow=glow,
                        showDivider=false,
                    )
                }
            },
        ) { inner ->
            LazyColumn(
                state=listState,
                modifier=Modifier.fillMaxSize().then(contentModifier).testTag(tag).overScrollVertical()
                    .springChain(springChainState)
                    .then(if(glowBackdrop!=null)Modifier.layerBackdrop(glowBackdrop)else Modifier)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding=musicScrollPadding(PaddingValues(
                    start=padding.calculateStartPadding(direction),
                    top=inner.calculateTopPadding()+padding.calculateTopPadding(),
                    end=padding.calculateEndPadding(direction),
                    bottom=inner.calculateBottomPadding()+padding.calculateBottomPadding(),
                )),
                verticalArrangement=Arrangement.spacedBy(verticalSpacing),
            ) {
                subtitle?.let {item {SettingsHint(it)}}
                springChainSettingsItems(springChainState) { content() }
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
        .background(COUITheme.colorScheme.background))
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
    val colors=COUITheme.colorScheme
    val ink=if(selected)colors.labelOnColor else colors.labelPrimary
    ElasticPress(modifier.selectable(selected,role=Role.RadioButton,onClick=onClick)) {
        Column(Modifier.squircleSurface(
            color = if(selected)colors.primary else colors.labelPrimary.copy(alpha=.04f),
            cornerRadius = 16.dp,
            weight = SquircleDefaults.Weight,
        )
            .padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(title,Modifier.weight(1f),fontSize=15.sp,lineHeight=20.sp,fontWeight=FontWeight.Medium,color=ink)
                if(selected)Icon(MusicIcons.Check,null,Modifier.size(18.dp),tint=ink)
            }
            Text(summary,fontSize=13.sp,lineHeight=18.sp,color=if(selected)ink.copy(alpha=.8f)else colors.labelSecondaryVariant)
        }
    }
}

@Composable internal fun SettingsGroup(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.fillMaxWidth().squircleSurface(
        color = COUITheme.colorScheme.cardBackground,
        cornerRadius = 24.dp,
        weight = SquircleDefaults.Weight,
    )) {
        Column(content = content)
    }
}

/** Same section hierarchy as the appearance page: icon heading, one list, footer outside the card. */
@Composable internal fun SettingsSection(title:String,icon:ImageVector,modifier:Modifier=Modifier,
    footer:String?=null,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        io.github.suqi8.coui.kmp.preference.PreferenceCategory(
            title=title,
            marginStartType=PreferenceCategoryMarginStartType.Small,
        )
        SettingsGroup(content=content)
        footer?.takeIf {it.isNotBlank()}?.let {SettingsHint(it)}
    }
}

@Composable internal fun SettingsDivider(start:androidx.compose.ui.unit.Dp=16.dp) {
    HorizontalDivider(modifier = Modifier.padding(start = start, end = 16.dp))
}

@Composable internal fun SettingsHint(text:String,modifier:Modifier=Modifier,error:Boolean=false) {
    Text(text,modifier.padding(horizontal=16.dp,vertical=8.dp),fontSize=13.sp,lineHeight=18.sp,
        color=if(error)COUITheme.colorScheme.error else COUITheme.colorScheme.labelSecondaryVariant)
}

@Composable internal fun SettingsSectionHeader(title:String,action:String?=null,onClick:()->Unit={}) {
    io.github.suqi8.coui.kmp.preference.PreferenceCategory(
        title = title,
        endWidget = action?.let { label ->
            {
                io.github.suqi8.coui.kmp.preference.PreferenceCategoryDefaults.TextButton(label, onClick)
            }
        },
    )
}

/** ListItem's 56dp minimum, 16dp horizontal inset and 17/13sp title/summary scale. */
@Composable internal fun SettingsPreference(title:String,summary:String?=null,modifier:Modifier=Modifier,
    enabled:Boolean=true,onClick:(()->Unit)?=null,role:Role=Role.Button,
    leading:(@Composable ()->Unit)?=null,trailing:(@Composable ()->Unit)?=null,
    footer:(@Composable ()->Unit)?=null) {
    val interactions=remember {MutableInteractionSource()}
    val ink=COUITheme.colorScheme.labelPrimary
    Column(modifier.fillMaxWidth().settingsPressFeedback(interactions).then(if(onClick!=null)Modifier.clickable(enabled=enabled,
        role=role,interactionSource=interactions,indication=null,onClick=onClick)else Modifier)) {
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).padding(horizontal=16.dp,vertical=10.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            leading?.invoke()
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(title,fontSize=17.sp,lineHeight=22.sp,color=ink.copy(alpha=if(enabled)1f else .38f))
                summary?.takeIf {it.isNotBlank()}?.let {Text(it,fontSize=13.sp,lineHeight=18.sp,
                    color=COUITheme.colorScheme.labelSecondaryVariant.let {it.copy(alpha=it.alpha*(if(enabled)1f else .45f))})}
            }
            if (trailing != null) trailing()
            else if (onClick != null && role == Role.Button) Icon(
                COUIIcons.Basic.ArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = COUITheme.colorScheme.labelTertiary,
            )
        }
        footer?.let {Box(Modifier.fillMaxWidth().padding(start=16.dp,end=16.dp,bottom=12.dp)){it()}}
    }
}

@Composable internal fun SettingsToggle(title:String,checked:Boolean,onCheckedChange:(Boolean)->Unit,
    modifier:Modifier=Modifier,summary:String?=null,enabled:Boolean=true,leading:(@Composable ()->Unit)?=null) {
    SwitchPreference(
        checked = checked,
        onCheckedChange = onCheckedChange,
        title = title,
        modifier = modifier,
        summary = summary,
        startAction = leading,
        enabled = enabled,
    )
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
    val summary:String?=null,val leadingContent:(@Composable ()->Unit)?=null,val onSelect:()->Unit)

/** Options open in COUI's native spinner popup; selecting an entry saves once. */
@Composable internal fun SettingsOptionsRow(title:String,value:String,options:List<SettingsOption>,
    modifier:Modifier=Modifier,summary:String?=null,enabled:Boolean=true,valueLeading:(@Composable ()->Unit)?=null) {
    val selectedIndex=options.indexOfFirst {it.selected}.takeIf {it>=0}?:0
    OverlaySpinnerPreference(
        items=options.map {option->
            DropdownItem(
                text=option.label,
                summary=option.summary,
                icon=option.leadingContent?.let {content->{ _:Modifier -> content() }},
            )
        },
        selectedIndex=selectedIndex,
        title=title,
        modifier=modifier,
        summary=summary,
        enabled=enabled,
        startAction=valueLeading,
        onSelectedIndexChange={index->options.getOrNull(index)?.onSelect()},
    )
}

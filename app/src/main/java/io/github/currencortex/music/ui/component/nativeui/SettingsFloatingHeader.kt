// SPDX-License-Identifier: GPL-3.0-only
// FloatingHeader scrim adapted from upstream lib/ui/sheet_header.dart and scaffold.dart
// @ 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7. The original is a scrim, not a blur filter.
package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.ui.component.LocalRootTabSwipe
import io.github.currencortex.music.ui.component.RootMotionItem
import io.github.currencortex.music.ui.component.liquid.rememberCombinedBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val SettingsToolbarHeight=72.dp

/** CommonScaffold's unpushed pageTitle bar: 24/30sp, 600 weight, 72dp tall. */
@Composable internal fun SettingsRootTopBar(title:String,modifier:Modifier=Modifier,
    titleContent:(@Composable ()->Unit)?=null,actions:@Composable RowScope.()->Unit={}) {
    Box(modifier.fillMaxWidth().statusBarsPadding()) {
        RootMotionItem {
        Row(Modifier.fillMaxWidth().height(SettingsToolbarHeight).padding(horizontal=16.dp)
            .then(LocalRootTabSwipe.current),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(titleContent!=null)Box(Modifier.weight(1f)){titleContent()}
            else Text(title,Modifier.weight(1f).semantics {heading()},fontSize=24.sp,lineHeight=30.sp,
                fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis,
                color=MiuixTheme.colorScheme.onSurface)
            actions()
        }
        }
    }
}

/** A sibling of the captured body, never part of its own backdrop. Its visual overhang is
 * pointer-transparent; the status/toolbar area blocks taps through to scrolled preferences. */
@Composable internal fun SettingsFloatingHeader(modifier:Modifier=Modifier,
    background:Color=MiuixTheme.colorScheme.background,
    toolbarHeight:Dp=SettingsToolbarHeight,
    opacity:()->Float={ .9f },
    content:@Composable ()->Unit) {
    val status=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(modifier.fillMaxWidth().testTag("settings_floating_header")
        .pointerInput(Unit) {
            // This node supplies the header's hit region. Child buttons still own their taps.
            awaitEachGesture {
                awaitFirstDown(false,PointerEventPass.Final)
                do {val event=awaitPointerEvent(PointerEventPass.Final)}while(event.changes.any {it.pressed})
            }
        }) {
        Box(Modifier.matchParentSize().layout {measurable,constraints->
            val height=constraints.maxHeight+16.dp.roundToPx()
            val child=measurable.measure(constraints.copy(minHeight=height,maxHeight=height))
            layout(constraints.maxWidth,constraints.maxHeight){child.place(0,0)}
        }.headerScrim(background,status,toolbarHeight,opacity))
        content()
    }
}

/** Host for settings screens with their own list/content rather than SettingsPage's picker. */
@Composable internal fun SettingsScrollLayout(modifier:Modifier=Modifier,
    background:Color=MiuixTheme.colorScheme.background,topBar:@Composable ()->Unit,
    toolbarHeight:Dp=SettingsToolbarHeight,
    footer:@Composable androidx.compose.foundation.layout.BoxScope.()->Unit={},
    glassOverContent:Boolean=true,
    content:@Composable (topInset:Dp)->Unit) {
    val base=rememberGlassBackdrop()
    val body=if(glassOverContent) rememberGlassBackdrop() else null
    val combined=if(base!=null&&body!=null)rememberCombinedBackdrop(base,body)else base
    val inset=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()+toolbarHeight
    Box(modifier) {
        Box(Modifier.matchParentSize().then(if(base!=null)Modifier.layerBackdrop(base)else Modifier).background(background))
        // Rows only sample the flat fill. The floating bars below still sample the real list.
        CompositionLocalProvider(LocalGlassBackdrop provides rememberSolidGlassBackdrop(base, background)) {
            Box(Modifier.fillMaxSize().then(if(body!=null)Modifier.layerBackdrop(body)else Modifier)){content(inset)}
        }
        CompositionLocalProvider(LocalGlassBackdrop provides combined) {
            SettingsFloatingHeader(background=background,toolbarHeight=toolbarHeight,content=topBar)
            footer()
        }
    }
}

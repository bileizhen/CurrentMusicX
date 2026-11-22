package io.github.currencortex.music.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.background
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.ui.component.nativeui.LocalPageGlassScene
import io.github.currencortex.music.ui.component.nativeui.PageGlassScene
import io.github.suqi8.coui.kmp.theme.COUITheme

/** Scoped to settings pages, so unrelated navigation keeps its existing transition. */
internal val LocalPageContainerSource = compositionLocalOf<((PageContainerOrigin, () -> Unit) -> Unit)?> { null }

private class SourceCoordinates { var value: LayoutCoordinates? = null }
internal class PageNavigationSource(val modifier: Modifier, val open: (() -> Unit) -> Unit)

@Composable internal fun PageNavigationIcon(icon:ImageVector) {
    io.github.suqi8.coui.kmp.basic.Icon(icon,null,Modifier.padding(end=12.dp).size(24.dp))
}

@Composable internal fun rememberPageNavigationSource(title: String, summary: String = "",
    icon: ImageVector? = null, playlistMotion: PlaylistContainerMotion? = null): PageNavigationSource {
    val coordinates=remember {SourceCoordinates()}
    val capture=LocalPageContainerSource.current
    val color=COUITheme.colorScheme.cardBackground
    val currentColor=rememberUpdatedState(color)
    // Keep the card's background and subtitle in the container crossfade too. Its
    // shared cover/title suppress their own drawing while the overlay owns them.
    val layer=if(capture!=null)rememberGraphicsLayer()else null
    val modifier=Modifier.onGloballyPositioned {coordinates.value=it}.then(if(layer==null)Modifier else Modifier.drawWithContent {
        layer.record {this@drawWithContent.drawContent()}
        drawLayer(layer)
    })
    return PageNavigationSource(modifier) { action ->
        val source=coordinates.value?.takeIf {it.isAttached}
        if(source!=null&&capture!=null)capture(PageContainerOrigin(source,color,title,summary,icon,currentColor,layer,playlistMotion),action)
        else action()
    }
}

@Composable internal fun PageNavigationPreference(title:String,summary:String?=null,
    modifier:Modifier=Modifier,icon:ImageVector?=null,onClick:()->Unit) {
    val source=rememberPageNavigationSource(title,summary.orEmpty(),icon)
    if(io.github.currencortex.music.ui.component.nativeui.LocalSettingsComponents.current) {
        io.github.currencortex.music.ui.component.nativeui.SettingsPreference(title,summary,modifier.then(source.modifier),
            onClick={source.open(onClick)},leading={icon?.let {io.github.suqi8.coui.kmp.basic.Icon(it,null,Modifier.size(24.dp))}},
            trailing={io.github.suqi8.coui.kmp.basic.Icon(MusicIcons.Forward,null,Modifier.size(16.dp))})
        return
    }
    io.github.suqi8.coui.kmp.preference.ArrowPreference(title=title,summary=summary,
        startAction={icon?.let {PageNavigationIcon(it)}},
        modifier=modifier.then(source.modifier),onClick={source.open(onClick)})
}

@Composable
internal fun MusicNavigationPage(
    route: String,
    background: Color,
    active: Boolean,
    bottomInset: Dp,
    moving: Boolean,
    covered: Boolean,
    entering: Boolean,
    content: @Composable () -> Unit,
) {
    val movingNow by rememberUpdatedState(moving)
    val enteringNow by rememberUpdatedState(entering)
    val glass = remember { PageGlassScene({ movingNow }, { enteringNow }) }
    Box(Modifier.fillMaxSize().then(pageSceneBlur(covered)).background(background)
        .onGloballyPositioned { glass.origin = it }.testTag("music_scene_$route")) {
        CompositionLocalProvider(
            LocalMusicBottomInset provides bottomInset,
            LocalMusicPageActive provides active,
            LocalPageGlassScene provides glass,
        ) { content() }
    }
}

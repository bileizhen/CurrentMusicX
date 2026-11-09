package io.github.currencortex.music.ui.component

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import io.github.currencortex.music.ui.component.nativeui.LocalPageGlassScene
import io.github.currencortex.music.ui.component.nativeui.PageGlassScene
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Scoped to settings pages, so unrelated navigation keeps its existing transition. */
internal val LocalPageContainerSource = compositionLocalOf<((PageContainerOrigin, () -> Unit) -> Unit)?> { null }

private class SourceCoordinates { var value: LayoutCoordinates? = null }
internal class PageNavigationSource(val modifier: Modifier, val open: (() -> Unit) -> Unit)

@Composable internal fun PageNavigationIcon(icon:ImageVector) {
    top.yukonga.miuix.kmp.basic.Icon(icon,null,Modifier.padding(end=12.dp).size(24.dp))
}

@Composable internal fun rememberPageNavigationSource(title: String, summary: String = "",
    icon: ImageVector? = null, playlistMotion: PlaylistContainerMotion? = null): PageNavigationSource {
    val coordinates=remember {SourceCoordinates()}
    val capture=LocalPageContainerSource.current
    val color=MiuixTheme.colorScheme.surfaceContainer
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
            onClick={source.open(onClick)},leading={icon?.let {top.yukonga.miuix.kmp.basic.Icon(it,null,Modifier.size(24.dp))}},
            trailing={top.yukonga.miuix.kmp.basic.Icon(MusicIcons.Forward,null,Modifier.size(16.dp))})
        return
    }
    top.yukonga.miuix.kmp.preference.ArrowPreference(title=title,summary=summary,
        startAction={icon?.let {PageNavigationIcon(it)}},
        modifier=modifier.then(source.modifier),onClick={source.open(onClick)})
}

@Stable internal class PageContainerNavigation {
    val origins=mutableStateMapOf<String,PageContainerOrigin>()
    var closingRoute by mutableStateOf<String?>(null)
        private set
    private var pendingOrigin:PageContainerOrigin?=null
    var onClosed:(String)->Unit = {}

    fun from(origin:PageContainerOrigin,action:()->Unit) {
        pendingOrigin=origin
        try {action()} finally {pendingOrigin=null}
    }
    fun record(route:String) {pendingOrigin?.let {origins[route]=it}}
    fun close(route:String):Boolean {
        if(route !in origins)return false
        closingRoute=route
        return true
    }
    fun presented(route:String,presented:Boolean) {
        if(!presented&&closingRoute==route) {
            closingRoute=null
            origins.remove(route)
            onClosed(route)
        }
    }
}

/** One scene key retains the source and destination entries throughout the container motion.
 * The actual back-stack pop happens after the return finishes, preserving entry lifecycles,
 * saveable state and the source list's scroll position. Normal routes still change scene keys.
 */
internal class PageContainerSceneStrategy(private val navigation:PageContainerNavigation,
    private val sourceRoutes:Set<String>):SceneStrategy<String> {
    val blur = PageSceneBlur()

    override fun SceneStrategyScope<String>.calculateScene(entries:List<NavEntry<String>>):Scene<String> {
        val base=entries.indexOfLast {it.contentKey.toString() !in navigation.origins}.coerceAtLeast(0)
        return PageContainerScene(entries[base].contentKey,entries.drop(base),entries.dropLast(1),navigation,sourceRoutes,base,blur)
    }
}

private data class PageContainerScene(override val key:Any,override val entries:List<NavEntry<String>>,
    override val previousEntries:List<NavEntry<String>>,val navigation:PageContainerNavigation,
    val sourceRoutes:Set<String>, val depth: Int, val blur: PageSceneBlur):Scene<String> {
    override val metadata get()=entries.first().metadata
    override val content:@Composable ()->Unit = {
        Box(Modifier.fillMaxSize().then(blur.layer(depth))) {
            entries.forEachIndexed {index,entry->key(entry.contentKey) {
                val route=entry.contentKey.toString()
                val capture=if(route in sourceRoutes||route in navigation.origins)navigation::from else null
                @Composable fun page() {
                    val motion = LocalNavAnimatedContentScope.current.transition
                    val glass = remember(motion) { PageGlassScene(motion) }
                    Box(Modifier.fillMaxSize().onGloballyPositioned { glass.origin = it }
                        .then(if(index<entries.lastIndex)Modifier.clearAndSetSemantics{}else Modifier)) {
                        CompositionLocalProvider(LocalPageContainerSource provides capture,
                            LocalPageGlassScene provides glass) {entry.Content()}
                    }
                }
                if(index==0)page()
                else PageContainerTransform(navigation.closingRoute!=route,navigation.origins[route],
                    onPresentedChanged={navigation.presented(route,it)},tag="page_container_$route") {
                    // Like the update page, commit back into the container's own return
                    // animation. Do not let NavDisplay seek to a different entry set sharing
                    // this scene key. Descendant dialogs/back handlers keep precedence.
                    BackHandler(enabled=index==entries.lastIndex&&navigation.closingRoute==null) {navigation.close(route)}
                    page()
                }
            }}
        }
    }
}

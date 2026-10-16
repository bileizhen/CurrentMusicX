package io.github.currencortex.music.feature.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.ui.component.LocalMusicPageActive
import io.github.currencortex.music.ui.component.MusicDialog
import io.github.currencortex.music.ui.component.MusicTextAction
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import top.yukonga.miuix.kmp.basic.Text

/** All master details stay inside Compose; returning preserves the gallery's exact scroll/pager state. */
@Composable fun SoundEffectsScreen(vm: SoundEffectsViewModel, onBack: () -> Unit, onEqualizer: () -> Unit) {
    var route by rememberSaveable { mutableStateOf("") }
    val holder = rememberSaveableStateHolder()
    val active = LocalMusicPageActive.current
    val playback by vm.playback.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val account by vm.originalPageAccount.collectAsStateWithLifecycle()
    val mood by vm.originalMood.collectAsStateWithLifecycle()
    val master by vm.originalAiMaster.collectAsStateWithLifecycle()
    val speaker by vm.originalSpeaker.collectAsStateWithLifecycle()
    val audition by vm.audition.collectAsStateWithLifecycle()
    val metadata by vm.masterDetail.collectAsStateWithLifecycle()
    val metadataSongId by vm.masterDetailSongId.collectAsStateWithLifecycle()
    val metadataLoading by vm.masterDetailLoading.collectAsStateWithLifecycle()
    val metadataError by vm.masterDetailError.collectAsStateWithLifecycle()
    val song = playback.song ?: queue.current
    fun leaveDetail() { vm.stopPreview(); route = "" }
    BackHandler(enabled = active && route.isNotEmpty()) { leaveDetail() }
    LaunchedEffect(route, song?.id, account.revision) {
        if (route == SoundEffectKind.AI_MASTER.name) vm.loadMasterDetail()
    }
    LaunchedEffect(active) { if (!active) vm.stopPreview() }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, vm) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) vm.stopPreview() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); vm.stopPreview() }
    }
    val window = LocalActivity.current?.window
    DisposableEffect(window, route.isNotEmpty(), active) {
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val status = controller?.isAppearanceLightStatusBars
        val navigation = controller?.isAppearanceLightNavigationBars
        if (route.isNotEmpty() && active) {
            controller?.isAppearanceLightStatusBars = false
            controller?.isAppearanceLightNavigationBars = false
        }
        onDispose {
            if (status != null) controller?.isAppearanceLightStatusBars = status
            if (navigation != null) controller?.isAppearanceLightNavigationBars = navigation
        }
    }
    AnimatedContent(route, modifier = Modifier.fillMaxSize(), label = "native-effects-detail",
        transitionSpec = {
            (fadeIn(tween(220)) + slideInHorizontally(tween(300)) { if (targetState.isEmpty()) -it / 6 else it / 5 }) togetherWith
                (fadeOut(tween(180)) + slideOutHorizontally(tween(300)) { if (targetState.isEmpty()) it / 5 else -it / 6 })
        }) { page ->
        holder.SaveableStateProvider(page) {
            CompositionLocalProvider(LocalMusicPageActive provides (active && page == route)) {
                when (page) {
                    SoundEffectKind.MOOD.name -> MoodEffectsDetail(mood, song, playback.playing,
                        onBack = ::leaveDetail,
                        onToggle = { if (mood.requestedEnabled) vm.turnOff() else vm.originalMoodEnabled(true) },
                        onPlayback = vm::togglePlayback)
                    SoundEffectKind.SPEAKER.name -> SpeakerEffectsDetail(speaker, song, onBack = ::leaveDetail,
                        onEnable = { scene -> vm.stopPreview(); vm.speakerScene(scene); vm.originalSpeakerEnabled(true) },
                        onDisable = vm::turnOff, previewing = audition.scene != null && audition.active,
                        previewScene = audition.scene, onPreviewStart = vm::previewSpeaker, onPreviewStop = vm::stopPreview)
                    SoundEffectKind.AI_MASTER.name -> {
                        val data = metadata.takeIf { metadataSongId == song?.id }
                        val display = master.copy(baseName = master.baseName.takeIf { master.songId == song?.id } ?: data?.baseName,
                            longText = master.longText.takeIf { master.songId == song?.id } ?: data?.longText,
                            description = master.description.takeIf { master.songId == song?.id } ?: data?.description,
                            loading = master.loading || metadataLoading,
                            message = if (!master.requestedEnabled && metadataError != null) metadataError!! else master.message)
                        AiMasterEffectsDetail(display, song, playback.playing, onBack = ::leaveDetail,
                            onToggle = { if (master.requestedEnabled) vm.turnOff() else vm.originalAiMasterEnabled(true) },
                            onPlayback = vm::togglePlayback, onSaveDiy = vm::saveMasterDiy,
                            onPreview = vm::previewMaster,
                            previewing = audition.scene == null && audition.active,
                            previewProcessed = audition.processed, onPreviewStop = vm::stopPreview)
                    }
                    else -> SoundEffectsMainScreen(vm, onBack, onEqualizer,
                        onDetails = { kind -> vm.stopPreview(); route = kind.name })
                }
            }
        }
    }
    audition.error?.let { message ->
        MusicDialog(title = "试听提示", onDismiss = vm::stopPreview,
            content = { Text(message) }, footer = { MusicTextAction("知道了", vm::stopPreview) })
    }
}

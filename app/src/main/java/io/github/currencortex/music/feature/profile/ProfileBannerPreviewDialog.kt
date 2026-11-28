package io.github.currencortex.music.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import io.github.currencortex.music.R
import io.github.currencortex.music.core.media.BannerVideoCache
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.VolumeOff
import io.github.suqi8.coui.kmp.icon.extended.VolumeUp
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode
import io.github.suqi8.coui.kmp.theme.ThemeController
import okhttp3.OkHttpClient

@Composable
internal fun ProfileBannerPreviewDialog(
    source: String,
    isVideo: Boolean,
    onDismiss: () -> Unit,
    httpClient: OkHttpClient? = null,
    videoCache: BannerVideoCache? = null,
    onChange: (() -> Unit)? = null,
) {
    var attempt by remember(source) { mutableIntStateOf(0) }
    var muted by remember(source, attempt) { mutableStateOf(true) }
    var hasAudio by remember(source, attempt) { mutableStateOf(false) }
    var loading by remember(source, attempt) { mutableStateOf(true) }
    var failed by remember(source, attempt) { mutableStateOf(false) }
    val title = stringResource(R.string.profile_banner_preview)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.let {
                WindowCompat.getInsetsController(it, it.decorView).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
        COUITheme(controller = remember { ThemeController(colorSchemeMode = ColorSchemeMode.Dark, isDark = true) }) {
            Column(
                Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()
                    .testTag("profile_banner_preview").semantics { paneTitle = title },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp).testTag("profile_banner_preview_close")) {
                        Icon(MusicIcons.Close, stringResource(R.string.profile_banner_preview_close))
                    }
                    if (isVideo && hasAudio && !failed) {
                        IconButton(
                            onClick = { muted = !muted },
                            modifier = Modifier.size(48.dp).testTag("profile_banner_preview_sound"),
                        ) {
                            Icon(
                                if (muted) COUIIcons.VolumeOff else COUIIcons.VolumeUp,
                                stringResource(if (muted) R.string.profile_banner_preview_unmute else R.string.profile_banner_preview_mute),
                            )
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    if (!failed) key(source, attempt) {
                        if (isVideo) {
                            BannerVideoSurface(
                                source = source,
                                httpClient = httpClient,
                                active = true,
                                modifier = Modifier.matchParentSize().testTag("profile_banner_preview_video"),
                                // Retry from the source if cached bytes couldn't be decoded.
                                videoCache = if (attempt == 0) videoCache else null,
                                fit = true,
                                audio = if (muted) BannerVideoAudio.Muted else BannerVideoAudio.Audible,
                                onFirstFrame = { loading = false },
                                onAudioAvailable = { hasAudio = it },
                                onAudioInterrupted = { muted = true },
                                onError = { failed = true },
                            )
                        } else {
                            AsyncImage(
                                model = source,
                                contentDescription = title,
                                modifier = Modifier.fillMaxSize().testTag("profile_banner_preview_image"),
                                contentScale = ContentScale.Fit,
                                onSuccess = { loading = false },
                                onError = { failed = true },
                            )
                        }
                    }
                    if (failed) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.profile_banner_preview_error))
                            TextButton(stringResource(R.string.profile_banner_preview_retry), onClick = { attempt++ })
                        }
                    } else if (loading) {
                        CircularProgressIndicator()
                    }
                }
                if (onChange != null) {
                    Button(
                        onClick = onChange,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            .widthIn(max = 360.dp).fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("profile_banner_preview_change"),
                    ) {
                        Text(stringResource(R.string.profile_banner_preview_change))
                    }
                }
            }
        }
    }
}

package io.github.currencortex.music

import android.graphics.Bitmap
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.data.library.Playlist
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.data.song.SongInsightsRepository
import io.github.currencortex.music.core.network.ApiClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import io.github.currencortex.music.feature.library.PlaylistArtworkPreview
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.basic.Scaffold
import java.io.File

class MusicArtworkPreviewTest {
    @get:Rule val compose = createComposeRule()
    private var played = 0
    private var queued = 0
    private var pageBacks = 0
    private var artistOpens = 0
    private var albumOpens = 0
    private var playlistOpens = 0
    private var shuffled = 0
    private var downloads = 0
    private var shares = 0
    private var selectedVersion: Song? = null
    private var playlistCanPlay by mutableStateOf(true)
    private var queueEnabled by mutableStateOf(true)
    private lateinit var back: OnBackPressedDispatcher
    private var settings by mutableStateOf(AppearanceSettings(themeMode = ThemeMode.LIGHT))

    private fun show(capture: Boolean = true, nearTop: Boolean = false, mixedTitles: Boolean = false,
        playlist: Boolean = false, middle: Boolean = false, longSubmenu: Boolean = false,
        insights: SongInsightsRepository? = null) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        val cover = File(context.cacheDir, "preview-test-cover.png")
        Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888).apply {
            val canvas = android.graphics.Canvas(this)
            canvas.drawColor(android.graphics.Color.rgb(42, 90, 150))
            val paint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(247, 173, 132) }
            canvas.drawCircle(160f, 130f, 100f, paint)
            paint.color = android.graphics.Color.WHITE
            paint.textSize = 26f
            canvas.drawText("CURRENT MUSIC", 40f, 280f, paint)
            cover.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
        compose.setContent { LeiTheme(settings) {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            BackHandler { pageBacks++ }
            val backdrop = if (capture) rememberMusicDialogBackdrop() else null
            var selected by remember { mutableStateOf<Rect?>(null) }
            CompositionLocalProvider(LocalMusicDialogBackdrop provides backdrop, LocalElasticPressDiagnostics provides true) {
                Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) {
                    Box(Modifier.fillMaxSize().musicDialogBackdropSource().testTag("preview_page")) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawRect(Color(0xFFEAD8C5))
                            val stripe = 12.dp.toPx()
                            repeat((size.width / stripe).toInt() + 1) { x ->
                                drawRect(if (x % 2 == 0) Color(0xFF6688AA) else Color(0xFFEAD8C5),
                                    Offset(x * stripe, 0f), Size(stripe, size.height))
                            }
                        }
                        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp),
                            verticalArrangement = if (nearTop) Arrangement.Top else if (middle) Arrangement.Center else Arrangement.Bottom) {
                            ElasticLazyRow(Modifier.testTag("preview_row"), edgeToEdgeInset = 16.dp) {
                                items((0..7).toList()) { index ->
                                    val labels = if (mixedTitles) when (index % 3) {
                                        0 -> "I Walk This Earth All By Myself" to "EKKSTACY"
                                        1 -> "ハナヒビク頃 (feat. 初音ミク)" to "Shino / 初音ミク"
                                        else -> "无尽幸福" to "凌晨一点的莱茵猫 / 二乘"
                                    } else "预览歌曲 $index" to "测试艺术家"
                                    MusicArtworkCard(cover.toURI().toString(), labels.first, labels.second,
                                        { if (playlist) playlistOpens++ else played++ }, Modifier.width(130.dp).testTag("preview_card_$index"),
                                        onLongClick = { selected = it }, longClickLabel = if (playlist) "预览歌单与操作" else "预览歌曲与操作")
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                        }
                    }
                    selected?.let { bounds -> if (playlist) {
                        PlaylistArtworkPreview(Playlist(1, "测试歌单", "", cover.toURI().toString(), "netease", 1, 3, emptyList()),
                            bounds, canPlay = playlistCanPlay,
                            onPlay = { shuffle -> played++; if (shuffle) shuffled++ },
                            onOpen = { playlistOpens++ }, onDismiss = { selected = null })
                    } else {
                    val metadata = insights?.let { songInsightsMenuItems(it, Song(7, "原曲")) { selectedVersion = it } }.orEmpty()
                    MusicArtworkPreview(cover.toURI().toString(), "预览歌曲", bounds,
                        actions = listOf(
                            GlassMenuItem("play", "播放", MusicIcons.Play, onClick = { played++ }),
                            GlassMenuItem("next", "下一首", MusicIcons.NextTrack, onClick = { queued++ }),
                            GlassMenuItem("queue", "加入队列", MusicIcons.List, enabled = queueEnabled, onClick = { queued++ })),
                        destinations = listOf(GlassMenuItem("artist", "前往艺术家", subtitle = "测试艺术家", onClick = { artistOpens++ }),
                            GlassMenuItem("album", "前往专辑", subtitle = "测试专辑", onClick = { albumOpens++ }),
                            GlassMenuItem("more", "更多操作", children = listOf(
                                GlassMenuItem("preview_download", "下载歌曲", onClick = { downloads++ }),
                                GlassMenuItem("preview_share", "分享歌曲", onClick = { shares++ }),
                                GlassMenuItem("preview_like", "喜欢 / 取消喜欢"),
                                GlassMenuItem("preview_add_playlist", "加入歌单"),
                            ) + metadata + if (longSubmenu) listOf(
                                GlassMenuItem("preview_artist_one", "歌手 · sapientdream"),
                                GlassMenuItem("preview_artist_two", "歌手 · Slushii"),
                                GlassMenuItem("preview_album", "查看专辑"),
                            ) else emptyList())), onDismiss = { selected = null }) } }
                }
            }
        } }
    }

    private fun open(cardIndex: Int = 0) {
        compose.onNodeWithTag("preview_card_$cardIndex").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithTag("artwork_preview_menu").assertIsDisplayed()
        assertEquals("Long press must not start playback", 0, played)
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.externalCacheDir, "artwork-preview-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun playlistLongPressOpensThePlaylistOnlyAfterDismissal() {
        show(playlist = true); open()
        assertEquals(0, playlistOpens)
        compose.onNodeWithText("播放全部").assertExists()
        compose.onNodeWithText("随机播放").assertExists()
        compose.onNodeWithTag("artwork_preview_artist").assertDoesNotExist()
        capture("playlist")
        compose.onNodeWithTag("artwork_preview_open_playlist").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("artwork_preview").assertDoesNotExist()
        assertEquals(1, playlistOpens)
        assertEquals(0, played)
    }

    @Test fun moreUsesTheSameUnfoldTransitionAsAnUpdateSubmenu() {
        show(middle = true); open()
        val parent = compose.onNodeWithTag("artwork_preview_menu").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("artwork_preview_more").performClick()
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        val first = compose.onNodeWithTag("artwork_preview_submenu").fetchSemanticsNode().config[GlassMenuUnfoldKey]
        compose.mainClock.advanceTimeBy(100)
        val middle = compose.onNodeWithTag("artwork_preview_submenu").fetchSemanticsNode().config[GlassMenuUnfoldKey]
        assertTrue("Submenu unfolds from the owner row", first < middle && first < .9f)
        compose.mainClock.advanceTimeBy(1200)
        val receded = compose.onNodeWithTag("artwork_preview_menu").fetchSemanticsNode().boundsInRoot
        assertTrue("Parent recedes behind the expanded child", receded.width < parent.width * .98f)
        compose.onNodeWithTag("artwork_preview_submenu_back").performClick()
        compose.mainClock.advanceTimeBy(100)
        val folding = compose.onNodeWithTag("artwork_preview_submenu").fetchSemanticsNode().config[GlassMenuUnfoldKey]
        assertTrue("Back folds the same surface instead of launching a popup return", folding in 0f..0.99f)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(parent.width, compose.onNodeWithTag("artwork_preview_menu").fetchSemanticsNode().boundsInRoot.width, 2f)
    }

    @Test fun foldingTheSubmenuDoesNotRecomposeTheOverlayOnEveryFrame() {
        show(middle = true); open()
        compose.onNodeWithTag("artwork_preview_more").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("artwork_preview_submenu_back").performClick()
        compose.mainClock.advanceTimeBy(96)
        compose.waitForIdle()
        val before = Recomposer.runningRecomposers.value.sumOf { it.changeCount }
        val first = compose.onNodeWithTag("artwork_preview_submenu").fetchSemanticsNode().config[GlassMenuUnfoldKey]
        repeat(8) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        val changes = Recomposer.runningRecomposers.value.sumOf { it.changeCount } - before
        val last = compose.onNodeWithTag("artwork_preview_submenu").fetchSemanticsNode().config[GlassMenuUnfoldKey]
        assertTrue("The fold must continue while composition remains stable", last < first)
        assertTrue("Eight draw frames must not rebuild the overlay each frame: $changes compositions", changes <= 2)
        compose.onNodeWithTag("artwork_preview_play").assertIsNotEnabled()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("artwork_preview_submenu").assertDoesNotExist()
        compose.onNodeWithTag("artwork_preview_play").assertIsEnabled()
    }

    @Test fun versionsLoadOnEntryRetryInsideTheMenuAndPlayOnlyAfterDismissal() {
        MockWebServer().use { server ->
            val address = server.url("/cm/").toString()
            val repository = SongInsightsRepository(ApiClient({ address }, { "unused" }, {}))
            show(nearTop = true, insights = repository); open()
            compose.onNodeWithTag("artwork_preview_more").performClick()
            compose.waitForIdle()
            assertEquals(0, server.requestCount)
            server.enqueue(MockResponse().setResponseCode(502))
            compose.onNodeWithTag("song_versions").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("song_versions_retry").fetchSemanticsNodes().isNotEmpty() }
            server.enqueue(MockResponse().setBody("""{"id":7,"songs":[{"ncm_id":8,"name":"其他版本","artists":"版本歌手"}]}"""))
            compose.onNodeWithTag("song_versions_retry").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("song_version_8").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("artwork_preview").assertExists()
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("song_version_8").performClick()
            assertNull(selectedVersion)
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            assertEquals(8L, selectedVersion?.id)
            assertEquals(0, played)
            assertEquals(2, server.requestCount)
            compose.onNodeWithTag("artwork_preview").assertDoesNotExist()
        }
    }

    @Test fun leavingAnInsightBranchCancelsItsLoadAndReenteringStartsFresh() {
        MockWebServer().use { server ->
            val address = server.url("/cm/").toString()
            val repository = SongInsightsRepository(ApiClient({ address }, { null }, {}))
            show(nearTop = true, insights = repository); open()
            compose.onNodeWithTag("artwork_preview_more").performClick()
            server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
            compose.onNodeWithTag("song_versions").performScrollTo().performClick()
            compose.waitUntil(5000) { server.requestCount == 1 }
            compose.onNodeWithTag("artwork_preview_submenu_back").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("song_versions_loading").assertDoesNotExist()
            server.enqueue(MockResponse().setBody("""{"id":7,"songs":[{"ncm_id":9,"name":"重新加载的版本"}]}"""))
            compose.onNodeWithTag("song_versions").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("song_version_9").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(2, server.requestCount)
            compose.onNodeWithTag("song_version_9").performClick()
            compose.waitForIdle()
            assertEquals(9L, selectedVersion?.id)
        }
    }

    @Test fun longQualityBranchStaysInsideTheScreenAndKeepsBackReachable() {
        MockWebServer().use { server ->
            val address = server.url("/cm/").toString()
            val repository = SongInsightsRepository(ApiClient({ address }, { null }, {}))
            val files = (0..11).joinToString(",") { """{"level":"level$it","label":"音质档位 $it","sr":48000,"br":320000}""" }
            server.enqueue(MockResponse().setBody("""{"id":7,"qualities":[$files]}"""))
            show(nearTop = true, insights = repository); open()
            compose.onNodeWithTag("artwork_preview_more").performClick()
            compose.onNodeWithTag("song_file_info").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("song_file_level11").fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()
            val pane = compose.onNodeWithTag("artwork_preview_submenu").fetchSemanticsNode().boundsInRoot
            val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
            assertTrue(pane.top >= viewport.top && pane.bottom <= viewport.bottom)
            assertTrue("A wider child must stay inside the horizontal edges: $pane", pane.left >= viewport.left && pane.right <= viewport.right)
            compose.onNodeWithTag("artwork_preview_submenu_back").assertIsDisplayed()
            compose.onNodeWithTag("song_file_level0").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
            compose.onNodeWithTag("song_file_level11").performScrollTo().assertIsDisplayed()
            capture("quality-files-scroll")
            compose.onNodeWithTag("artwork_preview_submenu_back").performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("song_file_info").assertExists()
            assertNull(selectedVersion)
        }
    }

    @Test fun playlistShuffleRunsOnceAndDisabledPlaybackStillAllowsOpening() {
        show(playlist = true); open()
        compose.onNodeWithTag("artwork_preview_shuffle").performClick()
        compose.waitForIdle()
        assertEquals(1, played); assertEquals(1, shuffled); assertEquals(0, playlistOpens)
        played = 0
        open()
        compose.runOnIdle { playlistCanPlay = false }
        compose.onNodeWithTag("artwork_preview_play").assertIsNotEnabled()
        compose.onNodeWithTag("artwork_preview_shuffle").assertIsNotEnabled()
        compose.onNodeWithTag("artwork_preview_open_playlist").performClick()
        compose.waitForIdle()
        assertEquals(0, played); assertEquals(1, playlistOpens)
    }

    private fun checkMenuEntrance(playlist: Boolean) {
        show(playlist = playlist)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("preview_card_0").performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        val first = compose.onNodeWithTag("artwork_preview_menu").fetchSemanticsNode().boundsInRoot
        compose.mainClock.advanceTimeBy(96)
        val middle = compose.onNodeWithTag("artwork_preview_menu").fetchSemanticsNode().boundsInRoot
        compose.mainClock.advanceTimeBy(1000)
        val settled = compose.onNodeWithTag("artwork_preview_menu").fetchSemanticsNode().boundsInRoot
        assertTrue("Menu must visibly grow after mounting: first=$first middle=$middle settled=$settled", first.width < middle.width)
        assertTrue("Menu must not mount at its final scale", first.width < settled.width * .98f)
        compose.mainClock.autoAdvance = true
    }

    @Test fun songMenuAnimatesAfterTheDialogIsMounted() = checkMenuEntrance(playlist = false)
    @Test fun playlistMenuAnimatesAfterTheDialogIsMounted() = checkMenuEntrance(playlist = true)

    @Test fun moreOperationsKeepsThePreviewAndBackReturnsOneLevelAtATime() {
        show(); open()
        val cover = compose.onNodeWithTag("artwork_preview_cover").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("artwork_preview_more").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("artwork_preview_submenu").assertIsDisplayed()
        compose.onNodeWithTag("song_actions_sheet").assertDoesNotExist()
        compose.onNodeWithTag("artwork_preview_play").assertIsNotEnabled()
        assertEquals(cover, compose.onNodeWithTag("artwork_preview_cover").fetchSemanticsNode().boundsInRoot)
        val pane = compose.onNodeWithTag("artwork_preview_submenu").fetchSemanticsNode().boundsInRoot
        val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(pane.left >= viewport.left && pane.right <= viewport.right && pane.top >= viewport.top && pane.bottom <= viewport.bottom)
        capture("more-submenu")
        compose.onNodeWithTag("artwork_preview_submenu_back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("artwork_preview_submenu").assertDoesNotExist()
        compose.onNodeWithTag("artwork_preview_play").assertIsEnabled()
        compose.onNodeWithTag("artwork_preview_more").performClick()
        compose.waitForIdle()
        compose.runOnIdle { back.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("artwork_preview_submenu").assertDoesNotExist()
        compose.onNodeWithTag("artwork_preview_cover").assertIsDisplayed()
        compose.runOnIdle { back.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("artwork_preview").assertDoesNotExist()
        assertEquals(0, pageBacks); assertEquals(0, downloads); assertEquals(0, shares)
    }

    @Test fun submenuSelectionRunsOnlyAfterTheWholePreviewCloses() {
        show(nearTop = true); open()
        val trigger = compose.onNodeWithTag("artwork_preview_more").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("artwork_preview_more").performClick()
        compose.waitForIdle()
        val cover = compose.onNodeWithTag("artwork_preview_cover").fetchSemanticsNode().boundsInRoot
        val pane = compose.onNodeWithTag("artwork_preview_submenu").fetchSemanticsNode().boundsInRoot
        assertTrue("Downward submenu must leave the lifted cover visible", pane.top > cover.bottom)
        val header = compose.onNodeWithTag("artwork_preview_submenu_back").fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithTag("preview_download").fetchSemanticsNode().boundsInRoot
        assertTrue("Downward header stays at the trigger", kotlin.math.abs(header.top - trigger.top) < 4f)
        assertTrue("Downward actions follow the header", action.top >= header.bottom)
        capture("more-submenu-below")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("preview_download").performClick()
        assertEquals(0, downloads)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(1, downloads); assertEquals(0, played)
        compose.onNodeWithTag("artwork_preview").assertDoesNotExist()
    }

    @Test fun tallSubmenuFlipsAboveItsTriggerAndKeepsTheHeaderAtTheBottom() {
        show(middle = true, longSubmenu = true); open()
        val trigger = compose.onNodeWithTag("artwork_preview_more").fetchSemanticsNode().boundsInRoot
        val cover = compose.onNodeWithTag("artwork_preview_cover").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("artwork_preview_more").performClick()
        compose.waitForIdle()
        val pane = compose.onNodeWithTag("artwork_preview_submenu").fetchSemanticsNode().boundsInRoot
        val header = compose.onNodeWithTag("artwork_preview_submenu_back").fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithTag("preview_album").fetchSemanticsNode().boundsInRoot
        val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Fixture must lack space below the trigger", viewport.bottom - trigger.top < pane.height)
        assertTrue("Menu opens above the trigger", pane.top < trigger.top)
        assertTrue("Upward header stays at the trigger: $header vs $trigger", kotlin.math.abs(header.bottom - trigger.bottom) < 4f)
        assertTrue("Actions sit above the upward header", last.bottom <= header.top)
        assertTrue("Menu stays inside the viewport", pane.top >= viewport.top && pane.bottom <= viewport.bottom)
        assertEquals(cover, compose.onNodeWithTag("artwork_preview_cover").fetchSemanticsNode().boundsInRoot)
        capture("more-submenu-flipped")
        compose.onNodeWithTag("artwork_preview_submenu_back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("artwork_preview_submenu").assertDoesNotExist()
        compose.onNodeWithTag("artwork_preview_more").assertIsEnabled()
    }

    @Test fun slidingInTheSubmenuRunsOnlyTheReleasedAction() {
        show(); open()
        compose.onNodeWithTag("artwork_preview_more").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val root = compose.onRoot()
        fun center(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center -
            root.fetchSemanticsNode().boundsInRoot.topLeft
        val start = center("preview_download")
        root.performTouchInput { down(start) }
        compose.mainClock.advanceTimeBy(300)
        val target = center("preview_share")
        root.performTouchInput { moveTo(target) }
        compose.mainClock.advanceTimeBy(150)
        assertEquals(0, downloads); assertEquals(0, shares)
        root.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(0, downloads); assertEquals(1, shares)
        compose.onNodeWithTag("artwork_preview").assertDoesNotExist()
    }

    @Test fun menuShadowIsVisibleWhileTheSurfaceIsStillFadingIn() {
        show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("preview_card_0").performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        compose.mainClock.advanceTimeBy(80)
        val bounds = compose.onNodeWithTag("artwork_preview_menu").fetchSemanticsNode().boundsInRoot
        capture("menu-entering")
        val shadow = compose.onRoot().captureToImage().asAndroidBitmap()
        var difference = 0.0
        var samples = 0
        // Outside the straight right edge: the material cannot affect these pixels,
        // and the fixture's vertical stripes/veil are identical further above the menu.
        val referenceY = bounds.top.toInt() - 120
        for (x in (bounds.right.toInt() + 4)..(bounds.right.toInt() + 24)) {
            for (y in (bounds.center.y.toInt() - 20)..(bounds.center.y.toInt() + 20)) {
                difference += android.graphics.Color.red(shadow.getPixel(x, referenceY)) -
                    android.graphics.Color.red(shadow.getPixel(x, y))
                samples++
            }
        }
        shadow.recycle()
        assertTrue("Shadow must already paint outside the partially transparent pane: delta=${difference / samples}",
            difference / samples > .5)
        compose.mainClock.advanceTimeBy(1000)
        val settled = compose.onNodeWithTag("artwork_preview_menu").fetchSemanticsNode().boundsInRoot
        assertTrue("Shadow sample must be taken before entrance finishes", bounds.width < settled.width)
        compose.mainClock.autoAdvance = true
    }

    private fun checkInsideMenuPull(playlist: Boolean) {
        show(playlist = playlist); open()
        compose.mainClock.autoAdvance = false
        val root = compose.onRoot()
        val panel = compose.onNodeWithTag("artwork_preview_menu_press")
        val resting = panel.fetchSemanticsNode().boundsInRoot
        fun frame() = panel.fetchSemanticsNode().config[ElasticPressGeometry]
        val origin = root.fetchSemanticsNode().boundsInRoot.topLeft
        val start = resting.center - origin
        root.performTouchInput { down(start) }
        compose.mainClock.advanceTimeBy(400)
        val delta = Offset(resting.width * .18f, resting.height * .18f)
        root.performTouchInput { moveTo(start + delta) }
        compose.mainClock.advanceTimeBy(240)
        assertTrue("An inside drag must follow on both axes, just like the dock", frame().pull.x > 5 && frame().pull.y > 5)
        root.performTouchInput { moveTo(start - delta) }
        compose.mainClock.advanceTimeBy(240)
        assertTrue("Reversing inside the pane must reverse its pull", frame().pull.x < -5 && frame().pull.y < -5)
        capture(if (playlist) "playlist-inside-pull" else "menu-inside-pull")
        root.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(1600)
        assertEquals(0f, frame().pull.x, .1f); assertEquals(0f, frame().pull.y, .1f)
        assertEquals(1f, frame().scaleX, .001f); assertEquals(1f, frame().scaleY, .001f)
        assertEquals(0, played); assertEquals(0, queued); assertEquals(0, playlistOpens)
        compose.mainClock.autoAdvance = true
    }

    @Test fun songMenuFollowsDraggingInsideItsBounds() = checkInsideMenuPull(playlist = false)
    @Test fun playlistMenuFollowsDraggingInsideItsBounds() = checkInsideMenuPull(playlist = true)

    @Test fun mixedLanguageCardsKeepTheirBottomEdgesAligned() {
        show(nearTop = true, mixedTitles = true)
        capture("mixed-language-cards")
        val cards = (0..2).map {
            compose.onNodeWithTag("preview_card_$it").fetchSemanticsNode().boundsInRoot
        }
        cards.drop(1).forEach {
            assertEquals("Mixed scripts must not make individual cards taller: $cards", cards.first().bottom, it.bottom, 1f)
        }
        for (title in listOf("I Walk This Earth All By Myself", "ハナヒビク頃 (feat. 初音ミク)")) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onAllNodesWithText(title, useUnmergedTree = true).onFirst()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals("Long titles must retain both lines: $title", 2, layouts.single().lineCount)
        }
    }

    @Test fun longPressLiftsCoverAndActionRunsOnceAfterClosing() {
        show(); open()
        val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithTag("artwork_preview_menu").fetchSemanticsNode().boundsInRoot
        val cover = compose.onNodeWithTag("artwork_preview_cover").fetchSemanticsNode().boundsInRoot
        assertTrue("Menu must stay on screen even for the bottom row", menu.bottom <= viewport.bottom)
        assertTrue("A bottom-edge anchor must put the menu above its cover: menu=$menu cover=$cover", menu.bottom < cover.top)
        assertTrue("The flipped menu must stay below the top edge", menu.top >= viewport.top)
        assertTrue(menu.left >= viewport.left && menu.right <= viewport.right)
        val quick = compose.onNodeWithTag("artwork_preview_play").fetchSemanticsNode().boundsInRoot
        val lastRow = compose.onNodeWithTag("artwork_preview_more").fetchSemanticsNode().boundsInRoot
        assertTrue("An upward menu keeps its quick actions next to the cover at the bottom", quick.top >= lastRow.bottom)
        capture("light")
        compose.runOnIdle { settings = settings.copy(themeMode = ThemeMode.DARK) }
        capture("dark")
        compose.onNodeWithTag("artwork_preview_queue").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("artwork_preview").assertDoesNotExist()
        assertEquals(1, queued); assertEquals(0, played)
    }

    @Test fun closingCoverLandsOnTheRestingArtworkWithoutJumping() {
        show()
        fun restingCover() = compose.onNode(hasContentDescription("歌曲封面") and
            hasAnyAncestor(hasTestTag("preview_card_0")), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val original = restingCover()
        open()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { back.onBackPressed() }
        // Start the tween, then inspect its last visible frame before the overlay is removed.
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(192)
        val landing = compose.onNodeWithTag("artwork_preview_cover").fetchSemanticsNode().boundsInRoot
        val resting = restingCover()
        for ((expected, actual) in listOf(original.left to resting.left, original.top to resting.top,
            resting.left to landing.left, resting.top to landing.top,
            resting.right to landing.right, resting.bottom to landing.bottom)) {
            assertEquals("Returning cover must meet the resting card: landing=$landing resting=$resting",
                expected, actual, 2f)
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("artwork_preview").assertDoesNotExist()
        assertEquals(0, played)
    }

    @Test fun topRightAnchorOpensBelowAndKeepsBothSurfacesInsideTheWindow() {
        show(nearTop = true)
        compose.onNodeWithTag("preview_row").performScrollToIndex(7)
        open(cardIndex = 7)
        val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithTag("artwork_preview_menu").fetchSemanticsNode().boundsInRoot
        val cover = compose.onNodeWithTag("artwork_preview_cover").fetchSemanticsNode().boundsInRoot
        assertTrue("A top-edge anchor must put the menu below its cover", cover.bottom < menu.top)
        val quick = compose.onNodeWithTag("artwork_preview_play").fetchSemanticsNode().boundsInRoot
        val firstRow = compose.onNodeWithTag("artwork_preview_artist").fetchSemanticsNode().boundsInRoot
        assertTrue("A downward menu keeps its quick actions next to the cover at the top", quick.bottom <= firstRow.top)
        for (bounds in listOf(menu, cover)) {
            assertTrue(bounds.left >= viewport.left && bounds.right <= viewport.right)
            assertTrue(bounds.top >= viewport.top && bounds.bottom <= viewport.bottom)
        }
        capture("top-right")
        compose.onNodeWithTag("artwork_preview_play").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(1, played)
        compose.onNodeWithTag("artwork_preview").assertDoesNotExist()
    }

    @Test fun scrollingDoesNotOpenPreviewAndDismissDoesNotPlay() {
        show()
        compose.onNodeWithTag("preview_row").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("artwork_preview").assertDoesNotExist()
        capture("row-scrolled")
        compose.onNodeWithTag("preview_row").performScrollToIndex(0)
        open()
        compose.onNodeWithTag("artwork_preview_dismiss").performTouchInput { click(Offset(20f, 20f)) }
        compose.waitForIdle()
        compose.onNodeWithTag("artwork_preview").assertDoesNotExist()
        assertEquals(0, played)
        compose.onNodeWithTag("preview_card_0").performClick()
        assertEquals(1, played)
    }

    @Test fun appearanceTogglesAndMissingBackdropKeepActionsUsable() {
        show(capture = false); open()
        compose.onNodeWithTag("artwork_preview_menu")
            .assert(SemanticsMatcher.expectValue(LiquidGlassOpticsKey, false))
        compose.runOnIdle { settings = settings.copy(liquidGlass = false, blur = false) }
        capture("fallback")
        compose.onNodeWithTag("artwork_preview_play").performClick()
        compose.waitForIdle()
        assertEquals(1, played)
        compose.onNodeWithTag("artwork_preview").assertDoesNotExist()
    }

    @Test fun liveAppearanceChangesAndBackOnlyDismissThePreview() {
        show(); open()
        compose.onNodeWithTag("artwork_preview_menu")
            .assert(SemanticsMatcher.expectValue(LiquidGlassOpticsKey, true))
        val frosted = compose.onRoot().captureToImage().asAndroidBitmap()
        compose.runOnIdle { settings = settings.copy(blur = false) }
        val clear = compose.onRoot().captureToImage().asAndroidBitmap()
        assertFalse("Blur switch must reach the retained root overlay", frosted.sameAs(clear))
        frosted.recycle(); clear.recycle()
        compose.runOnIdle { settings = settings.copy(liquidGlass = false) }
        compose.onNodeWithTag("artwork_preview_menu")
            .assert(SemanticsMatcher.expectValue(LiquidGlassOpticsKey, false))
        compose.runOnUiThread { back.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("artwork_preview").assertDoesNotExist()
        assertEquals(0, pageBacks); assertEquals(0, played); assertEquals(0, queued)
    }

    @Test fun menuPressStretchesTheWholePaneAndSpringsBackWithoutAccidentalAction() {
        show(); open()
        compose.mainClock.autoAdvance = false
        val panel = compose.onNodeWithTag("artwork_preview_menu_press")
        val play = compose.onNodeWithTag("artwork_preview_play")
        val root = compose.onRoot()
        fun frame() = panel.fetchSemanticsNode().config[ElasticPressGeometry]
        val resting = panel.fetchSemanticsNode().boundsInRoot
        val rootOrigin = root.fetchSemanticsNode().boundsInRoot.topLeft
        val start = play.fetchSemanticsNode().boundsInRoot.center - rootOrigin
        root.performTouchInput { down(start) }
        compose.mainClock.advanceTimeBy(600)
        assertTrue("Pressing a child action must swell the whole glass pane", frame().scaleX > 1.01f)
        // Keep the pointer in root coordinates while its original child moves away.
        root.performTouchInput { moveTo(Offset(resting.right + resting.width / 2 - rootOrigin.x, start.y)) }
        compose.mainClock.advanceTimeBy(250)
        val dragged = frame()
        assertTrue("The pane must follow the finger", dragged.pull.x > 5f)
        assertTrue("Horizontal dragging must stretch it horizontally", dragged.scaleX > dragged.scaleY)
        capture("menu-stretched")
        root.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1200)
        assertEquals(1f, frame().scaleX, .001f)
        assertEquals(1f, frame().scaleY, .001f)
        assertEquals(0f, frame().pull.x, .1f)
        assertEquals(resting, panel.fetchSemanticsNode().boundsInRoot)
        assertEquals(0, played); assertEquals(0, queued)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("artwork_preview_queue").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(1, queued)
        compose.onNodeWithTag("artwork_preview").assertDoesNotExist()
    }

    @Test fun slidingAcrossButtonsAndRowsHighlightsAndExecutesOnlyTheReleasedItem() {
        show(); open()
        compose.mainClock.autoAdvance = false
        val root = compose.onRoot()
        fun center(id: String) = compose.onNodeWithTag("artwork_preview_$id").fetchSemanticsNode().boundsInRoot.center -
            root.fetchSemanticsNode().boundsInRoot.topLeft
        fun selected() = compose.onNodeWithTag("artwork_preview_menu").fetchSemanticsNode().config[GlassMenuInteractionKey].selected
        val start = center("play")
        root.performTouchInput { down(start) }
        compose.mainClock.advanceTimeBy(400)
        assertEquals(0, selected())
        for ((id, index) in listOf("queue" to 2, "artist" to 3, "album" to 4, "artist" to 3)) {
            val target = center(id)
            root.performTouchInput { moveTo(target) }
            compose.mainClock.advanceTimeBy(120)
            assertEquals("Highlight must follow the finger to $id", index, selected())
        }
        capture("slide-selected")
        assertEquals(0, played); assertEquals(0, queued); assertEquals(0, artistOpens); assertEquals(0, albumOpens)
        root.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("artwork_preview").assertDoesNotExist()
        assertEquals(1, artistOpens)
        assertEquals(0, played); assertEquals(0, queued); assertEquals(0, albumOpens)
    }

    @Test fun slidingOntoDisabledItemOrCancellingNeverRunsAnAction() {
        queueEnabled = false
        show(); open()
        compose.mainClock.autoAdvance = false
        val root = compose.onRoot()
        fun center(id: String) = compose.onNodeWithTag("artwork_preview_$id").fetchSemanticsNode().boundsInRoot.center -
            root.fetchSemanticsNode().boundsInRoot.topLeft
        val start = center("play")
        root.performTouchInput { down(start) }
        compose.mainClock.advanceTimeBy(400)
        val disabled = center("queue")
        root.performTouchInput { moveTo(disabled) }
        compose.mainClock.advanceTimeBy(120)
        assertEquals(-1, compose.onNodeWithTag("artwork_preview_menu").fetchSemanticsNode().config[GlassMenuInteractionKey].selected)
        root.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("artwork_preview_menu").assertIsDisplayed()
        val artist = center("artist")
        root.performTouchInput { down(artist) }
        compose.mainClock.advanceTimeBy(300)
        root.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(-1, compose.onNodeWithTag("artwork_preview_menu").fetchSemanticsNode().config[GlassMenuInteractionKey].selected)
        assertEquals(0, played); assertEquals(0, queued); assertEquals(0, artistOpens)
        compose.mainClock.autoAdvance = true
    }
}

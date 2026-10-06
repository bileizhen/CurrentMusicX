package io.github.currencortex.music

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.ui.CurrentMusicApp
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Only avatar loading/reveal and home-to-profile travel, with isolated account storage. */
class HomeAvatarTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var server: MockWebServer
    private lateinit var container: AppContainer
    private val imageGate = CountDownLatch(1)
    private val imageRequested = CountDownLatch(1)
    private var opened = 0
    private val launch = LaunchBrandState()
    private lateinit var backDispatcher: androidx.activity.OnBackPressedDispatcher
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Before fun prepare() = runBlocking {
        val png = ByteArrayOutputStream().also { stream ->
            Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.rgb(32,176,96)) }
                .compress(Bitmap.CompressFormat.PNG, 100, stream)
        }.toByteArray()
        val frame = ByteArrayOutputStream().also { stream ->
            val bitmap = Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawCircle(64f,64f,56f,Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.MAGENTA; style = Paint.Style.STROKE; strokeWidth = 10f
            })
            bitmap.compress(Bitmap.CompressFormat.PNG,100,stream)
        }.toByteArray()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                "/avatar.png", "/cm/avatar/fixture.png" -> {
                    imageRequested.countDown(); imageGate.await(20, TimeUnit.SECONDS)
                    MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(png))
                }
                "/cm/decor/fixture.gif" -> MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(frame))
                "/cm/auth/me" -> json("""{"id":7,"nickname":"Avatar fixture","avatar":"fixture.png","avatarDecoration":"fixture","stat":{}}""")
                "/cm/ncm/search/hot/detail" -> json("""{"code":200,"data":[{"searchWord":"Fixture hot","content":"Live fixture"}]}""")
                "/cm/daily" -> json("""{"daily":[],"forYou":[],"artists":[]}""")
                "/cm/plays/recent", "/cm/likes/mine" -> json("""{"songs":[]}""")
                "/cm/playlists" -> json("""{"playlists":[]}""")
                "/cm/decorations/scales" -> json("""{"scales":{"fixture":1.5}}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        container = AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(), "avatar-${UUID.randomUUID()}")
        container.ready.await(); container.sessionRestored.await()
        val base = server.url("/cm/").toString()
        container.musicSettings.setServer(base); container.accountRepository.server = base
        container.accountRepository.save("isolated-test-token", UserDto(7, nickname = "Avatar fixture", avatar = "fixture.png"))
        container.updateSettings.setAutoCheck(false)
        container.settings.edit { AppearanceSettings(blur = false) }
    }
    @After fun finish() = runBlocking {
        imageGate.countDown()
        if (::container.isInitialized) { container.accountRepository.clear(); container.close() }
        if (::server.isInitialized) server.shutdown()
    }
    private fun pumpUntil(condition: () -> Boolean) = compose.waitUntil(15000) {
        compose.mainClock.advanceTimeBy(32); condition()
    }
    @Composable private fun Fixture(url: String?, ready: Boolean) {
        LeiTheme(AppearanceSettings(themeMode = ThemeMode.LIGHT, blur = false)) {
            CompositionLocalProvider(LocalLaunchBrand provides launch) {
                Box(Modifier.fillMaxSize().background(top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    HomeProfileAvatar(url, ready, onOpen = { _, _ -> opened++ })
                }
            }
        }
    }
    private fun avatarPixel(): Int {
        val bitmap = compose.onNodeWithTag("home_profile_avatar").captureToImage().asAndroidBitmap()
        return bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
    }
    private fun assertAvatarBlank() {
        val bitmap = compose.onNodeWithTag("home_profile_avatar").captureToImage().asAndroidBitmap()
        val background = bitmap.getPixel(0, 0)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            assertEquals("No loading placeholder should be visible", background, bitmap.getPixel(x, y))
        }
    }
    private fun green(pixel: Int) = Color.red(pixel) in 20..50 && Color.green(pixel) in 155..190 && Color.blue(pixel) in 75..115
    private fun magenta(pixel: Int) = Color.red(pixel) > 230 && Color.blue(pixel) > 230 && Color.green(pixel) < 30
    private fun imageBounds(predicate: (Int) -> Boolean): androidx.compose.ui.geometry.Rect {
        val bitmap = compose.onNodeWithTag("music_window").captureToImage().asAndroidBitmap()
        var left = bitmap.width; var top = bitmap.height; var right = 0; var bottom = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) if (predicate(bitmap.getPixel(x, y))) {
            left = minOf(left,x); top = minOf(top,y); right = maxOf(right,x); bottom = maxOf(bottom,y)
        }
        assertTrue("Exactly the retained avatar must be visible", right > left && bottom > top)
        return androidx.compose.ui.geometry.Rect(left.toFloat(),top.toFloat(),right.toFloat(),bottom.toFloat())
    }
    private fun greenBounds() = imageBounds(::green)
    private fun frameBounds() = imageBounds(::magenta)
    private fun assertFrameFollowsAvatar() {
        val avatar = greenBounds(); val frame = frameBounds()
        assertTrue("Frame must not be cropped to the circular photo", frame.width > avatar.width * 1.2f)
        assertTrue("Frame and photo must follow the same centre", kotlin.math.abs(frame.center.x - avatar.center.x) < 3 && kotlin.math.abs(frame.center.y - avatar.center.y) < 3)
    }

    @Test fun photoWaitsForDownloadAndLaunchThenFadesIn() {
        compose.mainClock.autoAdvance = false
        var ready by mutableStateOf(false)
        launch.active = true
        compose.setContent { Fixture(server.url("/avatar.png").toString(), ready) }
        pumpUntil { imageRequested.count == 0L }
        compose.mainClock.advanceTimeBy(800)
        assertAvatarBlank()
        compose.runOnUiThread { ready = true }; imageGate.countDown()
        pumpUntil { compose.onAllNodesWithTag("home_avatar_image", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(600)
        assertAvatarBlank()
        compose.runOnUiThread { launch.active = false }
        compose.mainClock.advanceTimeBy(96)
        val midway = Color.red(avatarPixel())
        assertTrue("Reveal must have an intermediate opacity", midway in 60..235)
        compose.mainClock.advanceTimeBy(500)
        assertTrue(green(avatarPixel()))
        compose.onNodeWithTag("home_profile_avatar").performClick(); assertEquals(1, opened)
    }

    @Test fun absentAndFailedPhotoKeepProfileEntryUsable() {
        compose.mainClock.autoAdvance = false
        var url by mutableStateOf<String?>(null)
        compose.setContent { Fixture(url, true) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("home_avatar_fallback", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("home_profile_avatar").performClick(); assertEquals(1, opened)
        compose.runOnUiThread { url = server.url("/missing.png").toString() }
        pumpUntil { compose.onAllNodesWithTag("home_avatar_fallback", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("home_profile_avatar").performClick(); assertEquals(2, opened)
    }

    @Test fun homeAvatarTravelsAlongCurveIntoMyProfile() = verifyDecoratedFlight(fromBottomTab = false)
    @Test fun bottomMyTabFliesTheHomeAvatarAndFrameIntoMyProfile() = verifyDecoratedFlight(fromBottomTab = true)
    @Test fun homeTabReturnsTheAvatarAndFrameToTheMeasuredHomePosition() = verifyDecoratedFlight(false, returnViaBack = false)
    @Test fun systemBackReturnsTheAvatarAndFrameToTheMeasuredHomePosition() = verifyDecoratedFlight(false, returnViaBack = true)

    @Test fun searchMovesInputUpFocusesAndReturnsToRetainedHome() {
        imageGate.countDown()
        compose.mainClock.autoAdvance = false
        compose.setContent { CurrentMusicApp(container) }
        pumpUntil { compose.onAllNodesWithTag("open_home_search").fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(1500)
        val homeId = compose.onNodeWithTag("music_home").fetchSemanticsNode().id
        val source = compose.onNodeWithTag("open_home_search").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("open_home_search").performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .35f, height * .5f)) }
        compose.mainClock.advanceTimeBy(64)
        fun progress() = compose.onNodeWithTag("search_sheet").fetchSemanticsNode().config[io.github.currencortex.music.feature.search.SearchSheetProgress]
        val early = progress()
        assertTrue("Search sheet must slide through intermediate frames", early > 0f && early < 1f)
        val earlyBounds = compose.onNodeWithTag("search_header_field").fetchSemanticsNode().boundsInRoot
        compose.mainClock.advanceTimeBy(160)
        val middleBounds = compose.onNodeWithTag("search_header_field").fetchSemanticsNode().boundsInRoot
        assertTrue("Input must move towards the top", middleBounds.top < earlyBounds.top)
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("search_input").assertIsFocused()
        val end = compose.onNodeWithTag("search_header_field").fetchSemanticsNode().boundsInRoot
        assertTrue(end.top < source.top)
        assertEquals("Search bar must keep the home width", source.width, end.width, 1f)
        assertEquals("Search bar must keep the home height", source.height, end.height, 1f)
        assertEquals(1f, progress(), .001f)
        pumpUntil { compose.onAllNodesWithTag("search_hot_0").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("search_input").performTextInput("sheet draft")
        compose.onNodeWithTag("submit_search").performClick()
        compose.onNodeWithTag("search_input").assertIsNotFocused()
        compose.onNodeWithTag("search_sheet").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.Dismiss) { it() }
        compose.mainClock.advanceTimeBy(80)
        assertTrue("Closing must animate instead of replacing the home page: ${progress()}", progress() > 0f && progress() < 1f)
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("search_sheet").assertDoesNotExist()
        compose.onNodeWithTag("open_home_search").assertIsDisplayed()
        assertEquals(homeId, compose.onNodeWithTag("music_home").fetchSemanticsNode().id)
        assertEquals(source, compose.onNodeWithTag("open_home_search").fetchSemanticsNode().boundsInRoot)
        assertEquals(7L, container.accountRepository.state.value.account!!.id)
        assertFalse(container.playerController.state.value.playing)
    }

    @Test fun fourTabsKeepSearchSecondaryAndSettingsPrimary() {
        imageGate.countDown()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            CurrentMusicApp(container)
        }
        pumpUntil { compose.onAllNodesWithTag("open_home_search").fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(1500)
        listOf("首页", "发现", "我的", "设置").forEachIndexed { index, label ->
            compose.onNodeWithTag("tab_$index").assertTextContains(label)
        }
        compose.onNodeWithTag("tab_3").performClick()
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("settings_screen").assertIsDisplayed()
        compose.onNodeWithTag("home_avatar_transition").assertDoesNotExist()
        compose.onNodeWithTag("tab_3").assertIsDisplayed()
        compose.onNodeWithTag("open_network").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("network_settings").assertIsDisplayed()
        compose.onNodeWithTag("tab_3").assertDoesNotExist()
        compose.runOnUiThread { backDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("settings_screen").assertIsDisplayed()
        compose.runOnUiThread { backDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("music_home").assertIsDisplayed()
        compose.onNodeWithTag("open_home_search").performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .35f, height * .5f)) }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("search_input").assertIsDisplayed()
        compose.onNodeWithTag("tab_2").assertDoesNotExist()
        compose.onNodeWithTag("search_input").performTextInput("kept draft")
        compose.onNodeWithTag("search_sheet").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.Dismiss) { it() }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("music_home").assertIsDisplayed()
        compose.onNodeWithTag("open_home_search").performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .35f, height * .5f)) }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("search_input").assertTextContains("kept draft")
        compose.runOnUiThread { backDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("music_home").assertIsDisplayed()
        assertEquals(7L, container.accountRepository.state.value.account!!.id)
        assertFalse(container.playerController.state.value.playing)
    }

    private fun verifyDecoratedFlight(fromBottomTab: Boolean, returnViaBack: Boolean? = null) {
        imageGate.countDown()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            CurrentMusicApp(container)
        }
        pumpUntil { compose.onAllNodesWithTag("home_avatar_image", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        pumpUntil {
            val bitmap = compose.onNodeWithTag("home_profile_avatar").captureToImage().asAndroidBitmap()
            (0 until bitmap.height).any { y -> (0 until bitmap.width).any { x -> magenta(bitmap.getPixel(x,y)) } }
        }
        compose.mainClock.advanceTimeBy(1200)
        val source = greenBounds()
        assertFrameFollowsAvatar()
        if (fromBottomTab) compose.onNodeWithTag("tab_2").performClick()
        else compose.onNodeWithTag("home_profile_avatar").performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("profile_screen").assertExists()
        compose.onNodeWithTag("home_avatar_transition").assertExists()
        compose.onNodeWithTag("flying_avatar_frame", useUnmergedTree = true).assertExists()
        compose.mainClock.advanceTimeBy(160)
        val middle = greenBounds()
        assertFrameFollowsAvatar()
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("home_avatar_transition").assertDoesNotExist()
        // The page itself slides now; compare the retained avatar with the final page geometry.
        val target = compose.onNodeWithTag("my_profile_avatar").fetchSemanticsNode().boundsInRoot
        assertTrue("Avatar must travel from right to left", middle.center.x < source.center.x && middle.center.x > target.center.x)
        assertTrue("Avatar must enlarge during travel", middle.width > source.width && middle.width < target.width)
        val along = (source.center.x - middle.center.x) / (source.center.x - target.center.x)
        val straightY = source.center.y + (target.center.y - source.center.y) * along
        assertTrue("Travel must arc above the straight path", middle.center.y < straightY - 5)
        val landed = greenBounds()
        assertFrameFollowsAvatar()
        assertTrue("Avatar must land in the measured profile slot", kotlin.math.abs(landed.center.x - target.center.x) < 3 && kotlin.math.abs(landed.center.y - target.center.y) < 3)
        compose.onAllNodesWithText("设置").onFirst().assertExists()
        assertFalse(container.playerController.state.value.playing)
        if (returnViaBack != null) {
            if (returnViaBack) compose.runOnUiThread { backDispatcher.onBackPressed() }
            else compose.onNodeWithTag("tab_0").performClick()
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithTag("music_home").assertExists()
            compose.onNodeWithTag("home_avatar_transition").assertExists()
            compose.onNodeWithTag("flying_avatar_frame", useUnmergedTree = true).assertExists()
            compose.mainClock.advanceTimeBy(160)
            val returning = greenBounds()
            assertFrameFollowsAvatar()
            assertTrue("Returning photo must move right and shrink", returning.center.x > landed.center.x && returning.center.x < source.center.x && returning.width < landed.width && returning.width > source.width)
            val alongBack = (returning.center.x - landed.center.x) / (source.center.x - landed.center.x)
            val straightBack = landed.center.y + (source.center.y - landed.center.y) * alongBack
            assertTrue("Returning flight must follow the same arc", returning.center.y < straightBack - 5)
            compose.mainClock.advanceTimeBy(700)
            compose.onNodeWithTag("home_avatar_transition").assertDoesNotExist()
            val returned = greenBounds()
            assertFrameFollowsAvatar()
            assertTrue("Returning avatar must land on the actual home photo", kotlin.math.abs(returned.center.x-source.center.x) < 3 && kotlin.math.abs(returned.center.y-source.center.y) < 3 && kotlin.math.abs(returned.width-source.width) < 3)
        }
    }
}

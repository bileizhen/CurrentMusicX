package io.github.currencortex.music

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.player.PlayerBackdrop
import io.github.currencortex.music.feature.player.PlayerBackgroundDialog
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import top.yukonga.miuix.kmp.basic.Scaffold

/** Generated local fixtures and independent preferences; never imports from the user's gallery. */
class PlayerBackgroundTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private fun fixture(directory: File): File {
        val bitmap = Bitmap.createBitmap(256, 512, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(256 * 512) { if (it % 256 / 16 % 2 == 0) 0xffff3366.toInt() else 0xff2244ff.toInt() }
        bitmap.setPixels(pixels, 0, 256, 0, 0, 256, 512)
        return File(directory, "fixture.png").also { file ->
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }

    @Test fun copiedImageSurvivesSourceRemovalAndStoreRestartAndResetDeletesOnlyOwnedImage() = runBlocking<Unit> {
        val root = File(context.cacheDir, "background-store-${UUID.randomUUID()}").apply { mkdirs() }
        val wrapped = object : ContextWrapper(context) { override fun getFilesDir() = root }
        val preferences = File(root, "settings.preferences_pb")
        var owner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = owner) { preferences }, owner)
            settings.edit { it.copy(themeMode = ThemeMode.DARK, playerBackgroundBlur = 72f, playerDynamicBackground = false) }
            val store = PlayerBackgroundStore(wrapped, settings)
            val input = fixture(root)
            val expected = input.readBytes()
            store.import(Uri.fromFile(input)); input.delete()
            val name = settings.snapshot().playerBackground
            val copied = requireNotNull(store.file(name))
            assertArrayEquals(expected, copied.readBytes())
            assertNull(store.file("../settings.preferences_pb"))
            owner.coroutineContext[Job]!!.cancelAndJoin()
            owner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = owner) { preferences }, owner)
            val reopened = PlayerBackgroundStore(wrapped, settings)
            assertEquals(name, settings.snapshot().playerBackground)
            assertTrue(requireNotNull(reopened.file(name)).isFile)
            assertEquals(72f, settings.snapshot().playerBackgroundBlur)
            assertFalse(settings.snapshot().playerDynamicBackground)
            reopened.clear()
            assertEquals("", settings.snapshot().playerBackground)
            assertFalse(copied.exists())
            assertEquals(ThemeMode.DARK, settings.snapshot().themeMode)
            assertEquals(72f, settings.snapshot().playerBackgroundBlur)
        } finally { owner.coroutineContext[Job]!!.cancelAndJoin(); root.deleteRecursively() }
    }

    @Test fun replacementAndInvalidInputKeepAnAtomicOwnedImage() = runBlocking<Unit> {
        val root = File(context.cacheDir, "background-invalid-${UUID.randomUUID()}").apply { mkdirs() }
        val wrapped = object : ContextWrapper(context) { override fun getFilesDir() = root }
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = owner) { File(root, "settings.preferences_pb") }, owner)
            val store = PlayerBackgroundStore(wrapped, settings)
            store.import(Uri.fromFile(fixture(root)))
            val first = requireNotNull(store.file(settings.snapshot().playerBackground))
            store.import(Uri.fromFile(fixture(root)))
            val name = settings.snapshot().playerBackground
            assertFalse(first.exists()); assertTrue(requireNotNull(store.file(name)).isFile)
            try { store.replace { "not an image".byteInputStream() }; fail("Invalid image accepted") }
            catch (_: IllegalArgumentException) { }
            assertEquals(name, settings.snapshot().playerBackground)
            val folder = requireNotNull(requireNotNull(store.file(name)).parentFile)
            assertEquals(1, requireNotNull(folder.listFiles()).size)
            store.clear()
        } finally { owner.coroutineContext[Job]!!.cancelAndJoin(); root.deleteRecursively() }
    }

    @Test fun blurVisiblySoftensTheCustomImageWithoutBlurringControls() = runBlocking<Unit> {
        val root = File(context.cacheDir, "background-render-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val image = fixture(root)
            val blur = mutableFloatStateOf(0f)
            compose.setContent { LeiTheme(AppearanceSettings()) {
                PlayerBackdrop("", Modifier.size(260.dp, 360.dp).testTag("background_render"), image, blur.floatValue)
            } }
            fun edgeContrast(): Int {
                val bitmap = compose.onNodeWithTag("background_render").captureToImage().asAndroidBitmap()
                val y = bitmap.height / 2
                return (8 until bitmap.width).maxOf { x ->
                    kotlin.math.abs((bitmap.getPixel(x, y) ushr 16 and 255) - (bitmap.getPixel(x - 8, y) ushr 16 and 255))
                }
            }
            compose.waitUntil(10000) { edgeContrast() > 80 }
            val sharp = edgeContrast()
            compose.onNodeWithTag("background_render").captureToImage().asAndroidBitmap().let { bitmap ->
                File(context.externalCacheDir, "background-sharp.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            compose.runOnIdle { blur.floatValue = 100f }
            compose.waitUntil(10000) { edgeContrast() < 20 }
            assertTrue(edgeContrast() < sharp / 3)
            compose.onNodeWithTag("background_render").captureToImage().asAndroidBitmap().let { bitmap ->
                File(context.externalCacheDir, "background-blurred.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun backgroundDialogAdjustsPersistsAndRestoresWithoutChangingTheQueue() = runBlocking<Unit> {
        val container = AppContainer(context, "background-dialog-${UUID.randomUUID()}")
        val root = File(context.cacheDir, "background-dialog-image-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            container.ready.await(); container.sessionRestored.await()
            container.playerBackgrounds.import(Uri.fromFile(fixture(root)))
            container.playbackQueue.replace(listOf(Song(900, "Paused fixture")), 0)
            compose.setContent { LeiTheme(AppearanceSettings()) { Scaffold {
                Box(Modifier.fillMaxSize().testTag("background_dialog_host")) { PlayerBackgroundDialog(container) {} }
            } } }
            compose.waitUntil(10000) { compose.onAllNodesWithTag("player_background_blur").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("player_background_blur").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(81f) }
            compose.waitUntil(5000) { container.settings.state.value.playerBackgroundBlur == 81f }
            compose.onNodeWithTag("player_background_blur").performTouchInput {
                swipe(Offset(width * .81f, centerY), Offset(0f, centerY), 600)
            }
            compose.waitUntil(5000) { container.settings.state.value.playerBackgroundBlur == 0f }
            compose.onNodeWithTag("player_background_blur").performTouchInput {
                swipe(Offset(24f, centerY), Offset(width.toFloat(), centerY), 600)
            }
            compose.waitUntil(5000) { container.settings.state.value.playerBackgroundBlur == 100f }
            compose.onNodeWithTag("player_background_reset").performScrollTo().performClick()
            compose.waitUntil(5000) { container.settings.state.value.playerBackground.isEmpty() }
            compose.onNodeWithTag("player_background_reset").assertIsNotEnabled()
            compose.onNodeWithTag("player_background_blur").assertIsNotEnabled()
            assertEquals(listOf(900L), container.playbackQueue.state.value.songs.map { it.id })
            assertFalse(container.playerController.state.value.playing)
        } finally { container.playerBackgrounds.clear(); container.close(); root.deleteRecursively() }
    }

    private fun solidCover(root: File, name: String, color: Int): File {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return File(root, name).also { file ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun dynamicSwitchUpdatesPreviewAndRemembersChoiceWhenReopened() = runBlocking<Unit> {
        val container = AppContainer(context, "background-switch-${UUID.randomUUID()}")
        var shown by mutableStateOf(true)
        try {
            container.ready.await(); container.sessionRestored.await()
            compose.setContent { LeiTheme(AppearanceSettings()) { Scaffold {
                Box(Modifier.fillMaxSize().testTag("background_dialog_host")) {
                    if (shown) PlayerBackgroundDialog(container) { shown = false }
                    else top.yukonga.miuix.kmp.basic.TextButton("再次打开", onClick = { shown = true })
                }
            } } }
            compose.waitUntil(5000) { compose.onAllNodesWithTag("player_dynamic_background").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("player_dynamic_background").assertIsOn().performClick()
            compose.waitUntil(5000) { !container.settings.state.value.playerDynamicBackground }
            compose.onNodeWithTag("player_static_background").assertExists()
            compose.onNodeWithTag("player_artwork_background").assertDoesNotExist()
            compose.onNodeWithText("完成").performScrollTo().performClick()
            compose.waitUntil(5000) { !shown }
            compose.onNodeWithText("再次打开").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("player_dynamic_background").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("player_dynamic_background").assertIsOff().performClick()
            compose.waitUntil(5000) { container.settings.state.value.playerDynamicBackground }
            compose.onNodeWithTag("player_artwork_background").assertExists()
            compose.onNodeWithTag("player_static_background").assertDoesNotExist()
            assertTrue(container.settings.snapshot().playerDynamicBackground)
        } finally { container.close() }
    }

    private fun renderedBackground() = compose.onNodeWithTag("background_render").captureToImage().asAndroidBitmap()

    private fun redBlueBalance(bitmap: Bitmap): Int {
        var balance = 0; var samples = 0
        for (y in 0 until bitmap.height step 8) for (x in 0 until bitmap.width step 8) {
            val color = bitmap.getPixel(x, y)
            balance += (color ushr 16 and 255) - (color and 255)
            samples++
        }
        return balance / samples
    }

    @Test fun artworkColoursFollowCoverChangesAndMissingArtworkReturnsToNeutral() {
        val root = File(context.cacheDir, "background-colours-${UUID.randomUUID()}").apply { mkdirs() }
        val warm = solidCover(root, "warm.png", 0xffe45b26.toInt())
        val cool = solidCover(root, "cool.png", 0xff245cde.toInt())
        var cover by mutableStateOf(warm.toURI().toString())
        compose.mainClock.autoAdvance = false
        try {
            compose.setContent { LeiTheme(AppearanceSettings(themeMode = ThemeMode.LIGHT)) {
                PlayerBackdrop(cover, Modifier.size(260.dp, 360.dp).testTag("background_render"), active = false)
            } }
            fun awaitBalance(predicate: (Int) -> Boolean) = compose.waitUntil(10_000) {
                compose.mainClock.advanceTimeBy(100)
                predicate(redBlueBalance(renderedBackground()))
            }
            awaitBalance { it > 20 }
            compose.runOnIdle { cover = cool.toURI().toString() }
            awaitBalance { it < -20 }
            compose.mainClock.advanceTimeBy(1200)
            renderedBackground().let { bitmap -> File(context.externalCacheDir, "player-flowing-cover-cool.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            compose.runOnIdle { cover = "" }
            awaitBalance { kotlin.math.abs(it) < 15 }
            compose.runOnIdle { cover = File(root, "missing.png").toURI().toString() }
            compose.mainClock.advanceTimeBy(1200)
            assertTrue(kotlin.math.abs(redBlueBalance(renderedBackground())) < 15)
        } finally { compose.mainClock.autoAdvance = true; root.deleteRecursively() }
    }

    @Test fun aboutMotionFlowsWithArtworkColoursAndStopsWhenInactive() {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 33 &&
            top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported())
        val root = File(context.cacheDir, "background-motion-${UUID.randomUUID()}").apply { mkdirs() }
        val cover = solidCover(root, "orange.png", 0xffe45b26.toInt()).toURI().toString()
        var active by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        try {
            compose.setContent { LeiTheme(AppearanceSettings(themeMode = ThemeMode.DARK)) {
                PlayerBackdrop(cover, Modifier.size(260.dp, 360.dp).testTag("background_render"), active = active)
            } }
            compose.waitUntil(10_000) {
                compose.mainClock.advanceTimeBy(100)
                redBlueBalance(renderedBackground()) > 20
            }
            compose.mainClock.advanceTimeBy(1500)
            val before = renderedBackground()
            compose.mainClock.advanceTimeBy(900)
            val after = renderedBackground()
            var changed = 0
            for (y in 0 until before.height step 4) for (x in 0 until before.width step 4) {
                if (before.getPixel(x, y) != after.getPixel(x, y)) changed++
            }
            assertTrue("About's colour pools must move even with a single-colour cover", changed > 200)
            File(context.externalCacheDir, "player-flowing-cover-warm.png").outputStream().use {
                after.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.runOnIdle { active = false }
            compose.mainClock.advanceTimeBy(1500)
            val stopped = renderedBackground()
            compose.mainClock.advanceTimeBy(900)
            assertTrue("Hidden player backgrounds must stop rendering motion", stopped.sameAs(renderedBackground()))
        } finally { compose.mainClock.autoAdvance = true; root.deleteRecursively() }
    }
}

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
            settings.edit { it.copy(themeMode = ThemeMode.DARK, playerBackgroundBlur = 72f) }
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
                PlayerBackgroundDialog(container) {}
            } } }
            compose.waitUntil(10000) { compose.onAllNodesWithTag("player_background_blur").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("player_background_blur").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(81f) }
            compose.waitUntil(5000) { container.settings.state.value.playerBackgroundBlur == 81f }
            compose.onNodeWithTag("player_background_blur").performTouchInput {
                swipe(Offset(width * .81f, centerY), Offset(0f, centerY), 600)
            }
            compose.waitUntil(5000) { container.settings.state.value.playerBackgroundBlur == 0f }
            compose.onNodeWithTag("player_background_blur").performTouchInput {
                swipe(Offset(24f, centerY), Offset(width.toFloat(), centerY), 600)
            }
            compose.waitUntil(5000) { container.settings.state.value.playerBackgroundBlur == 100f }
            compose.onNodeWithTag("player_background_reset").performClick()
            compose.waitUntil(5000) { container.settings.state.value.playerBackground.isEmpty() }
            compose.onNodeWithTag("player_background_reset").assertIsNotEnabled()
            compose.onNodeWithTag("player_background_blur").assertIsNotEnabled()
            assertEquals(listOf(900L), container.playbackQueue.state.value.songs.map { it.id })
            assertFalse(container.playerController.state.value.playing)
        } finally { container.playerBackgrounds.clear(); container.close(); root.deleteRecursively() }
    }
}

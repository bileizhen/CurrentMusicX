package io.github.currencortex.music

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.profile.AvatarCrop
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.profile.AvatarCropDialog
import io.github.currencortex.music.feature.profile.avatarCropBytes
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class AvatarCropTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dragAndZoomUploadTheDisplayedRegionAndResetRestoresCenter() {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val file = File(context.cacheDir, "avatar-crop-fixture.png")
        val source = Bitmap.createBitmap(360, 240, Bitmap.Config.ARGB_8888)
        Canvas(source).apply {
            for ((index, color) in listOf(Color.RED, Color.GREEN, Color.BLUE).withIndex()) {
                drawRect(index * 120f, 0f, (index + 1) * 120f, 240f, Paint().apply { this.color = color })
            }
        }
        file.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }; source.recycle()
        var submitted: AvatarCrop? = null
        compose.setContent { LeiTheme(AppearanceSettings()) {
            top.yukonga.miuix.kmp.basic.Scaffold {
                AvatarCropDialog(Uri.fromFile(file), false, {}, { submitted = it })
            }
        } }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("avatar_crop_preview").fetchSemanticsNodes().isNotEmpty() }
        fun moveCrop() {
            compose.onNodeWithTag("avatar_crop_zoom").performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
            compose.onNodeWithTag("avatar_crop_preview").performTouchInput {
                swipe(center.copy(x = width * .9f), center.copy(x = width * .1f), 500)
            }
        }
        moveCrop()
        compose.onNodeWithTag("avatar_crop_reset").performClick()
        compose.onNodeWithTag("confirm_avatar_upload").performClick()
        assertEquals(AvatarCrop(), submitted)
        submitted = null
        moveCrop()
        val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.externalCacheDir, "avatar-crop-preview.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithTag("confirm_avatar_upload").performClick()
        val crop = requireNotNull(submitted)
        assertEquals(2f, crop.zoom, .001f)
        assertTrue(crop.centerX > .7f)
        val bytes = avatarCropBytes(context.contentResolver, Uri.fromFile(file), crop)
        val result = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        try {
            assertEquals(120, result.width); assertEquals(120, result.height)
            val pixel = result.getPixel(60, 60)
            assertTrue("Uploaded square must contain the selected blue region", Color.blue(pixel) > 220 && Color.red(pixel) < 30 && Color.green(pixel) < 30)
        } finally { result.recycle(); file.delete() }
    }
}

package io.github.currencortex.music

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.AudioCache
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.core.media.AudioRequest
import io.github.currencortex.music.core.media.AudioSourceResolver
import io.github.currencortex.music.core.network.RequestSession
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.theme.deleteCustomFont
import io.github.currencortex.music.ui.theme.importCustomFont
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LocalMusicAndFontTest {
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()

    @Test fun importFontMakesPrivateCopyAndInvalidFileDoesNotReplaceIt() {
        val uri = Uri.parse("android.resource://${context.packageName}/${R.font.lxgw_wenkai_regular}")
        val name = importCustomFont(context, uri)
        val imported = File(context.filesDir, "custom-fonts/$name")
        try {
            assertTrue(imported.isFile)
            val bad = File(context.cacheDir, "diagnostics/bad-font-${UUID.randomUUID()}")
            bad.parentFile?.mkdirs(); bad.writeText("not a font")
            try {
                val badUri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", bad)
                assertThrows(Exception::class.java) { importCustomFont(context, badUri) }
                assertTrue(imported.isFile)
            } finally { bad.delete() }
        } finally { deleteCustomFont(context, name) }
        assertFalse(imported.exists())
    }

}

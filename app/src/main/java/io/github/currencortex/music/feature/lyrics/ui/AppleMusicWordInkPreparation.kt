package io.github.currencortex.music.feature.lyrics.ui

import android.content.Context
import android.os.Trace
import androidx.compose.ui.text.TextLayoutInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import io.github.currencortex.music.feature.lyrics.model.LyricWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

// A duet and the next cue may request several weights together. Keep the CPU/bitmap
// work serial across rows, while cancellation removes obsolete queued requests.
private val appleWordInkDispatcher = Dispatchers.Default.limitedParallelism(1)

internal suspend fun prepareAppleMusicWordInk(context: Context, input: TextLayoutInput,
    words: List<LyricWord>, sustained: List<Boolean>, weightIndex: Int): AppleMusicWordInk? {
    var unpublished: AppleMusicWordInk? = null
    try {
        val result = withContext(appleWordInkDispatcher) {
            val worker = currentCoroutineContext()
            worker.ensureActive()
            Trace.beginSection("AppleLyrics.wordInk.$weightIndex")
            try {
                // Compose's Android resolver factory explicitly supports background
                // Paragraph creation; its shared typeface caches are synchronized.
                // Never measure/draw a UI-owned paragraph or share its mutable paint.
                val measurer = TextMeasurer(createFontFamilyResolver(context), input.density,
                    input.layoutDirection, cacheSize = 0)
                val layout = measurer.measure(input.text, input.style, input.overflow,
                    input.softWrap, input.maxLines, input.placeholders, input.constraints)
                AppleMusicWordInk.create(layout, words, sustained, measurer, input.density,
                    input.layoutDirection, checkActive = { worker.ensureActive() }).also {
                    unpublished = it
                    worker.ensureActive()
                }
            } finally { Trace.endSection() }
        }
        unpublished = null
        return result
    } finally {
        // withContext checks cancellation before delivery. Reclaim an atlas that was
        // completed just as its row/layout disappeared, before it can reach a draw.
        unpublished?.discardUnpublished()
    }
}

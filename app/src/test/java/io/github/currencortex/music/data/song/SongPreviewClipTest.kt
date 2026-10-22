package io.github.currencortex.music.data.song

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class SongPreviewClipTest {
    private fun raw(value: String) = Json.parseToJsonElement(value).jsonObject
    @Test fun chorusUsesMillisecondsMatchesTheSongAndCapsTheAudition() {
        val clip = songPreviewClip(raw("""{"chorus":[{"id":8,"startTime":2000,"endTime":4000},{"id":9,"startTime":47500,"endTime":88500}]}"""), 9, 180000)
        assertEquals(SongPreviewClip(47500, 67500, true), clip)
    }
    @Test fun rangeCannotRunBeyondTheTrackAndInvalidMetadataUsesALabelledSample() {
        assertEquals(SongPreviewClip(47500, 50000, true), songPreviewClip(raw("""{"data":[{"id":9,"startTime":47500,"endTime":88500}]}"""), 9, 50000))
        assertFalse(songPreviewClip(raw("""{"chorus":[{"id":8,"startTime":2,"endTime":4000}]}"""), 9, 180000).chorus)
        assertEquals(SongPreviewClip(0, 700, false), songPreviewClip(null, 9, 700))
        assertFalse(songPreviewClip(raw("""{"chorus":[{"id":9,"startTime":6000,"endTime":4000}]}"""), 9, 180000).chorus)
    }
}

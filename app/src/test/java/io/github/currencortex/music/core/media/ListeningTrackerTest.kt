package io.github.currencortex.music.core.media
import org.junit.Assert.*
import org.junit.Test
class ListeningTrackerTest {
    @Test fun countsOnlyElapsedPlayingTimeAcrossPauseAndResume() {
        val tracker=ListeningTracker();tracker.update(1000,true);tracker.update(3000,false)
        tracker.update(9000,true);assertEquals(3000L,tracker.drain(10000));assertEquals(1000L,tracker.drain(11000))
        tracker.update(12000,false);assertEquals(1000L,tracker.drain(22000));assertEquals(0L,tracker.drain(23000))
    }
    @Test fun seekPositionsCannotIncreaseListeningTimeAndNegativeClockIsIgnored() {
        val tracker=ListeningTracker();tracker.update(1000,true);assertEquals(200L,tracker.drain(1200))
        assertEquals(0L,tracker.drain(1100));tracker.update(1100,false);assertEquals(0L,tracker.drain(2000))
    }
}

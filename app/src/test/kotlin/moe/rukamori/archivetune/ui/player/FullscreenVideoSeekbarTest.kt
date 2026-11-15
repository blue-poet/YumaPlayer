package moe.rukamori.archivetune.ui.player

import moe.rukamori.archivetune.ui.player.player_0.full.videoSeekTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FullscreenVideoSeekbarTest {
    @Test fun seekTargetsAreBoundedToTheSongDuration() {
        assertEquals(42_000L, videoSeekTarget(42_000f, 120_000L))
        assertEquals(0L, videoSeekTarget(-200f, 120_000L))
        assertEquals(120_000L, videoSeekTarget(150_000f, 120_000L))
    }

    @Test fun unavailableDurationAndInvalidPositionsCannotSeek() {
        assertNull(videoSeekTarget(10f, 0L))
        assertNull(videoSeekTarget(10f, -1L))
        assertNull(videoSeekTarget(Float.NaN, 120_000L))
        assertNull(videoSeekTarget(Float.POSITIVE_INFINITY, 120_000L))
    }

}

package moe.rukamori.archivetune.ui.player

import moe.rukamori.archivetune.audiodsp.MusicVideoAlignment
import moe.rukamori.archivetune.sponsorblock.SponsorBlockSegment
import moe.rukamori.archivetune.ui.player.canvas.playback.MusicVideoTimeline
import moe.rukamori.archivetune.ui.player.canvas.playback.musicVideoAlignmentCacheKey
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class MusicVideoTimelineTest {
    @Test fun skipsMiddleSceneAndKeepsLaterSeeksOnTheMusicClock() {
        val timeline = MusicVideoTimeline(listOf(SponsorBlockSegment(60_000, 75_000)))
        assertEquals(59_999L, timeline.videoPositionAt(59_999))
        assertEquals(75_000L, timeline.videoPositionAt(60_000))
        assertEquals(105_000L, timeline.videoPositionAt(90_000))
        assertEquals(60_000L, timeline.musicPositionAt(68_000))
        assertEquals(90_000L, timeline.musicPositionAt(105_000))
        assertTrue(timeline.isSkipped(60_000))
        assertFalse(timeline.isSkipped(75_000))
    }

    @Test fun handlesIntroAndMultipleScenesWithoutDoubleCountingOverlaps() {
        val timeline = MusicVideoTimeline(listOf(
            SponsorBlockSegment(30_000, 40_000),
            SponsorBlockSegment(0, 5_000),
            SponsorBlockSegment(35_000, 45_000),
            SponsorBlockSegment(80_000, 85_000),
        ))
        assertEquals(5_000L, timeline.videoPositionAt(0))
        assertEquals(45_000L, timeline.videoPositionAt(25_000))
        assertEquals(85_000L, timeline.videoPositionAt(60_000))
        assertEquals(110_000L, timeline.videoPositionAt(85_000))
        assertEquals(-500L, timeline.videoPositionAt(-500))
        assertEquals(3, timeline.segments.size)
    }

    @Test fun removesVideoIntroBeforeMeasuringOffsetSoItIsNotAppliedTwice() {
        val random = Random(7)
        val song = FloatArray(2_000) { random.nextFloat() }
        val video = FloatArray(song.size + 250) { if (it < 250) 0f else song[it - 250] }
        val timeline = MusicVideoTimeline(listOf(SponsorBlockSegment(0, 5_000)))
        val musicOnly = timeline.musicOnlyEnvelope(video)
        assertArrayEquals(song, musicOnly, 0f)
        assertEquals(0L, MusicVideoAlignment.estimate(song, musicOnly)!!.offsetMs)
        assertEquals(17_000L, timeline.videoPositionAt(12_000))
    }

    @Test fun removesMiddleSceneFromAlignmentEnvelope() {
        val random = Random(11)
        val song = FloatArray(2_000) { random.nextFloat() }
        val video = FloatArray(song.size + 750) { frame ->
            when {
                frame < 500 -> song[frame]
                frame < 1_250 -> 0.9f
                else -> song[frame - 750]
            }
        }
        val timeline = MusicVideoTimeline(listOf(SponsorBlockSegment(10_000, 25_000)))
        assertArrayEquals(song, timeline.musicOnlyEnvelope(video), 0f)
        assertEquals(0L, MusicVideoAlignment.estimate(song, timeline.musicOnlyEnvelope(video))!!.offsetMs)
    }

    @Test fun allNonMusicSampleDoesNotProduceOutOfBoundsReads() {
        val timeline = MusicVideoTimeline(listOf(SponsorBlockSegment(0, 90_000)))
        assertTrue(timeline.musicOnlyEnvelope(FloatArray(4_000)).isEmpty())
        assertTrue(timeline.musicOnlyEnvelope(FloatArray(0)).isEmpty())
    }

    @Test fun skipTimingHasSeparateCacheAndEquivalentMergedSegmentsReuseIt() {
        val plain = musicVideoAlignmentCacheKey("song", "video", emptyList())
        val skipped = musicVideoAlignmentCacheKey("song", "video", listOf(SponsorBlockSegment(0, 5_000)))
        val merged = musicVideoAlignmentCacheKey("song", "video", listOf(
            SponsorBlockSegment(0, 3_000), SponsorBlockSegment(2_000, 5_000),
        ))
        assertEquals("song:video", plain)
        assertNotEquals(plain, skipped)
        assertEquals(skipped, merged)
        assertNotEquals(skipped, musicVideoAlignmentCacheKey("song", "other", listOf(SponsorBlockSegment(0, 5_000))))
    }
}

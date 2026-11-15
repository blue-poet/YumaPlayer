package moe.rukamori.archivetune.ui.player

import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import moe.rukamori.archivetune.ui.player.canvas.playback.MusicVideoSynchronizer
import moe.rukamori.archivetune.sponsorblock.SponsorBlockSegment
import org.junit.Test

class MusicVideoSynchronizerTest {
    private val song = mockk<Player>(relaxed = true)
    private val video = mockk<Player>(relaxed = true)
    private var songPosition = 12_000L
    private var videoPosition = 0L
    private var state = Player.STATE_READY
    private var now = 1_000L

    private fun synchronizer(offset: Long = 6_000L, segments: List<SponsorBlockSegment> = emptyList()): MusicVideoSynchronizer {
        every { song.currentPosition } answers { songPosition }
        every { song.playbackParameters } returns PlaybackParameters(1f)
        every { song.isPlaying } returns true
        every { video.currentPosition } answers { videoPosition }
        every { video.playbackState } answers { state }
        every { video.duration } returns 120_000L
        every { video.seekTo(any()) } answers { videoPosition = firstArg() }
        return MusicVideoSynchronizer(video, song, offset, skipSegments = segments) { now }
    }

    @Test fun skipsAnInsertedSceneImmediatelyAndDoesNotSeekBackIntoIt() {
        songPosition = 9_900L
        val sync = synchronizer(offset = 0, segments = listOf(SponsorBlockSegment(10_000, 25_000)))
        sync.sync(true)
        songPosition = 10_100L
        videoPosition = 10_100L
        now += 250L // A cut must bypass the normal drift correction cooldown.
        sync.sync(true)
        verify { video.seekTo(25_100L) }

        songPosition = 11_000L
        videoPosition = 26_000L
        now += 250L
        sync.sync(true)
        verify(exactly = 2) { video.seekTo(any()) }
        verify(exactly = 0) { song.seekTo(any()) }
    }

    @Test fun skipsShortClipEvenWhenDriftIsBelowNormalCorrectionThreshold() {
        songPosition = 9_900L
        val sync = synchronizer(offset = 0, segments = listOf(SponsorBlockSegment(10_000, 10_300)))
        sync.sync(true)
        songPosition = 10_100L
        videoPosition = 10_100L
        now += 250L
        sync.sync(true)
        verify { video.seekTo(10_400L) }
        verify(exactly = 0) { song.seekTo(any()) }
    }

    @Test fun skippedOutroDoesNotCauseRepeatedSeeksAtClampedVideoEnd() {
        songPosition = 200_000L
        val sync = synchronizer(offset = 0, segments = listOf(SponsorBlockSegment(110_000, 120_000)))
        repeat(4) {
            sync.sync(true)
            now += 250L
        }
        verify(exactly = 1) { video.seekTo(119_999L) }
        verify { video.playWhenReady = false }
    }

    @Test fun seekingSongForwardAndBackwardUsesMappedPositions() {
        val sync = synchronizer(offset = 0, segments = listOf(SponsorBlockSegment(10_000, 25_000)))
        songPosition = 30_000L
        sync.sync(true, forceSeek = true)
        verify { video.seekTo(45_000L) }
        songPosition = 5_000L
        sync.sync(true, forceSeek = true)
        verify { video.seekTo(5_000L) }
        verify(exactly = 0) { song.seekTo(any()) }
    }

    @Test fun startsAtMeasuredOffsetAndNeverSeeksTheSong() {
        synchronizer().sync(allowPlayback = true)
        verify { video.seekTo(18_000L) }
        verify(exactly = 0) { song.seekTo(any()) }
    }

    @Test fun smallDriftUsesSpeedCorrection() {
        val sync = synchronizer()
        sync.sync(true)
        videoPosition = 18_400L
        now += 2_000L
        sync.sync(true)
        verify { video.setPlaybackSpeed(0.97f) }
        verify(exactly = 1) { video.seekTo(any()) }
    }

    @Test fun bufferingDoesNotCauseRepeatedSeeks() {
        state = Player.STATE_BUFFERING
        val sync = synchronizer()
        repeat(10) {
            sync.sync(true)
            videoPosition = 0L
            now += 250L
        }
        verify(exactly = 1) { video.seekTo(any()) }
    }

    @Test fun explicitSongSeekTakesEffectEvenWhileVideoBuffers() {
        state = Player.STATE_BUFFERING
        val sync = synchronizer()
        sync.sync(true)
        songPosition = 50_000L
        sync.sync(true, forceSeek = true)
        verify { video.seekTo(56_000L) }
    }

    @Test fun largeDriftSeeksAfterCooldown() {
        val sync = synchronizer()
        sync.sync(true)
        songPosition = 30_000L
        now += 2_000L
        sync.sync(true)
        verify { video.seekTo(36_000L) }
    }

    @Test fun pausesWhileHiddenOrSongIsPaused() {
        val sync = synchronizer()
        sync.sync(false)
        verify { video.playWhenReady = false }
        every { song.isPlaying } returns false
        sync.sync(true)
        verify(exactly = 2) { video.playWhenReady = false }
    }

    @Test fun clampsBeforeVideoStartAndAfterVideoEnd() {
        val sync = synchronizer(offset = -20_000L)
        sync.sync(true)
        verify { video.seekTo(0L) }
        verify { video.playWhenReady = false }
        songPosition = 200_000L
        sync.sync(true, forceSeek = true)
        verify { video.seekTo(119_999L) }
        verify(exactly = 2) { video.playWhenReady = false }
    }

    @Test fun followsUserPlaybackSpeed() {
        val sync = synchronizer()
        every { song.playbackParameters } returns PlaybackParameters(1.5f)
        sync.sync(true)
        verify { video.setPlaybackSpeed(1.5f) }
    }
}

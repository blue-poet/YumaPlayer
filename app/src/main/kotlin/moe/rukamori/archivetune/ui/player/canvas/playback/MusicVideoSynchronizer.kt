@file:Suppress("UnstableApi")

package moe.rukamori.archivetune.ui.player.canvas.playback

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlin.math.abs
import moe.rukamori.archivetune.sponsorblock.SponsorBlockSegment

/** The song is always the clock. This controller only changes the silent video player. */
internal class MusicVideoSynchronizer(
    private val video: Player,
    private val song: Player,
    private val offsetMs: Long,
    skipSegments: List<SponsorBlockSegment> = emptyList(),
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private val timeline = MusicVideoTimeline(skipSegments)
    private var initialSeek = true
    private var lastSeekMs = Long.MIN_VALUE

    fun sync(allowPlayback: Boolean, forceSeek: Boolean = false) {
        if (video.playbackState == Player.STATE_IDLE) return
        val rawPosition = timeline.videoPositionAt(song.currentPosition.coerceAtLeast(0L) + offsetMs)
        val duration = video.duration
        val hasDuration = duration != C.TIME_UNSET && duration > 0
        val position = rawPosition.coerceAtLeast(0L).let {
            if (hasDuration) it.coerceAtMost(duration - 1L) else it
        }
        val insideVideo = rawPosition >= 0 && (!hasDuration || rawPosition < duration)
        val shouldPlay = allowPlayback && song.isPlaying && insideVideo
        video.playWhenReady = shouldPlay
        val baseSpeed = song.playbackParameters.speed
        val drift = video.currentPosition - position
        val now = nowMs()
        val ready = video.playbackState == Player.STATE_READY
        val mayCorrect = ready && (lastSeekMs == Long.MIN_VALUE || now - lastSeekMs >= 1_500L)
        val crossesCut = ready && insideVideo && (
            timeline.isSkipped(video.currentPosition) ||
                timeline.sectionAt(video.currentPosition) != timeline.sectionAt(position)
            )
        if (initialSeek || forceSeek || crossesCut || (mayCorrect && abs(drift) > 1_000L)) {
            video.seekTo(position)
            lastSeekMs = now
            initialSeek = false
            video.setPlaybackSpeed(baseSpeed)
        } else if (ready && shouldPlay) {
            // Small errors converge without repeatedly discarding buffered video.
            val correction = if (abs(drift) <= 60L) 0f
                else (-drift / 5_000f).coerceIn(-0.03f, 0.03f)
            video.setPlaybackSpeed(baseSpeed * (1f + correction))
        } else {
            video.setPlaybackSpeed(baseSpeed)
        }
    }
}

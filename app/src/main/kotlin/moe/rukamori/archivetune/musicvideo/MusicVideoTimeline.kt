package moe.rukamori.archivetune.musicvideo

import moe.rukamori.archivetune.audiodsp.MusicVideoAlignment
import moe.rukamori.archivetune.sponsorblock.SponsorBlockSegment
import kotlin.math.min

/** Maps the uninterrupted music clock onto a video containing non-music inserts. */
internal class MusicVideoTimeline(skips: List<SponsorBlockSegment>) {
    val segments: List<SponsorBlockSegment> = buildList {
        skips.filter { it.startMs >= 0L && it.endMs > it.startMs }
            .sortedBy { it.startMs }
            .forEach { segment ->
                val previous = lastOrNull()
                if (previous != null && segment.startMs <= previous.endMs) {
                    this[lastIndex] = previous.copy(endMs = maxOf(previous.endMs, segment.endMs))
                } else add(segment)
            }
    }

    fun videoPositionAt(musicMs: Long): Long {
        if (musicMs < 0L) return musicMs
        var position = musicMs
        for (segment in segments) {
            if (position < segment.startMs) break
            val length = segment.endMs - segment.startMs
            position = if (position > Long.MAX_VALUE - length) Long.MAX_VALUE else position + length
        }
        return position
    }

    fun musicPositionAt(videoMs: Long): Long {
        if (videoMs < 0L) return videoMs
        var removed = 0L
        for (segment in segments) {
            if (videoMs <= segment.startMs) break
            removed += min(videoMs, segment.endMs) - segment.startMs
        }
        return videoMs - removed
    }

    fun isSkipped(videoMs: Long): Boolean =
        segments.any { videoMs >= it.startMs && videoMs < it.endMs }

    fun sectionAt(videoMs: Long): Int = segments.count { videoMs >= it.endMs }

    /** Resample onto a music-only clock, retaining exact segment lengths between 20ms frames. */
    fun musicOnlyEnvelope(video: FloatArray): FloatArray {
        if (segments.isEmpty() || video.isEmpty()) return video
        val hop = MusicVideoAlignment.HOP_MS.toLong()
        val count = (musicPositionAt(video.size.toLong() * hop) / hop).toInt().coerceAtLeast(0)
        return FloatArray(count) { frame ->
            val source = videoPositionAt(frame.toLong() * hop).toDouble() / hop
            val left = source.toInt().coerceIn(0, video.lastIndex)
            val right = (left + 1).coerceAtMost(video.lastIndex)
            val fraction = (source - left).coerceIn(0.0, 1.0).toFloat()
            video[left] + (video[right] - video[left]) * fraction
        }
    }
}

internal fun musicVideoAlignmentCacheKey(
    songId: String,
    videoId: String,
    skips: List<SponsorBlockSegment>,
): String {
    val segments = MusicVideoTimeline(skips).segments
    val pair = "$songId:$videoId"
    return if (segments.isEmpty()) pair
    else pair + ":sb1:" + segments.joinToString(";") { "${it.startMs}-${it.endMs}" }
}

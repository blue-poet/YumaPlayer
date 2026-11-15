package moe.rukamori.archivetune.audiodsp

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Positive offsets mean the same sound occurs later in the video than in the song. */
object MusicVideoAlignment {
    const val HOP_MS = 20
    private const val MIN_OVERLAP_FRAMES = 500 // Ten seconds, to reject coincidental short matches.
    private const val MAX_LAG_FRAMES = 3_000 // One minute of intro or different edits.

    private const val PEAK_GUARD_FRAMES = 10 // Verify peaks just beyond the allowed offset before accepting an edge match.

    data class Match(val offsetMs: Long, val confidence: Double)

    fun envelope(samples: FloatArray, sampleRate: Double): FloatArray {
        require(sampleRate.isFinite() && sampleRate > 0)
        val exactHop = sampleRate * HOP_MS / 1000.0
        val window = max(1, (exactHop * 2).roundToInt())
        if (samples.size < window) return FloatArray(0)
        val count = ((samples.size - window) / exactHop).toInt() + 1
        return FloatArray(count) { frame ->
            // Round each absolute timestamp, so fractional samples do not accumulate into drift.
            val start = (frame * exactHop).roundToInt()
            var energy = 0.0
            for (i in start until start + window) {
                val value = samples[i].toDouble()
                energy += value * value
            }
            sqrt(energy / window).toFloat()
        }
    }

    /** Normalized correlation tolerates different mastering levels; overlap weighting rejects repeats. */
    fun estimate(song: FloatArray, video: FloatArray): Match? {
        if (min(song.size, video.size) < MIN_OVERLAP_FRAMES) return null
        val lower = max(-MAX_LAG_FRAMES - PEAK_GUARD_FRAMES, MIN_OVERLAP_FRAMES - song.size)
        val upper = min(MAX_LAG_FRAMES + PEAK_GUARD_FRAMES, video.size - MIN_OVERLAP_FRAMES)
        if (lower >= upper) return null
        val scores = DoubleArray(upper - lower + 1) { Double.NEGATIVE_INFINITY }
        var bestLag = 0
        var bestScore = Double.NEGATIVE_INFINITY
        var bestConfidence = 0.0
        val fullOverlap = min(song.size, video.size).toDouble()
        for (lag in lower..upper) {
            val songStart = max(0, -lag)
            val videoStart = max(0, lag)
            val overlap = min(song.size - songStart, video.size - videoStart)
            var sumSong = 0.0
            var sumVideo = 0.0
            var squareSong = 0.0
            var squareVideo = 0.0
            var cross = 0.0
            for (i in 0 until overlap) {
                val a = song[songStart + i].toDouble()
                val b = video[videoStart + i].toDouble()
                sumSong += a
                sumVideo += b
                squareSong += a * a
                squareVideo += b * b
                cross += a * b
            }
            val varianceSong = max(0.0, squareSong - sumSong * sumSong / overlap)
            val varianceVideo = max(0.0, squareVideo - sumVideo * sumVideo / overlap)
            val denominator = sqrt(varianceSong * varianceVideo)
            if (denominator < 1e-9) continue
            val confidence = ((cross - sumSong * sumVideo / overlap) / denominator).coerceIn(-1.0, 1.0)
            val score = confidence * sqrt(overlap / fullOverlap)
            scores[lag - lower] = score
            if (score > bestScore) {
                bestScore = score
                bestConfidence = confidence
                bestLag = lag
            }
        }
        // A peak at the searched edge is not evidence that the true offset is inside the window.
        if (bestLag == lower || bestLag == upper || abs(bestLag) > MAX_LAG_FRAMES || bestConfidence < 0.65) return null
        val competitor = (lower..upper)
            .filter { abs(it - bestLag) > 10 }
            .maxOfOrNull { scores[it - lower] } ?: Double.NEGATIVE_INFINITY
        if (bestScore - competitor < 0.04) return null
        return Match(bestLag.toLong() * HOP_MS, bestConfidence)
    }
}

package moe.rukamori.archivetune.ui.player

import moe.rukamori.archivetune.audiodsp.MusicVideoAlignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class MusicVideoAlignmentTest {
    private fun recording(seed: Int = 7): FloatArray {
        val random = Random(seed)
        val amplitudes = FloatArray(450) { 0.1f + random.nextFloat() * 0.8f }
        return FloatArray(45_000) { i ->
            (sin(2 * PI * 100 * i / 1000) * amplitudes[i / 100]).toFloat()
        }
    }

    private fun envelope(samples: FloatArray) = MusicVideoAlignment.envelope(samples, 1000.0)

    @Test fun detectsVideoIntroDespiteDifferentVolume() {
        val song = recording()
        val video = FloatArray(song.size) { if (it >= 5_000) song[it - 5_000] * 0.35f else 0f }
        val result = MusicVideoAlignment.estimate(envelope(song), envelope(video))
        assertNotNull(result)
        assertEquals(5_000L, result!!.offsetMs)
    }

    @Test fun detectsSongIntroWithNegativeVideoOffset() {
        val video = recording()
        val song = FloatArray(video.size) { if (it >= 4_000) video[it - 4_000] else 0f }
        assertEquals(-4_000L, MusicVideoAlignment.estimate(envelope(song), envelope(video))!!.offsetMs)
    }

    @Test fun matchingCutsProduceMeasuredZero() {
        val song = envelope(recording())
        assertEquals(0L, MusicVideoAlignment.estimate(song, song)!!.offsetMs)
    }

    @Test fun unrelatedAudioAndSilenceAreNotTreatedAsSynchronized() {
        assertNull(MusicVideoAlignment.estimate(envelope(recording(7)), envelope(recording(99))))
        assertNull(MusicVideoAlignment.estimate(FloatArray(2250), FloatArray(2250)))
        assertNull(MusicVideoAlignment.estimate(FloatArray(2250) { 1f }, FloatArray(2250) { 2f }))
    }

    @Test fun shortSamplesAndAmbiguousRepeatsAreRejected() {
        assertNull(MusicVideoAlignment.estimate(FloatArray(100) { it.toFloat() }, FloatArray(100)))
        val repeat = FloatArray(2250) { (it % 50).toFloat() }
        assertNull(MusicVideoAlignment.estimate(repeat, repeat))
    }

    @Test fun fractionalSamplesPerHopDoNotAccumulateTimingError() {
        val sampleRate = 11025
        val random = Random(42)
        val amplitude = FloatArray(450) { 0.1f + random.nextFloat() * 0.8f }
        val song = FloatArray(45 * sampleRate) { i ->
            (sin(2 * PI * 440 * i / sampleRate) * amplitude[i * 10 / sampleRate]).toFloat()
        }
        val intro = 15 * sampleRate
        val video = FloatArray(song.size) { if (it >= intro) song[it - intro] else 0f }
        val result = MusicVideoAlignment.estimate(
            MusicVideoAlignment.envelope(song, sampleRate.toDouble()),
            MusicVideoAlignment.envelope(video, sampleRate.toDouble()),
        )
        assertEquals(15_000L, result!!.offsetMs)
    }

    @Test fun peakOnSearchBoundaryIsRejected() {
        val song = envelope(recording())
        val video = FloatArray(song.size) { if (it >= 1500) song[it - 1500] else 0f }
        assertNull(MusicVideoAlignment.estimate(song, video))
    }
}

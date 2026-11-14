package moe.rukamori.archivetune.ui.player

import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import moe.rukamori.archivetune.lyrics.model.LyricsEntry
import moe.rukamori.archivetune.lyrics.model.WordTimestamp
import moe.rukamori.archivetune.ui.player.player_0.full.videoCaptionEntry
import moe.rukamori.archivetune.ui.player.player_0.full.videoCaptionModel
import org.junit.Assert.*
import org.junit.Test

class VideoWordLyricsTest {
    private val lines = listOf(
        LyricsEntry(1_000L, "First line"),
        LyricsEntry(3_000L, "Second line", durationMs = 1_000L),
        LyricsEntry(6_000L, "Instrumental", isInstrumental = true),
        LyricsEntry(10_000L, "Last line"),
    )

    @Test fun selectsLineExactlyAtItsTimestamp() {
        assertNull(videoCaptionEntry(lines, 999L, 0, true))
        assertEquals("First line", videoCaptionEntry(lines, 1_000L, 0, true)?.text)
        assertEquals("First line", videoCaptionEntry(lines, 2_999L, 0, true)?.text)
        assertEquals("Second line", videoCaptionEntry(lines, 3_000L, 0, true)?.text)
    }

    @Test fun followsForwardAndBackwardSeeks() {
        assertEquals("Last line", videoCaptionEntry(lines, 11_000L, 0, true)?.text)
        assertEquals("First line", videoCaptionEntry(lines, 1_500L, 0, true)?.text)
    }

    @Test fun appliesManualLyricsOffsets() {
        assertEquals("First line", videoCaptionEntry(lines, 500L, 500, true)?.text)
        assertNull(videoCaptionEntry(lines, 1_200L, -500, true))
        assertEquals("First line", videoCaptionEntry(lines, 1_500L, -500, true)?.text)
        assertNull(videoCaptionEntry(listOf(LyricsEntry(0L, "Opening")), 0L, -500, true))
    }

    @Test fun clearsGapsAndInstrumentals() {
        assertNull(videoCaptionEntry(lines, 4_000L, 0, true))
        assertNull(videoCaptionEntry(lines, 6_500L, 0, true))
        assertNull(videoCaptionEntry(lines, 21_000L, 0, true))
    }

    @Test fun ignoresUnavailableOrUnsyncedLyrics() {
        assertNull(videoCaptionEntry(emptyList(), 1_500L, 0, true))
        assertNull(videoCaptionEntry(lines, 1_500L, 0, false))
        assertNull(videoCaptionEntry(listOf(LyricsEntry(0L, "")), 500L, 0, true))
    }

    @Test fun preservesRealWordTimingAndSeparatesBackingVocals() {
        val entry = LyricsEntry(1_000L, "Hello world", words = listOf(
            WordTimestamp("Hello ", 1.0, 1.6),
            WordTimestamp("echo", 1.2, 1.5, isBackground = true),
            WordTimestamp("world", 1.6, 2.0),
        ))
        val model = videoCaptionModel(entry) as KaraokeLine.MainKaraokeLine
        assertEquals(listOf("Hello ", "world"), model.syllables.map { it.content })
        assertEquals(listOf(1_000, 1_600), model.syllables.map { it.start })
        assertEquals(listOf(1_600, 2_000), model.syllables.map { it.end })
    }

    @Test fun lineOnlyLyricsDoNotInventWordTimings() {
        val model = videoCaptionModel(LyricsEntry(1_000L, "Line timed lyrics"))
        assertNotNull(model)
        assertFalse(model is KaraokeLine)
    }
}

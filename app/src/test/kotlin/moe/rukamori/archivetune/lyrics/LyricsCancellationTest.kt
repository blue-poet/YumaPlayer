package moe.rukamori.archivetune.lyrics

import android.media.MediaDataSource
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import moe.rukamori.archivetune.constants.PreferredLyricsProvider
import moe.rukamori.archivetune.lyrics.providers.YouTubeAutoCaptionsProvider
import moe.rukamori.archivetune.playback.smart.AudioDecoder
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class LyricsCancellationTest {
    @After fun tearDown() { unmockkAll() }

    @Test fun aCancelledCaptionCheckDoesNotBecomeANotFoundResult() = runTest {
        mockkObject(YouTubeAutoCaptionsProvider)
        val cancellation = CancellationException("leaving provider menu")
        coEvery { YouTubeAutoCaptionsProvider.getLyrics(any(), any(), any(), any(), any()) } throws cancellation
        try {
            LyricsCandidatesManager.checkCandidate(
                PreferredLyricsProvider.YOUTUBE_AUTO_CAPTIONS, "abcdefghijk", "Song", "Artist", null, 100,
            )
            fail("Expected cancellation")
        } catch (actual: CancellationException) {
            // Coroutine stack-trace recovery may copy the exception across suspension.
            assertEquals(cancellation.message, actual.message)
            assertTrue(actual === cancellation || actual.cause === cancellation)
        }
    }

    @Test fun cancelledAlignmentDoesNotAllocateAnAndroidDecoder() {
        val source = mockk<MediaDataSource>()
        val cancellation = CancellationException("song changed")
        try {
            AudioDecoder.decodeForAlignment(source) { throw cancellation }
            fail("Expected cancellation")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }
}

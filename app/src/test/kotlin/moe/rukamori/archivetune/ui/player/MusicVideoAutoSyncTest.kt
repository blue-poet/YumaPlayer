package moe.rukamori.archivetune.ui.player

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import moe.rukamori.archivetune.ui.player.canvas.playback.MusicVideoAutoSync
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicVideoAutoSyncTest {
    @Test fun identicalVideoAndSongNeedNoAnalysis() = runTest {
        val context = mockk<Context>()
        assertEquals(0L, MusicVideoAutoSync.measure(context, "same", "same"))
        verify(exactly = 0) { context.getSharedPreferences(any(), any()) }
    }

    @Test fun cachedNegativeOffsetIsRestoredWithoutFetchingAudio() = runTest {
        val context = mockk<Context>()
        val preferences = mockk<SharedPreferences>()
        every { context.getSharedPreferences("music_video_alignment_v1", Context.MODE_PRIVATE) } returns preferences
        every { preferences.contains("song:video") } returns true
        every { preferences.getLong("song:video", 0L) } returns -3_250L

        assertEquals(-3_250L, MusicVideoAutoSync.measure(context, "song", "video"))
        verify(exactly = 0) { preferences.edit() }
    }
}

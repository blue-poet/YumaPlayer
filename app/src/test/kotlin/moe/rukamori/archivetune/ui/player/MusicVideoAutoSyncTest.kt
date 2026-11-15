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

    @Test fun corruptCacheEntriesAreRemovedBeforeRecheckingTheSharedCache() = runTest {
        val context = mockk<Context>()
        val preferences = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        var corrupt = true
        every { context.getSharedPreferences("music_video_alignment_v1", Context.MODE_PRIVATE) } returns preferences
        every { preferences.contains("song:video") } returns true
        every { preferences.getLong("song:video", 0L) } answers {
            if (corrupt) throw ClassCastException("old invalid cache value") else -1_000L
        }
        every { preferences.edit() } returns editor
        every { editor.remove("song:video") } answers {
            // Simulate a valid concurrent writer after the corrupt entry is purged.
            corrupt = false
            editor
        }
        assertEquals(-1_000L, MusicVideoAutoSync.measure(context, "song", "video"))
        verify(exactly = 1) { editor.remove("song:video") }
        verify(exactly = 1) { editor.apply() }
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

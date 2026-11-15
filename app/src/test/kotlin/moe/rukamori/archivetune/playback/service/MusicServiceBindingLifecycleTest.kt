package moe.rukamori.archivetune.playback.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.just
import io.mockk.Runs
import android.content.ServiceConnection
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.host.PlayerConnectionHolder
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MusicServiceBindingLifecycleTest {
    private val holder = PlayerConnectionHolder()
    private val activity = mockk<MainActivity> {
        every { playerConnectionHolder } returns holder
        every { isFinishing } returns false
    }
    private val connection = mockk<PlayerConnection>(relaxed = true)
    private val binding = MusicServiceBinding(activity).also {
        it.playerConnection = connection
        holder.connection.value = connection
    }

    @Test fun serviceDeathStillUnbindsTheRegisteredConnectionOnStop() {
        every { activity.unbindService(any()) } just Runs
        val flag = MusicServiceBinding::class.java.getDeclaredField("isMusicServiceBound").apply { isAccessible = true }
        flag.setBoolean(binding, true)
        val callbackField = MusicServiceBinding::class.java.getDeclaredField("serviceConnection").apply { isAccessible = true }
        val callback = requireNotNull(ServiceConnection::class.java.cast(callbackField.get(binding)))
        callback.onServiceDisconnected(null)
        assertTrue(binding.isMusicServiceBound)
        binding.onStop()
        assertFalse(binding.isMusicServiceBound)
        verify(exactly = 1) { activity.unbindService(callback) }
    }

    @Test fun lateConnectionCallbackCannotRepublishAnUnboundConnection() {
        val callbackField = MusicServiceBinding::class.java.getDeclaredField("serviceConnection").apply { isAccessible = true }
        val callback = requireNotNull(ServiceConnection::class.java.cast(callbackField.get(binding)))
        binding.onStop()
        callback.onServiceConnected(null, null)
        assertNull(holder.connection.value)
    }

    @Test fun stopDisposesConnectionWithoutWaitingForDisconnectCallback() {
        binding.onStop()
        verify(exactly = 1) { connection.dispose() }
        assertNull(holder.connection.value)
    }

    @Test fun destroyAlsoDisposesAndDropsConnection() {
        binding.onDestroy()
        verify(exactly = 1) { connection.dispose() }
        assertNull(binding.playerConnection)
        assertNull(holder.connection.value)
    }

    @Test fun oldActivityCleanupPreservesNewActivityConnection() {
        val replacement = mockk<PlayerConnection>(relaxed = true)
        holder.connection.value = replacement
        binding.onStop()
        verify(exactly = 1) { connection.dispose() }
        verify(exactly = 0) { replacement.dispose() }
        assertSame(replacement, holder.connection.value)
    }
}

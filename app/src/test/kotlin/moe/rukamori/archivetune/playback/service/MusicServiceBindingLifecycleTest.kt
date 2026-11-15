package moe.rukamori.archivetune.playback.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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

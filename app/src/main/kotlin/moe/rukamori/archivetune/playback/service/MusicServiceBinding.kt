package moe.rukamori.archivetune.playback.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.constants.StopMusicOnTaskClearKey
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.MusicService.MusicBinder
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.navigation.MainIntentRouter
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.reportException

class MusicServiceBinding(
    private val activity: MainActivity,
) {
    var playerConnection by mutableStateOf<PlayerConnection?>(null)
        internal set

    var isMusicServiceBound: Boolean = false
        private set

    var intentRouter: MainIntentRouter? = null

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (!isMusicServiceBound) return
                if (moe.rukamori.archivetune.BuildConfig.DEBUG) {
                    moe.rukamori.archivetune.playback.PlaybackTiming.log("BINDER_CONNECTED")
                }
                if (service is MusicBinder) {
                    disposePlayerConnection()
                    val conn = PlayerConnection(activity.applicationContext, service, activity.database, activity.lifecycleScope)
                    playerConnection = conn
                    activity.playerConnectionHolder.connection.value = conn

                    intentRouter?.onServiceConnected(conn)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                // The binding remains registered until explicitly unbound, even after service death.
                intentRouter?.onServiceDisconnected()
                disposePlayerConnection()
                playerConnection = null
            }
        }

    fun onStart() {
        if (isMusicServiceBound) return
        isMusicServiceBound =
            activity.bindService(
                Intent(activity, MusicService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE,
            )
        intentRouter?.onStart()
    }

    fun safeUnbindMusicService() {
        if (!isMusicServiceBound) return
        isMusicServiceBound = false
        try {
            activity.unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
            reportException(e)
        } catch (e: Exception) {
            reportException(e)
        }
    }

    private fun disposePlayerConnection() {
        val connection = playerConnection ?: return
        connection.dispose()
        val holder = activity.playerConnectionHolder.connection
        if (holder.value === connection) holder.value = null
    }

    fun onStop() {
        // Explicit unbinding does not call onServiceDisconnected.
        disposePlayerConnection()
        safeUnbindMusicService()
    }

    fun onDestroy() {
        val shouldStopOnTaskClear =
            if (!activity.isFinishing) {
                false
            } else {
                activity.dataStore.get(StopMusicOnTaskClearKey, false)
            }

        if (shouldStopOnTaskClear) {
            playerConnection?.service?.stopAndClearPlayback(clearPersistentState = true)
            safeUnbindMusicService()
            activity.stopService(Intent(activity, MusicService::class.java))
        }
        disposePlayerConnection()
        safeUnbindMusicService()
        playerConnection = null
    }
}

package moe.rukamori.archivetune.ui.player.canvas.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import moe.rukamori.archivetune.sponsorblock.SponsorBlockSegment
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection

@Composable
internal fun SynchronizeMusicVideo(video: Player, isPlaying: Boolean, offsetMs: Long?, skipSegments: List<SponsorBlockSegment> = emptyList()) {
    LaunchedEffect(video) {
        video.repeatMode = Player.REPEAT_MODE_OFF
    }
    val song = LocalPlayerConnection.current?.player ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    val playing by rememberUpdatedState(isPlaying)
    val synchronizer = remember(video, song, offsetMs, skipSegments) {
        MusicVideoSynchronizer(video, song, offsetMs ?: 0L, skipSegments = skipSegments)
    }

    DisposableEffect(song, synchronizer, lifecycle) {
        fun sync(force: Boolean = false) {
            synchronizer.sync(
                allowPlayback = playing && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
                forceSeek = force,
            )
        }
        val listener = object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) = sync(force = true)

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = sync()
            override fun onIsPlayingChanged(isPlaying: Boolean) = sync()
        }
        song.addListener(listener)
        onDispose { song.removeListener(listener) }
    }

    LaunchedEffect(video, synchronizer, lifecycleState, isPlaying) {
        if (!lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
            video.pause()
            return@LaunchedEffect
        }
        while (isActive) {
            synchronizer.sync(allowPlayback = isPlaying)
            delay(250L)
        }
    }
}

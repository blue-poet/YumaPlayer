@file:Suppress("UnstableApi")

package moe.rukamori.archivetune.ui.player.canvas.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.Player
import moe.rukamori.archivetune.ui.player.canvas.playback.CanvasPlaybackController
import moe.rukamori.archivetune.ui.player.canvas.playback.SynchronizeMusicVideo
import moe.rukamori.archivetune.ui.player.canvas.playback.rememberCanvasPlaybackController
import moe.rukamori.archivetune.ui.player.player_0.PlayerCanvasState

internal data class VideoSession(
    val controller: CanvasPlaybackController?,
    val fullscreen: Boolean,
    val setFullscreen: (Boolean) -> Unit,
)

internal val LocalVideoSession = staticCompositionLocalOf<VideoSession?> { null }

/** The normal and fullscreen views share a decoder, buffering state and measured audio offset. */
@Composable
internal fun VideoSessionHost(
    canvas: PlayerCanvasState,
    isPlaying: Boolean,
    fullscreen: Boolean,
    setFullscreen: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val controller = if (canvas.isMusicVideo && canvas.artwork != null) {
        rememberCanvasPlaybackController(
            primaryUrl = canvas.primaryUrl,
            fallbackUrl = canvas.fallbackUrl,
            isPlaying = isPlaying,
            isMusicVideo = true,
            onReadyChange = canvas::onVideoReady,
        )
    } else null
    if (controller != null) {
        LaunchedEffect(controller.hasFailed) {
            if (controller.hasFailed) canvas.markVideoUnavailable()
        }
        controller.player?.let { player ->
            LaunchedEffect(player) { player.repeatMode = Player.REPEAT_MODE_OFF }
            SynchronizeMusicVideo(player, isPlaying, canvas.videoSyncOffsetMs)
        }
    }
    CompositionLocalProvider(
        LocalVideoSession provides VideoSession(controller, fullscreen, setFullscreen),
        content = content,
    )
}

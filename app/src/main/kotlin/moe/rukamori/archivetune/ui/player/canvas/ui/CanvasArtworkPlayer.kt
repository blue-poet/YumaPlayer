/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:Suppress("UnstableApi")

package moe.rukamori.archivetune.ui.player.canvas.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import moe.rukamori.archivetune.ui.player.canvas.playback.SynchronizeMusicVideo
import androidx.media3.common.Player
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import moe.rukamori.archivetune.ui.player.canvas.playback.rememberCanvasPlaybackController

@Composable
internal fun CanvasArtworkPlayer(
    primaryUrl: String?,
    fallbackUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    onReadyChange: (Boolean) -> Unit = {},
    isMusicVideo: Boolean = false,
    videoSyncOffsetMs: Long? = null,
    onPlaybackError: () -> Unit = {},
    isFullscreenSurface: Boolean = false,
) {
    val session = if (isMusicVideo) LocalVideoSession.current else null
    if (session?.fullscreen == true && !isFullscreenSurface) return
    val sharedController = session?.controller
    val controller = sharedController ?:
        rememberCanvasPlaybackController(
            primaryUrl = primaryUrl,
            fallbackUrl = fallbackUrl,
            isPlaying = isPlaying,
            onReadyChange = onReadyChange,
            isMusicVideo = isMusicVideo,
        )

    val currentOnPlaybackError by rememberUpdatedState(onPlaybackError)
    val currentOnReadyChange by rememberUpdatedState(onReadyChange)
    LaunchedEffect(controller, controller.isVideoReady) {
        if (sharedController != null) currentOnReadyChange(controller.isVideoReady)
    }
    LaunchedEffect(controller.hasFailed) {
        if (sharedController == null && controller.hasFailed) currentOnPlaybackError()
    }
    val player = controller.player ?: return

    LaunchedEffect(player, isMusicVideo, sharedController) {
        if (sharedController != null) return@LaunchedEffect
        player.repeatMode = if (isMusicVideo) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
        if (!isMusicVideo) player.setPlaybackSpeed(1f)
    }
    if (isMusicVideo && sharedController == null) {
        SynchronizeMusicVideo(player, isPlaying, videoSyncOffsetMs)
    }

    val alpha by animateFloatAsState(
        targetValue = if (controller.isVideoReady) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "canvasAlpha",
    )

    ContentFrame(
        player = player,
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        contentScale = resizeMode.toContentScale(),
        keepContentOnReset = false,
        shutter = {},
        modifier = modifier.alpha(alpha),
    )
}

private fun Int.toContentScale(): ContentScale =
    when (this) {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> ContentScale.Crop

        AspectRatioFrameLayout.RESIZE_MODE_FILL -> ContentScale.FillBounds

        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT,
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        -> ContentScale.Fit

        else -> ContentScale.Fit
    }

// Media3 ContentFrame and resize policies require this lint opt-in for the renderer adapter.
@file:Suppress("UnstableApi")

package moe.rukamori.archivetune.ui.player.player_0.full

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconButton
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.ui.settings.PlayerVideoTokens
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.flow.StateFlow
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.player.canvas.ui.CanvasArtworkPlayer
import moe.rukamori.archivetune.ui.player.player_0.PlayerCanvasState
import moe.rukamori.archivetune.ui.player.player_0.VideoCanvasStatus
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.state.PlayerUiState

@Composable
internal fun FullscreenVideoOverlay(
    state: PlayerUiState,
    canvas: PlayerCanvasState,
    playbackProgress: StateFlow<Long>,
    onAction: (PlayerAction) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val controlsFadeMillis = if (animationsDisabled) 0 else PlayerVideoTokens.ControlsFadeMillis
    var controlsVisible by remember { mutableStateOf(true) }
    var lyricsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var touching by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    LaunchedEffect(controlsVisible, interaction, touching, scrubbing) {
        if (controlsVisible && !touching && !scrubbing) {
            delay(PlayerVideoTokens.ControlsTimeoutMillis)
            controlsVisible = false
        }
    }
    val captionBottom by animateDpAsState(
        if (controlsVisible) PlayerVideoTokens.CaptionControlsBottom else PlayerVideoTokens.CaptionBottom,
        animationSpec = tween(if (animationsDisabled) 0 else PlayerVideoTokens.CaptionOffsetMillis),
        label = "videoCaptionControlsOffset",
    )
    val currentOnAction by rememberUpdatedState(onAction)
    LaunchedEffect(state.trackUrl) {
        if (state.lyricsList.isEmpty() && !state.isLoadingLyrics) currentOnAction(PlayerAction.SearchLyrics)
    }
    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BackHandler(onBack = onExit)
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            window?.let {
                WindowCompat.getInsetsController(it, view).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }
            onDispose {
                window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) }
            }
        }
        DisposableEffect(window, state.isPlaying) {
            val wasAwake = ((window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
            if (state.isPlaying) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { if (!wasAwake) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
        BoxWithConstraints(
            modifier.fillMaxSize().background(PlayerVideoTokens.Background).windowInsetsPadding(WindowInsets.displayCutout),
        ) {
            val turn = if (maxHeight > maxWidth) Modifier
                .layout { measurable, constraints ->
                    val child = measurable.measure(Constraints.fixed(constraints.maxHeight, constraints.maxWidth))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        child.place((constraints.maxWidth - child.width) / 2, (constraints.maxHeight - child.height) / 2)
                    }
                }
                .graphicsLayer { rotationZ = 90f }
            else Modifier
            Box(
                Modifier.fillMaxSize().then(turn)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            touching = true
                            try {
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                } while (event.changes.any { it.pressed })
                            } finally {
                                touching = false
                                scrubbing = false
                                interaction++
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures {
                            controlsVisible = !controlsVisible
                            interaction++
                        }
                    },
            ) {
                CanvasArtworkPlayer(
                    primaryUrl = canvas.primaryUrl,
                    fallbackUrl = canvas.fallbackUrl,
                    isPlaying = state.isPlaying,
                    isMusicVideo = true,
                    isFullscreenSurface = true,
                    videoSyncOffsetMs = canvas.videoSyncOffsetMs,
                    videoSkipSegments = canvas.videoSkipSegments,
                    onReadyChange = canvas::onVideoReady,
                    onPlaybackError = canvas::markVideoUnavailable,
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                    modifier = Modifier.fillMaxSize(),
                )
                if (lyricsVisible) {
                    VideoWordLyrics(
                        state, playbackProgress,
                        Modifier.align(Alignment.BottomStart).padding(start = PlayerVideoTokens.CaptionStartPadding, end = PlayerVideoTokens.CaptionEndPadding)
                            .graphicsLayer { translationY = -captionBottom.toPx() },
                        backdrop = {
                            Box(
                                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(PlayerVideoTokens.CaptionScrimHeight)
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, PlayerVideoTokens.CaptionScrim))),
                            )
                        },
                    )
                }
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(tween(controlsFadeMillis)),
                    exit = fadeOut(tween(controlsFadeMillis)),
                    modifier = Modifier.align(Alignment.BottomStart)
                        .fillMaxWidth().padding(start = PlayerVideoTokens.SeekbarStartPadding, end = PlayerVideoTokens.SeekbarEndPadding, bottom = PlayerVideoTokens.SeekbarBottomPadding),
                ) {
                    FullscreenVideoSeekbar(
                        state = state,
                        playbackProgress = playbackProgress,
                        onAction = {
                            interaction++
                            onAction(it)
                        },
                        onScrubbingChange = {
                            scrubbing = it
                            interaction++
                        },
                    )
                }
                VideoCanvasStatus(canvas, Modifier.align(Alignment.Center))
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(tween(controlsFadeMillis)),
                    exit = fadeOut(tween(controlsFadeMillis)),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Row(
                        modifier = Modifier.padding(PlayerVideoTokens.ControlPadding),
                        horizontalArrangement = Arrangement.spacedBy(PlayerVideoTokens.ControlGap),
                    ) {
                        IconToggleButton(
                            checked = lyricsVisible,
                            onCheckedChange = {
                                lyricsVisible = it
                                interaction++
                            },
                            modifier = Modifier.size(PlayerVideoTokens.ControlSize),
                        ) {
                            Icon(
                                Icons.Rounded.Subtitles,
                                stringResource(
                                    if (lyricsVisible) R.string.player_video_hide_lyrics
                                    else R.string.player_video_show_lyrics,
                                ),
                                tint = if (lyricsVisible) PlayerVideoTokens.Foreground else PlayerVideoTokens.InactiveForeground,
                            )
                        }
                        IconButton(
                            onClick = onExit,
                            modifier = Modifier.size(PlayerVideoTokens.ControlSize)
                                .background(PlayerVideoTokens.ControlContainer, CircleShape),
                        ) {
                            Icon(Icons.Rounded.FullscreenExit, stringResource(R.string.player_video_exit_fullscreen), tint = PlayerVideoTokens.Foreground)
                        }
                    }
                }
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(tween(controlsFadeMillis)),
                    exit = fadeOut(tween(controlsFadeMillis)),
                    modifier = Modifier.align(Alignment.BottomEnd),
                ) {
                    IconButton(
                        onClick = {
                            interaction++
                            onAction(PlayerAction.PlayPause)
                        },
                        modifier = Modifier.padding(PlayerVideoTokens.TransportPadding).size(PlayerVideoTokens.ControlSize)
                            .background(PlayerVideoTokens.ControlContainer, CircleShape),
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            stringResource(if (state.isPlaying) R.string.player_video_pause else R.string.play),
                            tint = PlayerVideoTokens.Foreground,
                        )
                    }
                }
            }
        }
    }
}

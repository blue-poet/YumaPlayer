package moe.rukamori.archivetune.ui.player.player_0.full

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.StateFlow
import moe.rukamori.archivetune.ui.player.player_0.PlayerSeekBar
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.state.PlayerUiState
import kotlin.math.roundToLong

internal fun videoSeekTarget(position: Float, durationMs: Long): Long? =
    if (!position.isFinite() || durationMs <= 0L) null
    else position.roundToLong().coerceIn(0L, durationMs)

internal fun videoTimeLabel(positionMs: Long): String {
    val seconds = positionMs.coerceAtLeast(0L) / 1000L
    val hours = seconds / 3600L
    val minutes = (seconds / 60L) % 60L
    val remainder = seconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, remainder)
    else "%d:%02d".format(minutes, remainder)
}

@Composable
internal fun FullscreenVideoSeekbar(
    state: PlayerUiState,
    playbackProgress: StateFlow<Long>,
    onAction: (PlayerAction) -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        PlayerSeekBar(
            state = state,
            playbackProgress = playbackProgress,
            durationMs = state.durationMs.coerceAtLeast(0L),
            vibrantColor = Color(state.vibrantColor),
            slideOffset = { 1f },
            onSeekStarted = { onScrubbingChange(true) },
            onSeek = { position ->
                videoSeekTarget(position, state.durationMs)?.let { onAction(PlayerAction.SeekTo(it)) }
                onScrubbingChange(false)
            },
            enabled = state.durationMs > 0L,
        )
    }
}

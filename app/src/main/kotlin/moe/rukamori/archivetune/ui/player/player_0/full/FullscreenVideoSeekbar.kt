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

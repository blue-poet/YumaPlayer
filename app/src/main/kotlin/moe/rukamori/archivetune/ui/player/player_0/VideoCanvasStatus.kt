package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R

@Composable
internal fun VideoCanvasStatus(state: PlayerCanvasState, modifier: Modifier = Modifier) {
    if (!state.isMusicVideo) return
    val label = when {
        state.isVideoUnavailable -> R.string.player_video_unavailable
        state.isVideoLoading -> R.string.player_video_loading
        state.isVideoSyncing -> R.string.player_video_syncing
        state.isVideoSyncUnavailable -> R.string.player_video_sync_unavailable
        else -> return
    }
    val canRetry = state.isVideoUnavailable || state.isVideoSyncUnavailable
    Text(
        text = stringResource(label),
        color = Color.White,
        fontSize = 12.sp,
        modifier = modifier
            .padding(12.dp)
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .clickable(enabled = canRetry, onClick = state::retryVideo)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import moe.rukamori.archivetune.ui.settings.PlayerVideoTokens
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    var expired by remember(state, label, state.retryGeneration) { mutableStateOf(false) }
    LaunchedEffect(state, label, state.retryGeneration) {
        if (label == R.string.player_video_sync_unavailable) {
            delay(PlayerVideoTokens.SyncStatusTimeoutMillis)
            expired = true
        }
    }
    if (expired) return
    val canRetry = state.isVideoUnavailable || state.isVideoSyncUnavailable
    Text(
        text = stringResource(label),
        color = PlayerVideoTokens.Foreground,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier
            .padding(SettingsDimensions.SectionSpacing)
            .background(PlayerVideoTokens.StatusContainer, MaterialTheme.shapes.medium)
            .clickable(enabled = canRetry, onClick = state::retryVideo)
            .padding(horizontal = PlayerVideoTokens.StatusPaddingHorizontal, vertical = PlayerVideoTokens.StatusPaddingVertical),
    )
}

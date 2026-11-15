package moe.rukamori.archivetune.ui.player.canvas.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.musicvideo.MusicVideoPlaybackRequest
import moe.rukamori.archivetune.musicvideo.MusicVideoPlaybackState
import moe.rukamori.archivetune.musicvideo.MusicVideoPlaybackStatus

/** Wires lifecycle-aware video lookup/alignment state; repositories own SponsorBlock and network work. */
@Composable
internal fun rememberMusicVideoPlayback(request: MusicVideoPlaybackRequest): MusicVideoPlaybackState {
    if (LocalInspectionMode.current) return MusicVideoPlaybackState()
    val viewModel = hiltViewModel<MusicVideoPlaybackViewModel>(key = "music-video-playback")
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, request) { viewModel.load(request) }
    DisposableEffect(viewModel, request) { onDispose { viewModel.clear(request) } }
    return if (state.request == request) state.playback
    else MusicVideoPlaybackState(status = MusicVideoPlaybackStatus.LOADING)
}

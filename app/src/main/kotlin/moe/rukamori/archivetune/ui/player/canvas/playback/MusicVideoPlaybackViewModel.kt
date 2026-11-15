package moe.rukamori.archivetune.ui.player.canvas.playback

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import moe.rukamori.archivetune.musicvideo.MusicVideoPlaybackRequest
import moe.rukamori.archivetune.musicvideo.MusicVideoPlaybackState
import moe.rukamori.archivetune.musicvideo.MusicVideoPlaybackStatus
import moe.rukamori.archivetune.musicvideo.ObserveMusicVideoPlaybackUseCase
import moe.rukamori.archivetune.utils.reportException

@Immutable
internal data class MusicVideoPlaybackUiState(
    val request: MusicVideoPlaybackRequest? = null,
    val playback: MusicVideoPlaybackState = MusicVideoPlaybackState(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class MusicVideoPlaybackViewModel @Inject constructor(
    observePlayback: ObserveMusicVideoPlaybackUseCase,
) : ViewModel() {
    private val requests = MutableStateFlow<MusicVideoPlaybackRequest?>(null)
    val state: StateFlow<MusicVideoPlaybackUiState> = requests.flatMapLatest { request ->
        if (request == null) flowOf(MusicVideoPlaybackUiState())
        else observePlayback(request)
            .map { MusicVideoPlaybackUiState(request, it) }
            .catch { error ->
                if (error is CancellationException) throw error
                reportException(error)
                emit(MusicVideoPlaybackUiState(request, MusicVideoPlaybackState(status = MusicVideoPlaybackStatus.UNAVAILABLE)))
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), MusicVideoPlaybackUiState())

    fun load(request: MusicVideoPlaybackRequest) {
        requests.value = request
    }

    fun clear(request: MusicVideoPlaybackRequest) {
        requests.compareAndSet(request, null)
    }
}

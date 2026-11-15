package moe.rukamori.archivetune.ui.player.player_0.sett

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.musicvideo.MusicVideoSearchItem
import moe.rukamori.archivetune.musicvideo.SearchMusicVideosUseCase

@Immutable
internal data class MusicVideoPickerUiState(
    val query: String = "",
    val results: List<MusicVideoSearchItem> = emptyList(),
    val continuation: String? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val searchFailed: Boolean = false,
    val paginationFailed: Boolean = false,
)

@HiltViewModel
internal class MusicVideoPickerViewModel @Inject constructor(
    private val searchVideos: SearchMusicVideosUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(MusicVideoPickerUiState())
    val state: StateFlow<MusicVideoPickerUiState> = _state.asStateFlow()
    private var generation = 0L
    private var searchJob: Job? = null
    private var moreJob: Job? = null

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val requestGeneration = ++generation
        searchJob?.cancel()
        moreJob?.cancel()
        _state.value = MusicVideoPickerUiState(query = trimmed, loading = true)
        searchJob = viewModelScope.launch {
            try {
                val page = searchVideos(trimmed).getOrThrow()
                if (requestGeneration != generation) return@launch
                _state.value = MusicVideoPickerUiState(trimmed, page.items, page.continuation)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (requestGeneration == generation) {
                    _state.update { it.copy(loading = false, searchFailed = true) }
                }
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        val token = current.continuation ?: return
        if (current.loading || current.loadingMore) return
        val requestGeneration = generation
        _state.update { it.copy(loadingMore = true, paginationFailed = false) }
        moreJob = viewModelScope.launch {
            try {
                val page = searchVideos(current.query, token).getOrThrow()
                if (requestGeneration != generation) return@launch
                _state.update {
                    it.copy(
                        results = (it.results + page.items).distinctBy(MusicVideoSearchItem::id),
                        continuation = page.continuation?.takeUnless { next -> next == token },
                        loadingMore = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (requestGeneration == generation) {
                    _state.update { it.copy(loadingMore = false, paginationFailed = true) }
                }
            }
        }
    }

    fun cancelRequests() {
        generation++
        searchJob?.cancel()
        moreJob?.cancel()
    }
}

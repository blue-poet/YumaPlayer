package moe.rukamori.archivetune.musicvideo

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import moe.rukamori.archivetune.sponsorblock.SponsorBlockSegment

data class MusicVideoPlaybackRequest(
    val songId: String,
    val title: String,
    val artist: String,
    val preferredVideoId: String?,
    val currentTrackIsMusicVideo: Boolean,
    val revision: Int,
)

enum class MusicVideoPlaybackStatus { IDLE, LOADING, READY, UNAVAILABLE }

data class MusicVideoPlaybackState(
    val status: MusicVideoPlaybackStatus = MusicVideoPlaybackStatus.IDLE,
    val videoId: String? = null,
    val streamUrl: String? = null,
    val skipSegments: List<SponsorBlockSegment> = emptyList(),
    val offsetMs: Long? = null,
    val aligning: Boolean = false,
    val alignmentUnavailable: Boolean = false,
)

interface MusicVideoPlaybackRepository {
    fun observe(request: MusicVideoPlaybackRequest): Flow<MusicVideoPlaybackState>
}

class ObserveMusicVideoPlaybackUseCase @Inject constructor(
    private val repository: MusicVideoPlaybackRepository,
) {
    operator fun invoke(request: MusicVideoPlaybackRequest): Flow<MusicVideoPlaybackState> =
        repository.observe(request)
}

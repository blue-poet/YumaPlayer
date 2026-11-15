package moe.rukamori.archivetune.musicvideo

import javax.inject.Inject

data class MusicVideoSearchItem(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val durationSeconds: Int?,
)

data class MusicVideoSearchPage(
    val items: List<MusicVideoSearchItem>,
    val continuation: String?,
)

enum class MusicVideoSearchFailure { NETWORK, TIMEOUT, UNAVAILABLE }

class MusicVideoSearchException(
    val reason: MusicVideoSearchFailure,
    cause: Throwable,
) : Exception("Music video search failed: $reason", cause)

interface MusicVideoSearchRepository {
    suspend fun search(query: String, continuation: String? = null): Result<MusicVideoSearchPage>
}

class SearchMusicVideosUseCase @Inject constructor(
    private val repository: MusicVideoSearchRepository,
) {
    suspend operator fun invoke(query: String, continuation: String? = null): Result<MusicVideoSearchPage> =
        repository.search(query, continuation)
}

private val youtubeVideoIdPattern = Regex("[A-Za-z0-9_-]{11}")

internal fun validMusicVideoId(value: String?): String? = value?.takeIf(youtubeVideoIdPattern::matches)

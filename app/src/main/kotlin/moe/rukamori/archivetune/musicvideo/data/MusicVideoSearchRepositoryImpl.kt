package moe.rukamori.archivetune.musicvideo.data

import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.innertube.SearchFilter
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.musicvideo.MusicVideoSearchException
import moe.rukamori.archivetune.musicvideo.MusicVideoSearchFailure
import moe.rukamori.archivetune.musicvideo.MusicVideoSearchItem
import moe.rukamori.archivetune.musicvideo.MusicVideoSearchPage
import moe.rukamori.archivetune.musicvideo.MusicVideoSearchRepository
import moe.rukamori.archivetune.musicvideo.validMusicVideoId

class MusicVideoSearchRepositoryImpl @Inject constructor() : MusicVideoSearchRepository {
    override suspend fun search(query: String, continuation: String?): Result<MusicVideoSearchPage> =
        withContext(Dispatchers.IO) {
            try {
                val page = if (continuation == null) {
                    YouTube.search(query, SearchFilter.FILTER_VIDEO).getOrThrow()
                } else {
                    YouTube.searchContinuation(continuation).getOrThrow()
                }
                val items = page.items.filterIsInstance<SongItem>()
                    .filter { validMusicVideoId(it.id) != null }
                    .distinctBy { it.id }
                    .map { video ->
                        MusicVideoSearchItem(
                            id = video.id,
                            title = video.title,
                            artist = video.artists.joinToString(", ") { it.name },
                            thumbnailUrl = video.thumbnail,
                            durationSeconds = video.duration,
                        )
                    }
                Result.success(MusicVideoSearchPage(items, page.continuation))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val reason = when (error) {
                    is SocketTimeoutException -> MusicVideoSearchFailure.TIMEOUT
                    is IOException -> MusicVideoSearchFailure.NETWORK
                    else -> MusicVideoSearchFailure.UNAVAILABLE
                }
                Result.failure(MusicVideoSearchException(reason, error))
            }
        }
}

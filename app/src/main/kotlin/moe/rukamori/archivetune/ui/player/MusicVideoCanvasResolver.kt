package moe.rukamori.archivetune.ui.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.innertube.SearchFilter
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.pages.NewPipeUtils
import timber.log.Timber
import moe.rukamori.archivetune.utils.resolver.PlaybackStreamUrlDecipher
import moe.rukamori.archivetune.utils.resolver.PlaybackStreamValidator

internal data class ResolvedMusicVideoCanvas(val artwork: CanvasArtwork, val videoId: String)

// Keep signed stream URLs out of the persistent artwork cache.
internal suspend fun resolveMusicVideoCanvas(
    mediaId: String,
    title: String,
    artist: String,
    preferredVideoId: String? = null,
): ResolvedMusicVideoCanvas? = withContext(Dispatchers.IO) {
    if (!Regex("[A-Za-z0-9_-]{11}").matches(mediaId) || title.isBlank() || artist.isBlank()) {
        Timber.tag("PlayerCanvas").d("Music video lookup skipped: missing YouTube ID or metadata")
        return@withContext null
    }
    try {
        val searchItems = if (validMusicVideoChoice(preferredVideoId) != null) {
            emptyList()
        } else {
            YouTube.search(query = "$title $artist", filter = SearchFilter.FILTER_VIDEO)
                .getOrThrow().items.filterIsInstance<SongItem>()
        }
        val candidates = musicVideoCandidates(searchItems, title, artist, preferredVideoId)
        Timber.tag("PlayerCanvas").d("Music video lookup: %d matching candidates for %s", candidates.size, mediaId)
        val authState = YouTube.currentPlaybackAuthState()
        for (candidate in candidates) {
            for (client in listOf(YouTubeClient.VISIONOS, YouTubeClient.ANDROID_VR_1_61_48, YouTubeClient.ANDROID_VR_1_43_32, YouTubeClient.ANDROID_MUSIC, YouTubeClient.WEB_REMIX)) {
                val timestamp = if (client.useSignatureTimestamp) {
                    NewPipeUtils.getSignatureTimestamp(candidate.id).getOrNull()
                } else null
                val result = YouTube.player(candidate.id, client = client, signatureTimestamp = timestamp, authState = authState)
                result.exceptionOrNull()?.let {
                    if (it is CancellationException) throw it
                    Timber.tag("PlayerCanvas").w("Music video player request failed: %s", it.message)
                }
                val response = result.getOrNull() ?: continue
                if (response.playabilityStatus.status != "OK") {
                    Timber.tag("PlayerCanvas").d("Music video %s unavailable: %s", candidate.id, response.playabilityStatus.reason)
                    continue
                }
                val data = response.streamingData ?: continue
                val formats = (data.adaptiveFormats + data.formats.orEmpty())
                    .filter { !it.isAudio && it.mimeType.startsWith("video/mp4") }
                    .sortedWith(
                        compareByDescending<moe.rukamori.archivetune.innertube.models.response.PlayerResponse.StreamingData.Format> {
                            (it.height ?: Int.MAX_VALUE) <= 720 && it.mimeType.contains("avc1")
                        }.thenByDescending { (it.height ?: Int.MAX_VALUE) <= 720 }
                            .thenByDescending { it.height ?: 0 },
                    )
                for (format in formats.take(2)) {
                    val url = PlaybackStreamUrlDecipher.findUrlOrNull(format, candidate.id, client, authState)
                        ?: continue
                    if (!PlaybackStreamValidator.validateStatus(url)) {
                        Timber.tag("PlayerCanvas").d("Music video stream refused: %s, itag=%d", candidate.id, format.itag)
                        continue
                    }
                    Timber.tag("PlayerCanvas").i("Music video resolved: %s, itag=%d", candidate.id, format.itag)
                    return@withContext ResolvedMusicVideoCanvas(
                        artwork = CanvasArtwork(name = title, artist = artist, videoUrl = url),
                        videoId = candidate.id,
                    )
                }
            }
        }
        Timber.tag("PlayerCanvas").d("No playable music video found for %s", mediaId)
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Timber.tag("PlayerCanvas").w(error, "Music video canvas lookup failed")
        null
    }
}

internal fun musicVideoCandidates(
    searchItems: List<SongItem>,
    title: String,
    artist: String,
    preferredVideoId: String? = null,
): List<SongItem> {
    val chosen = validMusicVideoChoice(preferredVideoId)
    if (chosen != null) {
        // A manual pick must not be rejected or silently replaced by automatic matching.
        return listOf(SongItem(id = chosen, title = title, artists = emptyList(), thumbnail = ""))
    }
    return searchItems.filter { matchesMusicVideoCanvas(it, title, artist) }.take(3)
}

internal fun matchesMusicVideoCanvas(candidate: SongItem, title: String, artist: String): Boolean {
    fun words(value: String) = value.lowercase(java.util.Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().split(" ").filter { it.isNotBlank() }
    fun cleanTitle(value: String) = value
        .replace(Regex("""(?i)\s*[(\[](?:(?:official\s*)?(?:(?:music\s*)?video|audio|lyrics?|visuali[sz]er|hd|4k))[^)\]]*[)\]]"""), "")
        .replace(Regex("""(?i)\s*[(\[](?:feat\.?|ft\.?|featuring)\b[^)\]]*[)\]]"""), "")
    val videoWords = words(cleanTitle(candidate.title)).toSet()
    val titleWords = words(cleanTitle(title))
    val artistWords = words(artist)
    val attribution = words(candidate.title + " " + candidate.artists.joinToString(" ") { it.name }).toSet()
    val type = candidate.endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType
    // YouTube's official-video classification is enough even without "(Official Video)" in the title.
    val isOfficialVideo = type == "MUSIC_VIDEO_TYPE_OMV"
    val videoLabel = Regex("(?i)\\b(music video|official video|lyric video|lyrics video|visuali[sz]er|mv)\\b")
    return (isOfficialVideo || videoLabel.containsMatchIn(candidate.title)) &&
        titleWords.isNotEmpty() && titleWords.all { it in videoWords } &&
        artistWords.isNotEmpty() && artistWords.all { it in attribution }
}

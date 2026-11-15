/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player.player_0

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import androidx.compose.runtime.Composable
import moe.rukamori.archivetune.musicvideo.MusicVideoPlaybackRequest
import moe.rukamori.archivetune.musicvideo.MusicVideoPlaybackState
import moe.rukamori.archivetune.musicvideo.MusicVideoPlaybackStatus
import moe.rukamori.archivetune.ui.player.canvas.playback.rememberMusicVideoPlayback
import moe.rukamori.archivetune.sponsorblock.SponsorBlockSegment
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.canvas.CanvasSource
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.ui.player.PlayerArtworkMode
import moe.rukamori.archivetune.ui.player.rememberPlayerArtworkMode
import moe.rukamori.archivetune.constants.CanvasSourceKey
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlaybackCache
import moe.rukamori.archivetune.ui.player.resolveCanvasArtworkForPlayback
import moe.rukamori.archivetune.ui.player.rememberMusicVideoChoice
import moe.rukamori.archivetune.utils.rememberPreference
import timber.log.Timber

@Stable
class PlayerCanvasState internal constructor(
    val isCanvasEnabled: Boolean,
    artwork: CanvasArtwork?,
) {
    var artwork by mutableStateOf(artwork)
        internal set

    internal var resolvedVideoId by mutableStateOf<String?>(null)

    var isMusicVideo by mutableStateOf(false)
        internal set

    private var videoReady by mutableStateOf(false)
    private var lookupLoading by mutableStateOf(false)
    private var videoPlaybackFailed = false

    val isVideoLoading: Boolean
        get() = isMusicVideo && !isVideoUnavailable &&
            (lookupLoading || (artwork != null && !videoReady))

    var isVideoUnavailable by mutableStateOf(false)
        private set

    internal var videoSkipSegments by mutableStateOf<List<SponsorBlockSegment>>(emptyList())

    var videoSyncOffsetMs by mutableStateOf<Long?>(null)
        internal set

    var isVideoSyncing by mutableStateOf(false)
        internal set

    var isVideoSyncUnavailable by mutableStateOf(false)
        internal set

    internal var retryGeneration by mutableStateOf(0)
        private set

    internal fun retryVideo() {
        videoPlaybackFailed = false
        retryGeneration++
    }

    internal fun onVideoReady(ready: Boolean) {
        if (isMusicVideo && !isVideoUnavailable) videoReady = ready
    }

    internal fun markVideoUnavailable() {
        videoPlaybackFailed = true
        resolvedVideoId = null
        videoSkipSegments = emptyList()
        lookupLoading = false
        isVideoSyncing = false
        isVideoUnavailable = true
        artwork = null
    }

    internal fun applyPlayback(playback: MusicVideoPlaybackState, request: MusicVideoPlaybackRequest) {
        if (videoPlaybackFailed) return
        isMusicVideo = true
        lookupLoading = playback.status == MusicVideoPlaybackStatus.LOADING
        isVideoUnavailable = playback.status == MusicVideoPlaybackStatus.UNAVAILABLE
        isVideoSyncing = playback.aligning
        isVideoSyncUnavailable = playback.alignmentUnavailable
        videoSyncOffsetMs = playback.offsetMs
        videoSkipSegments = playback.skipSegments
        if (resolvedVideoId != playback.videoId || primaryUrl != playback.streamUrl) videoReady = false
        resolvedVideoId = playback.videoId
        artwork = playback.streamUrl?.let { url ->
            CanvasArtwork(name = request.title, artist = request.artist, videoUrl = url)
        }
    }

    val primaryUrl: String?
        get() = artwork?.preferredAnimationUrl

    val fallbackUrl: String?
        get() = artwork?.let { it.videoUrl.takeIf { url -> url != it.preferredAnimationUrl } }
}

internal fun hasCanvasMetadata(songTitle: String?, artistName: String?): Boolean =
    !songTitle.isNullOrBlank() || !artistName.isNullOrBlank()

@Composable
fun rememberPlayerCanvasState(
    mediaId: String?,
    songTitle: String?,
    artistName: String?,
): PlayerCanvasState {
    val metadata = LocalPlayerConnection.current?.mediaMetadata?.collectAsStateWithLifecycle()?.value
    val currentTrackIsMusicVideo = metadata?.id == mediaId && metadata?.isMusicVideo == true
    val (artworkMode) = rememberPlayerArtworkMode()
    val (selectedVideoId) = rememberMusicVideoChoice(mediaId.orEmpty())
    val isCanvasEnabled = artworkMode != PlayerArtworkMode.ALBUM_ART
    val (canvasSourcePreference) = rememberPreference(CanvasSourceKey, defaultValue = CanvasSource.ALL.name)
    val canvasSource = remember(canvasSourcePreference) { CanvasSource.fromPreference(canvasSourcePreference) }
    val canvasState = remember(mediaId, artworkMode, canvasSource, selectedVideoId, currentTrackIsMusicVideo) {
        PlayerCanvasState(isCanvasEnabled, null)
    }

    if (artworkMode == PlayerArtworkMode.MUSIC_VIDEO) {
        val request = MusicVideoPlaybackRequest(
            mediaId.orEmpty(), songTitle.orEmpty(), artistName.orEmpty(),
            selectedVideoId, currentTrackIsMusicVideo, canvasState.retryGeneration,
        )
        val playback = rememberMusicVideoPlayback(request)
        LaunchedEffect(canvasState, playback, request) { canvasState.applyPlayback(playback, request) }
    }
    val hasMetadata = hasCanvasMetadata(songTitle, artistName)
    val currentTitle by rememberUpdatedState(songTitle)
    val currentArtist by rememberUpdatedState(artistName)

    LaunchedEffect(artworkMode, canvasSource, mediaId, songTitle, artistName, selectedVideoId, currentTrackIsMusicVideo, canvasState.retryGeneration) {
        if (artworkMode == PlayerArtworkMode.MUSIC_VIDEO) return@LaunchedEffect
        Timber.tag("PlayerCanvas").d("Lookup trigger: isCanvasEnabled=$isCanvasEnabled, source=$canvasSource, mediaId=$mediaId, title=$currentTitle, artist=$currentArtist")
        if (!isCanvasEnabled) {
            canvasState.artwork = null
            Timber.tag("PlayerCanvas").d("Canvas is disabled in settings (archiveTuneCanvas = false)")
            return@LaunchedEffect
        }
        if (mediaId.isNullOrBlank()) {
            canvasState.artwork = null
            Timber.tag("PlayerCanvas").d("mediaId is null or blank, skipping lookup")
            return@LaunchedEffect
        }
        if (canvasState.artwork != null && canvasSource.accepts(canvasState.artwork?.source)) {
            return@LaunchedEffect
        }

        val cached =
            withContext(Dispatchers.IO) {
                CanvasArtworkPlaybackCache.get(mediaId = mediaId, source = canvasSource)
            }
        if (cached != null) {
            Timber.tag("PlayerCanvas").d("Canvas cache hit for %s", mediaId)
            canvasState.artwork = cached
            return@LaunchedEffect
        }

        if (!hasMetadata) {
            canvasState.artwork = null
            Timber.tag("PlayerCanvas").d("Metadata missing for $mediaId, waiting for metadata...")
            return@LaunchedEffect
        }

        canvasState.artwork = null
        Timber.tag("PlayerCanvas").d("Cache miss, resolving from network for '$currentTitle' by '$currentArtist' (mediaId=$mediaId, source=$canvasSource)...")
        val resolved =
            resolveCanvasArtworkForPlayback(
                mediaId = mediaId,
                songTitleRaw = currentTitle ?: "",
                artistNameRaw = currentArtist ?: "",
                storefront = "us",
                requireVertical = false,
                allowNetwork = true,
                source = canvasSource,
            )
        Timber.tag("PlayerCanvas").i("Canvas lookup complete for %s: available=%s", mediaId, resolved != null)
        canvasState.artwork = resolved
    }

    return canvasState
}

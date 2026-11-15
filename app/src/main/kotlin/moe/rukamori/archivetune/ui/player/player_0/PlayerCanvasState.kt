/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import moe.rukamori.archivetune.ui.player.canvas.playback.MusicVideoAutoSync
import moe.rukamori.archivetune.ui.player.canvas.playback.rememberMusicVideoSkips
import moe.rukamori.archivetune.sponsorblock.SponsorBlockSegment
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.canvas.CanvasSource
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.ui.player.PlayerArtworkMode
import moe.rukamori.archivetune.ui.player.rememberPlayerArtworkMode
import moe.rukamori.archivetune.constants.CanvasSourceKey
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlaybackCache
import moe.rukamori.archivetune.ui.player.resolveCanvasArtworkForPlayback
import moe.rukamori.archivetune.ui.player.resolveMusicVideoCanvas
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

    var isVideoLoading by mutableStateOf(false)
        internal set

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
        retryGeneration++
    }

    internal fun onVideoReady(ready: Boolean) {
        if (isMusicVideo && !isVideoUnavailable) isVideoLoading = !ready
    }

    internal fun markVideoUnavailable() {
        resolvedVideoId = null
        videoSkipSegments = emptyList()
        isVideoLoading = false
        isVideoSyncing = false
        isVideoUnavailable = true
        artwork = null
    }

    internal fun beginVideoLookup() {
        resolvedVideoId = null
        videoSkipSegments = emptyList()
        isVideoUnavailable = false
        isVideoLoading = true
        isVideoSyncUnavailable = false
        videoSyncOffsetMs = null
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
    val context = LocalContext.current.applicationContext
    val (artworkMode) = rememberPlayerArtworkMode()
    val (selectedVideoId) = rememberMusicVideoChoice(mediaId.orEmpty())
    val isCanvasEnabled = artworkMode != PlayerArtworkMode.ALBUM_ART
    val (canvasSourcePreference) = rememberPreference(CanvasSourceKey, defaultValue = CanvasSource.ALL.name)
    val canvasSource = remember(canvasSourcePreference) { CanvasSource.fromPreference(canvasSourcePreference) }
    val canvasState = remember(mediaId, artworkMode, canvasSource, selectedVideoId) {
        PlayerCanvasState(isCanvasEnabled, null)
    }

    val videoSkips = rememberMusicVideoSkips(mediaId, canvasState.resolvedVideoId, canvasState.retryGeneration)
    val hasMetadata = hasCanvasMetadata(songTitle, artistName)
    val currentTitle by rememberUpdatedState(songTitle)
    val currentArtist by rememberUpdatedState(artistName)

    LaunchedEffect(artworkMode, canvasSource, mediaId, songTitle, artistName, selectedVideoId, canvasState.retryGeneration) {
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
        if (artworkMode == PlayerArtworkMode.MUSIC_VIDEO) {
            canvasState.artwork = null
            canvasState.isMusicVideo = true
            canvasState.beginVideoLookup()
            if (hasMetadata) {
                val video = resolveMusicVideoCanvas(mediaId, currentTitle.orEmpty(), currentArtist.orEmpty(), selectedVideoId)
                canvasState.artwork = video?.artwork
                if (video == null) {
                    canvasState.markVideoUnavailable()
                } else {
                    canvasState.resolvedVideoId = video.videoId
                }
            }
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
            Timber.tag("PlayerCanvas").d("Cache hit for $mediaId: primaryUrl=${cached.preferredAnimationUrl}, fallbackUrl=${cached.videoUrl}")
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
        Timber.tag("PlayerCanvas").i("Resolved result for $mediaId: ${if (resolved != null) "primaryUrl=${resolved.preferredAnimationUrl}, videoUrl=${resolved.videoUrl}" else "null"}")
        canvasState.artwork = resolved
    }

    LaunchedEffect(canvasState, canvasState.resolvedVideoId, canvasState.retryGeneration, videoSkips) {
        canvasState.videoSkipSegments = videoSkips.segments
        canvasState.videoSyncOffsetMs = null
        canvasState.isVideoSyncUnavailable = false
        canvasState.isVideoSyncing = false
        val videoId = canvasState.resolvedVideoId
        if (videoId == null || mediaId.isNullOrBlank()) return@LaunchedEffect
        canvasState.isVideoSyncing = true
        if (videoSkips.loading) return@LaunchedEffect
        try {
            canvasState.videoSyncOffsetMs = MusicVideoAutoSync.measure(context, mediaId, videoId, videoSkips.segments)
            canvasState.isVideoSyncUnavailable = canvasState.videoSyncOffsetMs == null
        } finally {
            if (isActive) canvasState.isVideoSyncing = false
        }
    }

    return canvasState
}

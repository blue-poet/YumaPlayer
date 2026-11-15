package moe.rukamori.archivetune.ui.player

import moe.rukamori.archivetune.innertube.models.SongItem

// Retain the player's existing resolver API while data access lives outside presentation.
internal typealias ResolvedMusicVideoCanvas =
    moe.rukamori.archivetune.musicvideo.data.ResolvedMusicVideoCanvas

internal suspend fun resolveMusicVideoCanvas(
    mediaId: String,
    title: String,
    artist: String,
    preferredVideoId: String? = null,
    currentTrackIsMusicVideo: Boolean = false,
): ResolvedMusicVideoCanvas? = moe.rukamori.archivetune.musicvideo.data.resolveMusicVideoCanvas(
    mediaId, title, artist, preferredVideoId, currentTrackIsMusicVideo,
)

internal fun directMusicVideoCanvasId(
    mediaId: String,
    currentTrackIsMusicVideo: Boolean,
    preferredVideoId: String?,
): String? = moe.rukamori.archivetune.musicvideo.data.directMusicVideoCanvasId(
    mediaId, currentTrackIsMusicVideo, preferredVideoId,
)

internal fun musicVideoCandidates(
    searchItems: List<SongItem>,
    title: String,
    artist: String,
    preferredVideoId: String? = null,
): List<SongItem> = moe.rukamori.archivetune.musicvideo.data.musicVideoCandidates(
    searchItems, title, artist, preferredVideoId,
)

internal fun matchesMusicVideoCanvas(candidate: SongItem, title: String, artist: String): Boolean =
    moe.rukamori.archivetune.musicvideo.data.matchesMusicVideoCanvas(candidate, title, artist)

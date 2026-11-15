package moe.rukamori.archivetune.ui.player.canvas.playback

import moe.rukamori.archivetune.sponsorblock.SponsorBlockSegment

internal typealias MusicVideoTimeline = moe.rukamori.archivetune.musicvideo.MusicVideoTimeline

internal fun musicVideoAlignmentCacheKey(
    songId: String,
    videoId: String,
    skips: List<SponsorBlockSegment>,
): String = moe.rukamori.archivetune.musicvideo.musicVideoAlignmentCacheKey(songId, videoId, skips)

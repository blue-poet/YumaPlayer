package moe.rukamori.archivetune.ui.player.canvas.playback

import android.content.Context
import moe.rukamori.archivetune.sponsorblock.SponsorBlockSegment

// Preserve the playback API; HTTP, decoding and persistence belong to the data implementation.
internal object MusicVideoAutoSync {
    suspend fun measure(
        context: Context,
        songId: String,
        videoId: String,
        skipSegments: List<SponsorBlockSegment> = emptyList(),
    ): Long? = moe.rukamori.archivetune.musicvideo.data.MusicVideoAutoSync.measure(
        context, songId, videoId, skipSegments,
    )
}

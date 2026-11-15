package moe.rukamori.archivetune.musicvideo.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.musicvideo.MusicVideoPlaybackRepository
import moe.rukamori.archivetune.musicvideo.MusicVideoPlaybackRequest
import moe.rukamori.archivetune.musicvideo.MusicVideoPlaybackState
import moe.rukamori.archivetune.musicvideo.MusicVideoPlaybackStatus
import moe.rukamori.archivetune.sponsorblock.GetSponsorBlockSegmentsUseCase
import moe.rukamori.archivetune.sponsorblock.ObserveSponsorBlockSettingsUseCase
import moe.rukamori.archivetune.sponsorblock.SponsorBlockSettings
import moe.rukamori.archivetune.sponsorblock.SponsorBlockCategory
import timber.log.Timber

class MusicVideoPlaybackRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val observeSettings: ObserveSponsorBlockSettingsUseCase,
    private val getSegments: GetSponsorBlockSegmentsUseCase,
) : MusicVideoPlaybackRepository {
    override fun observe(request: MusicVideoPlaybackRequest): Flow<MusicVideoPlaybackState> = channelFlow {
        send(MusicVideoPlaybackState(status = MusicVideoPlaybackStatus.LOADING))
        val source = resolveMusicVideoCanvas(
            request.songId, request.title, request.artist,
            request.preferredVideoId, request.currentTrackIsMusicVideo,
        )
        if (source == null) {
            send(MusicVideoPlaybackState(status = MusicVideoPlaybackStatus.UNAVAILABLE))
            return@channelFlow
        }
        val ready = MusicVideoPlaybackState(
            status = MusicVideoPlaybackStatus.READY,
            videoId = source.videoId,
            streamUrl = source.artwork.preferredAnimationUrl,
            aligning = true,
        )
        send(ready)
        observeSettings().catch { error ->
            if (error is CancellationException) throw error
            Timber.tag("PlayerCanvas").w(error, "Video skip settings unavailable")
            emit(SponsorBlockSettings.Default)
        }.distinctUntilChanged().collectLatest { settings ->
            send(ready)
            val shouldSkip = request.songId != source.videoId && settings.enabled &&
                SponsorBlockCategory.MUSIC_OFF_TOPIC in settings.categories
            val segments = if (shouldSkip) {
                try {
                    getSegments(
                        videoId = source.videoId,
                        settings = settings.copy(categories = setOf(SponsorBlockCategory.MUSIC_OFF_TOPIC)),
                        source = PlaybackSource.YT_MUSIC,
                    ).toList()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Timber.tag("PlayerCanvas").w(error, "Non-music video sections unavailable")
                    emptyList()
                }
            } else emptyList()
            val offset = try {
                MusicVideoAutoSync.measure(context, request.songId, source.videoId, segments)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.tag("PlayerCanvas").w(error, "Video alignment unavailable")
                null
            }
            send(
                ready.copy(
                    skipSegments = segments,
                    offsetMs = offset,
                    aligning = false,
                    alignmentUnavailable = offset == null,
                ),
            )
        }
    }.flowOn(Dispatchers.IO)
}

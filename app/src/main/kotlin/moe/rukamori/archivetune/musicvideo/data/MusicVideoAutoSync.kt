package moe.rukamori.archivetune.musicvideo.data

import moe.rukamori.archivetune.musicvideo.MusicVideoTimeline
import moe.rukamori.archivetune.musicvideo.musicVideoAlignmentCacheKey

import android.content.Context
import android.content.SharedPreferences
import moe.rukamori.archivetune.sponsorblock.SponsorBlockSegment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.audiodsp.MusicVideoAlignment
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.playback.smart.AudioDecoder
import moe.rukamori.archivetune.network.createMediaHttpClient
import moe.rukamori.archivetune.utils.resolver.PlaybackStreamUrlDecipher
import moe.rukamori.archivetune.utils.resolver.PlaybackStreamValidator
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.currentCoroutineContext
import kotlin.time.Duration.Companion.seconds

internal object MusicVideoAutoSync {
    private val measurementMutex = Mutex()
    private val client by lazy {
        createMediaHttpClient().newBuilder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    // Music-only video time = song time + offset. The playback timeline restores skipped inserts.
    suspend fun measure(context: Context, songId: String, videoId: String, skipSegments: List<SponsorBlockSegment> = emptyList()): Long? = withContext(Dispatchers.IO) {
        if (songId == videoId) return@withContext 0L
        val preferences = context.getSharedPreferences("music_video_alignment_v1", Context.MODE_PRIVATE)
        val timeline = MusicVideoTimeline(skipSegments)
        val key = musicVideoAlignmentCacheKey(songId, videoId, timeline.segments)
        cachedOffset(preferences, key)?.let { return@withContext it }
        withTimeoutOrNull(60.seconds) {
            measurementMutex.withLock {
                cachedOffset(preferences, key)?.let { return@withLock it }
                try {
                    val samples = coroutineScope {
                        val song = async(Dispatchers.IO) { decodeHead(songId) }
                        val video = async(Dispatchers.IO) { decodeHead(videoId) }
                        song.await() to video.await()
                    }
                    val song = samples.first ?: return@withLock null
                    val video = samples.second ?: return@withLock null
                    val match = withContext(Dispatchers.Default) {
                        val songEnvelope = MusicVideoAlignment.envelope(song, AudioDecoder.TARGET_SAMPLE_RATE)
                        val videoEnvelope = timeline.musicOnlyEnvelope(
                            MusicVideoAlignment.envelope(video, AudioDecoder.TARGET_SAMPLE_RATE),
                        )
                        ensureActive()
                        MusicVideoAlignment.estimate(songEnvelope, videoEnvelope)
                    } ?: return@withLock null
                    currentCoroutineContext().ensureActive()
                    preferences.edit().putLong(key, match.offsetMs).apply()
                    Timber.tag("PlayerCanvas").i(
                        "Video auto-sync measured: song=%s video=%s offset=%dms confidence=%.3f",
                        songId, videoId, match.offsetMs, match.confidence,
                    )
                    match.offsetMs
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Timber.tag("PlayerCanvas").w(error, "Video audio alignment failed")
                    null
                }
            }
        }
    }

    private fun cachedOffset(preferences: SharedPreferences, key: String): Long? {
        if (!preferences.contains(key)) return null
        return try {
            preferences.getLong(key, 0L).takeIf { it in -60_000L..60_000L }
                ?: run {
                    preferences.edit().remove(key).apply()
                    Timber.tag("PlayerCanvas").w("Removed out-of-range video alignment cache entry")
                    null
                }
        } catch (_: ClassCastException) {
            preferences.edit().remove(key).apply()
            Timber.tag("PlayerCanvas").w("Removed invalid video alignment cache entry")
            null
        }
    }

    private suspend fun decodeHead(videoId: String): FloatArray? {
        val auth = YouTube.currentPlaybackAuthState()
        for (profile in listOf(YouTubeClient.VISIONOS, YouTubeClient.ANDROID_VR_1_61_48, YouTubeClient.ANDROID_VR_1_43_32)) {
            currentCoroutineContext().ensureActive()
            val response = YouTube.player(videoId, client = profile, authState = auth)
            response.exceptionOrNull()?.let { error ->
                if (error is CancellationException) throw error
                Timber.tag("PlayerCanvas").w(
                    "Video audio sample request failed: client=%s cause=%s",
                    profile.clientName, error.javaClass.simpleName,
                )
            }
            val formats = response.getOrNull()?.streamingData?.adaptiveFormats.orEmpty()
                .filter { it.isAudio && it.mimeType.startsWith("audio/mp4") && (it.contentLength ?: 0) > 0 }
                .sortedBy { it.bitrate }
            for (format in formats.take(2)) {
                val url = PlaybackStreamUrlDecipher.findUrlOrNull(format, videoId, profile, auth) ?: continue
                if (!PlaybackStreamValidator.validateStatus(url)) continue
                try {
                    val jobContext = currentCoroutineContext()
                    RangeAudioSource(client, url, format.contentLength!!, jobContext).use { source ->
                        val samples = AudioDecoder.decodeForAlignment(source) { jobContext.ensureActive() }
                        jobContext.ensureActive()
                        if (samples != null && samples.size >= AudioDecoder.TARGET_SAMPLE_RATE * 12) return samples
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Timber.tag("PlayerCanvas").d(error, "Trying another audio sample for video sync")
                }
            }
        }
        return null
    }
}


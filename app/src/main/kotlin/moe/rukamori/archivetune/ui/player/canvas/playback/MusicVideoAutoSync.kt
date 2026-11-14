@file:Suppress("UnstableApi")

package moe.rukamori.archivetune.ui.player.canvas.playback

import android.content.Context
import android.media.MediaDataSource
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
import moe.rukamori.archivetune.ui.player.canvas.network.createOkHttpClient
import moe.rukamori.archivetune.utils.resolver.PlaybackStreamUrlDecipher
import moe.rukamori.archivetune.utils.resolver.PlaybackStreamValidator
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.math.min

internal object MusicVideoAutoSync {
    private val measurementMutex = Mutex()
    private val client by lazy {
        createOkHttpClient().newBuilder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    // Video time = song time + offset. Only confident measurements are saved.
    suspend fun measure(context: Context, songId: String, videoId: String): Long? = withContext(Dispatchers.IO) {
        if (songId == videoId) return@withContext 0L
        val preferences = context.getSharedPreferences("music_video_alignment_v1", Context.MODE_PRIVATE)
        val key = "$songId:$videoId"
        if (preferences.contains(key)) return@withContext preferences.getLong(key, 0L)
        withTimeoutOrNull(60_000L) {
            measurementMutex.withLock {
                if (preferences.contains(key)) return@withLock preferences.getLong(key, 0L)
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
                        val videoEnvelope = MusicVideoAlignment.envelope(video, AudioDecoder.TARGET_SAMPLE_RATE)
                        ensureActive()
                        MusicVideoAlignment.estimate(songEnvelope, videoEnvelope)
                    } ?: return@withLock null
                    coroutineContext.ensureActive()
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

    private suspend fun decodeHead(videoId: String): FloatArray? {
        val auth = YouTube.currentPlaybackAuthState()
        for (profile in listOf(YouTubeClient.VISIONOS, YouTubeClient.ANDROID_VR_1_61_48, YouTubeClient.ANDROID_VR_1_43_32)) {
            coroutineContext.ensureActive()
            val response = YouTube.player(videoId, client = profile, authState = auth)
            response.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            val formats = response.getOrNull()?.streamingData?.adaptiveFormats.orEmpty()
                .filter { it.isAudio && it.mimeType.startsWith("audio/mp4") && (it.contentLength ?: 0) > 0 }
                .sortedBy { it.bitrate }
            for (format in formats.take(2)) {
                val url = PlaybackStreamUrlDecipher.findUrlOrNull(format, videoId, profile, auth) ?: continue
                if (!PlaybackStreamValidator.validateStatus(url)) continue
                try {
                    val jobContext = coroutineContext
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

/** Random access for MediaExtractor, with bounded reads rather than downloading a complete song. */
internal class RangeAudioSource(
    private val client: OkHttpClient,
    private val url: String,
    private val contentLength: Long,
    private val jobContext: CoroutineContext,
) : MediaDataSource() {
    private val chunks = LinkedHashMap<Long, ByteArray>(16, 0.75f, true)
    private var downloaded = 0L
    private var closed = false

    override fun getSize(): Long = contentLength

    @Synchronized
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        jobContext.ensureActive()
        if (closed) throw IOException("Audio analysis source is closed")
        if (size == 0) return 0
        if (position < 0 || position >= contentLength) return -1
        val chunkStart = position / CHUNK_SIZE * CHUNK_SIZE
        val bytes = chunks[chunkStart] ?: fetch(chunkStart).also {
            chunks[chunkStart] = it
            if (chunks.size > 16) chunks.remove(chunks.keys.first())
        }
        val start = (position - chunkStart).toInt()
        val count = min(size, bytes.size - start)
        if (count <= 0) return -1
        bytes.copyInto(buffer, offset, start, start + count)
        return count
    }

    private fun fetch(position: Long): ByteArray {
        val count = min(CHUNK_SIZE, contentLength - position).toInt()
        if (downloaded + count > MAX_BYTES) throw IOException("Audio alignment download limit reached")
        val request = Request.Builder().url(url)
            .header("Range", "bytes=$position-${position + count - 1}").build()
        return client.newCall(request).execute().use { response ->
            if (response.code != 206 && !(response.code == 200 && position == 0L)) {
                throw IOException("Audio sample range refused: ${response.code}")
            }
            // Never treat bytes from a different range as the requested part of the recording.
            if (response.code == 206 && !response.header("Content-Range").orEmpty().startsWith("bytes $position-")) {
                throw IOException("Audio sample returned an unexpected range")
            }
            val result = ByteArray(count)
            val source = response.body.byteStream()
            var read = 0
            while (read < count) {
                jobContext.ensureActive()
                val next = source.read(result, read, count - read)
                if (next < 0) throw EOFException("Truncated audio sample")
                read += next
            }
            downloaded += read
            result
        }
    }

    @Synchronized
    override fun close() {
        closed = true
        chunks.clear()
    }

    private companion object {
        const val CHUNK_SIZE = 65_536L
        const val MAX_BYTES = 4L * 1024 * 1024
    }
}

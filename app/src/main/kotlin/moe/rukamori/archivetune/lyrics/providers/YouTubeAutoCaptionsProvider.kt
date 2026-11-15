package moe.rukamori.archivetune.lyrics.providers

import android.content.Context
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.constants.EnableYouTubeAutoCaptionsKey
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.captionText
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse.Captions.CaptionTrack
import moe.rukamori.archivetune.lyrics.LyricsProvider
import moe.rukamori.archivetune.lyrics.SharedLyricsEngine
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.potoken.BotGuardTokenGenerator
import timber.log.Timber

object YouTubeAutoCaptionsProvider : LyricsProvider {
    override val name = "YouTube Auto Captions"
    override val requestTimeoutMs = 90_000L

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableYouTubeAutoCaptionsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = withContext(Dispatchers.IO) { fetchLyrics(id) }

    private suspend fun fetchLyrics(id: String): Result<String> {
        if (!Regex("[A-Za-z0-9_-]{11}").matches(id)) {
            return Result.failure(IllegalArgumentException("Captions require a YouTube video ID"))
        }
        for (client in youtubeCaptionClients()) {
            val lyrics = attempt {
                withTimeoutOrNull(12_000L) {
                    val player = YouTube.player(id, client = client).getOrThrow()
                    val tracks = player.captions?.playerCaptionsTracklistRenderer?.captionTracks.orEmpty()
                    Timber.tag("YouTubeCaptions").d(
                        "Caption lookup video=%s client=%s/%s status=%s tracks=%d",
                        id, client.clientName, client.clientVersion, player.playabilityStatus.status, tracks.size,
                    )
                    fetchTracks(tracks, client)
                }
            }
            if (lyrics != null) return Result.success(lyrics)
        }

        // The watch page can list captions even when mobile player clients omit them.
        val watch = attempt {
            withTimeoutOrNull(4_000L) {
                val response = SharedLyricsEngine.httpClient.get("https://www.youtube.com/watch") {
                    url.parameters["v"] = id
                    header("User-Agent", YouTubeClient.WEB.userAgent)
                }
                val html = response.bodyAsText()
                withContext(Dispatchers.Default) { watchCaptionData(html, id) }
            }
        }
        if (watch != null) {
            attempt { fetchTracks(watch.tracks, YouTubeClient.WEB) }
                ?.let { return Result.success(it) }
            // A 200 with no cues can mean timedtext requires proof of origin.
            // Mint for this video, using the watch page's visitor session; don't
            // reuse a player token belonging to another song.
            val visitor = watch.visitorData
            if (!visitor.isNullOrBlank()) {
                val token = attempt {
                    withTimeoutOrNull(15_000L) {
                        BotGuardTokenGenerator.mintToken(id, visitor)?.playerToken
                    }
                }
                if (!token.isNullOrBlank()) {
                    attempt { fetchTracks(watch.tracks, YouTubeClient.WEB, token) }
                        ?.let { return Result.success(it) }
                }
            }
        }
        // The transcript route has different availability from timedtext and retains this video's clock.
        val transcript = attempt {
            withTimeoutOrNull(4_000L) {
                YouTube.transcript(id).getOrThrow().takeIf { it.isNotBlank() }
            }
        }
        if (transcript != null) return Result.success(transcript)
        return Result.failure(IllegalStateException("YouTube captions could not be retrieved"))
    }

    private suspend fun fetchTracks(
        tracks: List<CaptionTrack>,
        client: YouTubeClient,
        token: String? = null,
    ): String? {
        for (track in captionTracksForLyrics(tracks, YouTube.locale.hl).take(2)) {
            val lyrics = attempt {
                withTimeoutOrNull(8_000L) {
                    val text = YouTube.captionText(track.baseUrl, client, token)
                    Timber.tag("YouTubeCaptions").d(
                        "Caption response client=%s language=%s kind=%s bytes=%d",
                        client.clientName, track.languageCode, track.kind ?: "uploaded", text.length,
                    )
                    withContext(Dispatchers.Default) { autoCaptionsToLrc(text).takeIf { it.isNotBlank() } }
                }
            }
            if (lyrics != null) return lyrics
        }
        return null
    }

    private suspend fun <T> attempt(block: suspend () -> T?): T? {
        currentCoroutineContext().ensureActive()
        return try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Never log signed caption URLs or token values.
            Timber.tag("YouTubeCaptions").d("Caption attempt failed: %s", error.javaClass.simpleName)
            null
        }
    }
}

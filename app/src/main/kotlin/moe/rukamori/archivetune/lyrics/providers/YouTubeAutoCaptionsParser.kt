package moe.rukamori.archivetune.lyrics.providers

import moe.rukamori.archivetune.innertube.models.response.PlayerResponse.Captions.CaptionTrack
import kotlinx.serialization.json.decodeFromJsonElement

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.util.Locale

private val soundLabels = Regex("""\[[^\]]*\]""")
private val whitespace = Regex("""\s+""")

/** JSON3 caption events become timed lyric lines; rolling duplicates and sound labels are omitted. */
internal fun autoCaptionsToLrc(json: String): String {
    val root = Json.parseToJsonElement(json) as? JsonObject ?: return ""
    val events = root["events"] as? JsonArray ?: return ""
    val lines = mutableListOf<Pair<Long, String>>()
    for (event in events) {
        val cue = event as? JsonObject ?: continue
        val start = (cue["tStartMs"] as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0L } ?: continue
        val segments = cue["segs"] as? JsonArray ?: continue
        val text = segments.joinToString("") {
            ((it as? JsonObject)?.get("utf8") as? JsonPrimitive)?.contentOrNull.orEmpty()
        }.replace(soundLabels, " ").replace('♪', ' ').replace(whitespace, " ").trim()
        if (text.isNotEmpty()) lines += start to text
    }
    val result = mutableListOf<String>()
    var previous: String? = null
    for ((time, text) in lines.sortedBy { it.first }) {
        if (text == previous) continue
        previous = text
        result += String.format(Locale.ROOT, "[%02d:%02d.%03d]%s",
            time / 60_000, time / 1000 % 60, time % 1000, text)
    }
    return result.joinToString("\n")
}

internal fun captionTracksForLyrics(tracks: List<CaptionTrack>, preferredLanguage: String): List<CaptionTrack> {
    fun language(code: String) = code.lowercase(Locale.ROOT).substringBefore('-')
    val auto = tracks.filter { it.kind == "asr" }
    val source = auto.firstOrNull { language(it.languageCode) == language(preferredLanguage) }
        ?: auto.firstOrNull()
    // An ASR track identifies the sung language. Don't fall back to an unrelated translation.
    val wanted = source?.languageCode
        ?: tracks.firstOrNull { language(it.languageCode) == language(preferredLanguage) }?.languageCode
        ?: tracks.firstOrNull()?.languageCode
        ?: return emptyList()
    return tracks.filter { language(it.languageCode) == language(wanted) }
        .sortedBy { it.kind == "asr" }
}

internal data class WatchCaptionData(val tracks: List<CaptionTrack>, val visitorData: String?)

internal fun watchCaptionData(html: String, expectedVideoId: String): WatchCaptionData? {
    val assignment = Regex("""(?:ytInitialPlayerResponse|window\["ytInitialPlayerResponse"\])\s*=\s*""")
        .find(html) ?: return null
    val start = html.indexOf('{', assignment.range.last + 1)
    if (start < 0) return null
    var depth = 0
    var quoted = false
    var escaped = false
    var end = -1
    for (index in start until html.length) {
        val char = html[index]
        if (quoted) {
            if (escaped) escaped = false
            else if (char == '\\') escaped = true
            else if (char == '"') quoted = false
        } else {
            when (char) {
                '"' -> quoted = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        end = index + 1
                        break
                    }
                }
            }
        }
    }
    if (end < 0) return null
    val root = Json.parseToJsonElement(html.substring(start, end)) as? JsonObject ?: return null
    val details = root["videoDetails"] as? JsonObject ?: return null
    if ((details["videoId"] as? JsonPrimitive)?.contentOrNull != expectedVideoId) return null
    val captions = (root["captions"] as? JsonObject)
        ?.get("playerCaptionsTracklistRenderer") as? JsonObject ?: return null
    val tracksJson = captions["captionTracks"] as? JsonArray ?: return null
    val decoder = Json { ignoreUnknownKeys = true }
    val tracks = tracksJson.map { decoder.decodeFromJsonElement<CaptionTrack>(it) }
    val visitor = ((root["responseContext"] as? JsonObject)
        ?.get("visitorData") as? JsonPrimitive)?.contentOrNull
    return WatchCaptionData(tracks, visitor).takeIf { tracks.isNotEmpty() }
}

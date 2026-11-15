package moe.rukamori.archivetune.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.datastore.preferences.core.stringPreferencesKey
import moe.rukamori.archivetune.utils.rememberPreference

internal fun validMusicVideoChoice(videoId: String?): String? =
    videoId?.takeIf { Regex("[A-Za-z0-9_-]{11}").matches(it) }

/** Store only the YouTube ID: signed playback URLs are resolved afresh when needed. */
@Composable
internal fun rememberMusicVideoChoice(mediaId: String): MutableState<String> = key(mediaId) {
    rememberPreference(stringPreferencesKey("musicVideoChoice:$mediaId"), "")
}

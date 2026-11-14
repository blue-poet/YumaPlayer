package moe.rukamori.archivetune.ui.player

import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.stringPreferencesKey
import moe.rukamori.archivetune.constants.ArchiveTuneCanvasKey
import moe.rukamori.archivetune.utils.rememberPreference

enum class PlayerArtworkMode {
    ALBUM_ART,
    ANIMATED_CANVAS,
    MUSIC_VIDEO,
}

private val PlayerArtworkModeKey = stringPreferencesKey("playerArtworkMode")

internal fun resolvePlayerArtworkMode(enabled: Boolean, preference: String): PlayerArtworkMode =
    if (!enabled) PlayerArtworkMode.ALBUM_ART
    else if (preference == PlayerArtworkMode.MUSIC_VIDEO.name) PlayerArtworkMode.MUSIC_VIDEO
    else PlayerArtworkMode.ANIMATED_CANVAS

@Composable
internal fun rememberPlayerArtworkMode(): Pair<PlayerArtworkMode, (PlayerArtworkMode) -> Unit> {
    val (enabled, setEnabled) = rememberPreference(ArchiveTuneCanvasKey, false)
    val (preference, setPreference) = rememberPreference(PlayerArtworkModeKey, PlayerArtworkMode.ANIMATED_CANVAS.name)
    return resolvePlayerArtworkMode(enabled, preference) to { mode ->
        setPreference(mode.name)
        setEnabled(mode != PlayerArtworkMode.ALBUM_ART)
    }
}

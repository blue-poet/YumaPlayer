package moe.rukamori.archivetune.ui.player.player_0.full

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLineText
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.StateFlow
import moe.rukamori.archivetune.lyrics.model.LyricsEntry
import moe.rukamori.archivetune.ui.component.lyrics.buildSyncedLyrics
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily

internal fun videoCaptionEntry(
    lines: List<LyricsEntry>,
    positionMs: Long,
    offsetMs: Int,
    isSynced: Boolean,
): LyricsEntry? {
    if (!isSynced || lines.isEmpty()) return null
    val time = positionMs + offsetMs
    if (time < 0L) return null
    var low = 0
    var high = lines.lastIndex
    var index = -1
    while (low <= high) {
        val middle = (low + high).ushr(1)
        if (lines[middle].time <= time) {
            index = middle
            low = middle + 1
        } else high = middle - 1
    }
    val line = lines.getOrNull(index) ?: return null
    if (line.time < 0L || line.isInstrumental || line.text.isBlank()) return null
    val next = lines.getOrNull(index + 1)?.time?.takeIf { it > line.time }
    val end = if (line.durationMs > 0L) minOf(line.time + line.durationMs, next ?: Long.MAX_VALUE)
        else next ?: (line.time + 10_000L)
    return line.takeIf { time < end }
}

internal fun videoCaptionModel(entry: LyricsEntry): ISyncedLine? =
    buildSyncedLyrics(listOf(entry), isTtml = true, romanizationMap = emptyMap()).lines.firstOrNull()

/** Reuses the lyrics screen's syllable renderer, driven directly by the song's audio clock. */
@Composable
internal fun VideoWordLyrics(
    state: PlayerUiState,
    playbackProgress: StateFlow<Long>,
    modifier: Modifier = Modifier,
) {
    val player = LocalPlayerConnection.current?.player
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val position = remember(player) { mutableLongStateOf(playbackProgress.value) }
    val offset = rememberUpdatedState(state.lyricsSyncOffset)
    LaunchedEffect(player, state.trackUrl, lifecycleState) {
        if (!lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) return@LaunchedEffect
        while (isActive) {
            position.longValue = player?.currentPosition?.coerceAtLeast(0L) ?: playbackProgress.value
            if (player?.isPlaying == true) withFrameNanos { } else delay(50L)
        }
    }
    val entry by remember(state.lyricsList, state.isSynced, state.lyricsSyncOffset) {
        derivedStateOf { videoCaptionEntry(state.lyricsList, position.longValue, offset.value, state.isSynced) }
    }
    val line = remember(entry) { entry?.let(::videoCaptionModel) }
    val clock = remember(position, offset) {
        { (position.longValue + offset.value).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt() }
    }
    val textStyle = TextStyle(
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = LocalArchiveTuneFontFamily.current,
        textMotion = TextMotion.Animated,
    )
    // KaraokeLineText remembers glyph layouts internally; each new line needs its own composition.
    key(state.trackUrl, line, textStyle) {
        when (line) {
            is KaraokeLine -> KaraokeLineText(
                line = line,
                currentTimeProvider = clock,
                normalLineTextStyle = textStyle,
                accompanimentLineTextStyle = textStyle.copy(fontSize = 20.sp),
                phoneticTextStyle = textStyle.copy(fontSize = 14.sp),
                activeColor = Color.White,
                blendMode = BlendMode.SrcOver,
                showTranslation = false,
                showPhonetic = false,
                modifier = modifier.fillMaxWidth(),
            )
            else -> entry?.let { Text(it.text, color = Color.White, style = textStyle, modifier = modifier.fillMaxWidth()) }
        }
    }
}

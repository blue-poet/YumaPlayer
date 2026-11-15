package moe.rukamori.archivetune.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Video always renders on a dark media surface, independent of the surrounding app theme. */
object PlayerVideoTokens {
    val Background = Color.Black
    val Foreground = Color.White
    val InactiveForeground = Color.Gray
    val ControlContainer = Background.copy(alpha = 0.6f)
    val CaptionScrim = Background.copy(alpha = 0.78f)
    val StatusContainer = Background.copy(alpha = 0.7f)
    val PickerThumbnailHeight = 48.dp
    val LoadingIndicatorSize = 20.dp
    val StatusPaddingHorizontal = 12.dp
    val StatusPaddingVertical = 8.dp
    val ControlSize = 48.dp
    val ControlPadding = 16.dp
    val TransportPadding = 20.dp
    val ControlGap = 8.dp
    val CaptionStartPadding = 32.dp
    val CaptionEndPadding = 100.dp
    val CaptionControlsBottom = 104.dp
    val CaptionBottom = 28.dp
    val CaptionScrimHeight = 220.dp
    val SeekbarStartPadding = 16.dp
    val SeekbarEndPadding = 84.dp
    val SeekbarBottomPadding = 16.dp
    val CaptionSize = 26.sp
    val CaptionLineHeight = 32.sp
    val AccompanimentSize = 18.sp
    val PhoneticSize = 12.sp
    const val ArtworkCrossfadeMillis = 500
    const val ControlsFadeMillis = 150
    const val CaptionOffsetMillis = 180
    const val ControlsTimeoutMillis = 3_000L
    const val SyncStatusTimeoutMillis = 3_000L
}

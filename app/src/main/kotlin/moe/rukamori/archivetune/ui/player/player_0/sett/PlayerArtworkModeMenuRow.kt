package moe.rukamori.archivetune.ui.player.player_0.sett

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.player.PlayerArtworkMode
import moe.rukamori.archivetune.ui.player.rememberPlayerArtworkMode
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

@Composable
internal fun PlayerArtworkModeMenuRow(index: Int, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (mode) = rememberPlayerArtworkMode()
    CompactMenuRow(
        modifier = modifier,
        title = stringResource(R.string.player_artwork_mode),
        subtitle = stringResource(mode.labelResource),
        iconResId = R.drawable.ic_palette,
        onClick = onClick,
        showArrow = true,
        index = index,
        count = count,
    )
}

@Composable
internal fun PlayerArtworkMenuContent(modifier: Modifier = Modifier, onChooseVideo: () -> Unit = {}, canChooseVideo: Boolean = false) {
    val (mode, setMode) = rememberPlayerArtworkMode()
    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.player_artwork_mode),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = SettingsDimensions.BottomSheetTitleBottomPadding),
        )
        PlayerArtworkMode.entries.forEachIndexed { index, choice ->
            if (index > 0) Spacer(Modifier.height(SettingsDimensions.SegmentedItemGap))
            SettingsMenuRow(
                title = stringResource(choice.labelResource),
                subtitle = stringResource(choice.descriptionResource),
                iconResId = R.drawable.ic_palette,
                onClick = { setMode(choice) },
                index = index,
                count = PlayerArtworkMode.entries.size,
                modifier = Modifier.semantics {
                    selected = mode == choice
                    role = Role.RadioButton
                },
                trailingContent = { VideoSelectionIndicator(mode == choice) },
            )
        }
        if (canChooseVideo) {
            Spacer(Modifier.height(SettingsDimensions.BottomSheetTitleBottomPadding))
            SettingsMenuRow(
                title = stringResource(R.string.player_video_choose),
                subtitle = stringResource(R.string.player_video_choose_description),
                iconResId = R.drawable.search,
                onClick = onChooseVideo,
                showArrow = true,
            )
        }
    }
}

private val PlayerArtworkMode.labelResource: Int
    get() = when (this) {
        PlayerArtworkMode.ALBUM_ART -> R.string.player_artwork_album_art
        PlayerArtworkMode.ANIMATED_CANVAS -> R.string.player_artwork_animated_canvas
        PlayerArtworkMode.MUSIC_VIDEO -> R.string.player_artwork_music_video
    }

private val PlayerArtworkMode.descriptionResource: Int
    get() = when (this) {
        PlayerArtworkMode.ALBUM_ART -> R.string.player_artwork_album_description
        PlayerArtworkMode.ANIMATED_CANVAS -> R.string.player_artwork_canvas_description
        PlayerArtworkMode.MUSIC_VIDEO -> R.string.player_artwork_video_description
    }

package moe.rukamori.archivetune.ui.player.player_0.sett

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.musicvideo.MusicVideoSearchItem
import moe.rukamori.archivetune.ui.player.PlayerArtworkMode
import moe.rukamori.archivetune.ui.player.rememberMusicVideoChoice
import moe.rukamori.archivetune.ui.player.rememberPlayerArtworkMode
import moe.rukamori.archivetune.ui.settings.PlayerVideoTokens
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.component.segmentedPreferenceItemShape
import moe.rukamori.archivetune.ui.theme.yumaCombinedClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.theme.yumaSegmentPosition

@Immutable
private data class MusicVideoPickerActions(
    val onQueryChange: (String) -> Unit,
    val onSearch: () -> Unit,
    val onRetry: () -> Unit,
    val onMore: () -> Unit,
    val onChoose: (String) -> Unit,
)

@Composable
internal fun MusicVideoPickerMenuContent(
    state: PlayerUiState,
    onPicked: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MusicVideoPickerViewModel = hiltViewModel(key = "music-video-picker:${state.trackUrl}"),
) {
    var chosen by rememberMusicVideoChoice(state.trackUrl)
    val (_, setArtworkMode) = rememberPlayerArtworkMode()
    val seed = "${state.title} ${state.artist}".trim()
    var query by remember(state.trackUrl) { mutableStateOf(seed) }
    val searchState by viewModel.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(viewModel, state.trackUrl) { viewModel.search(seed) }
    DisposableEffect(viewModel) { onDispose { viewModel.cancelRequests() } }
    MusicVideoPickerContent(
        state = searchState,
        query = query,
        chosen = chosen,
        actions = MusicVideoPickerActions(
            onQueryChange = { query = it },
            onSearch = {
                viewModel.search(query)
                keyboard?.hide()
            },
            onRetry = { viewModel.search(searchState.query) },
            onMore = viewModel::loadMore,
            onChoose = {
                chosen = it
                setArtworkMode(PlayerArtworkMode.MUSIC_VIDEO)
                keyboard?.hide()
                onPicked()
            },
        ),
        modifier = modifier,
    )
}

@Composable
private fun MusicVideoPickerContent(
    state: MusicVideoPickerUiState,
    query: String,
    chosen: String,
    actions: MusicVideoPickerActions,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().heightIn(max = SettingsDimensions.BottomSheetListMaxHeight)) {
        Text(
            stringResource(R.string.player_video_choose),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = SettingsDimensions.BottomSheetTitleBottomPadding),
        )
        Text(
            stringResource(R.string.player_video_choose_about),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = SettingsDimensions.SectionSpacing),
        )
        OutlinedTextField(
            value = query,
            onValueChange = actions.onQueryChange,
            label = { Text(stringResource(R.string.player_video_search)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { actions.onSearch() }),
            trailingIcon = {
                IconButton(onClick = actions.onSearch, enabled = query.isNotBlank()) {
                    Icon(painterResource(R.drawable.search), stringResource(R.string.player_video_search))
                }
            },
        )
        Spacer(Modifier.height(SettingsDimensions.SectionSpacing))
        SettingsMenuRow(
            title = stringResource(R.string.player_video_automatic),
            subtitle = stringResource(R.string.player_video_automatic_description),
            iconResId = R.drawable.ic_palette,
            onClick = { actions.onChoose("") },
            trailingContent = { VideoSelectionIndicator(chosen.isBlank()) },
            modifier = Modifier.semantics { selected = chosen.isBlank(); role = Role.RadioButton },
        )
        Spacer(Modifier.height(SettingsDimensions.SectionSpacing))
        if (state.loading) {
            Row(
                Modifier.fillMaxWidth().padding(SettingsDimensions.ScreenHorizontalPadding),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(Modifier.size(PlayerVideoTokens.LoadingIndicatorSize))
                Spacer(Modifier.width(SettingsDimensions.SectionSpacing))
                Text(stringResource(R.string.player_video_searching))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentedItemGap),
            ) {
                if (state.results.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(if (state.searchFailed) R.string.player_video_search_error else R.string.player_video_search_empty),
                            modifier = Modifier.padding(vertical = SettingsDimensions.SectionSpacing),
                        )
                    }
                }
                itemsIndexed(state.results, key = { _, video -> video.id }) { index, video ->
                    MusicVideoResultRow(
                        video = video,
                        selected = chosen == video.id,
                        index = index,
                        count = state.results.size,
                        onClick = { actions.onChoose(video.id) },
                    )
                }
                if (state.searchFailed) {
                    item(key = "retry") {
                        TextButton(onClick = actions.onRetry) { Text(stringResource(R.string.player_video_retry)) }
                    }
                } else if (state.continuation != null) {
                    item(key = "more") {
                        TextButton(enabled = !state.loadingMore, onClick = actions.onMore) {
                            if (state.loadingMore) CircularProgressIndicator(Modifier.size(PlayerVideoTokens.LoadingIndicatorSize))
                            else Text(stringResource(if (state.paginationFailed) R.string.player_video_retry else R.string.player_video_more))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun VideoSelectionIndicator(selected: Boolean) {
    if (selected) {
        Icon(painterResource(R.drawable.check), null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MusicVideoResultRow(
    video: MusicVideoSearchItem,
    selected: Boolean,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalYumaColors.current
    val shape = remember(index, count) { segmentedPreferenceItemShape(index, count) }
    Row(
        modifier = modifier.fillMaxWidth()
            .semantics { this.selected = selected; role = Role.RadioButton }
            .yumaCombinedClickable(onClick = onClick)
            .yumaGlassCard(
                shape = shape,
                position = yumaSegmentPosition(index, count),
                backgroundColor = colors.glassBackground,
                borderColor = if (selected) MaterialTheme.colorScheme.primary else colors.glassBorder,
                strokeWidth = SettingsDimensions.GlassBorderThickness,
            )
            .clip(shape)
            .padding(horizontal = SettingsDimensions.SegmentedItemPaddingHorizontal, vertical = SettingsDimensions.SegmentedItemPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .height(PlayerVideoTokens.PickerThumbnailHeight)
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.small),
        )
        Spacer(Modifier.width(SettingsDimensions.YumaRowIconSpacing))
        Column(Modifier.weight(1f)) {
            Text(
                video.title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val duration = video.durationSeconds?.let { "%d:%02d".format(it / 60, it % 60) }
            Text(
                listOfNotNull(video.artist.takeIf(String::isNotBlank), duration).joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        VideoSelectionIndicator(selected)
    }
}

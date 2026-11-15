package moe.rukamori.archivetune.ui.player.player_0.sett

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.SearchFilter
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.ui.player.PlayerArtworkMode
import moe.rukamori.archivetune.ui.player.rememberMusicVideoChoice
import moe.rukamori.archivetune.ui.player.rememberPlayerArtworkMode
import moe.rukamori.archivetune.ui.player.validMusicVideoChoice
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaCombinedClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard

@Composable
internal fun MusicVideoPickerMenuContent(
    state: PlayerUiState,
    onPicked: () -> Unit,
) {
    var chosen by rememberMusicVideoChoice(state.trackUrl)
    val (_, setArtworkMode) = rememberPlayerArtworkMode()
    var query by remember { mutableStateOf("${state.title} ${state.artist}".trim()) }
    var submitted by remember { mutableStateOf(query) }
    var retry by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var continuation by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var moreJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    fun choose(videoId: String) {
        chosen = videoId
        setArtworkMode(PlayerArtworkMode.MUSIC_VIDEO)
        keyboard?.hide()
        onPicked()
    }

    fun submitSearch() {
        if (query.isNotBlank()) {
            submitted = query.trim()
            retry++
            keyboard?.hide()
        }
    }

    LaunchedEffect(submitted, retry) {
        moreJob?.cancel()
        loadingMore = false
        loading = true
        error = false
        results = emptyList()
        continuation = null
        try {
            val page = YouTube.search(submitted, SearchFilter.FILTER_VIDEO).getOrThrow()
            ensureActive()
            results = page.items.filterIsInstance<SongItem>()
                .filter { validMusicVideoChoice(it.id) != null }.distinctBy { it.id }
            continuation = page.continuation
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = true
        } finally {
            loading = false
        }
    }
    DisposableEffect(Unit) {
        onDispose { moreJob?.cancel() }
    }

    Column(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
        Text(
            stringResource(R.string.player_video_choose),
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = LocalArchiveTuneFontFamily.current,
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp),
        )
        Text(
            stringResource(R.string.player_video_choose_about),
            color = Color.White.copy(alpha = SettingsDimensions.YumaRowSubtitleAlpha),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.player_video_search)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
            trailingIcon = {
                IconButton(onClick = { submitSearch() }, enabled = query.isNotBlank()) {
                    Icon(painterResource(R.drawable.search), stringResource(R.string.player_video_search))
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        SettingsMenuRow(
            title = stringResource(R.string.player_video_automatic),
            subtitle = stringResource(R.string.player_video_automatic_description),
            iconResId = R.drawable.ic_palette,
            onClick = { choose("") },
            trailingContent = { RadioButton(selected = chosen.isBlank(), onClick = null) },
            modifier = Modifier.semantics { selected = chosen.isBlank(); role = Role.RadioButton },
        )
        Spacer(Modifier.height(12.dp))
        if (loading) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.player_video_searching))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentedItemGap),
            ) {
                if (results.isEmpty()) {
                    item {
                        Text(
                            stringResource(if (error) R.string.player_video_search_error else R.string.player_video_search_empty),
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
                itemsIndexed(results, key = { _, video -> video.id }) { _, video ->
                    MusicVideoResultRow(
                        video = video,
                        selected = chosen == video.id,
                        onClick = { choose(video.id) },
                    )
                }
                if (error) {
                    item {
                        TextButton(onClick = { retry++ }) { Text(stringResource(R.string.player_video_retry)) }
                    }
                } else if (continuation != null) {
                    item {
                        TextButton(
                            enabled = !loadingMore,
                            onClick = {
                                val token = continuation ?: return@TextButton
                                loadingMore = true
                                moreJob = scope.launch {
                                    try {
                                        val page = YouTube.searchContinuation(token).getOrThrow()
                                        ensureActive()
                                        results = (results + page.items.filterIsInstance<SongItem>())
                                            .filter { validMusicVideoChoice(it.id) != null }.distinctBy { it.id }
                                        continuation = page.continuation
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        error = true
                                    } finally {
                                        loadingMore = false
                                    }
                                }
                            },
                        ) {
                            if (loadingMore) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text(stringResource(R.string.player_video_more))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicVideoResultRow(video: SongItem, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalYumaColors.current
    Row(
        modifier = Modifier.fillMaxWidth()
            .semantics { this.selected = selected; role = Role.RadioButton }
            .yumaCombinedClickable(onClick = onClick)
            .yumaGlassCard(
                shape = RoundedCornerShape(SettingsDimensions.SegmentedCornerLarge),
                backgroundColor = colors.glassBackground,
                borderColor = if (selected) MaterialTheme.colorScheme.primary else colors.glassBorder,
                strokeWidth = SettingsDimensions.GlassBorderThickness,
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = video.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 80.dp, height = 48.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                video.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = LocalArchiveTuneFontFamily.current,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val duration = video.duration?.let { "%d:%02d".format(it / 60, it % 60) }
            Text(
                listOfNotNull(video.artists.joinToString(", ") { it.name }.takeIf { it.isNotBlank() }, duration).joinToString(" · "),
                color = Color.White.copy(alpha = SettingsDimensions.YumaRowSubtitleAlpha),
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RadioButton(selected = selected, onClick = null)
    }
}

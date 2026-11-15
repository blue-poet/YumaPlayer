package moe.rukamori.archivetune.ui.player.canvas.playback

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.sponsorblock.GetSponsorBlockSegmentsUseCase
import moe.rukamori.archivetune.sponsorblock.ObserveSponsorBlockSettingsUseCase
import moe.rukamori.archivetune.sponsorblock.SponsorBlockCategory
import moe.rukamori.archivetune.sponsorblock.SponsorBlockSegment
import moe.rukamori.archivetune.sponsorblock.SponsorBlockSettings
import timber.log.Timber

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface MusicVideoSponsorBlockEntryPoint {
    fun observeSettings(): ObserveSponsorBlockSettingsUseCase
    fun getSegments(): GetSponsorBlockSegmentsUseCase
}

internal data class MusicVideoSkipState(
    val segments: List<SponsorBlockSegment> = emptyList(),
    val loading: Boolean = false,
)

@Composable
internal fun rememberMusicVideoSkips(
    songId: String?,
    videoId: String?,
    retryGeneration: Int,
): MusicVideoSkipState {
    if (videoId == null || videoId == songId || LocalInspectionMode.current) return MusicVideoSkipState()
    val context = LocalContext.current.applicationContext
    val dependencies = remember(context) {
        EntryPointAccessors.fromApplication(context, MusicVideoSponsorBlockEntryPoint::class.java)
    }
    val settingsFlow = remember(dependencies) { dependencies.observeSettings().invoke() }
    val settings by settingsFlow.collectAsState(initial = SponsorBlockSettings.Default)
    val selectedCategory = setOf(SponsorBlockCategory.MUSIC_OFF_TOPIC)
    // With the same source, the existing audio SponsorBlock controller advances both players.
    val shouldFetch = settings.enabled && SponsorBlockCategory.MUSIC_OFF_TOPIC in settings.categories
    var result by remember(songId, videoId, settings, retryGeneration) {
        mutableStateOf(MusicVideoSkipState(loading = shouldFetch))
    }
    LaunchedEffect(songId, videoId, settings, retryGeneration) {
        if (!shouldFetch) return@LaunchedEffect
        result = try {
            val segments = dependencies.getSegments().invoke(
                videoId = videoId,
                settings = settings.copy(categories = selectedCategory),
                source = PlaybackSource.YT_MUSIC,
            )
            MusicVideoSkipState(segments)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("PlayerCanvas").w(error, "Non-music video segments unavailable for %s", videoId)
            MusicVideoSkipState()
        }
    }
    return result
}

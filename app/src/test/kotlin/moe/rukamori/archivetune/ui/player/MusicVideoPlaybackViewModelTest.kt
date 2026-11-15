package moe.rukamori.archivetune.ui.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import moe.rukamori.archivetune.musicvideo.*
import moe.rukamori.archivetune.ui.player.canvas.playback.MusicVideoPlaybackViewModel
import moe.rukamori.archivetune.ui.player.player_0.PlayerCanvasState
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicVideoPlaybackViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun request(id: String) = MusicVideoPlaybackRequest(id, "Song", "Artist", null, false, 0)

    @Test fun alignmentIsNotStartedWithoutALifecycleSubscriberAndStopsWhenItLeaves() = runTest(dispatcher) {
        var started = 0
        var cancelled = 0
        val repository = object : MusicVideoPlaybackRepository {
            override fun observe(request: MusicVideoPlaybackRequest) = flow {
                started++
                try {
                    emit(MusicVideoPlaybackState(status = MusicVideoPlaybackStatus.READY))
                    awaitCancellation()
                } finally {
                    cancelled++
                }
            }
        }
        val viewModel = MusicVideoPlaybackViewModel(ObserveMusicVideoPlaybackUseCase(repository))
        viewModel.load(request("one"))
        runCurrent()
        assertEquals(0, started)
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        runCurrent()
        assertEquals(1, started)
        collector.cancel()
        runCurrent()
        assertEquals(1, cancelled)
    }

    @Test fun disposingAnOlderRequestDoesNotClearTheNewSong() = runTest(dispatcher) {
        val repository = object : MusicVideoPlaybackRepository {
            override fun observe(request: MusicVideoPlaybackRequest) =
                kotlinx.coroutines.flow.flowOf(MusicVideoPlaybackState(status = MusicVideoPlaybackStatus.READY))
        }
        val viewModel = MusicVideoPlaybackViewModel(ObserveMusicVideoPlaybackUseCase(repository))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        val old = request("old")
        val current = request("current")
        viewModel.load(old)
        runCurrent()
        viewModel.load(current)
        viewModel.clear(old)
        runCurrent()
        assertEquals(current, viewModel.state.value.request)
    }

    @Test fun completedAlignmentCannotResurrectAVideoThatFailedToPlay() {
        val canvas = PlayerCanvasState(true, null)
        val request = request("song")
        val ready = MusicVideoPlaybackState(
            status = MusicVideoPlaybackStatus.READY,
            videoId = "abcdefghijk",
            streamUrl = "https://example.com/video.mp4",
        )
        canvas.applyPlayback(ready, request)
        canvas.markVideoUnavailable()
        canvas.applyPlayback(ready.copy(offsetMs = 1_000L), request)
        assertTrue(canvas.isVideoUnavailable)
        assertNull(canvas.artwork)
        canvas.retryVideo()
        canvas.applyPlayback(ready, request)
        assertFalse(canvas.isVideoUnavailable)
        assertNotNull(canvas.artwork)
    }
}

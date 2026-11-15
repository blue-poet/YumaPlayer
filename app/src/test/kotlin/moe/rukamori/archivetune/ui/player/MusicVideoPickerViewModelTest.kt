package moe.rukamori.archivetune.ui.player

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import moe.rukamori.archivetune.musicvideo.*
import moe.rukamori.archivetune.ui.player.player_0.sett.MusicVideoPickerViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicVideoPickerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<MusicVideoSearchRepository>()
    private fun item(id: String) = MusicVideoSearchItem(id, id, "Artist", "", null)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun aCancelledOlderSearchCannotReplaceTheCurrentResults() = runTest(dispatcher) {
        coEvery { repository.search("old", null) } coAnswers {
            try { delay(1_000L) } catch (_: CancellationException) {
                // Some external sources finish even after cancellation.
            }
            Result.success(MusicVideoSearchPage(listOf(item("old")), null))
        }
        coEvery { repository.search("new", null) } returns
            Result.success(MusicVideoSearchPage(listOf(item("new")), null))
        val viewModel = MusicVideoPickerViewModel(SearchMusicVideosUseCase(repository))
        viewModel.search("old")
        runCurrent()
        viewModel.search("new")
        advanceUntilIdle()
        assertEquals(listOf("new"), viewModel.state.value.results.map { it.id })
        assertFalse(viewModel.state.value.loading)
    }

    @Test fun failedPaginationRetainsResultsAndCanRetryTheSameCursor() = runTest(dispatcher) {
        coEvery { repository.search("song", null) } returns
            Result.success(MusicVideoSearchPage(listOf(item("one")), "cursor"))
        coEvery { repository.search("song", "cursor") } returnsMany listOf(
            Result.failure(IllegalStateException("temporarily unavailable")),
            Result.success(MusicVideoSearchPage(listOf(item("one"), item("two")), null)),
        )
        val viewModel = MusicVideoPickerViewModel(SearchMusicVideosUseCase(repository))
        viewModel.search("song")
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.paginationFailed)
        assertEquals(listOf("one"), viewModel.state.value.results.map { it.id })
        assertEquals("cursor", viewModel.state.value.continuation)
        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("one", "two"), viewModel.state.value.results.map { it.id })
        assertNull(viewModel.state.value.continuation)
        assertFalse(viewModel.state.value.loadingMore)
    }

    @Test fun repeatedContinuationDoesNotCauseAnEndlessLoadMoreLoop() = runTest(dispatcher) {
        coEvery { repository.search("song", any()) } returns
            Result.success(MusicVideoSearchPage(listOf(item("one")), "cursor"))
        val viewModel = MusicVideoPickerViewModel(SearchMusicVideosUseCase(repository))
        viewModel.search("song")
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()
        assertNull(viewModel.state.value.continuation)
    }
}

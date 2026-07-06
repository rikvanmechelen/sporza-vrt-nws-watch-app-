package me.vanmechelen.vrtsporza.ui

import app.cash.turbine.test
import me.vanmechelen.vrtsporza.MainDispatcherRule
import me.vanmechelen.vrtsporza.data.MatchesRepository
import me.vanmechelen.vrtsporza.model.BlockType
import me.vanmechelen.vrtsporza.model.ContentBlock
import me.vanmechelen.vrtsporza.model.Match
import me.vanmechelen.vrtsporza.model.MatchDetail
import me.vanmechelen.vrtsporza.model.MatchStatus
import me.vanmechelen.vrtsporza.ui.matches.MatchDetailUiState
import me.vanmechelen.vrtsporza.ui.matches.MatchDetailViewModel
import me.vanmechelen.vrtsporza.ui.matches.MatchesViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun match(id: String) = Match(
    id = id, sportSlug = "voetbal", competition = "C", home = "H", away = "A",
    homeLogoUrl = null, awayLogoUrl = null, score = null, statusText = "", status = MatchStatus.LIVE,
    detailUrl = "https://x/$id", title = "H - A",
)

private class FakeMatchesRepo : MatchesRepository {
    val flow = MutableStateFlow<List<Match>>(emptyList())
    val syncedAt = MutableStateFlow<Long?>(null)
    var refreshResult: Result<Unit> = Result.success(Unit)
    var detailResult: Result<MatchDetail> = Result.success(MatchDetail(emptyList(), emptyList(), emptyList()))
    override fun matches(): Flow<List<Match>> = flow
    override fun lastSyncedAt(): Flow<Long?> = syncedAt
    override suspend fun refresh(): Result<Unit> = refreshResult
    override suspend fun detail(url: String): Result<MatchDetail> = detailResult
}

class MatchesViewModelsTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test
    fun refreshesOnInitAndExposesMatches() = runTest {
        val repo = FakeMatchesRepo()
        val vm = MatchesViewModel(repo)
        vm.uiState.test {
            awaitItem() // initial
            repo.flow.value = listOf(match("a"), match("b"))
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertEquals(2, state.matches.size)
            assertFalse(state.isRefreshing)
            assertFalse(state.loadFailed)
        }
    }

    @Test
    fun exposesLastSyncedTimeFromRepository() = runTest {
        val repo = FakeMatchesRepo()
        val vm = MatchesViewModel(repo)
        vm.uiState.test {
            awaitItem()
            repo.syncedAt.value = 321L
            advanceUntilIdle()
            assertEquals(321L, expectMostRecentItem().lastSyncedEpochMs)
        }
    }

    @Test
    fun refreshFailureWithCacheShowsOfflineBanner() = runTest {
        val repo = FakeMatchesRepo().apply {
            flow.value = listOf(match("a"))
            refreshResult = Result.failure(RuntimeException("offline"))
        }
        val vm = MatchesViewModel(repo)
        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state.loadFailed)
            assertTrue(state.showOfflineBanner)
            assertFalse(state.showError)
        }
    }

    @Test
    fun detailReadyOnSuccess() = runTest {
        val repo = FakeMatchesRepo().apply {
            detailResult = Result.success(
                MatchDetail(emptyList(), emptyList(), listOf(ContentBlock(BlockType.PARAGRAPH, "hi"))),
            )
        }
        val vm = MatchDetailViewModel(repo, "https://x/a", now = { 555L })
        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state is MatchDetailUiState.Ready)
            assertEquals("hi", (state as MatchDetailUiState.Ready).detail.recap.first().text)
            assertEquals("detail carries its load time for the freshness marker", 555L, state.syncedAtEpochMs)
        }
    }

    @Test
    fun detailFailedOnError() = runTest {
        val repo = FakeMatchesRepo().apply { detailResult = Result.failure(RuntimeException("boom")) }
        val vm = MatchDetailViewModel(repo, "https://x/a")
        vm.uiState.test {
            advanceUntilIdle()
            assertTrue(expectMostRecentItem() is MatchDetailUiState.Failed)
        }
    }
}

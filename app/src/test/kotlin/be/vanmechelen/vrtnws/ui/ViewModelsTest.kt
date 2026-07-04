package be.vanmechelen.vrtnws.ui

import app.cash.turbine.test
import be.vanmechelen.vrtnws.MainDispatcherRule
import be.vanmechelen.vrtnws.data.NewsRepository
import be.vanmechelen.vrtnws.model.Article
import be.vanmechelen.vrtnws.model.ArticleContent
import be.vanmechelen.vrtnws.model.BlockType
import be.vanmechelen.vrtnws.model.ContentBlock
import be.vanmechelen.vrtnws.ui.article.ArticleUiState
import be.vanmechelen.vrtnws.ui.article.ArticleViewModel
import be.vanmechelen.vrtnws.ui.headlines.HeadlinesViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun article(id: String) =
    Article(id, "t-$id", "", "https://x/$id", null, 0L, null)

private class FakeRepo : NewsRepository {
    val flow = MutableStateFlow<List<Article>>(emptyList())
    var refreshResult: Result<Unit> = Result.success(Unit)
    var bodyResult: Result<ArticleContent> = Result.success(ArticleContent(emptyList()))
    override fun headlines(): Flow<List<Article>> = flow
    override suspend fun refresh(): Result<Unit> = refreshResult
    override suspend fun body(id: String, url: String): Result<ArticleContent> = bodyResult
    override suspend fun latestHeadline(): Article? = flow.value.firstOrNull()
}

class ViewModelsTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test
    fun headlinesRefreshOnInitAndExposesArticles() = runTest {
        val repo = FakeRepo()
        val vm = HeadlinesViewModel(repo)
        vm.uiState.test {
            awaitItem() // initial
            repo.flow.value = listOf(article("a"), article("b"))
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertEquals(2, state.articles.size)
            assertFalse(state.isRefreshing)
            assertFalse(state.loadFailed)
        }
    }

    @Test
    fun headlinesRefreshFailureWithCacheShowsOfflineBanner() = runTest {
        val repo = FakeRepo().apply {
            flow.value = listOf(article("a"))
            refreshResult = Result.failure(RuntimeException("offline"))
        }
        val vm = HeadlinesViewModel(repo)
        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state.loadFailed)
            assertTrue(state.showOfflineBanner)
            assertFalse(state.showError)
        }
    }

    @Test
    fun articleReadyOnSuccess() = runTest {
        val repo = FakeRepo().apply {
            bodyResult = Result.success(ArticleContent(listOf(ContentBlock(BlockType.PARAGRAPH, "hi"))))
        }
        val vm = ArticleViewModel(repo, "a", "https://x/a")
        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state is ArticleUiState.Ready)
            assertEquals("hi", (state as ArticleUiState.Ready).content.blocks.first().text)
        }
    }

    @Test
    fun articleFailedOnError() = runTest {
        val repo = FakeRepo().apply { bodyResult = Result.failure(RuntimeException("boom")) }
        val vm = ArticleViewModel(repo, "a", "https://x/a")
        vm.uiState.test {
            advanceUntilIdle()
            assertTrue(expectMostRecentItem() is ArticleUiState.Failed)
        }
    }
}

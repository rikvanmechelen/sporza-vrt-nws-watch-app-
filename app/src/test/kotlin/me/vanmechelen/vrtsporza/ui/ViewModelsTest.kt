package me.vanmechelen.vrtsporza.ui

import app.cash.turbine.test
import me.vanmechelen.vrtsporza.MainDispatcherRule
import me.vanmechelen.vrtsporza.data.NewsRepository
import me.vanmechelen.vrtsporza.model.Article
import me.vanmechelen.vrtsporza.model.ArticleContent
import me.vanmechelen.vrtsporza.model.BlockType
import me.vanmechelen.vrtsporza.model.ContentBlock
import me.vanmechelen.vrtsporza.model.NewsSource
import me.vanmechelen.vrtsporza.ui.article.ArticleUiState
import me.vanmechelen.vrtsporza.ui.article.ArticleViewModel
import me.vanmechelen.vrtsporza.ui.headlines.HeadlinesViewModel
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
    override fun headlines(source: NewsSource): Flow<List<Article>> = flow
    override suspend fun refresh(source: NewsSource): Result<Unit> = refreshResult
    override suspend fun body(url: String): Result<ArticleContent> = bodyResult
    override suspend fun latestHeadline(): Article? = flow.value.firstOrNull()
}

class ViewModelsTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test
    fun headlinesRefreshOnInitAndExposesArticles() = runTest {
        val repo = FakeRepo()
        val vm = HeadlinesViewModel(repo, NewsSource.NEWS_LATEST)
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
        val vm = HeadlinesViewModel(repo, NewsSource.NEWS_LATEST)
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
        val vm = ArticleViewModel(repo, "https://x/a")
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
        val vm = ArticleViewModel(repo, "https://x/a")
        vm.uiState.test {
            advanceUntilIdle()
            assertTrue(expectMostRecentItem() is ArticleUiState.Failed)
        }
    }
}

package me.vanmechelen.vrtsporza.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.vanmechelen.vrtsporza.data.NewsRepository
import me.vanmechelen.vrtsporza.model.Article
import me.vanmechelen.vrtsporza.model.ArticleContent
import me.vanmechelen.vrtsporza.model.NewsSource
import me.vanmechelen.vrtsporza.ui.headlines.HeadlinesScreen
import me.vanmechelen.vrtsporza.ui.headlines.HeadlinesViewModel
import me.vanmechelen.vrtsporza.ui.theme.VrtNwsTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeadlinesHeaderRefreshTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private class FakeRepo : NewsRepository {
        val items = MutableStateFlow(
            listOf(
                Article("a", "een kop", "", "https://x/a", null, 0L, null),
            ),
        )
        val syncedAt = MutableStateFlow<Long?>(null)
        var refreshCount = 0
        override fun headlines(source: NewsSource): Flow<List<Article>> = items
        override fun lastSyncedAt(source: NewsSource): Flow<Long?> = syncedAt
        override suspend fun refresh(source: NewsSource): Result<Unit> {
            refreshCount++
            return Result.success(Unit)
        }
        override suspend fun body(url: String): Result<ArticleContent> =
            Result.success(ArticleContent(emptyList()))
        override suspend fun latestHeadline(): Article? = items.value.firstOrNull()
    }

    @Test
    fun tappingSourceTitleTriggersRefresh() {
        val repo = FakeRepo()
        val viewModel = HeadlinesViewModel(repo, NewsSource.NEWS_TOP)

        rule.setContent {
            VrtNwsTheme {
                HeadlinesScreen(viewModel = viewModel, source = NewsSource.NEWS_TOP, onArticleClick = {})
            }
        }
        rule.waitForIdle()

        // refresh() runs once on init; tapping the header title ("Kort") must run it again.
        val before = repo.refreshCount
        rule.onNodeWithText("Kort").performClick()
        rule.waitForIdle()

        assertEquals(before + 1, repo.refreshCount)
    }
}

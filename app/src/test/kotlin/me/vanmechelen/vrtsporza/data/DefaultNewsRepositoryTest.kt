package me.vanmechelen.vrtsporza.data

import me.vanmechelen.vrtsporza.model.Article
import me.vanmechelen.vrtsporza.model.ArticleContent
import me.vanmechelen.vrtsporza.model.BlockType
import me.vanmechelen.vrtsporza.model.ContentBlock
import me.vanmechelen.vrtsporza.model.NewsSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun article(id: String, published: Long = 0L) =
    Article(id = id, title = "t-$id", summary = "", url = "https://x/$id", imageUrl = null, publishedEpochMs = published, category = null)

private fun content(text: String) = ArticleContent(listOf(ContentBlock(BlockType.PARAGRAPH, text)))

private class FakeCache : ArticleCache {
    val headlines = mutableMapOf<NewsSource, MutableStateFlow<List<Article>>>()
    val bodies = mutableMapOf<String, ArticleContent>()

    private fun flowFor(source: NewsSource) = headlines.getOrPut(source) { MutableStateFlow(emptyList()) }

    override fun observeHeadlines(source: NewsSource): Flow<List<Article>> = flowFor(source)
    override suspend fun upsertHeadlines(source: NewsSource, articles: List<Article>) {
        val flow = flowFor(source)
        val byId = (articles + flow.value).associateBy { it.id }
        flow.value = byId.values.sortedByDescending { it.publishedEpochMs }
    }
    override suspend fun cachedBody(url: String): ArticleContent? = bodies[url]
    override suspend fun saveBody(url: String, content: ArticleContent) { bodies[url] = content }
    override suspend fun latestHeadline(source: NewsSource): Article? =
        flowFor(source).value.maxByOrNull { it.publishedEpochMs }
}

private class FakeFeedService(var result: (String) -> List<Article>) : FeedService {
    val fetchedUrls = mutableListOf<String>()
    override suspend fun fetchHeadlines(feedUrl: String): List<Article> {
        fetchedUrls += feedUrl
        return result(feedUrl)
    }
}

private class FakeArticleService(var result: (String) -> ArticleContent) : ArticleService {
    var calls = 0
    override suspend fun fetchBody(url: String): ArticleContent { calls++; return result(url) }
}

class DefaultNewsRepositoryTest {

    private val cache = FakeCache()
    private val feed = FakeFeedService { emptyList() }
    private val articles = FakeArticleService { content("body of $it") }
    private val repo = DefaultNewsRepository(cache, feed, articles)

    @Test
    fun refreshFetchesTheSourcesFeedUrlAndStoresPerSource() = runTest {
        feed.result = { listOf(article("a", 1), article("b", 2)) }
        assertTrue(repo.refresh(NewsSource.SPORT).isSuccess)
        assertEquals(NewsSource.SPORT.feedUrl, feed.fetchedUrls.single())
        assertEquals(listOf("b", "a"), repo.headlines(NewsSource.SPORT).first().map { it.id })
        // a different source is unaffected
        assertTrue(repo.headlines(NewsSource.NEWS_LATEST).first().isEmpty())
    }

    @Test
    fun overlappingArticleCoexistsAcrossSources() = runTest {
        feed.result = { listOf(article("dup", 5)) }
        repo.refresh(NewsSource.NEWS_LATEST)
        repo.refresh(NewsSource.NEWS_TOP)
        assertEquals(listOf("dup"), repo.headlines(NewsSource.NEWS_LATEST).first().map { it.id })
        assertEquals(listOf("dup"), repo.headlines(NewsSource.NEWS_TOP).first().map { it.id })
    }

    @Test
    fun refreshFailurePreservesExistingCache() = runTest {
        feed.result = { listOf(article("a", 1)) }
        repo.refresh(NewsSource.NEWS_LATEST)
        feed.result = { throw java.io.IOException("offline") }
        assertTrue(repo.refresh(NewsSource.NEWS_LATEST).isFailure)
        assertEquals(listOf("a"), repo.headlines(NewsSource.NEWS_LATEST).first().map { it.id })
    }

    @Test
    fun bodyIsCacheFirstByUrl() = runTest {
        cache.saveBody("https://x/a", content("cached body"))
        val result = repo.body("https://x/a")
        assertEquals("cached body", result.getOrThrow().blocks.first().text)
        assertEquals("service must not be called on cache hit", 0, articles.calls)
    }

    @Test
    fun bodyFetchesAndCachesOnMiss() = runTest {
        val first = repo.body("https://x/a").getOrThrow()
        assertEquals("body of https://x/a", first.blocks.first().text)
        assertEquals(1, articles.calls)
        repo.body("https://x/a") // second call served from cache
        assertEquals(1, articles.calls)
    }

    @Test
    fun emptyExtractionIsNotCached() = runTest {
        articles.result = { ArticleContent(emptyList()) }
        val r = repo.body("https://x/a").getOrThrow()
        assertTrue(r.isEmpty)
        assertNull(cache.cachedBody("https://x/a"))
    }

    @Test
    fun latestHeadlineComesFromNewsLatest() = runTest {
        feed.result = { listOf(article("news", 99)) }
        repo.refresh(NewsSource.NEWS_LATEST)
        feed.result = { listOf(article("sport", 999)) }
        repo.refresh(NewsSource.SPORT)
        assertEquals("news", repo.latestHeadline()?.id) // tile shows NEWS, not sport
    }

    @Test
    fun bodyPropagatesFetchFailure() = runTest {
        articles.result = { throw java.io.IOException("no net") }
        assertTrue(repo.body("https://x/a").isFailure)
    }
}

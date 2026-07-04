package be.vanmechelen.vrtnws.data

import be.vanmechelen.vrtnws.model.Article
import be.vanmechelen.vrtnws.model.ArticleContent
import be.vanmechelen.vrtnws.model.BlockType
import be.vanmechelen.vrtnws.model.ContentBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
    val headlines = MutableStateFlow<List<Article>>(emptyList())
    val bodies = mutableMapOf<String, ArticleContent>()
    override fun observeHeadlines(): Flow<List<Article>> = headlines
    override suspend fun upsertHeadlines(articles: List<Article>) {
        val byId = (articles + headlines.value).associateBy { it.id } // new wins, keep old too
        headlines.value = byId.values.sortedByDescending { it.publishedEpochMs }
    }
    override suspend fun cachedBody(id: String): ArticleContent? = bodies[id]
    override suspend fun saveBody(id: String, content: ArticleContent) { bodies[id] = content }
    override suspend fun findByUrl(url: String): Article? = headlines.value.firstOrNull { it.url == url }
    override suspend fun latestHeadline(): Article? = headlines.value.maxByOrNull { it.publishedEpochMs }
}

private class FakeFeedService(var result: () -> List<Article>) : FeedService {
    override suspend fun fetchHeadlines(): List<Article> = result()
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
    fun refreshStoresHeadlinesAndExposesThem() = runTest {
        feed.result = { listOf(article("a", published = 1), article("b", published = 2)) }
        assertTrue(repo.refresh().isSuccess)
        val stored = repo.headlines().first()
        assertEquals(listOf("b", "a"), stored.map { it.id }) // newest first
    }

    @Test
    fun refreshFailurePreservesExistingCache() = runTest {
        feed.result = { listOf(article("a", published = 1)) }
        repo.refresh()
        feed.result = { throw java.io.IOException("offline") }
        assertTrue(repo.refresh().isFailure)
        assertEquals(listOf("a"), repo.headlines().first().map { it.id })
    }

    @Test
    fun bodyIsCacheFirstAndDoesNotRefetch() = runTest {
        cache.saveBody("a", content("cached body"))
        val result = repo.body("a", "https://x/a")
        assertEquals("cached body", result.getOrThrow().blocks.first().text)
        assertEquals("service must not be called on cache hit", 0, articles.calls)
    }

    @Test
    fun bodyFetchesAndCachesOnMiss() = runTest {
        val first = repo.body("a", "https://x/a").getOrThrow()
        assertEquals("body of https://x/a", first.blocks.first().text)
        assertEquals(1, articles.calls)
        // second call served from cache
        repo.body("a", "https://x/a")
        assertEquals(1, articles.calls)
    }

    @Test
    fun bodyPropagatesFetchFailure() = runTest {
        articles.result = { throw java.io.IOException("no net") }
        assertTrue(repo.body("a", "https://x/a").isFailure)
    }

    @Test
    fun emptyExtractionIsNotCached() = runTest {
        articles.result = { ArticleContent(emptyList()) }
        val r = repo.body("a", "https://x/a").getOrThrow()
        assertTrue(r.isEmpty)
        assertNull(cache.cachedBody("a"))
        assertFalse(cache.bodies.containsKey("a"))
    }

    @Test
    fun latestHeadlineReturnsMostRecent() = runTest {
        feed.result = { listOf(article("old", 10), article("new", 99), article("mid", 50)) }
        repo.refresh()
        assertEquals("new", repo.latestHeadline()?.id)
    }
}

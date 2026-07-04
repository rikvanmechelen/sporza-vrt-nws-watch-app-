package be.vanmechelen.vrtnws.data

import be.vanmechelen.vrtnws.model.Article
import be.vanmechelen.vrtnws.model.ArticleContent
import kotlinx.coroutines.flow.Flow

class DefaultNewsRepository(
    private val cache: ArticleCache,
    private val feedService: FeedService,
    private val articleService: ArticleService,
) : NewsRepository {

    override fun headlines(): Flow<List<Article>> = cache.observeHeadlines()

    override suspend fun refresh(): Result<Unit> = runCatching {
        val fresh = feedService.fetchHeadlines()
        cache.upsertHeadlines(fresh)
    }

    override suspend fun body(id: String, url: String): Result<ArticleContent> = runCatching {
        cache.cachedBody(id)?.let { return@runCatching it }
        val content = articleService.fetchBody(url)
        if (!content.isEmpty) cache.saveBody(id, content)
        content
    }

    override suspend fun latestHeadline(): Article? = cache.latestHeadline()
}

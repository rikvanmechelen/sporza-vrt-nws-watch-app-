package me.vanmechelen.vrtsporza.data

import me.vanmechelen.vrtsporza.model.Article
import me.vanmechelen.vrtsporza.model.ArticleContent
import me.vanmechelen.vrtsporza.model.NewsSource
import kotlinx.coroutines.flow.Flow

class DefaultNewsRepository(
    private val cache: ArticleCache,
    private val feedService: FeedService,
    private val articleService: ArticleService,
) : NewsRepository {

    override fun headlines(source: NewsSource): Flow<List<Article>> = cache.observeHeadlines(source)

    override suspend fun refresh(source: NewsSource): Result<Unit> = runCatching {
        val fresh = feedService.fetchHeadlines(source.feedUrl)
        cache.upsertHeadlines(source, fresh)
    }

    override suspend fun body(url: String): Result<ArticleContent> = runCatching {
        cache.cachedBody(url)?.let { return@runCatching it }
        val content = articleService.fetchBody(url)
        if (!content.isEmpty) cache.saveBody(url, content)
        content
    }

    override suspend fun latestHeadline(): Article? = cache.latestHeadline(NewsSource.NEWS_LATEST)
}

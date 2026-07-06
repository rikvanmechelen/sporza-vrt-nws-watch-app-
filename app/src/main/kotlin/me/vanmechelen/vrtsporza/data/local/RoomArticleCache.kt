package me.vanmechelen.vrtsporza.data.local

import me.vanmechelen.vrtsporza.data.ArticleCache
import me.vanmechelen.vrtsporza.model.Article
import me.vanmechelen.vrtsporza.model.ArticleContent
import me.vanmechelen.vrtsporza.model.NewsSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room-backed implementation of [ArticleCache]. Validated end-to-end on device. */
class RoomArticleCache(private val dao: ArticleDao) : ArticleCache {

    override fun observeHeadlines(source: NewsSource): Flow<List<Article>> =
        dao.observeBySource(source.name).map { rows -> rows.map { it.toArticle() } }

    override suspend fun upsertHeadlines(source: NewsSource, articles: List<Article>) =
        dao.upsertHeadlines(source.name, articles.map { it.toEntity(source) })

    override suspend fun cachedBody(url: String): ArticleContent? =
        dao.getBody(url)?.let { ArticleContent(it.body) }

    override suspend fun saveBody(url: String, content: ArticleContent) =
        dao.upsertBody(ArticleBodyEntity(url, content.blocks, System.currentTimeMillis()))

    override suspend fun latestHeadline(source: NewsSource): Article? =
        dao.latestForSource(source.name)?.toArticle()

    override fun observeSyncedAt(source: NewsSource): Flow<Long?> =
        dao.observeSyncedAt(source.name)

    override suspend fun recordSyncedAt(source: NewsSource, epochMs: Long) =
        dao.upsertSyncState(SyncStateEntity(source.name, epochMs))
}

private fun ArticleEntity.toArticle() = Article(
    id = id,
    title = title,
    summary = summary,
    url = url,
    imageUrl = imageUrl,
    publishedEpochMs = publishedEpochMs,
    category = category,
)

private fun Article.toEntity(source: NewsSource) = ArticleEntity(
    id = id,
    source = source.name,
    title = title,
    summary = summary,
    url = url,
    imageUrl = imageUrl,
    publishedEpochMs = publishedEpochMs,
    category = category,
)

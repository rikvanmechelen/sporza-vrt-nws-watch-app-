package be.vanmechelen.vrtnws.data.local

import be.vanmechelen.vrtnws.data.ArticleCache
import be.vanmechelen.vrtnws.model.Article
import be.vanmechelen.vrtnws.model.ArticleContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room-backed implementation of [ArticleCache]. Validated end-to-end on device. */
class RoomArticleCache(private val dao: ArticleDao) : ArticleCache {

    override fun observeHeadlines(): Flow<List<Article>> =
        dao.observeAll().map { rows -> rows.map { it.toArticle() } }

    override suspend fun upsertHeadlines(articles: List<Article>) =
        dao.upsertHeadlines(articles.map { it.toEntity() })

    override suspend fun cachedBody(id: String): ArticleContent? =
        dao.getById(id)?.body?.let { ArticleContent(it) }

    override suspend fun saveBody(id: String, content: ArticleContent) =
        dao.updateBody(id, content.blocks, System.currentTimeMillis())

    override suspend fun findByUrl(url: String): Article? = dao.getByUrl(url)?.toArticle()

    override suspend fun latestHeadline(): Article? = dao.latest()?.toArticle()
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

private fun Article.toEntity() = ArticleEntity(
    id = id,
    title = title,
    summary = summary,
    url = url,
    imageUrl = imageUrl,
    publishedEpochMs = publishedEpochMs,
    category = category,
    body = null,
    bodyFetchedEpochMs = null,
)

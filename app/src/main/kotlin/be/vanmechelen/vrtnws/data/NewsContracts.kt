package be.vanmechelen.vrtnws.data

import be.vanmechelen.vrtnws.model.Article
import be.vanmechelen.vrtnws.model.ArticleContent
import kotlinx.coroutines.flow.Flow

/** Fetches the latest headlines from the remote Atom feed. */
interface FeedService {
    suspend fun fetchHeadlines(): List<Article>
}

/** Fetches and extracts the readable body of a single article. */
interface ArticleService {
    suspend fun fetchBody(url: String): ArticleContent
}

/** Local persistence for offline reading. Room lives behind this interface. */
interface ArticleCache {
    fun observeHeadlines(): Flow<List<Article>>
    suspend fun upsertHeadlines(articles: List<Article>)
    suspend fun cachedBody(id: String): ArticleContent?
    suspend fun saveBody(id: String, content: ArticleContent)
    suspend fun findByUrl(url: String): Article?
    suspend fun latestHeadline(): Article?
}

/** Single source of truth for the UI: cache-first headlines and article bodies. */
interface NewsRepository {
    /** Emits cached headlines, newest first; updates whenever the cache changes. */
    fun headlines(): Flow<List<Article>>

    /** Fetches fresh headlines and stores them. Existing cache is preserved on failure. */
    suspend fun refresh(): Result<Unit>

    /** Returns the article body, cache-first; fetches and caches it on a miss. */
    suspend fun body(id: String, url: String): Result<ArticleContent>

    /** The single most recent headline, for the Tile and complication. */
    suspend fun latestHeadline(): Article?
}

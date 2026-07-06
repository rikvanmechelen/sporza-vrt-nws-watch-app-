package me.vanmechelen.vrtsporza.data

import me.vanmechelen.vrtsporza.model.Article
import me.vanmechelen.vrtsporza.model.ArticleContent
import me.vanmechelen.vrtsporza.model.NewsSource
import kotlinx.coroutines.flow.Flow

/** Fetches the latest headlines from a given Atom feed URL. */
interface FeedService {
    suspend fun fetchHeadlines(feedUrl: String): List<Article>
}

/** Fetches and extracts the readable body of a single article. */
interface ArticleService {
    suspend fun fetchBody(url: String): ArticleContent
}

/**
 * Local persistence for offline reading. Headlines are kept per [NewsSource] (feeds
 * overlap, so a row is keyed by article id + source); article bodies are cached once,
 * keyed by url (a body is the same regardless of which feed surfaced it).
 */
interface ArticleCache {
    fun observeHeadlines(source: NewsSource): Flow<List<Article>>
    suspend fun upsertHeadlines(source: NewsSource, articles: List<Article>)
    suspend fun cachedBody(url: String): ArticleContent?
    suspend fun saveBody(url: String, content: ArticleContent)
    suspend fun latestHeadline(source: NewsSource): Article?
}

/** Single source of truth for the UI: cache-first per-source headlines and article bodies. */
interface NewsRepository {
    /** Emits cached headlines for [source], newest first; updates when the cache changes. */
    fun headlines(source: NewsSource): Flow<List<Article>>

    /** Fetches fresh headlines for [source] and stores them. Cache is preserved on failure. */
    suspend fun refresh(source: NewsSource): Result<Unit>

    /** Returns the article body, cache-first by url; fetches and caches it on a miss. */
    suspend fun body(url: String): Result<ArticleContent>

    /** The single most recent NEWS headline, for the Tile and complication. */
    suspend fun latestHeadline(): Article?
}

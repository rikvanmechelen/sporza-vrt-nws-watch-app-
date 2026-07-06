package me.vanmechelen.vrtsporza.model

/**
 * A news headline as it appears in the Atom feed. The full body is not in the feed —
 * it is fetched and extracted separately (see ArticleExtractor) into [ArticleContent].
 */
data class Article(
    val id: String,
    val title: String,
    val summary: String,
    val url: String,
    val imageUrl: String?,
    val publishedEpochMs: Long,
    val category: String?,
)

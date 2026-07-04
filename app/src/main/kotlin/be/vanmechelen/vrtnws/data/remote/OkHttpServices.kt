package be.vanmechelen.vrtnws.data.remote

import be.vanmechelen.vrtnws.data.ArticleService
import be.vanmechelen.vrtnws.data.FeedService
import be.vanmechelen.vrtnws.model.Article
import be.vanmechelen.vrtnws.model.ArticleContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

private const val USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel Watch) AppleWebKit/537.36 (KHTML, like Gecko) VrtNwsWear/1.0"

private suspend fun OkHttpClient.getText(url: String): String = withContext(Dispatchers.IO) {
    val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
    newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
        response.body?.string() ?: throw IOException("Empty body for $url")
    }
}

class OkHttpFeedService(
    private val client: OkHttpClient,
) : FeedService {
    override suspend fun fetchHeadlines(feedUrl: String): List<Article> =
        AtomFeedParser.parse(client.getText(feedUrl))
}

class OkHttpArticleService(
    private val client: OkHttpClient,
) : ArticleService {
    override suspend fun fetchBody(url: String): ArticleContent =
        ArticleExtractor.extract(client.getText(url))
}

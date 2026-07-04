package be.vanmechelen.vrtnws

import android.content.Context
import be.vanmechelen.vrtnws.data.DefaultMatchesRepository
import be.vanmechelen.vrtnws.data.DefaultNewsRepository
import be.vanmechelen.vrtnws.data.MatchesRepository
import be.vanmechelen.vrtnws.data.NewsRepository
import be.vanmechelen.vrtnws.data.local.NewsDatabase
import be.vanmechelen.vrtnws.data.local.RoomArticleCache
import be.vanmechelen.vrtnws.data.remote.OkHttpArticleService
import be.vanmechelen.vrtnws.data.remote.OkHttpFeedService
import be.vanmechelen.vrtnws.data.remote.OkHttpMatchesService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/** Minimal manual dependency graph — no DI framework needed for an app this size. */
class AppGraph(context: Context) {

    private val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(logging)
        .build()

    private val database = NewsDatabase.build(context)
    private val cache = RoomArticleCache(database.articleDao())

    val repository: NewsRepository = DefaultNewsRepository(
        cache = cache,
        feedService = OkHttpFeedService(client),
        articleService = OkHttpArticleService(client),
    )

    // Matches use an in-memory cache (scores are ephemeral) — no Room involved.
    val matchesRepository: MatchesRepository = DefaultMatchesRepository(
        service = OkHttpMatchesService(client),
    )
}

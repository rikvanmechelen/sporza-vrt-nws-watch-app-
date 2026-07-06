package me.vanmechelen.vrtsporza

import android.content.Context
import me.vanmechelen.vrtsporza.data.DefaultMatchesRepository
import me.vanmechelen.vrtsporza.data.DefaultNewsRepository
import me.vanmechelen.vrtsporza.data.MatchesRepository
import me.vanmechelen.vrtsporza.data.NewsRepository
import me.vanmechelen.vrtsporza.data.local.NewsDatabase
import me.vanmechelen.vrtsporza.data.local.RoomArticleCache
import me.vanmechelen.vrtsporza.data.remote.OkHttpArticleService
import me.vanmechelen.vrtsporza.data.remote.OkHttpFeedService
import me.vanmechelen.vrtsporza.data.remote.OkHttpMatchesService
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

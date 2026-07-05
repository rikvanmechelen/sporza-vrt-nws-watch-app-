package be.vanmechelen.vrtnws.data.remote

import be.vanmechelen.vrtnws.data.ArticleService
import be.vanmechelen.vrtnws.data.FeedService
import be.vanmechelen.vrtnws.data.MatchesService
import be.vanmechelen.vrtnws.model.Article
import be.vanmechelen.vrtnws.model.ArticleContent
import be.vanmechelen.vrtnws.model.Match
import be.vanmechelen.vrtnws.model.MatchDetail
import be.vanmechelen.vrtnws.model.MatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

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

private const val KALENDER_URL = "https://sporza.be/nl/kalender"
private const val SCHEDULE_URL = "https://api.sporza.be/web/content/schedule"
private val BRUSSELS = ZoneId.of("Europe/Brussels")

class OkHttpMatchesService(
    private val client: OkHttpClient,
) : MatchesService {
    override suspend fun fetchCalendar(): List<Match> {
        val matches = MatchCalendarParser.parse(client.getText(KALENDER_URL))
        // Promoted "livestream-card" matches carry only a broadcast window, not a kickoff. When any
        // are present, look up their real kickoff from the schedule API (which the calendar's own
        // date-nav uses) and patch it in. Skip the extra fetches entirely when there are none.
        val needsKickoff = matches.any {
            it.id.startsWith("livestream-") && it.status == MatchStatus.UPCOMING &&
                it.home != null && it.away != null
        }
        return if (needsKickoff) applyScheduleKickoffs(matches, fetchSchedule()) else matches
    }

    /**
     * Fetches the schedule for a small forward window (today .. +2 days) and merges it — carousel
     * cards can be a day or two out. Per-day failures are tolerated; the card keeps its window time.
     */
    private suspend fun fetchSchedule(): List<ScheduleParser.Fixture> {
        val today = LocalDate.now(BRUSSELS)
        return (0..2L).flatMap { offset ->
            runCatching { ScheduleParser.parse(client.getText("$SCHEDULE_URL?date=${today.plusDays(offset)}")) }
                .getOrDefault(emptyList())
        }
    }

    // The calendar links to /nl/sport/{sport}/~{id}/ which 302s to the full detail page;
    // OkHttp follows redirects, so we can extract straight from the given url.
    override suspend fun fetchDetail(url: String): MatchDetail =
        MatchDetailExtractor.extract(client.getText(url))
}

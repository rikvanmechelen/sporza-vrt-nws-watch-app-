package be.vanmechelen.vrtnws.data.remote

import be.vanmechelen.vrtnws.model.Match
import be.vanmechelen.vrtnws.model.MatchSports
import be.vanmechelen.vrtnws.model.MatchStatus
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Parses the Sporza calendar page (`/nl/kalender`) into a list of [Match]es, grouped by
 * sport (voetbal first). The page is server-rendered; each match is a `<a>` "scoreboard".
 *
 * Sporza uses CSS-module class names with a stable prefix and a per-build hash suffix
 * (e.g. `_scoreboard_mdatp_36`), so — like [ArticleExtractor] — we match on `[class*=prefix]`
 * rather than exact class names. Scoreboards differ per sport: football has two teams + a
 * goal score, tennis has players + set columns (score only in the a11y text), cycling has
 * no teams at all. So team/score fields are best-effort and [Match.title] is the fallback.
 */
object MatchCalendarParser {

    private val hrefRegex = Regex("""/sport/([^/]+)/~(\d+)""")
    private val timeRegex = Regex("""\d{1,2}:\d{2}""")

    fun parse(html: String): List<Match> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html)

        var lastCompetition: String? = null
        val matches = doc.select("a[href~=/sport/[^/]+/~\\d+]").mapNotNull { a ->
            val href = a.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val m = hrefRegex.find(href) ?: return@mapNotNull null
            val sportSlug = m.groupValues[1]
            val id = m.groupValues[2]

            // Competition header only renders on the first match of each group; carry it forward.
            a.selectFirst("[class*=competitionHeading]")?.text()?.trim()?.takeIf { it.isNotBlank() }
                ?.let { lastCompetition = it }

            val (home, homeLogo, away, awayLogo) = teams(a)
            val hidden = a.selectFirst("[class*=hidden]")?.text()?.trim().orEmpty()
            val scoreWrap = a.selectFirst("[class*=score]")
            val scoreClass = scoreWrap?.className()
            // The score renders as adjacent inline spans (home, dash, away) with no whitespace,
            // so join the numeric spans ourselves to get "3 - 2" rather than "3-2".
            val score = scoreWrap?.selectFirst("[class*=numbers]")?.let { nums ->
                nums.select("span").map { it.text().trim() }
                    .filter { it.any(Char::isDigit) }
                    .takeIf { it.size >= 2 }
                    ?.let { "${it.first()} - ${it.last()}" }
            }
            val label = scoreWrap?.selectFirst("[class*=label]")?.text()?.trim()
                ?.takeIf { it.isNotBlank() }
            val live = a.className().contains("live") || scoreClass?.contains("live") == true

            val status = statusOf(scoreClass, hidden, live)
            val title = when {
                home != null && away != null -> "$home - $away"
                else -> hidden.substringBefore(",").trim().takeIf { it.isNotBlank() }
                    ?: lastCompetition.orEmpty()
            }

            Match(
                id = id,
                sportSlug = sportSlug,
                competition = lastCompetition,
                home = home,
                away = away,
                homeLogoUrl = homeLogo,
                awayLogoUrl = awayLogo,
                score = score,
                statusText = label ?: statusFallbackText(status, hidden),
                status = status,
                detailUrl = href,
                title = title,
            )
        }

        // Group by sport (voetbal first, unknown sports last); stable within a sport = source order.
        return matches.sortedBy { MatchSports.rank(it.sportSlug) }
    }

    private data class Teams(
        val home: String?, val homeLogo: String?, val away: String?, val awayLogo: String?,
    )

    private fun teams(anchor: Element): Teams {
        val teamEls = anchor.select("[class*=teamname]")
        // Each side's display name lives in a `[class*=name]` that is NOT the teamname wrapper
        // itself (the wrapper's own class also contains "name"). Doubles have >1 name per side.
        fun namesIn(el: Element): String? = el.select("[class*=name]")
            .filter { !it.className().contains("teamname") }
            .mapNotNull { it.text().trim().takeIf(String::isNotBlank) }
            .distinct()
            .joinToString(" / ")
            .takeIf { it.isNotBlank() }

        fun logoIn(el: Element): String? =
            el.selectFirst("img[src]")?.attr("src")?.trim()?.takeIf { it.isNotBlank() }?.let(::normalizeUrl)

        val homeEl = teamEls.firstOrNull { it.className().contains("home") } ?: teamEls.getOrNull(0)
        val awayEl = teamEls.firstOrNull { it.className().contains("away") } ?: teamEls.getOrNull(1)
        return Teams(
            home = homeEl?.let(::namesIn),
            homeLogo = homeEl?.let(::logoIn),
            away = awayEl?.let(::namesIn),
            awayLogo = awayEl?.let(::logoIn),
        )
    }

    private fun statusOf(scoreClass: String?, hidden: String, live: Boolean): MatchStatus = when {
        live -> MatchStatus.LIVE
        scoreClass?.contains("notStarted") == true -> MatchStatus.UPCOMING
        scoreClass?.contains("end") == true -> MatchStatus.FINISHED
        hidden.contains("afgelopen", ignoreCase = true) ||
            hidden.contains("einde", ignoreCase = true) -> MatchStatus.FINISHED
        hidden.contains("live", ignoreCase = true) -> MatchStatus.LIVE
        hidden.contains("vandaag", ignoreCase = true) || timeRegex.containsMatchIn(hidden) ->
            MatchStatus.UPCOMING
        else -> MatchStatus.UNKNOWN
    }

    private fun statusFallbackText(status: MatchStatus, hidden: String): String = when (status) {
        MatchStatus.LIVE -> "live"
        MatchStatus.FINISHED -> "afgelopen"
        MatchStatus.UPCOMING -> timeRegex.find(hidden)?.value ?: "gepland"
        MatchStatus.UNKNOWN -> ""
    }

    private fun normalizeUrl(url: String): String = when {
        url.startsWith("//") -> "https:$url"
        else -> url
    }
}

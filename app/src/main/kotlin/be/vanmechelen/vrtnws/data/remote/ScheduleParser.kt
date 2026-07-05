package be.vanmechelen.vrtnws.data.remote

import be.vanmechelen.vrtnws.model.Match
import be.vanmechelen.vrtnws.model.MatchStatus

/**
 * Parses the Sporza schedule API (`api.sporza.be/web/content/schedule?date=YYYY-MM-DD`) into the
 * real state of each team fixture — its kickoff, status, and (for finished football) score.
 *
 * This is the trustworthy source for promoted "livestream-card" carousel matches, which on the
 * calendar page carry only a TV **broadcast** state: a broadcast window ("20:30 - 23:20", the
 * pre-show start, not kickoff) and a broadcast status that lingers "live" after the final whistle.
 * Every schedule entry's `ariaLabel` instead states the real kickoff — `"Portugal tegen Spanje,
 * 06/07 21:00"` / `"Brazilië tegen Noorwegen, vandaag\nom 22:00"` (Europe/Brussels) — or, once
 * finished, the score — `"Brazilië tegen Noorwegen, afgelopen, 1, 2, winnaar Noorwegen"`. We read
 * it with a regex off the raw body, like the livestream-card parser in [MatchCalendarParser]: the
 * app carries no JSON library, and the shape is regular enough.
 *
 * Non-team entries (F1, cycling stages) have no " tegen " pair and are skipped.
 */
object ScheduleParser {

    data class Fixture(
        val home: String,
        val away: String,
        /** Real kickoff (Europe/Brussels `HH:mm`) while not-yet-started; null once live/finished. */
        val kickoff: String?,
        /** The true match state from the schedule feed. */
        val status: MatchStatus,
        /** Final goal score for a finished football match ("1 - 2"); null otherwise. */
        val score: String?,
    )

    // One match object's fields, read in feed order (sport → status → … → ariaLabel). `[^{}]` keeps
    // each capture inside its own `{…}` object (so we never pull a neighbour's ariaLabel) while
    // tolerating the intervening "label" field / whitespace being present or absent. Away stops at
    // the first comma; `rest` is everything after it — it holds the kickoff and, for football, score.
    private val rowRegex = Regex(
        """"sport":"(\w+)"[^{}]*?"status":"([A-Z_]+)"[^{}]*?"ariaLabel":"([^"]+?) tegen ([^",]+?),([^"]*)"""",
    )
    private val kickoffRegex = Regex("""\b(\d{1,2}:\d{2})\b""")
    // Football final score in the ariaLabel: "…, afgelopen, <home>, <away>[, winnaar …]". Tennis
    // renders "afgelopen, N sets, …" (no bare "H, A"), so it never matches — but we also gate on
    // sport below as a second guard.
    private val footballScoreRegex = Regex("""afgelopen, (\d{1,2}), (\d{1,2})\b""")

    fun parse(json: String): List<Fixture> {
        if (json.isBlank()) return emptyList()
        return rowRegex.findAll(json).map { m ->
            val (sport, statusRaw, home, away, rest) = m.destructured
            val status = when (statusRaw) {
                "END", "ENDED", "FINISHED" -> MatchStatus.FINISHED
                "LIVE" -> MatchStatus.LIVE
                "NOT_STARTED", "AFTER_TODAY" -> MatchStatus.UPCOMING
                else -> MatchStatus.UNKNOWN
            }
            val score = if (sport == "soccer") {
                footballScoreRegex.find(rest)?.let { "${it.groupValues[1]} - ${it.groupValues[2]}" }
            } else {
                null
            }
            Fixture(
                home = home.trim(),
                away = away.trim(),
                kickoff = kickoffRegex.find(rest)?.value,
                status = status,
                score = score,
            )
        }.toList()
    }
}

/** Order-insensitive identity of a team fixture; mirrors [MatchCalendarParser]'s team dedup key. */
private fun teamKey(home: String, away: String): String =
    listOf(home, away).map { it.trim().lowercase() }.sorted().joinToString("|")

/**
 * Enriches promoted carousel matches with the real state from [fixtures]. The parser synthesises a
 * `livestream-` id for those (they have no scoreboard — only a broadcast window/status); we match
 * each against the schedule by team pair and correct it:
 *
 * - **status** is promoted *forward only* (upcoming → live → finished). The schedule reports the
 *   real MATCH state, so a card whose broadcast lingers "live" after the whistle becomes FINISHED;
 *   an upcoming card that has kicked off becomes LIVE. We never walk a live card back to upcoming
 *   on a stale schedule row.
 * - **score** (finished football) is filled in — the card itself never carries one.
 * - **kickoff** replaces the broadcast-window time, but only while the card is still upcoming.
 *
 * Scoreboard matches (numeric ids, already correct from the calendar) are left untouched.
 */
fun applySchedule(matches: List<Match>, fixtures: List<ScheduleParser.Fixture>): List<Match> {
    if (fixtures.isEmpty()) return matches
    val byKey = fixtures.associateBy { teamKey(it.home, it.away) }
    return matches.map { m ->
        if (!m.id.startsWith("livestream-") || m.home == null || m.away == null) return@map m
        val f = byKey[teamKey(m.home, m.away)] ?: return@map m

        var out = m
        when (f.status) {
            MatchStatus.FINISHED -> out = out.copy(status = MatchStatus.FINISHED, statusText = "afgelopen")
            MatchStatus.LIVE -> out = out.copy(status = MatchStatus.LIVE, statusText = "live")
            MatchStatus.UPCOMING ->
                if (out.status == MatchStatus.UPCOMING && f.kickoff != null) {
                    out = out.copy(statusText = f.kickoff)
                }
            MatchStatus.UNKNOWN -> Unit
        }
        if (f.score != null) out = out.copy(score = f.score)
        out
    }
}

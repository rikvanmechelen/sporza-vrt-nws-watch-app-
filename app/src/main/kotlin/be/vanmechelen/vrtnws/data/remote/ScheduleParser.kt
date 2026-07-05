package be.vanmechelen.vrtnws.data.remote

import be.vanmechelen.vrtnws.model.Match
import be.vanmechelen.vrtnws.model.MatchStatus

/**
 * Parses the Sporza schedule API (`api.sporza.be/web/content/schedule?date=YYYY-MM-DD`) into the
 * kickoff time of each team fixture. Unlike the promoted "livestream-card" carousel on the
 * calendar page — which carries only a TV **broadcast window** ("20:30 - 23:20"), i.e. the
 * pre-show start, not the kickoff — every schedule entry's `ariaLabel` states the real kickoff:
 * `"Portugal tegen Spanje, 06/07 21:00"` / `"Brazilië tegen Noorwegen, vandaag\nom 22:00"` (all in
 * Europe/Brussels time). We read it with a regex off the raw body, like the livestream-card parser
 * in [MatchCalendarParser] — the app carries no JSON library, and the shape is regular enough.
 *
 * Non-team entries (F1, cycling stages) have no " tegen " pair and are skipped.
 */
object ScheduleParser {

    data class Fixture(val home: String, val away: String, val kickoff: String)

    // "Home tegen Away, <anything> HH:MM" inside an ariaLabel. Away stops at the comma; the kickoff
    // is the first HH:MM after it (dates render as "06/07" — no colon — so they don't match).
    private val ariaRegex =
        Regex(""""ariaLabel":"([^"]+?) tegen ([^",]+?),[^"]*?(\d{1,2}:\d{2})[^"]*"""")

    fun parse(json: String): List<Fixture> {
        if (json.isBlank()) return emptyList()
        return ariaRegex.findAll(json).map {
            Fixture(it.groupValues[1].trim(), it.groupValues[2].trim(), it.groupValues[3])
        }.toList()
    }
}

/** Order-insensitive identity of a team fixture; mirrors [MatchCalendarParser]'s team dedup key. */
private fun teamKey(home: String, away: String): String =
    listOf(home, away).map { it.trim().lowercase() }.sorted().joinToString("|")

/**
 * Fills in the true kickoff for promoted carousel matches. The parser synthesises a `livestream-`
 * id for those (they have no scoreboard, only a broadcast window in [Match.statusText]); we match
 * each against [fixtures] by team pair and, when found, replace its time with the real kickoff.
 * Scoreboard matches (numeric ids, already correct) and live/finished cards are left untouched.
 */
fun applyScheduleKickoffs(matches: List<Match>, fixtures: List<ScheduleParser.Fixture>): List<Match> {
    if (fixtures.isEmpty()) return matches
    val byKey = fixtures.associate { teamKey(it.home, it.away) to it.kickoff }
    return matches.map { m ->
        if (m.id.startsWith("livestream-") && m.status == MatchStatus.UPCOMING &&
            m.home != null && m.away != null
        ) {
            byKey[teamKey(m.home, m.away)]?.let { m.copy(statusText = it) } ?: m
        } else {
            m
        }
    }
}

package be.vanmechelen.vrtnws.tile

import be.vanmechelen.vrtnws.model.Match
import be.vanmechelen.vrtnws.model.MatchStatus

/**
 * What the sports Tile should show, distilled from the (already rank-sorted, voetbal-first)
 * calendar. Live games take priority; when none are live we fall back to the next upcoming match.
 */
data class MatchesTileModel(
    /** Up to [maxRows][matchesTileModel] live games, or a single next-upcoming match; may be empty. */
    val rows: List<Match>,
    /** Live games beyond the ones shown in [rows]; 0 when nothing is truncated. */
    val moreLiveCount: Int,
    /** true when [rows] are live games, false when showing an upcoming fallback (or empty). */
    val isLive: Boolean,
)

/**
 * Picks the Tile content from [matches]. The list is assumed already sorted by
 * [MatchSports.rank][be.vanmechelen.vrtnws.model.MatchSports] (voetbal first), so we preserve
 * order and simply filter.
 */
fun matchesTileModel(matches: List<Match>, maxRows: Int = 3): MatchesTileModel {
    val live = matches.filter { it.status == MatchStatus.LIVE }
    if (live.isNotEmpty()) {
        return MatchesTileModel(
            rows = live.take(maxRows),
            moreLiveCount = (live.size - maxRows).coerceAtLeast(0),
            isLive = true,
        )
    }
    val next = matches.firstOrNull { it.status == MatchStatus.UPCOMING }
    return MatchesTileModel(
        rows = listOfNotNull(next),
        moreLiveCount = 0,
        isLive = false,
    )
}

/**
 * Compact one-line label for a Tile row: score-first when live/available, plus a short team hint.
 * Falls back to the always-present [Match.title]. Kickoff time ([Match.statusText]) leads the
 * upcoming fallback.
 */
fun matchRowLabel(match: Match, isLive: Boolean): String {
    val teams = if (match.home != null && match.away != null) {
        "${match.home} - ${match.away}"
    } else {
        match.title
    }
    val lead = if (isLive) {
        match.score ?: match.statusText.ifBlank { "live" }
    } else {
        match.statusText.ifBlank { "gepland" } // upcoming: kickoff time
    }
    return "$lead  $teams"
}

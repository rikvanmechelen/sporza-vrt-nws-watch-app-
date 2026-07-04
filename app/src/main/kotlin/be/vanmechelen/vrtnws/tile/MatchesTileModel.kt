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
    // Guard against the same match appearing twice (Sporza repeats featured fixtures). Dedup by
    // id — the match's true identity — which also catches repeats whose URLs differ only in
    // formatting (trailing slash, fragment), unlike the parser's URL-based dedup.
    val unique = matches.distinctBy { it.id }
    val live = unique.filter { it.status == MatchStatus.LIVE }
    if (live.isNotEmpty()) {
        return MatchesTileModel(
            rows = live.take(maxRows),
            moreLiveCount = (live.size - maxRows).coerceAtLeast(0),
            isLive = true,
        )
    }
    val next = unique.firstOrNull { it.status == MatchStatus.UPCOMING }
    return MatchesTileModel(
        rows = listOfNotNull(next),
        moreLiveCount = 0,
        isLive = false,
    )
}

/**
 * The accented "hero" cell of a row: the goal/points score when the scoreboard exposes one
 * (e.g. "3 - 2"), otherwise the short status — a live minute/phase ("45'", "1e set", "live") or,
 * for the upcoming fallback, the kickoff time ("20:45").
 */
fun matchMidText(match: Match, isLive: Boolean): String =
    match.score ?: match.statusText.ifBlank { if (isLive) "live" else "gepland" }

/** Emoji that stands in for a sport, so a row reads at a glance without leaning on names. */
fun sportEmoji(sportSlug: String): String = when (sportSlug) {
    "voetbal" -> "⚽"
    "tennis" -> "🎾"
    "wielrennen", "veldrijden" -> "🚴"
    // Motorsport: a formula car for circuit racing, a road car for rally.
    "formule-1", "formule-2", "formule-e" -> "🏎"
    "rally", "rallycross", "rally-raid", "wrc" -> "🚗"
    "basketbal" -> "🏀"
    "atletiek" -> "🏃"
    "volleybal" -> "🏐"
    "hockey" -> "🏑"
    else -> "🏅"
}

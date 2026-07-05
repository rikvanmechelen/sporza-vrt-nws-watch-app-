package be.vanmechelen.vrtnws.tile

import be.vanmechelen.vrtnws.model.Match
import be.vanmechelen.vrtnws.model.MatchStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

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

private val SPORZA_ZONE = ZoneId.of("Europe/Brussels")
private val timeToken = Regex("""\b(\d{1,2}):(\d{2})\b""")

/**
 * Rewrites any HH:mm kickoff clock in [text] from Sporza's Europe/Brussels wall time (the site
 * is Belgian and server-renders every time in CET/CEST) into [targetZone] — the watch's zone
 * ([ZoneId.systemDefault]) at runtime. [today] anchors the date so DST is applied correctly; the
 * date is never displayed, so a near-term fixture being off by a calendar day doesn't matter —
 * only the clock does. Non-time text ("live", "45'", "afgelopen", "gepland") and scores ("3 - 2")
 * have no colon-time and pass through unchanged, so this is safe to apply to any [Match.statusText].
 */
fun localizeKickoffTime(
    text: String,
    targetZone: ZoneId = ZoneId.systemDefault(),
    sourceZone: ZoneId = SPORZA_ZONE,
    today: LocalDate = LocalDate.now(sourceZone),
): String {
    if (targetZone == sourceZone) return text
    return timeToken.replace(text) { m ->
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        if (h > 23 || min > 59) return@replace m.value // not a clock time
        val tgt = ZonedDateTime.of(today, LocalTime.of(h, min), sourceZone)
            .withZoneSameInstant(targetZone)
        "%02d:%02d".format(tgt.hour, tgt.minute)
    }
}

/**
 * Shortens a tennis player name so long (especially doubles) names fit a scoreboard row without
 * pushing the score off. "Frances Tiafoe" → "F. Tiafoe", or "Tiafoe" when [surnameOnly].
 * Multi-word surnames (particles) survive: "Alex de Minaur" → "A. de Minaur" / "de Minaur".
 * Doubles pairs ("A/B") and single-token names are left untouched. Callers gate this on tennis;
 * team names (football) must never be abbreviated.
 */
fun abbreviatePlayerName(name: String, surnameOnly: Boolean = false): String {
    val trimmed = name.trim()
    if ('/' in trimmed) return trimmed // doubles — don't mangle into one surname
    val tokens = trimmed.split(" ").filter { it.isNotBlank() }
    if (tokens.size < 2) return trimmed
    val surname = tokens.drop(1).joinToString(" ")
    return if (surnameOnly) surname else "${tokens.first().first().uppercaseChar()}. $surname"
}

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

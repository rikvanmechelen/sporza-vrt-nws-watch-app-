package be.vanmechelen.vrtnws.ui.matches

import be.vanmechelen.vrtnws.model.Match
import be.vanmechelen.vrtnws.model.MatchSports

/**
 * The kicker shown over the match detail's lead hero — the competition when Sporza names one
 * (e.g. "Champions League"), else the sport as a fallback ("Voetbal"), so the hero always carries
 * a label the way an article's lead carries its category. Both blank/absent → null (no kicker).
 */
fun matchKicker(match: Match): String? {
    match.competition?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    return MatchSports.label(match.sportSlug).trim().takeIf { it.isNotEmpty() }
}

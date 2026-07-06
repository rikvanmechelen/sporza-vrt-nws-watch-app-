package me.vanmechelen.vrtsporza.model

/** Where a match is in its lifecycle, derived from the Sporza scoreboard CSS state. */
enum class MatchStatus { LIVE, FINISHED, UPCOMING, UNKNOWN }

/**
 * A single match/fixture from the Sporza calendar (`/nl/kalender`).
 *
 * Sporza lists many sports and their scoreboards differ: football has two teams and a goal
 * score, tennis has players and set scores, cycling has no teams at all. So [home]/[away]
 * and [score] are nullable, and [title] carries a display fallback that always works.
 */
data class Match(
    val id: String,
    val sportSlug: String,
    val competition: String?,
    val home: String?,
    val away: String?,
    val homeLogoUrl: String?,
    val awayLogoUrl: String?,
    /**
     * The headline score when the scoreboard exposes it: goals/points ("3 - 2") for football,
     * or sets won ("1 - 2") for tennis. Null otherwise.
     */
    val score: String?,
    /** Short status/label: kickoff time ("19:00"), live minute ("45'"), or "einde (n.v.)". */
    val statusText: String,
    val status: MatchStatus,
    val detailUrl: String,
    /** Always-present display title: "Home - Away", or a race/event descriptor. */
    val title: String,
    /** Secondary score shown small next to [score]: the current-set games in tennis ("4-3"). */
    val subScore: String? = null,
    /**
     * True when Sporza promotes this fixture in the calendar's "livestream-card" carousel (the
     * marquee matches — e.g. World Cup knockouts). Also set on a scoreboard match that is promoted,
     * even though its duplicate card is dropped. Drives "featured first" ordering in the list + tile.
     */
    val featured: Boolean = false,
)

/** Ordering + display labels for the per-sport sections. Voetbal first, unknowns last. */
object MatchSports {
    /** Section order; anything not listed sorts after these, alphabetically by slug. */
    private val order = listOf(
        "voetbal", "tennis", "wielrennen", "formule-1", "basketbal", "atletiek",
    )

    fun rank(slug: String): Int = order.indexOf(slug).let { if (it < 0) order.size else it }

    fun label(slug: String): String =
        slug.replace('-', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

/** One quick-event on the match timeline (goal, substitution, card). */
data class MatchEvent(
    val minute: String,
    val type: MatchEventType,
    /** Human-readable event text, e.g. "Doelpunt - Lionel Messi (1 - 0)". */
    val text: String,
)

enum class MatchEventType { GOAL, OWN_GOAL, SUBSTITUTION, YELLOW_CARD, RED_CARD, OTHER }

/** One entry in the "Fase per fase" live update stream. */
data class StreamItem(
    val time: String?,
    val title: String?,
    val text: String,
)

/**
 * The extracted detail for one match: quick [events], the "Fase per fase" [stream], and the
 * editorial [recap] (reusing [ContentBlock] so it renders with the article body composables).
 * [isEmpty] drives the "open op telefoon" fallback, like [ArticleContent].
 */
data class MatchDetail(
    val events: List<MatchEvent>,
    val stream: List<StreamItem>,
    val recap: List<ContentBlock>,
) {
    val isEmpty: Boolean get() = events.isEmpty() && stream.isEmpty() && recap.isEmpty()
}

package me.vanmechelen.vrtsporza.data.remote

import me.vanmechelen.vrtsporza.model.Match
import me.vanmechelen.vrtsporza.model.MatchStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleParserTest {

    // Trimmed to the shape api.sporza.be/web/content/schedule actually returns: each match entry
    // carries an ariaLabel "Home tegen Away, <when> HH:MM" (the kickoff), in Europe/Brussels time.
    // Non-team entries (F1, cycling) have no " tegen " and must be ignored. `\n` appears escaped in
    // the raw body, exactly as it arrives over the wire.
    private val json = """
        {"componentProps":{"matchId":"3320766","sport":"formula1","status":"NOT_STARTED",
         "ariaLabel":"Grote Prijs van Groot-Brittannië, vandaag\nom 16:00"}},
        {"componentProps":{"matchId":"3332995","sport":"soccer","status":"NOT_STARTED",
         "ariaLabel":"Brazilië tegen Noorwegen, vandaag\nom 22:00"}},
        {"componentProps":{"matchId":"3332976","sport":"soccer","status":"AFTER_TODAY",
         "ariaLabel":"Mexico tegen Engeland, 06/07 02:00"}},
        {"componentProps":{"matchId":"3333022","sport":"soccer","status":"AFTER_TODAY",
         "ariaLabel":"Portugal tegen Spanje, 06/07 21:00"}},
        {"componentProps":{"matchId":"3333099","sport":"soccer","status":"AFTER_TODAY",
         "ariaLabel":"Verenigde Staten tegen België, 07/07 01:15"}}
    """.trimIndent()

    // The real (compact) shape once matches have kicked off / finished: a "label" field sits between
    // status and ariaLabel, and a finished football match bakes the score into the ariaLabel
    // ("afgelopen, <home>, <away>, winnaar <x>"). Tennis uses "N sets" phrasing (no bare "H, A").
    private val playedJson = """
        [{"componentProps":{"matchId":3332995,"highlight":true,"sport":"soccer","status":"END","label":"einde","ariaLabel":"Brazilië tegen Noorwegen, afgelopen, 1, 2, winnaar Noorwegen"}},
         {"componentProps":{"sport":"tennis","status":"END","label":"einde","ariaLabel":"Elise Mertens en Zhang Shuai tegen Hsieh Su-Wei en Wang Xinyu, afgelopen, 2 sets, set 1: 7 - 6"}},
         {"componentProps":{"sport":"soccer","status":"LIVE","label":"66'","ariaLabel":"Mexico tegen Engeland, bezig"}}]
    """.trimIndent()

    @Test
    fun parsesTeamFixturesWithBrusselsKickoff() {
        val fixtures = ScheduleParser.parse(json)
        val byPair = fixtures.associate { "${it.home} - ${it.away}" to it.kickoff }
        assertEquals("22:00", byPair["Brazilië - Noorwegen"])
        assertEquals("02:00", byPair["Mexico - Engeland"])
        assertEquals("21:00", byPair["Portugal - Spanje"])
        assertEquals("01:15", byPair["Verenigde Staten - België"])
    }

    @Test
    fun ignoresNonTeamEntries() {
        // The F1 GP entry has no " tegen " pair and must not appear.
        assertEquals(4, ScheduleParser.parse(json).size)
    }

    @Test
    fun emptyOrBlankJsonYieldsNothing() {
        assertEquals(emptyList<ScheduleParser.Fixture>(), ScheduleParser.parse(""))
        assertEquals(emptyList<ScheduleParser.Fixture>(), ScheduleParser.parse("   "))
    }

    // --- status + score parsing (played matches) ---

    @Test
    fun parsesFinishedFootballScoreAndStatus() {
        val f = ScheduleParser.parse(playedJson).single { it.home == "Brazilië" }
        assertEquals(MatchStatus.FINISHED, f.status)
        assertEquals("1 - 2", f.score)
        assertEquals(null, f.kickoff) // finished rows have no clock time in the ariaLabel
    }

    @Test
    fun doesNotParseAScoreForTennis() {
        // Tennis renders "afgelopen, 2 sets, …" — never the bare "H, A" football pattern.
        val f = ScheduleParser.parse(playedJson).single { it.home.startsWith("Elise") }
        assertEquals(MatchStatus.FINISHED, f.status)
        assertEquals(null, f.score)
    }

    @Test
    fun parsesLiveStatus() {
        val f = ScheduleParser.parse(playedJson).single { it.home == "Mexico" }
        assertEquals(MatchStatus.LIVE, f.status)
        assertEquals(null, f.score)
    }

    // --- applySchedule: enrich livestream-card matches with the real status/score/kickoff ---

    private fun card(home: String, away: String, statusText: String, status: MatchStatus = MatchStatus.UPCOMING) =
        Match(
            id = "livestream-${home.lowercase()}-${away.lowercase()}", sportSlug = "voetbal",
            competition = "WK", home = home, away = away, homeLogoUrl = null, awayLogoUrl = null,
            score = null, statusText = statusText, status = status,
            detailUrl = "https://sporza.be/nl/livestream/#$home", title = "$home - $away",
        )

    @Test
    fun replacesCardBroadcastTimeWithRealKickoff() {
        // The card carried the broadcast-window start (01:50); the schedule has the true kickoff.
        val enriched = applySchedule(listOf(card("Mexico", "Engeland", "01:50")), ScheduleParser.parse(json))
        assertEquals("02:00", enriched.single().statusText)
    }

    @Test
    fun matchesTeamsRegardlessOfOrder() {
        val enriched = applySchedule(listOf(card("Engeland", "Mexico", "01:50")), ScheduleParser.parse(json))
        assertEquals("02:00", enriched.single().statusText)
    }

    @Test
    fun leavesScoreboardMatchesUntouched() {
        // A real scoreboard match (numeric id) already has the correct kickoff — never overwrite it.
        val scoreboard = card("Brazilië", "Noorwegen", "22:00").copy(id = "3332995")
        val enriched = applySchedule(listOf(scoreboard), ScheduleParser.parse(json))
        assertEquals("22:00", enriched.single().statusText)
        assertEquals("3332995", enriched.single().id)
    }

    @Test
    fun doesNotRegressALiveCardToUpcoming() {
        // Card is live; the (stale) schedule row is still upcoming — never walk the status backwards.
        val live = card("Mexico", "Engeland", "live", status = MatchStatus.LIVE)
        val enriched = applySchedule(listOf(live), ScheduleParser.parse(json))
        assertEquals(MatchStatus.LIVE, enriched.single().status)
        assertEquals("live", enriched.single().statusText)
    }

    @Test
    fun leavesUnmatchedCardsUntouched() {
        val unknown = card("Duitsland", "Italië", "18:00")
        val enriched = applySchedule(listOf(unknown), ScheduleParser.parse(json))
        assertEquals("18:00", enriched.single().statusText)
    }

    @Test
    fun finishedScheduleOverridesLingeringLiveCardAndFillsScore() {
        // The reported bug: a promoted card whose livestream/broadcast is still "live" after the
        // final whistle. The schedule says the match ended 1-2 → show that, not "live".
        val liveCard = card("Brazilië", "Noorwegen", "live", status = MatchStatus.LIVE)
        val enriched = applySchedule(listOf(liveCard), ScheduleParser.parse(playedJson)).single()
        assertEquals(MatchStatus.FINISHED, enriched.status)
        assertEquals("afgelopen", enriched.statusText)
        assertEquals("1 - 2", enriched.score)
    }

    @Test
    fun liveSchedulePromotesAnUpcomingCard() {
        val upcoming = card("Mexico", "Engeland", "02:00", status = MatchStatus.UPCOMING)
        val enriched = applySchedule(listOf(upcoming), ScheduleParser.parse(playedJson)).single()
        assertEquals(MatchStatus.LIVE, enriched.status)
        assertEquals("live", enriched.statusText)
    }
}

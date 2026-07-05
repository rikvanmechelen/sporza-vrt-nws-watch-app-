package be.vanmechelen.vrtnws.data.remote

import be.vanmechelen.vrtnws.model.Match
import be.vanmechelen.vrtnws.model.MatchStatus
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

    // --- applyScheduleKickoffs: overwrite only livestream-card kickoff times ---

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
        val enriched = applyScheduleKickoffs(listOf(card("Mexico", "Engeland", "01:50")), ScheduleParser.parse(json))
        assertEquals("02:00", enriched.single().statusText)
    }

    @Test
    fun matchesTeamsRegardlessOfOrder() {
        val enriched = applyScheduleKickoffs(listOf(card("Engeland", "Mexico", "01:50")), ScheduleParser.parse(json))
        assertEquals("02:00", enriched.single().statusText)
    }

    @Test
    fun leavesScoreboardMatchesUntouched() {
        // A real scoreboard match (numeric id) already has the correct kickoff — never overwrite it.
        val scoreboard = card("Brazilië", "Noorwegen", "22:00").copy(id = "3332995")
        val enriched = applyScheduleKickoffs(listOf(scoreboard), ScheduleParser.parse(json))
        assertEquals("22:00", enriched.single().statusText)
        assertEquals("3332995", enriched.single().id)
    }

    @Test
    fun leavesLiveCardsUntouched() {
        val live = card("Mexico", "Engeland", "live", status = MatchStatus.LIVE)
        val enriched = applyScheduleKickoffs(listOf(live), ScheduleParser.parse(json))
        assertEquals("live", enriched.single().statusText)
    }

    @Test
    fun leavesUnmatchedCardsUntouched() {
        val unknown = card("Duitsland", "Italië", "18:00")
        val enriched = applyScheduleKickoffs(listOf(unknown), ScheduleParser.parse(json))
        assertEquals("18:00", enriched.single().statusText)
    }
}

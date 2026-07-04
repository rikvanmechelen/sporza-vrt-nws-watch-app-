package be.vanmechelen.vrtnws.data.remote

import be.vanmechelen.vrtnws.model.Match
import be.vanmechelen.vrtnws.model.MatchSports
import be.vanmechelen.vrtnws.model.MatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MatchCalendarParserTest {

    private lateinit var matches: List<Match>

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing fixture $name" }
            .bufferedReader().use { it.readText() }

    @Before
    fun setUp() {
        matches = MatchCalendarParser.parse(fixture("sporza_kalender.html"))
    }

    @Test
    fun parsesManyMatchesAcrossSports() {
        assertTrue("expected many matches, got ${matches.size}", matches.size > 40)
        val sports = matches.map { it.sportSlug }.toSet()
        assertTrue("expected voetbal + tennis", sports.containsAll(setOf("voetbal", "tennis")))
    }

    @Test
    fun groupsVoetbalFirst() {
        // Sorted by sport rank: the first match must be a football match.
        assertEquals("voetbal", matches.first().sportSlug)
        // Ranks must be non-decreasing across the list (i.e. grouped by sport).
        val ranks = matches.map { MatchSports.rank(it.sportSlug) }
        assertEquals(ranks.sorted(), ranks)
    }

    @Test
    fun parsesFootballTeamsAndStatus() {
        val argentina = matches.first { it.home == "Argentinië" }
        assertEquals("Kaapverdië", argentina.away)
        assertEquals("voetbal", argentina.sportSlug)
        assertEquals("FIFA Wereldkampioenschap", argentina.competition)
        assertEquals(MatchStatus.FINISHED, argentina.status)
        assertEquals("3 - 2", argentina.score)
        assertTrue("expected a team logo", argentina.homeLogoUrl?.startsWith("http") == true)
        assertTrue("detail url points at the match", argentina.detailUrl.contains("/sport/voetbal/~"))
    }

    @Test
    fun parsesUpcomingFootballWithKickoffTime() {
        val canada = matches.first { it.home == "Canada" }
        assertEquals(MatchStatus.UPCOMING, canada.status)
        assertEquals(null, canada.score)
        assertEquals("19:00", canada.statusText)
    }

    @Test
    fun tennisMatchesHavePlayersAndTitleFallback() {
        val tennis = matches.filter { it.sportSlug == "tennis" }
        assertTrue("expected tennis matches", tennis.isNotEmpty())
        // Every match has a non-blank display title (even where score parsing can't apply).
        matches.forEach { assertTrue("blank title for ${it.id}", it.title.isNotBlank()) }
        // A known live singles match.
        val paolini = matches.firstOrNull { it.title.contains("Paolini") }
        assertNotNull("expected Paolini match", paolini)
        assertEquals(MatchStatus.LIVE, paolini!!.status)
    }

    @Test
    fun tennisSinglesParsesBothPlayers() {
        val m = matches.first { it.home == "Jasmine Paolini" }
        assertEquals("Maria Sakkari", m.away)
        assertEquals("tennis", m.sportSlug)
        assertEquals("Jasmine Paolini - Maria Sakkari", m.title)
    }

    @Test
    fun tennisDoublesJoinsPlayersPerSide() {
        val doubles = matches.firstOrNull { it.sportSlug == "tennis" && it.home?.contains(" / ") == true }
        assertNotNull("expected a doubles match with two players a side", doubles)
        assertTrue("away side also has two players", doubles!!.away?.contains(" / ") == true)
    }

    @Test
    fun everyMatchHasStableIdSportAndUrl() {
        matches.forEach {
            assertTrue("blank id", it.id.isNotBlank())
            assertTrue("blank sport", it.sportSlug.isNotBlank())
            assertTrue("bad url ${it.detailUrl}", it.detailUrl.startsWith("http"))
        }
    }

    @Test
    fun deduplicatesRepeatedMatchesByUrl() {
        // Sporza lists some fixtures more than once (e.g. a featured match also shown under its
        // competition). The same detailUrl must not appear twice — the matches list is keyed by
        // it in a LazyColumn, and duplicate keys crash the screen while scrolling.
        val html = """
            <html><body>
              <a href="https://sporza.be/nl/sport/voetbal/~3333005/">Club - Anderlecht</a>
              <a href="https://sporza.be/nl/sport/voetbal/~3333005/">Club - Anderlecht</a>
              <a href="https://sporza.be/nl/sport/tennis/~999/">Speler</a>
            </body></html>
        """.trimIndent()
        val parsed = MatchCalendarParser.parse(html)
        assertEquals(2, parsed.size)
        assertEquals(parsed.map { it.detailUrl }.distinct(), parsed.map { it.detailUrl })
    }

    @Test
    fun realFixtureHasNoDuplicateUrls() {
        val urls = matches.map { it.detailUrl }
        assertEquals(urls.distinct().size, urls.size)
    }
}

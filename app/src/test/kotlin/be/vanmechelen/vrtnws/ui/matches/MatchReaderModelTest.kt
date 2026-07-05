package be.vanmechelen.vrtnws.ui.matches

import be.vanmechelen.vrtnws.model.Match
import be.vanmechelen.vrtnws.model.MatchStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchReaderModelTest {

    private fun match(competition: String?, sportSlug: String) = Match(
        id = "1",
        sportSlug = sportSlug,
        competition = competition,
        home = "A",
        away = "B",
        homeLogoUrl = null,
        awayLogoUrl = null,
        score = null,
        statusText = "20:45",
        status = MatchStatus.UPCOMING,
        detailUrl = "https://sporza.be/x",
        title = "A - B",
    )

    @Test
    fun `kicker prefers the competition when present`() {
        assertEquals("Champions League", matchKicker(match("Champions League", "voetbal")))
    }

    @Test
    fun `kicker trims the competition`() {
        assertEquals("Serie A", matchKicker(match("  Serie A  ", "voetbal")))
    }

    @Test
    fun `kicker falls back to the sport label when no competition`() {
        assertEquals("Voetbal", matchKicker(match(null, "voetbal")))
        assertEquals("Wielrennen", matchKicker(match("  ", "wielrennen")))
    }

    @Test
    fun `kicker is null when neither competition nor sport is known`() {
        assertEquals(null, matchKicker(match(null, "")))
    }
}

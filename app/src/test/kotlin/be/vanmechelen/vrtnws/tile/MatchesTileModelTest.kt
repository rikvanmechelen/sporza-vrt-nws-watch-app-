package be.vanmechelen.vrtnws.tile

import be.vanmechelen.vrtnws.model.Match
import be.vanmechelen.vrtnws.model.MatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun match(
    id: String,
    sport: String = "voetbal",
    status: MatchStatus = MatchStatus.UPCOMING,
) = Match(
    id = id, sportSlug = sport, competition = "C", home = "H-$id", away = "A-$id",
    homeLogoUrl = null, awayLogoUrl = null, score = null, statusText = "",
    status = status, detailUrl = "https://x/$id", title = "H-$id - A-$id",
)

class MatchesTileModelTest {

    @Test
    fun singleLiveMatchIsShownAsLive() {
        val model = matchesTileModel(listOf(match("a", status = MatchStatus.LIVE)))
        assertTrue(model.isLive)
        assertEquals(listOf("a"), model.rows.map { it.id })
        assertEquals(0, model.moreLiveCount)
    }

    @Test
    fun moreThanMaxLiveTruncatesAndCounts() {
        val live = (1..5).map { match("m$it", status = MatchStatus.LIVE) }
        val model = matchesTileModel(live)
        assertTrue(model.isLive)
        assertEquals(listOf("m1", "m2", "m3"), model.rows.map { it.id })
        assertEquals(2, model.moreLiveCount)
    }

    @Test
    fun noLiveFallsBackToFirstUpcoming() {
        val matches = listOf(
            match("done", status = MatchStatus.FINISHED),
            match("next", status = MatchStatus.UPCOMING),
            match("later", status = MatchStatus.UPCOMING),
        )
        val model = matchesTileModel(matches)
        assertFalse(model.isLive)
        assertEquals(listOf("next"), model.rows.map { it.id })
        assertEquals(0, model.moreLiveCount)
    }

    @Test
    fun noLiveAndNoUpcomingYieldsEmptyRows() {
        val model = matchesTileModel(listOf(match("done", status = MatchStatus.FINISHED)))
        assertFalse(model.isLive)
        assertTrue(model.rows.isEmpty())
        assertEquals(0, model.moreLiveCount)
    }

    @Test
    fun emptyInputYieldsEmptyRows() {
        val model = matchesTileModel(emptyList())
        assertFalse(model.isLive)
        assertTrue(model.rows.isEmpty())
    }

    @Test
    fun liveOrderIsPreservedFootballFirst() {
        // The calendar arrives already rank-sorted (voetbal first); the model must not reorder.
        val matches = listOf(
            match("foot", sport = "voetbal", status = MatchStatus.LIVE),
            match("tenn", sport = "tennis", status = MatchStatus.LIVE),
            match("cycl", sport = "wielrennen", status = MatchStatus.LIVE),
        )
        val model = matchesTileModel(matches)
        assertEquals(listOf("foot", "tenn", "cycl"), model.rows.map { it.id })
    }

    @Test
    fun liveMatchesAreSelectedRegardlessOfListPosition() {
        val matches = listOf(
            match("up", status = MatchStatus.UPCOMING),
            match("live", status = MatchStatus.LIVE),
            match("done", status = MatchStatus.FINISHED),
        )
        val model = matchesTileModel(matches)
        assertTrue(model.isLive)
        assertEquals(listOf("live"), model.rows.map { it.id })
    }

    @Test
    fun liveRowLabelLeadsWithScore() {
        val m = match("a", status = MatchStatus.LIVE).copy(
            home = "Club Brugge", away = "Anderlecht", score = "2 - 1", statusText = "45'",
        )
        assertEquals("2 - 1  Club Brugge - Anderlecht", matchRowLabel(m, isLive = true))
    }

    @Test
    fun liveRowLabelWithoutScoreUsesStatusText() {
        val m = match("a", status = MatchStatus.LIVE).copy(
            home = null, away = null, score = null, statusText = "3e ronde", title = "Wielrennen",
        )
        assertEquals("3e ronde  Wielrennen", matchRowLabel(m, isLive = true))
    }

    @Test
    fun upcomingRowLabelLeadsWithKickoffTime() {
        val m = match("a", status = MatchStatus.UPCOMING).copy(
            home = "Gent", away = "STVV", score = null, statusText = "20:45",
        )
        assertEquals("20:45  Gent - STVV", matchRowLabel(m, isLive = false))
    }
}

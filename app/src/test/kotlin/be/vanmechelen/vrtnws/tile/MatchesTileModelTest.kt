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
    fun dropsDuplicateLiveMatchesByIdAcrossUrlVariants() {
        val a = match("dup", status = MatchStatus.LIVE)
        // Same match repeated with a slightly different URL (parser's url-dedup would miss this).
        val b = a.copy(detailUrl = a.detailUrl + "#featured")
        val model = matchesTileModel(listOf(a, b, match("other", status = MatchStatus.LIVE)))
        assertEquals(listOf("dup", "other"), model.rows.map { it.id })
        assertEquals(0, model.moreLiveCount)
    }

    @Test
    fun duplicatesDoNotInflateMoreCount() {
        // 3 shown + what looks like 2 more, but both extras are dupes of shown matches → no "+N".
        val live = listOf(
            match("a", status = MatchStatus.LIVE),
            match("b", status = MatchStatus.LIVE),
            match("c", status = MatchStatus.LIVE),
            match("a", status = MatchStatus.LIVE),
            match("b", status = MatchStatus.LIVE),
        )
        val model = matchesTileModel(live)
        assertEquals(listOf("a", "b", "c"), model.rows.map { it.id })
        assertEquals(0, model.moreLiveCount)
    }

    @Test
    fun midTextIsScoreWhenPresent() {
        val m = match("a", status = MatchStatus.LIVE).copy(score = "2 - 1", statusText = "45'")
        assertEquals("2 - 1", matchMidText(m, isLive = true))
    }

    @Test
    fun midTextFallsBackToStatusThenLive() {
        val withStatus = match("a", status = MatchStatus.LIVE).copy(score = null, statusText = "1e set")
        assertEquals("1e set", matchMidText(withStatus, isLive = true))
        val blank = match("b", status = MatchStatus.LIVE).copy(score = null, statusText = "")
        assertEquals("live", matchMidText(blank, isLive = true))
    }

    @Test
    fun midTextForUpcomingIsKickoffTime() {
        val m = match("a", status = MatchStatus.UPCOMING).copy(score = null, statusText = "20:45")
        assertEquals("20:45", matchMidText(m, isLive = false))
        val blank = match("b", status = MatchStatus.UPCOMING).copy(score = null, statusText = "")
        assertEquals("gepland", matchMidText(blank, isLive = false))
    }

    @Test
    fun sportEmojiCoversKnownSportsAndFallsBack() {
        assertEquals("⚽", sportEmoji("voetbal"))
        assertEquals("🎾", sportEmoji("tennis"))
        assertEquals("🚴", sportEmoji("wielrennen"))
        assertEquals("🏅", sportEmoji("onbekende-sport"))
    }

    @Test
    fun motorsportSplitsFormulaFromRally() {
        assertEquals("🏎", sportEmoji("formule-1"))
        assertEquals("🚗", sportEmoji("rally"))
        assertEquals("🚗", sportEmoji("rallycross"))
    }

    @Test
    fun abbreviatesPlayerToInitialPlusSurname() {
        assertEquals("F. Tiafoe", abbreviatePlayerName("Frances Tiafoe"))
        assertEquals("A. Bublik", abbreviatePlayerName("Alexander Bublik"))
    }

    @Test
    fun abbreviatesToSurnameOnlyWhenRequested() {
        assertEquals("Tiafoe", abbreviatePlayerName("Frances Tiafoe", surnameOnly = true))
        assertEquals("Berrettini", abbreviatePlayerName("Matteo Berrettini", surnameOnly = true))
    }

    @Test
    fun keepsSurnameParticles() {
        // Multi-word surnames (particles) survive: initial + the rest.
        assertEquals("A. de Minaur", abbreviatePlayerName("Alex de Minaur"))
        assertEquals("de Minaur", abbreviatePlayerName("Alex de Minaur", surnameOnly = true))
    }

    @Test
    fun leavesDoublesAndShortNamesUntouched() {
        // Doubles pairs (a "/" separator) must not be mangled into one surname.
        assertEquals("Tiafoe/Bublik", abbreviatePlayerName("Tiafoe/Bublik"))
        // Already a single token — nothing to abbreviate.
        assertEquals("Alcaraz", abbreviatePlayerName("Alcaraz"))
        assertEquals("Alcaraz", abbreviatePlayerName("Alcaraz", surnameOnly = true))
        // Blank stays blank.
        assertEquals("", abbreviatePlayerName("   "))
    }
}

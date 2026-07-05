package be.vanmechelen.vrtnws.tile

import be.vanmechelen.vrtnws.model.Match
import be.vanmechelen.vrtnws.model.MatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

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

    // --- localizeKickoffTime: convert Sporza's Brussels wall-clock to the watch's zone ---

    private val brussels = ZoneId.of("Europe/Brussels")
    private val newYork = ZoneId.of("America/New_York")
    private val jul4 = LocalDate.of(2026, 7, 4)

    @Test
    fun kickoffConvertsBrusselsToWesternZone() {
        // The reported bug: 22:00 CEST (UTC+2) is 16:00 EDT (UTC-4).
        assertEquals(
            "16:00",
            localizeKickoffTime("22:00", targetZone = newYork, sourceZone = brussels, today = jul4),
        )
    }

    @Test
    fun kickoffUnchangedWhenTargetIsSourceZone() {
        assertEquals(
            "22:00",
            localizeKickoffTime("22:00", targetZone = brussels, sourceZone = brussels, today = jul4),
        )
    }

    @Test
    fun kickoffAppliesDstFromTheAnchorDate() {
        // Winter: Brussels is CET (UTC+1), New York EST (UTC-5) → 6h offset, not 6h as in summer
        // but with a different absolute result. 22:00 CET → 16:00 EST.
        val jan4 = LocalDate.of(2026, 1, 4)
        assertEquals(
            "16:00",
            localizeKickoffTime("22:00", targetZone = newYork, sourceZone = brussels, today = jan4),
        )
        // Same clock, summer: 22:00 CEST → 16:00 EDT. Both read 16:00, but the winter case only
        // lands there because the date anchored CET/EST rather than CEST/EDT.
        assertEquals(
            "16:00",
            localizeKickoffTime("22:00", targetZone = newYork, sourceZone = brussels, today = jul4),
        )
        // A time where the DST difference is visible: 13:30 CET → 07:30 EST, but 13:30 CEST →
        // 07:30 EDT as well — pick one that crosses differently. 00:30 CEST → 18:30 (prev) EDT.
        assertEquals(
            "18:30",
            localizeKickoffTime("00:30", targetZone = newYork, sourceZone = brussels, today = jul4),
        )
    }

    @Test
    fun kickoffLeavesNonTimeTextUntouched() {
        for (s in listOf("live", "45'", "afgelopen", "gepland", "3 - 2", "1e set", "")) {
            assertEquals(s, localizeKickoffTime(s, targetZone = newYork, sourceZone = brussels, today = jul4))
        }
    }

    @Test
    fun kickoffShowsTheInstantsClockAcrossADayBoundary() {
        // 23:30 CEST (UTC+2) = 21:30 UTC = 06:30 next-day JST (UTC+9). We show the clock, 06:30.
        assertEquals(
            "06:30",
            localizeKickoffTime("23:30", targetZone = ZoneId.of("Asia/Tokyo"), sourceZone = brussels, today = jul4),
        )
    }

    @Test
    fun kickoffZeroPadsTheResult() {
        // 9:30 CEST → 03:30 EDT, padded to two digits.
        assertEquals(
            "03:30",
            localizeKickoffTime("9:30", targetZone = newYork, sourceZone = brussels, today = jul4),
        )
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

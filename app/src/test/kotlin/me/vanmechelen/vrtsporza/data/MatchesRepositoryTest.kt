package me.vanmechelen.vrtsporza.data

import me.vanmechelen.vrtsporza.model.BlockType
import me.vanmechelen.vrtsporza.model.ContentBlock
import me.vanmechelen.vrtsporza.model.Match
import me.vanmechelen.vrtsporza.model.MatchDetail
import me.vanmechelen.vrtsporza.model.MatchStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun match(id: String, sport: String = "voetbal") = Match(
    id = id, sportSlug = sport, competition = "C", home = "H-$id", away = "A-$id",
    homeLogoUrl = null, awayLogoUrl = null, score = null, statusText = "", status = MatchStatus.UPCOMING,
    detailUrl = "https://x/$id", title = "H-$id - A-$id",
)

private fun detail(text: String) =
    MatchDetail(emptyList(), emptyList(), listOf(ContentBlock(BlockType.PARAGRAPH, text)))

private class FakeMatchesService : MatchesService {
    var calendar: () -> List<Match> = { emptyList() }
    var onDetail: (String) -> MatchDetail = { detail("body of $it") }
    var detailCalls = 0
    override suspend fun fetchCalendar(): List<Match> = calendar()
    override suspend fun fetchDetail(url: String): MatchDetail { detailCalls++; return onDetail(url) }
}

class MatchesRepositoryTest {

    private val service = FakeMatchesService()
    private val repo = DefaultMatchesRepository(service)

    @Test
    fun refreshPublishesCalendar() = runTest {
        service.calendar = { listOf(match("a"), match("b")) }
        assertTrue(repo.refresh().isSuccess)
        assertEquals(listOf("a", "b"), repo.matches().first().map { it.id })
    }

    @Test
    fun refreshFailurePreservesLastGood() = runTest {
        service.calendar = { listOf(match("a")) }
        repo.refresh()
        service.calendar = { throw java.io.IOException("offline") }
        assertTrue(repo.refresh().isFailure)
        assertEquals(listOf("a"), repo.matches().first().map { it.id })
    }

    @Test
    fun detailIsCacheFirstByUrl() = runTest {
        val first = repo.detail("https://x/a").getOrThrow()
        assertEquals("body of https://x/a", first.recap.first().text)
        assertEquals(1, service.detailCalls)
        repo.detail("https://x/a") // served from cache
        assertEquals(1, service.detailCalls)
    }

    @Test
    fun emptyDetailIsNotCached() = runTest {
        service.onDetail = { MatchDetail(emptyList(), emptyList(), emptyList()) }
        val r = repo.detail("https://x/a").getOrThrow()
        assertTrue(r.isEmpty)
        repo.detail("https://x/a")
        assertEquals("empty detail must not be cached", 2, service.detailCalls)
    }

    @Test
    fun detailPropagatesFailure() = runTest {
        service.onDetail = { throw java.io.IOException("no net") }
        assertTrue(repo.detail("https://x/a").isFailure)
    }
}

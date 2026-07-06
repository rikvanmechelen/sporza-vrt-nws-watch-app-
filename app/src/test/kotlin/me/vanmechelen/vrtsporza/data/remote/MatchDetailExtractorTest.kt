package me.vanmechelen.vrtsporza.data.remote

import me.vanmechelen.vrtsporza.model.MatchDetail
import me.vanmechelen.vrtsporza.model.MatchEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MatchDetailExtractorTest {

    private lateinit var detail: MatchDetail

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing fixture $name" }
            .bufferedReader().use { it.readText() }

    @Before
    fun setUp() {
        // A finished FIFA WC 2026 football match (Argentinië - Kaapverdië) with goals, subs, cards.
        detail = MatchDetailExtractor.extract(fixture("sporza_match.html"))
    }

    @Test
    fun extractsQuickEventsWithMinutesAndTypes() {
        assertTrue("expected several events, got ${detail.events.size}", detail.events.size >= 10)

        val firstGoal = detail.events.first { it.type == MatchEventType.GOAL }
        assertEquals("29'", firstGoal.minute)
        assertTrue(firstGoal.text.contains("Lionel Messi"))

        assertTrue("expected an own goal", detail.events.any { it.type == MatchEventType.OWN_GOAL })
        assertTrue("expected a substitution", detail.events.any { it.type == MatchEventType.SUBSTITUTION })
        assertTrue("expected a yellow card", detail.events.any { it.type == MatchEventType.YELLOW_CARD })
    }

    @Test
    fun extractsFasePerFaseStream() {
        assertTrue("expected stream items, got ${detail.stream.size}", detail.stream.size >= 3)
        // The closing update should be present with real prose.
        assertTrue(
            "expected an 'Einde' stream entry",
            detail.stream.any { (it.title ?: "").contains("Einde", ignoreCase = true) },
        )
        assertTrue("stream entries should carry text", detail.stream.any { it.text.length > 40 })
    }

    @Test
    fun extractsRecapBody() {
        assertTrue("expected recap blocks, got ${detail.recap.size}", detail.recap.size >= 3)
        val recapText = detail.recap.joinToString(" ") { it.text }
        assertTrue("recap should mention the notendop summary", recapText.contains("notendop"))
    }

    @Test
    fun recapDoesNotDuplicateLiveTimeline() {
        // The "Fase per fase" widget text must be excluded from the editorial recap.
        val recapText = detail.recap.joinToString(" ") { it.text }
        assertTrue(
            "recap leaked the live timeline heading",
            !recapText.contains("Fase per fase"),
        )
    }

    @Test
    fun blankHtmlYieldsEmptyDetail() {
        val empty = MatchDetailExtractor.extract("")
        assertTrue(empty.isEmpty)
    }
}

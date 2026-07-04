package be.vanmechelen.vrtnws.data.remote

import be.vanmechelen.vrtnws.model.Article
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.OffsetDateTime

class AtomFeedParserTest {

    private lateinit var articles: List<Article>

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing fixture $name" }
            .bufferedReader().use { it.readText() }

    @Before
    fun setUp() {
        articles = AtomFeedParser.parse(fixture("feed_sample.xml"))
    }

    @Test
    fun parsesAllEntries() {
        // The sample feed has many entries; the feed-level <link> at the top must not
        // be mistaken for an article.
        assertTrue("expected several articles, got ${articles.size}", articles.size > 20)
    }

    @Test
    fun firstEntryHasExpectedFields() {
        val first = articles.first()
        assertEquals(
            "\"We waren heel nerveus voor deze show\": The xx maakt ingetogen comeback na pauze van 8 jaar",
            first.title,
        )
        assertEquals("https://vrtnws.be/p.Wk6pRlL87", first.url)
        assertEquals(
            "https://images.vrt.be/vrtnws_share/2026/07/04/21fa324f-410c-49ec-9b93-dcfc33b08502.jpg",
            first.imageUrl,
        )
        assertEquals("Festivals", first.category)
    }

    @Test
    fun parsesPublishedTimestamp() {
        val expected = OffsetDateTime.parse("2026-07-03T11:00:33.000Z").toInstant().toEpochMilli()
        assertEquals(expected, articles.first().publishedEpochMs)
    }

    @Test
    fun decodesHtmlEntitiesInTitles() {
        // e.g. the Capaldi headline uses &quot; entities in the raw XML.
        val capaldi = articles.first { it.url == "https://vrtnws.be/p.dLe8QdyW3" }
        assertTrue(capaldi.title.contains("\""))
        assertTrue(capaldi.title.contains("Ik ga weg"))
    }

    @Test
    fun parsesSporzaFeedTheSameWay() {
        // Sporza is a structurally identical Atom feed on a different host, with pipe-delimited
        // nstag (first token = sport). The same parser must handle it.
        val sporza = AtomFeedParser.parse(fixture("sporza_feed.xml"))
        assertTrue("expected Sporza entries", sporza.size >= 10)
        val first = sporza.first()
        assertTrue(first.url.startsWith("https://sporza.be/"))
        assertTrue("expected a sport nstag", sporza.any { it.category?.startsWith("voetbal") == true || it.category?.contains("|") == true })
    }

    @Test
    fun everyArticleHasStableIdAndUrl() {
        articles.forEach {
            assertTrue("blank id", it.id.isNotBlank())
            assertTrue("url should be an article link: ${it.url}", it.url.startsWith("http"))
            assertNotNull(it.title)
        }
        // ids are unique
        assertEquals(articles.size, articles.map { it.id }.toSet().size)
    }
}

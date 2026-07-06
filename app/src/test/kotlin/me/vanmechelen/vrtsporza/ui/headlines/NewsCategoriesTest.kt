package me.vanmechelen.vrtsporza.ui.headlines

import me.vanmechelen.vrtsporza.data.remote.AtomFeedParser
import me.vanmechelen.vrtsporza.model.Article
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsCategoriesTest {

    private fun article(category: String?, publishedEpochMs: Long, id: String = category.orEmpty() + publishedEpochMs): Article =
        Article(
            id = id,
            title = "t-$id",
            summary = "",
            url = "https://example.test/$id",
            imageUrl = null,
            publishedEpochMs = publishedEpochMs,
            category = category,
        )

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing fixture $name" }
            .bufferedReader().use { it.readText() }

    @Test
    fun topicsAreOrderedByNewestActivityFirst() {
        val articles = listOf(
            article("Binnenland", 100),
            article("Binnenland", 300), // Binnenland newest = 300
            article("Europa", 500),     // Europa newest = 500
            article("Sport", 200),      // Sport newest = 200
        )
        val tiles = categoryTiles(articles)
        assertEquals(
            listOf(
                CategorySelection.Topic("Europa"),
                CategorySelection.Topic("Binnenland"),
                CategorySelection.Topic("Sport"),
            ),
            tiles.map { it.selection },
        )
    }

    @Test
    fun countsPerCategoryAreCorrect() {
        val articles = listOf(
            article("Binnenland", 100),
            article("Binnenland", 300),
            article("Europa", 500),
        )
        val tiles = categoryTiles(articles)
        assertEquals(2, tiles.first { it.selection == CategorySelection.Topic("Binnenland") }.count)
        assertEquals(1, tiles.first { it.selection == CategorySelection.Topic("Europa") }.count)
    }

    @Test
    fun uncategorisedArticlesFoldIntoOverigWhichIsAlwaysLast() {
        val articles = listOf(
            article(null, 9_999),  // newest overall, but Overig must still be last
            article("", 8_888),    // blank folds into the same Overig group
            article("Binnenland", 100),
        )
        val tiles = categoryTiles(articles)
        assertEquals(CategorySelection.Topic(null), tiles.last().selection)
        assertEquals(2, tiles.last().count)
    }

    @Test
    fun noOverigTileWhenEveryArticleHasACategory() {
        val tiles = categoryTiles(listOf(article("Binnenland", 100)))
        assertTrue(tiles.none { it.selection == CategorySelection.Topic(null) })
    }

    @Test
    fun allTileIsNotEmittedByTheHelper() {
        val tiles = categoryTiles(listOf(article("Binnenland", 100)))
        assertTrue(tiles.none { it.selection == CategorySelection.All })
    }

    @Test
    fun matchesFiltersByCategory() {
        val binnenland = article("Binnenland", 1)
        val europa = article("Europa", 2)
        val untagged = article(null, 3)

        assertTrue(CategorySelection.All.matches(binnenland))
        assertTrue(CategorySelection.Topic("Binnenland").matches(binnenland))
        assertTrue(!CategorySelection.Topic("Binnenland").matches(europa))
        assertTrue(CategorySelection.Topic(null).matches(untagged))
        assertTrue(!CategorySelection.Topic(null).matches(binnenland))
    }

    @Test
    fun matchesTreatsBlankCategoryAsOverig() {
        assertTrue(CategorySelection.Topic(null).matches(article("", 1)))
        assertTrue(CategorySelection.Topic(null).matches(article("   ", 1)))
    }

    @Test
    fun groupsRealFeedFixtureCountsSumToArticleCount() {
        val articles = AtomFeedParser.parse(fixture("feed_sample.xml"))
        val tiles = categoryTiles(articles)
        // Every article lands in exactly one tile.
        assertEquals(articles.size, tiles.sumOf { it.count })
        // The fixture is dominated by Binnenland; it must be present with several articles.
        val binnenland = tiles.firstOrNull { it.selection == CategorySelection.Topic("Binnenland") }
        assertNull("fixture sanity: Binnenland expected", null.takeIf { binnenland == null })
        assertTrue("expected multiple Binnenland articles", (binnenland?.count ?: 0) > 1)
        // Named topics are ordered newest-activity-first (ignoring a trailing Overig).
        val named = tiles.filter { (it.selection as CategorySelection.Topic).name != null }
        val newest = named.map { tile ->
            articles.filter { tile.selection.matches(it) }.maxOf { it.publishedEpochMs }
        }
        assertEquals(newest.sortedDescending(), newest)
    }
}

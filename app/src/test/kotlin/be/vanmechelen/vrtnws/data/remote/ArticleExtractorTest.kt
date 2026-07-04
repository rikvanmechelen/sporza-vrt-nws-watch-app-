package be.vanmechelen.vrtnws.data.remote

import be.vanmechelen.vrtnws.model.ArticleContent
import be.vanmechelen.vrtnws.model.BlockType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleExtractorTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing fixture $name" }
            .bufferedReader().use { it.readText() }

    private fun extract(name: String): ArticleContent = ArticleExtractor.extract(fixture(name))

    @Test
    fun regularArticleBodyIsExtracted() {
        val content = extract("article_regular.html")
        assertFalse("expected body blocks", content.isEmpty)
        assertTrue("expected several paragraphs, got ${content.blocks.size}", content.blocks.size >= 4)
        assertTrue(content.blocks.first().text.contains("Delhaize waarschuwt"))
        assertTrue(content.plainText.contains("gluten"))
    }

    @Test
    fun regularArticleExcludesSidebarNoise() {
        val text = extract("article_regular.html").plainText
        // "Meest gelezen" / "Lees ook" are sidebar sections that use non-article prose
        // classes and must not leak into the body.
        assertFalse("sidebar leaked into body", text.contains("Meest gelezen"))
        assertFalse("related links leaked into body", text.contains("Lees ook"))
    }

    @Test
    fun noBlockIsBlank() {
        extract("article_regular.html").blocks.forEach {
            assertTrue("blank block", it.text.isNotBlank())
        }
        extract("article_liveblog.html").blocks.forEach {
            assertTrue("blank block", it.text.isNotBlank())
        }
    }

    @Test
    fun liveblogBodyIsExtractedWithHeadings() {
        val content = extract("article_liveblog.html")
        assertTrue("expected many blocks, got ${content.blocks.size}", content.blocks.size > 10)
        assertTrue(content.plainText.contains("liveblog"))
        assertTrue(content.plainText.contains("The xx"))
        assertTrue("expected at least one heading", content.blocks.any { it.type == BlockType.HEADING })
    }

    @Test
    fun emptyOrJunkHtmlYieldsEmptyContent() {
        assertTrue(ArticleExtractor.extract("").isEmpty)
        assertTrue(ArticleExtractor.extract("<html><body><nav>menu</nav></body></html>").isEmpty)
    }

    @Test
    fun sporzaRegularArticleBodyIsExtracted() {
        // Sporza articles have no prose-article-* classes and no JSON-LD articleBody;
        // the body lives in <p> tags inside the main container.
        val content = extract("sporza_article.html")
        assertFalse("expected Sporza body", content.isEmpty)
        assertTrue(content.plainText.contains("Kaapverdië"))
        // related "Lees meer" / "Gerelateerd" teasers must not dominate the body
        assertTrue("expected several paragraphs, got ${content.blocks.size}", content.blocks.size >= 3)
    }

    @Test
    fun sporzaMatchPageYieldsBodyViaJsonLd() {
        // Live match pages carry text in JSON-LD liveBlogUpdate[].articleBody.
        val content = extract("sporza_match.html")
        assertFalse("expected match body via JSON-LD", content.isEmpty)
    }
}

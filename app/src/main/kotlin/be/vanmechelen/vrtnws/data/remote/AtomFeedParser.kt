package be.vanmechelen.vrtnws.data.remote

import be.vanmechelen.vrtnws.model.Article
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.time.OffsetDateTime

/** Parses the VRT NWS Atom feed into a list of [Article] headlines. */
object AtomFeedParser {

    fun parse(xml: String): List<Article> {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        return doc.getElementsByTag("entry").mapNotNull { it.toArticle() }
    }

    private fun Element.toArticle(): Article? {
        val id = firstText("id") ?: return null
        val title = firstText("title") ?: return null

        val links = getElementsByTag("link")
        val url = links.firstOrNull { it.attr("rel") == "alternate" }?.attr("href")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val imageUrl = links.firstOrNull { it.attr("rel") == "enclosure" }
            ?.attr("href")?.takeIf { it.isNotBlank() }

        return Article(
            id = id,
            title = title,
            summary = firstText("summary").orEmpty(),
            url = url,
            imageUrl = imageUrl,
            publishedEpochMs = parseTime(firstText("published") ?: firstText("updated")),
            category = firstText("vrtns:nstag")?.takeIf { it.isNotBlank() },
        )
    }

    private fun Element.firstText(tag: String): String? =
        getElementsByTag(tag).firstOrNull()?.text()?.trim()

    private fun parseTime(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .getOrDefault(0L)
    }
}

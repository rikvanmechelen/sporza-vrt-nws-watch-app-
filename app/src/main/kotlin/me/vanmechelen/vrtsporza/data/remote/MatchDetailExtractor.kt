package me.vanmechelen.vrtsporza.data.remote

import me.vanmechelen.vrtsporza.model.BlockType
import me.vanmechelen.vrtsporza.model.ContentBlock
import me.vanmechelen.vrtsporza.model.MatchDetail
import me.vanmechelen.vrtsporza.model.MatchEvent
import me.vanmechelen.vrtsporza.model.MatchEventType
import me.vanmechelen.vrtsporza.model.StreamItem
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Extracts a match detail page (`/nl/sport/{sport}/~{id}/`) into a [MatchDetail].
 *
 * The page is server-rendered and contains three distinct regions we care about:
 *  1. **Quick events** — a `_fieldTimeline_` widget whose `_hoverLabel_` spans already carry
 *     pre-formatted text ("29' - Doelpunt - Lionel Messi (1 - 0)").
 *  2. **Stream** — the "Fase per fase" live blog, a `.sw-timeline` list of `.sw-timeline-item`s.
 *  3. **Recap** — the editorial article body (nutshell summary + narrative headings/paragraphs).
 *
 * Like [ArticleExtractor], we match on `[class*=prefix]` (hashed CSS-module names) and fall
 * back gracefully; an empty result drives the UI's "open op telefoon" fallback.
 */
object MatchDetailExtractor {

    private val minuteRegex = Regex("""^\s*(\d+'(?:\s*\+\s*\d+)?)\s*[-–]\s*(.*)$""")

    fun extract(html: String): MatchDetail {
        if (html.isBlank()) return MatchDetail(emptyList(), emptyList(), emptyList())
        val doc = Jsoup.parse(html)
        return MatchDetail(
            events = extractEvents(doc),
            stream = extractStream(doc),
            recap = extractRecap(doc),
        )
    }

    private fun extractEvents(doc: Document): List<MatchEvent> =
        doc.select("[class*=fieldTimeline] [class*=hoverLabel]")
            .mapNotNull { el ->
                val raw = el.text().trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val match = minuteRegex.find(raw)
                val minute = match?.groupValues?.getOrNull(1)?.trim().orEmpty()
                val body = match?.groupValues?.getOrNull(2)?.trim() ?: raw
                MatchEvent(minute = minute, type = classify(body), text = body)
            }
            .distinct()

    private fun classify(body: String): MatchEventType = when {
        body.startsWith("Own goal", ignoreCase = true) -> MatchEventType.OWN_GOAL
        body.startsWith("Doelpunt", ignoreCase = true) -> MatchEventType.GOAL
        body.startsWith("Verv", ignoreCase = true) -> MatchEventType.SUBSTITUTION
        body.startsWith("Geel", ignoreCase = true) -> MatchEventType.YELLOW_CARD
        body.startsWith("Rood", ignoreCase = true) || body.startsWith("Rode", ignoreCase = true) ->
            MatchEventType.RED_CARD
        else -> MatchEventType.OTHER
    }

    private fun extractStream(doc: Document): List<StreamItem> =
        doc.select(".sw-timeline-item").mapNotNull { item ->
            val title = item.selectFirst("h2, h3, [class*=title]")?.text()?.trim()
                ?.takeIf { it.isNotBlank() }
            val text = item.select("p").joinToString("\n\n") { it.text().trim() }.trim()
            if (title.isNullOrBlank() && text.isBlank()) return@mapNotNull null
            val time = item.selectFirst("[class*=label], time")?.text()?.trim()
                ?.takeIf { it.isNotBlank() && it.length <= 12 }
            StreamItem(time = time, title = title, text = text)
        }

    private fun extractRecap(doc: Document): List<ContentBlock> {
        val root = doc.selectFirst("[class*=mainBody], [class*=mainContent], main, article")
            ?: doc.body() ?: return emptyList()
        val scoped = recapFrom(root)
        return if (scoped.size >= 2) scoped else recapFrom(doc.body() ?: root)
    }

    // Work on a clone so removing the live widgets doesn't disturb event/stream extraction,
    // then collect the nutshell bullets + narrative headings/paragraphs in document order.
    private fun recapFrom(root: Element): List<ContentBlock> {
        val clone = root.clone()
        clone.select(
            ".sw-timeline, [class*=fieldTimeline], [class*=goals], [class*=scoreboard], " +
                "[class*=relatedContent], [class*=storyCard], [class*=lineup], " +
                "nav, header, footer, aside, figure, script, style",
        ).remove()

        return clone.select("h2, h3, p, blockquote, [class*=nutshell] li").mapNotNull { el ->
            val text = el.text().trim()
            if (text.isBlank()) return@mapNotNull null
            when (el.tagName()) {
                "h2", "h3" -> ContentBlock(BlockType.HEADING, text)
                "blockquote" -> ContentBlock(BlockType.QUOTE, text)
                "li" -> ContentBlock(BlockType.PARAGRAPH, text)
                else -> if (text.length < 40) null else ContentBlock(BlockType.PARAGRAPH, text)
            }
        }
    }
}

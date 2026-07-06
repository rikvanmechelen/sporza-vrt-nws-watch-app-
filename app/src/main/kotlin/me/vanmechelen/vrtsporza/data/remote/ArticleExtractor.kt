package me.vanmechelen.vrtsporza.data.remote

import me.vanmechelen.vrtsporza.model.ArticleContent
import me.vanmechelen.vrtsporza.model.BlockType
import me.vanmechelen.vrtsporza.model.ContentBlock
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Extracts a clean, reader-friendly body from a VRT NWS article HTML page.
 *
 * VRT renders both regular articles and liveblogs server-side, tagging body content with
 * `prose-article-*` CSS classes (`-body-*`, `-h2/-h3`, `-quote`). Sidebar/related sections
 * use different prose classes, so scoping to `prose-article-*` naturally excludes them.
 *
 * Layered for robustness (the single place to adjust if VRT changes their markup):
 *  1. DOM by `prose-article-*` classes — primary; works for regular articles AND liveblogs.
 *  2. JSON-LD `articleBody` / `liveBlogUpdate[].articleBody` — fallback.
 *  3. Generic long-paragraph heuristic — last resort.
 * When all fail, returns empty content and the UI falls back to summary + "open on phone".
 */
object ArticleExtractor {

    fun extract(html: String): ArticleContent {
        if (html.isBlank()) return ArticleContent(emptyList())
        val doc = Jsoup.parse(html)
        val blocks = fromProseClasses(doc)
            .ifEmpty { fromJsonLd(html) }
            .ifEmpty { fromMainContent(doc) }
        return ArticleContent(blocks)
    }

    private fun fromProseClasses(doc: Document): List<ContentBlock> {
        val selector = "[class*=prose-article-body], [class*=prose-article-h], [class*=prose-article-quote]"
        val selected = doc.select(selector)
        val set = selected.toSet()
        return selected
            // Drop elements nested inside another selected element to avoid duplicated text.
            .filter { el -> el.parents().none { it in set } }
            .mapNotNull { el ->
                val cls = el.className()
                val type = when {
                    cls.contains("prose-article-h1") -> return@mapNotNull null // page headline, shown separately
                    // `-small-` = captions/bylines/language switch; bare `-body-m` = category tags.
                    cls.contains("prose-article-body-small") -> return@mapNotNull null
                    cls.contains("prose-article-quote") -> BlockType.QUOTE
                    cls.contains("prose-article-h") -> BlockType.HEADING // h2 / h3
                    cls.contains("prose-article-body-r") || cls.contains("prose-article-body-sb") ->
                        BlockType.PARAGRAPH
                    else -> return@mapNotNull null
                }
                el.text().trim().takeIf { it.isNotBlank() }?.let { ContentBlock(type, it) }
            }
    }

    private val articleBodyRegex = Regex("\"articleBody\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")

    private fun fromJsonLd(html: String): List<ContentBlock> =
        articleBodyRegex.findAll(html)
            .map { unescapeJson(it.groupValues[1]) }
            .flatMap { body -> body.split("\n").asSequence() }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            // liveBlogUpdate arrays often repeat entries; keep first occurrence, preserve order.
            .distinct()
            .map { ContentBlock(BlockType.PARAGRAPH, it) }
            .toList()

    // Sites without prose-article-* classes (e.g. Sporza) keep body text in plain <p>/<h2>
    // inside a main container. Scope to it to skip nav/related teasers; fall back to the
    // whole document if no recognisable container is found or scoping finds too little.
    private fun fromMainContent(doc: Document): List<ContentBlock> {
        val root = doc.selectFirst(
            "[class*=mainBody], [class*=article-body], [class*=articleBody], article, main",
        ) ?: doc.body() ?: return emptyList()
        val scoped = paragraphsIn(root)
        return if (scoped.size >= 2) scoped else paragraphsIn(doc.body() ?: root)
    }

    private fun paragraphsIn(root: Element): List<ContentBlock> =
        root.select("p, h2, h3, blockquote").mapNotNull { el ->
            val text = el.text().trim()
            if (text.isBlank()) return@mapNotNull null
            val type = when (el.tagName()) {
                "h2", "h3" -> BlockType.HEADING
                "blockquote" -> BlockType.QUOTE
                else -> BlockType.PARAGRAPH
            }
            if (type == BlockType.PARAGRAPH && text.length < 40) return@mapNotNull null
            ContentBlock(type, text)
        }

    private fun unescapeJson(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> {}
                    'u' -> {
                        if (i + 5 < s.length) {
                            sb.append(s.substring(i + 2, i + 6).toInt(16).toChar())
                            i += 4
                        }
                    }
                    else -> sb.append(n)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}

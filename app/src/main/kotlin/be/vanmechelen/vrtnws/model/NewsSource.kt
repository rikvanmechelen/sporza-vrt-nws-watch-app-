package be.vanmechelen.vrtnws.model

import be.vanmechelen.vrtnws.R

/**
 * A selectable feed. All are Atom feeds with the same shape, parsed by AtomFeedParser.
 * Order defines the horizontal pager order on the headline screen.
 */
enum class NewsSource(val feedUrl: String, val labelRes: Int) {
    // Declaration order = left-to-right pager order (Kort, Sport, Nieuws).
    NEWS_TOP("https://www.vrt.be/vrtnws/nl.rss.headlines.xml", R.string.source_top),
    SPORT("https://sporza.be/nl.rss.articles.xml", R.string.source_sport),
    NEWS_LATEST("https://www.vrt.be/vrtnws/nl.rss.articles.xml", R.string.source_news);

    companion object {
        val DEFAULT = NEWS_LATEST
        fun fromName(name: String?): NewsSource =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

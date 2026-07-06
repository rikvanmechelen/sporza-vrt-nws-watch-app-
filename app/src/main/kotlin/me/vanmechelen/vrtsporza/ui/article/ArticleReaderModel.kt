package me.vanmechelen.vrtsporza.ui.article

import me.vanmechelen.vrtsporza.ui.theme.Section

/**
 * The brand identity shown in the article reader's meta row (source mark + name), derived from
 * the section the article was opened from: VRT NWS (Kort, Nieuws) vs Sporza (Sport).
 */
data class ReaderSource(val label: String, val mark: String)

fun readerSourceFor(section: Section): ReaderSource = when (section) {
    Section.NEWS -> ReaderSource(label = "VRT NWS", mark = "N")
    Section.SPORT -> ReaderSource(label = "Sporza", mark = "S")
}

/**
 * The single, tidy kicker shown over the lead image. The feed's tag field can carry several
 * pipe-delimited tags (e.g. "WIELRENNEN|TOUR DE FRANCE|LENNERT LISSENS"); the kicker is just the
 * first, so it reads as one clean label. Null/blank in → null out (no kicker line).
 */
fun kickerLabel(category: String?): String? =
    category?.split('|')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

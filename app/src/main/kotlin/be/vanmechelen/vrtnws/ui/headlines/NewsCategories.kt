package be.vanmechelen.vrtnws.ui.headlines

import be.vanmechelen.vrtnws.model.Article

/**
 * A choice in the Nieuws category flow. Shared by the category list, the AppRoot
 * navigation state, and the filtered [HeadlinesScreen].
 */
sealed interface CategorySelection {
    /** The full, unfiltered article list ("Alles"). */
    data object All : CategorySelection

    /** A single category. [name] == null means the uncategorised "Overig" group. */
    data class Topic(val name: String?) : CategorySelection

    fun matches(article: Article): Boolean = when (this) {
        All -> true
        is Topic -> article.category.normalizedCategory() == name
    }
}

/** One row in the Nieuws category list. Excludes the pinned "Alles" tile. */
data class CategoryTile(val selection: CategorySelection.Topic, val count: Int)

/** Treat blank categories as "no category" so they fold into the Overig group. */
private fun String?.normalizedCategory(): String? = this?.trim()?.takeIf { it.isNotBlank() }

/**
 * Groups [articles] by category for the Nieuws category list. Topics are ordered by
 * newest activity (their most recent article) first; the uncategorised group
 * ([CategorySelection.Topic] with a null name → "Overig") is always placed last.
 *
 * The pinned "Alles" tile is added by the caller, not here.
 */
fun categoryTiles(articles: List<Article>): List<CategoryTile> {
    val groups = articles.groupBy { it.category.normalizedCategory() }

    val namedTiles = groups.entries
        .filter { it.key != null }
        .sortedWith(
            compareByDescending<Map.Entry<String?, List<Article>>> { entry ->
                entry.value.maxOf { it.publishedEpochMs }
            }.thenBy { it.key },
        )
        .map { (name, arts) -> CategoryTile(CategorySelection.Topic(name), arts.size) }

    val overigTile = groups[null]
        ?.let { CategoryTile(CategorySelection.Topic(null), it.size) }

    return namedTiles + listOfNotNull(overigTile)
}

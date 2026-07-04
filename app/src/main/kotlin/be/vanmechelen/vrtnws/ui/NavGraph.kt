package be.vanmechelen.vrtnws.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.SwipeToDismissBox
import androidx.wear.compose.material.SwipeToDismissValue
import androidx.wear.compose.material.rememberSwipeToDismissBoxState
import be.vanmechelen.vrtnws.data.MatchesRepository
import be.vanmechelen.vrtnws.data.NewsRepository
import be.vanmechelen.vrtnws.model.Article
import be.vanmechelen.vrtnws.model.Match
import be.vanmechelen.vrtnws.model.NewsSource
import be.vanmechelen.vrtnws.ui.article.ArticleScreen
import be.vanmechelen.vrtnws.ui.article.ArticleViewModel
import be.vanmechelen.vrtnws.ui.headlines.CategoriesScreen
import be.vanmechelen.vrtnws.ui.headlines.CategorySelection
import be.vanmechelen.vrtnws.ui.headlines.HeadlinesScreen
import be.vanmechelen.vrtnws.ui.headlines.HeadlinesViewModel
import be.vanmechelen.vrtnws.ui.matches.MatchDetailScreen
import be.vanmechelen.vrtnws.ui.matches.MatchDetailViewModel
import be.vanmechelen.vrtnws.ui.matches.MatchesScreen
import be.vanmechelen.vrtnws.ui.matches.MatchesViewModel
import be.vanmechelen.vrtnws.ui.theme.VrtNwsTheme

/** A page in the top-level pager: one of the news feeds, or the Matches section. */
private sealed interface PagerTab {
    data class News(val source: NewsSource) : PagerTab
    data object Matches : PagerTab
}

/** The pager tabs: the news feeds (in [NewsSource] order) then Matches as the last page. */
private val PAGER_TABS: List<PagerTab> =
    NewsSource.entries.map { PagerTab.News(it) } + PagerTab.Matches

/** Index of the Matches page — used by the sports Tile's deep-link. */
val MATCHES_TAB_INDEX: Int = PAGER_TABS.lastIndex

/**
 * List level = a horizontal pager over [NewsSource] (Nieuws / Kort / Sport); swipe left/right
 * to switch source. Selecting an article opens the reader in a [SwipeToDismissBox] (swipe from
 * the left edge to return to the list).
 */
@Composable
fun AppRoot(
    repository: NewsRepository,
    matchesRepository: MatchesRepository,
    initialTab: Int = 0,
) {
    VrtNwsTheme {
        val context = LocalContext.current
        var selected by remember { mutableStateOf<Article?>(null) }
        // Non-null while browsing a category of the Nieuws feed (see CategoriesScreen).
        var newsSelection by remember { mutableStateOf<CategorySelection?>(null) }
        // Non-null while viewing a match detail.
        var matchSelection by remember { mutableStateOf<Match?>(null) }

        // Hoisted so the pager keeps its page across the reader / detail screens.
        // Tabs = the news feeds (Kort / Sport / Nieuws) then Matches as the 4th page.
        val tabs = PAGER_TABS
        val pagerState = rememberPagerState(
            initialPage = initialTab.coerceIn(0, tabs.lastIndex),
            pageCount = { tabs.size },
        )

        val current = selected
        val category = newsSelection
        val match = matchSelection
        when {
            current != null -> {
                val dismissState = rememberSwipeToDismissBoxState()
                LaunchedEffect(dismissState.currentValue) {
                    if (dismissState.currentValue == SwipeToDismissValue.Dismissed) {
                        selected = null
                        dismissState.snapTo(SwipeToDismissValue.Default)
                    }
                }
                SwipeToDismissBox(state = dismissState) { isBackground ->
                    if (isBackground) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colors.background))
                    } else {
                        val articleViewModel: ArticleViewModel = viewModel(
                            key = current.url,
                            factory = articleViewModelFactory(repository, current.url),
                        )
                        ArticleScreen(
                            viewModel = articleViewModel,
                            title = current.title,
                            onOpenOnPhone = { openOnPhone(context, it) },
                        )
                    }
                }
            }

            category != null -> {
                val dismissState = rememberSwipeToDismissBoxState()
                LaunchedEffect(dismissState.currentValue) {
                    if (dismissState.currentValue == SwipeToDismissValue.Dismissed) {
                        newsSelection = null
                        dismissState.snapTo(SwipeToDismissValue.Default)
                    }
                }
                SwipeToDismissBox(state = dismissState) { isBackground ->
                    if (isBackground) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colors.background))
                    } else {
                        // Same ViewModel instance as the Nieuws page (keyed by source name),
                        // so the filtered list shares its already-loaded articles.
                        val vm: HeadlinesViewModel = viewModel(
                            key = NewsSource.NEWS_LATEST.name,
                            factory = headlinesViewModelFactory(repository, NewsSource.NEWS_LATEST),
                        )
                        HeadlinesScreen(
                            viewModel = vm,
                            source = NewsSource.NEWS_LATEST,
                            onArticleClick = { selected = it },
                            selection = category,
                        )
                    }
                }
            }

            match != null -> {
                val dismissState = rememberSwipeToDismissBoxState()
                LaunchedEffect(dismissState.currentValue) {
                    if (dismissState.currentValue == SwipeToDismissValue.Dismissed) {
                        matchSelection = null
                        dismissState.snapTo(SwipeToDismissValue.Default)
                    }
                }
                SwipeToDismissBox(state = dismissState) { isBackground ->
                    if (isBackground) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colors.background))
                    } else {
                        val vm: MatchDetailViewModel = viewModel(
                            key = match.detailUrl,
                            factory = matchDetailViewModelFactory(matchesRepository, match.detailUrl),
                        )
                        MatchDetailScreen(
                            viewModel = vm,
                            match = match,
                            onOpenOnPhone = { openOnPhone(context, it) },
                        )
                    }
                }
            }

            else -> {
                Box(Modifier.fillMaxSize()) {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        val active = page == pagerState.currentPage
                        when (val tab = tabs[page]) {
                            is PagerTab.News -> {
                                val src = tab.source
                                val vm: HeadlinesViewModel = viewModel(
                                    key = src.name,
                                    factory = headlinesViewModelFactory(repository, src),
                                )
                                if (src == NewsSource.NEWS_LATEST) {
                                    CategoriesScreen(
                                        viewModel = vm,
                                        onCategoryClick = { newsSelection = it },
                                        isActive = active,
                                    )
                                } else {
                                    HeadlinesScreen(
                                        viewModel = vm,
                                        source = src,
                                        onArticleClick = { selected = it },
                                        isActive = active,
                                    )
                                }
                            }

                            PagerTab.Matches -> {
                                val vm: MatchesViewModel = viewModel(
                                    key = "matches",
                                    factory = matchesViewModelFactory(matchesRepository),
                                )
                                MatchesScreen(
                                    viewModel = vm,
                                    onMatchClick = { matchSelection = it },
                                    isActive = active,
                                )
                            }
                        }
                    }
                    TopPageIndicator(
                        pageCount = tabs.size,
                        selectedPage = pagerState.currentPage,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** A small row of dots at the top showing how many source pages there are and which is active. */
@Composable
private fun TopPageIndicator(pageCount: Int, selectedPage: Int, modifier: Modifier = Modifier) {
    if (pageCount <= 1) return
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val active = index == selectedPage
            Box(
                Modifier
                    .size(if (active) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colors.onBackground
                        else MaterialTheme.colors.onSurfaceVariant.copy(alpha = 0.4f),
                    ),
            )
        }
    }
}

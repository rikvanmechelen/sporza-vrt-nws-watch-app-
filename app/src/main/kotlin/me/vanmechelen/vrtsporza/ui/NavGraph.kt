package me.vanmechelen.vrtsporza.ui

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
import me.vanmechelen.vrtsporza.data.MatchesRepository
import me.vanmechelen.vrtsporza.data.NewsRepository
import me.vanmechelen.vrtsporza.model.Article
import me.vanmechelen.vrtsporza.model.Match
import me.vanmechelen.vrtsporza.model.NewsSource
import me.vanmechelen.vrtsporza.ui.article.ArticleScreen
import me.vanmechelen.vrtsporza.ui.article.ArticleViewModel
import me.vanmechelen.vrtsporza.ui.headlines.CategoriesScreen
import me.vanmechelen.vrtsporza.ui.headlines.CategorySelection
import me.vanmechelen.vrtsporza.ui.headlines.HeadlinesScreen
import me.vanmechelen.vrtsporza.ui.headlines.HeadlinesViewModel
import me.vanmechelen.vrtsporza.ui.matches.MatchDetailScreen
import me.vanmechelen.vrtsporza.ui.matches.MatchDetailViewModel
import me.vanmechelen.vrtsporza.ui.matches.MatchesScreen
import me.vanmechelen.vrtsporza.ui.matches.MatchesViewModel
import me.vanmechelen.vrtsporza.ui.theme.Section
import me.vanmechelen.vrtsporza.ui.theme.VrtNwsTheme

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

/** Colour follows the content's brand: Sporza (Sport feed, Matches) green, VRT NWS purple. */
private fun sectionOf(source: NewsSource): Section =
    if (source == NewsSource.SPORT) Section.SPORT else Section.NEWS

private fun PagerTab.section(): Section = when (this) {
    is PagerTab.News -> sectionOf(source)
    PagerTab.Matches -> Section.SPORT
}

/**
 * List level = a horizontal pager over [NewsSource] (Nieuws / Kort / Sport); swipe left/right
 * to switch source. Selecting an article opens the reader in a [SwipeToDismissBox] (swipe from
 * the left edge to return to the list). Each page / detail screen is wrapped in its own
 * section-coloured [VrtNwsTheme] so `Colors.primary` swaps between purple (news) and green (sport).
 */
@Composable
fun AppRoot(
    repository: NewsRepository,
    matchesRepository: MatchesRepository,
    initialTab: Int = 0,
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<Article?>(null) }
    // The section the open article came from, so the reader keeps that brand's accent.
    var selectedSection by remember { mutableStateOf(Section.NEWS) }
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
        current != null -> VrtNwsTheme(selectedSection) {
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
                        article = current,
                        section = selectedSection,
                        onOpenOnPhone = { openOnPhone(context, it) },
                    )
                }
            }
        }

        category != null -> VrtNwsTheme(Section.NEWS) {
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
                        onArticleClick = { selected = it; selectedSection = Section.NEWS },
                        selection = category,
                    )
                }
            }
        }

        match != null -> VrtNwsTheme(Section.SPORT) {
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

        else -> Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val active = page == pagerState.currentPage
                val tab = tabs[page]
                VrtNwsTheme(tab.section()) {
                    when (tab) {
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
                                    onArticleClick = { selected = it; selectedSection = sectionOf(src) },
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
            }
            // The dot rail's active dot takes the current page's section accent.
            VrtNwsTheme(tabs[pagerState.currentPage].section()) {
                TopPageIndicator(
                    pageCount = tabs.size,
                    selectedPage = pagerState.currentPage,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp),
                )
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
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val active = index == selectedPage
            Box(
                Modifier
                    .size(if (active) 9.dp else 7.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colors.primary
                        else MaterialTheme.colors.onSurfaceVariant.copy(alpha = 0.28f),
                    ),
            )
        }
    }
}

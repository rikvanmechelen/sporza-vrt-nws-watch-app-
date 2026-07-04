package be.vanmechelen.vrtnws.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.HorizontalPageIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PageIndicatorState
import androidx.wear.compose.material.SwipeToDismissBox
import androidx.wear.compose.material.SwipeToDismissValue
import androidx.wear.compose.material.rememberSwipeToDismissBoxState
import be.vanmechelen.vrtnws.data.NewsRepository
import be.vanmechelen.vrtnws.model.Article
import be.vanmechelen.vrtnws.model.NewsSource
import be.vanmechelen.vrtnws.ui.article.ArticleScreen
import be.vanmechelen.vrtnws.ui.article.ArticleViewModel
import be.vanmechelen.vrtnws.ui.headlines.HeadlinesScreen
import be.vanmechelen.vrtnws.ui.headlines.HeadlinesViewModel
import be.vanmechelen.vrtnws.ui.theme.VrtNwsTheme

/**
 * List level = a horizontal pager over [NewsSource] (Nieuws / Kort / Sport); swipe left/right
 * to switch source. Selecting an article opens the reader in a [SwipeToDismissBox] (swipe from
 * the left edge to return to the list).
 */
@Composable
fun AppRoot(repository: NewsRepository) {
    VrtNwsTheme {
        val context = LocalContext.current
        var selected by remember { mutableStateOf<Article?>(null) }

        val current = selected
        if (current == null) {
            val sources = NewsSource.entries
            val pagerState = rememberPagerState(pageCount = { sources.size })
            val pageIndicatorState = remember(pagerState) {
                object : PageIndicatorState {
                    override val pageOffset: Float get() = pagerState.currentPageOffsetFraction
                    override val selectedPage: Int get() = pagerState.currentPage
                    override val pageCount: Int get() = pagerState.pageCount
                }
            }
            Box(Modifier.fillMaxSize()) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val src = sources[page]
                    val vm: HeadlinesViewModel =
                        viewModel(key = src.name, factory = headlinesViewModelFactory(repository, src))
                    HeadlinesScreen(
                        viewModel = vm,
                        source = src,
                        onArticleClick = { selected = it },
                        isActive = page == pagerState.currentPage,
                    )
                }
                HorizontalPageIndicator(
                    pageIndicatorState = pageIndicatorState,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        } else {
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
    }
}

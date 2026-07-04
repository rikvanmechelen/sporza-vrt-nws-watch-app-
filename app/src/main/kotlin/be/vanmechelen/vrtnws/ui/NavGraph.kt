package be.vanmechelen.vrtnws.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.SwipeToDismissBox
import androidx.wear.compose.material.SwipeToDismissValue
import androidx.wear.compose.material.rememberSwipeToDismissBoxState
import be.vanmechelen.vrtnws.data.NewsRepository
import be.vanmechelen.vrtnws.model.Article
import be.vanmechelen.vrtnws.ui.article.ArticleScreen
import be.vanmechelen.vrtnws.ui.article.ArticleViewModel
import be.vanmechelen.vrtnws.ui.headlines.HeadlinesScreen
import be.vanmechelen.vrtnws.ui.headlines.HeadlinesViewModel
import be.vanmechelen.vrtnws.ui.theme.VrtNwsTheme

/**
 * Two-screen navigation done manually: the headline list, and (when one is selected) the
 * article reader wrapped in a [SwipeToDismissBox] so the standard swipe-from-left gesture
 * returns to the list.
 */
@Composable
fun AppRoot(repository: NewsRepository) {
    VrtNwsTheme {
        val context = LocalContext.current
        var selected by remember { mutableStateOf<Article?>(null) }
        val headlinesViewModel: HeadlinesViewModel =
            viewModel(factory = headlinesViewModelFactory(repository))

        val current = selected
        if (current == null) {
            HeadlinesScreen(
                viewModel = headlinesViewModel,
                onArticleClick = { selected = it },
            )
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
                        key = current.id,
                        factory = articleViewModelFactory(repository, current.id, current.url),
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

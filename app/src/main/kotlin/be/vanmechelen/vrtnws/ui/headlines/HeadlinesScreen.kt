package be.vanmechelen.vrtnws.ui.headlines

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.compose.foundation.layout.PaddingValues
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import be.vanmechelen.vrtnws.R
import be.vanmechelen.vrtnws.model.Article
import be.vanmechelen.vrtnws.ui.components.ListCard
import be.vanmechelen.vrtnws.ui.components.LoadingState
import be.vanmechelen.vrtnws.ui.components.ErrorState
import be.vanmechelen.vrtnws.ui.components.OfflineBanner
import be.vanmechelen.vrtnws.ui.components.SectionHeader
import coil.compose.AsyncImage

@OptIn(androidx.wear.compose.foundation.ExperimentalWearFoundationApi::class)
@Composable
fun HeadlinesScreen(
    viewModel: HeadlinesViewModel,
    source: be.vanmechelen.vrtnws.model.NewsSource,
    onArticleClick: (Article) -> Unit,
    isActive: Boolean = true,
    /** When non-null, show only this category's articles and use its name as the header. */
    selection: CategorySelection? = null,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }
    // Only the visible pager page should own the rotary crown focus. Re-request once content is
    // ready too: the scrollable (which the requester attaches to) isn't composed during the
    // loading/error states, so a focus request that fires while this page is still loading would
    // otherwise be lost and the crown would stay dead until the page is swiped away and back.
    val contentReady = !ui.isInitialLoading && !ui.showError
    LaunchedEffect(isActive, contentReady) {
        if (isActive && contentReady) runCatching { focusRequester.requestFocus() }
    }

    val articles = if (selection == null) ui.articles else ui.articles.filter(selection::matches)

    when {
        ui.isInitialLoading -> LoadingState()
        ui.showError -> ErrorState(stringResource(R.string.load_error), onRetry = viewModel::refresh)
        else -> ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .rotaryScrollable(RotaryScrollableDefaults.behavior(listState), focusRequester),
            autoCentering = null,
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 48.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val headerText = when (selection) {
                    null, CategorySelection.All -> stringResource(source.labelRes)
                    is CategorySelection.Topic ->
                        selection.name ?: stringResource(R.string.category_other)
                }
                SectionHeader(
                    title = headerText,
                    isRefreshing = ui.isRefreshing,
                    onRefresh = viewModel::refresh,
                )
            }
            if (ui.showOfflineBanner) {
                item { OfflineBanner() }
            }
            itemsIndexed(articles, key = { _, a -> a.id }) { index, article ->
                HeadlineCard(article, accent = index == 0, onClick = { onArticleClick(article) })
            }
        }
    }
}

@Composable
private fun HeadlineCard(article: Article, accent: Boolean, onClick: () -> Unit) {
    ListCard(onClick = onClick, accent = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (article.imageUrl != null) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(62.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.title2.copy(fontSize = 16.sp),
                    color = MaterialTheme.colors.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(7.dp))
                val time = remember(article.publishedEpochMs) {
                    DateUtils.getRelativeTimeSpanString(article.publishedEpochMs).toString()
                }
                Text(
                    text = time,
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

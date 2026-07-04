package be.vanmechelen.vrtnws.ui.headlines

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import be.vanmechelen.vrtnws.R
import be.vanmechelen.vrtnws.model.Article
import coil.compose.AsyncImage

@OptIn(androidx.wear.compose.foundation.ExperimentalWearFoundationApi::class)
@Composable
fun HeadlinesScreen(
    viewModel: HeadlinesViewModel,
    source: be.vanmechelen.vrtnws.model.NewsSource,
    onArticleClick: (Article) -> Unit,
    isActive: Boolean = true,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }
    // Only the visible pager page should own the rotary crown focus.
    LaunchedEffect(isActive) { if (isActive) runCatching { focusRequester.requestFocus() } }

    when {
        ui.isInitialLoading -> CenteredProgress()
        ui.showError -> CenteredMessage(stringRes = R.string.load_error, onRetry = viewModel::refresh)
        else -> ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .rotaryScrollable(RotaryScrollableDefaults.behavior(listState), focusRequester),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                val refreshLabel = androidx.compose.ui.res.stringResource(R.string.refresh)
                Row(
                    modifier = Modifier
                        .clickable(onClick = viewModel::refresh)
                        .semantics { contentDescription = refreshLabel },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(source.labelRes),
                        style = MaterialTheme.typography.title3,
                        color = MaterialTheme.colors.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    if (ui.isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(android.R.drawable.stat_notify_sync),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colors.primary,
                        )
                    }
                }
            }
            if (ui.showOfflineBanner) {
                item {
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.offline),
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurfaceVariant,
                    )
                }
            }
            items(ui.articles, key = { it.id }) { article ->
                HeadlineCard(article, onClick = { onArticleClick(article) })
            }
        }
    }
}

@Composable
private fun HeadlineCard(article: Article, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (article.imageUrl != null) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.body2,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val time = remember(article.publishedEpochMs) {
                    DateUtils.getRelativeTimeSpanString(article.publishedEpochMs).toString()
                }
                Text(
                    text = time,
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CenteredProgress() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CenteredMessage(stringRes: Int, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = androidx.compose.ui.res.stringResource(stringRes),
                style = MaterialTheme.typography.body2,
            )
            Spacer(Modifier.height(8.dp))
            Chip(
                label = { Text(androidx.compose.ui.res.stringResource(R.string.refresh)) },
                onClick = onRetry,
                colors = ChipDefaults.primaryChipColors(),
            )
        }
    }
}

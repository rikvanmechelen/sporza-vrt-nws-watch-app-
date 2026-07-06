package me.vanmechelen.vrtsporza.ui.headlines

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import me.vanmechelen.vrtsporza.R
import me.vanmechelen.vrtsporza.model.NewsSource
import me.vanmechelen.vrtsporza.ui.components.ErrorState
import me.vanmechelen.vrtsporza.ui.components.ListCard
import me.vanmechelen.vrtsporza.ui.components.LoadingState
import me.vanmechelen.vrtsporza.ui.components.OfflineBanner
import me.vanmechelen.vrtsporza.ui.components.SectionHeader

/**
 * The Nieuws category list: a pinned "Alles" row (full feed) followed by one row per
 * category, ordered by newest activity, each showing its article count. Tapping a row
 * opens the filtered article list. Reuses the same [HeadlinesViewModel] as the article
 * list, so the two views share cache/refresh state without refetching.
 */
@OptIn(androidx.wear.compose.foundation.ExperimentalWearFoundationApi::class)
@Composable
fun CategoriesScreen(
    viewModel: HeadlinesViewModel,
    onCategoryClick: (CategorySelection) -> Unit,
    isActive: Boolean = true,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }
    // Re-request once content is ready too: the scrollable the requester attaches to isn't
    // composed during loading/error, so a request that fires while still loading is lost (dead
    // crown until swiped away and back).
    val contentReady = !ui.isInitialLoading && !ui.showError
    LaunchedEffect(isActive, contentReady) {
        if (isActive && contentReady) runCatching { focusRequester.requestFocus() }
    }

    val tiles = remember(ui.articles) { categoryTiles(ui.articles) }

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
                SectionHeader(
                    title = stringResource(NewsSource.NEWS_LATEST.labelRes),
                    isRefreshing = ui.isRefreshing,
                    onRefresh = viewModel::refresh,
                )
            }
            if (ui.showOfflineBanner) {
                item { OfflineBanner() }
            }
            item {
                CategoryRow(
                    label = stringResource(R.string.category_all),
                    count = ui.articles.size,
                    emphasized = true,
                    onClick = { onCategoryClick(CategorySelection.All) },
                )
            }
            items(tiles, key = { (it.selection.name ?: " overig") }) { tile ->
                CategoryRow(
                    label = tile.selection.name ?: stringResource(R.string.category_other),
                    count = tile.count,
                    emphasized = false,
                    onClick = { onCategoryClick(tile.selection) },
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(label: String, count: Int, emphasized: Boolean, onClick: () -> Unit) {
    ListCard(onClick = onClick, accent = emphasized) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.title2.copy(fontSize = 18.sp),
                color = MaterialTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            CountPill(count = count, emphasized = emphasized)
        }
    }
}

@Composable
private fun CountPill(count: Int, emphasized: Boolean) {
    val bg = if (emphasized) MaterialTheme.colors.primary else MaterialTheme.colors.primary.copy(alpha = 0.14f)
    val fg = if (emphasized) MaterialTheme.colors.onPrimary else MaterialTheme.colors.secondary
    Box(
        Modifier
            .padding(start = 10.dp)
            .clip(CircleShape)
            .background(bg)
            .defaultMinSize(minWidth = 34.dp)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.button,
            color = fg,
            textAlign = TextAlign.Center,
        )
    }
}

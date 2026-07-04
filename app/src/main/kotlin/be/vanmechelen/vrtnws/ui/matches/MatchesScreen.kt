package be.vanmechelen.vrtnws.ui.matches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
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
import be.vanmechelen.vrtnws.model.Match
import be.vanmechelen.vrtnws.model.MatchSports
import be.vanmechelen.vrtnws.model.MatchStatus

/** A row in the matches list: either a per-sport section header or a match card. */
private sealed interface MatchesEntry {
    data class SportHeader(val slug: String) : MatchesEntry
    data class MatchCard(val match: Match) : MatchesEntry
}

// The list arrives already sorted voetbal-first; insert a header whenever the sport changes.
private fun List<Match>.toEntries(): List<MatchesEntry> {
    val entries = mutableListOf<MatchesEntry>()
    var lastSport: String? = null
    for (m in this) {
        if (m.sportSlug != lastSport) {
            entries += MatchesEntry.SportHeader(m.sportSlug)
            lastSport = m.sportSlug
        }
        entries += MatchesEntry.MatchCard(m)
    }
    return entries
}

@OptIn(androidx.wear.compose.foundation.ExperimentalWearFoundationApi::class)
@Composable
fun MatchesScreen(
    viewModel: MatchesViewModel,
    onMatchClick: (Match) -> Unit,
    isActive: Boolean = true,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isActive) { if (isActive) runCatching { focusRequester.requestFocus() } }

    when {
        ui.isInitialLoading -> CenteredProgress()
        ui.showError -> CenteredError(onRetry = viewModel::refresh)
        else -> {
            val entries = remember(ui.matches) { ui.matches.toEntries() }
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .rotaryScrollable(RotaryScrollableDefaults.behavior(listState), focusRequester),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    RefreshHeader(
                        title = stringResource(R.string.source_matches),
                        isRefreshing = ui.isRefreshing,
                        onRefresh = viewModel::refresh,
                    )
                }
                if (ui.showOfflineBanner) {
                    item {
                        Text(
                            text = stringResource(R.string.offline),
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurfaceVariant,
                        )
                    }
                }
                if (ui.matches.isEmpty() && !ui.isRefreshing) {
                    item {
                        Text(
                            text = stringResource(R.string.matches_empty),
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurfaceVariant,
                        )
                    }
                }
                items(
                    items = entries,
                    key = {
                        when (it) {
                            is MatchesEntry.SportHeader -> "h-${it.slug}"
                            is MatchesEntry.MatchCard -> "m-${it.match.detailUrl}"
                        }
                    },
                ) { entry ->
                    when (entry) {
                        is MatchesEntry.SportHeader -> SportHeader(entry.slug)
                        is MatchesEntry.MatchCard ->
                            MatchCard(entry.match, onClick = { onMatchClick(entry.match) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RefreshHeader(title: String, isRefreshing: Boolean, onRefresh: () -> Unit) {
    val refreshLabel = stringResource(R.string.refresh)
    Row(
        modifier = Modifier
            .clickable(onClick = onRefresh)
            .semantics { contentDescription = refreshLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.title3,
            color = MaterialTheme.colors.primary,
        )
        Spacer(Modifier.width(6.dp))
        if (isRefreshing) {
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

@Composable
private fun SportHeader(slug: String) {
    Text(
        text = MatchSports.label(slug),
        style = MaterialTheme.typography.caption1,
        color = MaterialTheme.colors.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, start = 4.dp),
    )
}

@Composable
private fun MatchCard(match: Match, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            if (!match.competition.isNullOrBlank()) {
                Text(
                    text = match.competition,
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (match.home != null && match.away != null) {
                        Text(match.home, style = MaterialTheme.typography.body2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(match.away, style = MaterialTheme.typography.body2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        Text(match.title, style = MaterialTheme.typography.body2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(8.dp))
                ScoreOrStatus(match)
            }
        }
    }
}

@Composable
private fun ScoreOrStatus(match: Match) {
    val live = match.status == MatchStatus.LIVE
    Column(horizontalAlignment = Alignment.End) {
        if (live) {
            Box(
                Modifier
                    .padding(bottom = 2.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935)),
            )
        }
        Text(
            text = match.score ?: match.statusText.ifBlank { "—" },
            style = MaterialTheme.typography.title3,
            color = if (live) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
        )
        if (match.score != null && match.statusText.isNotBlank()) {
            Text(
                text = match.statusText,
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
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
private fun CenteredError(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.matches_load_error),
                style = MaterialTheme.typography.body2,
            )
            Spacer(Modifier.height(8.dp))
            Chip(
                label = { Text(stringResource(R.string.refresh)) },
                onClick = onRetry,
                colors = ChipDefaults.primaryChipColors(),
            )
        }
    }
}

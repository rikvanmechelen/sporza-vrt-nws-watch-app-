package be.vanmechelen.vrtnws.ui.matches

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
import be.vanmechelen.vrtnws.R
import be.vanmechelen.vrtnws.model.Match
import be.vanmechelen.vrtnws.model.MatchSports
import be.vanmechelen.vrtnws.model.MatchStatus
import be.vanmechelen.vrtnws.tile.abbreviatePlayerName
import be.vanmechelen.vrtnws.tile.localizeKickoffTime
import be.vanmechelen.vrtnws.ui.components.EmptyState
import be.vanmechelen.vrtnws.ui.components.ErrorState
import be.vanmechelen.vrtnws.ui.components.ListCard
import be.vanmechelen.vrtnws.ui.components.LiveDot
import be.vanmechelen.vrtnws.ui.components.LoadingState
import be.vanmechelen.vrtnws.ui.components.OfflineBanner
import be.vanmechelen.vrtnws.ui.components.SectionHeader

/** A row in the matches list: a section header (featured or per-sport) or a match card. */
private sealed interface MatchesEntry {
    data object FeaturedHeader : MatchesEntry
    data class SportHeader(val slug: String) : MatchesEntry
    data class MatchCard(val match: Match) : MatchesEntry
}

// Sporza-promoted "featured" matches lead under an "Uitgelicht" header (any sport, kept in the
// list's existing sport-rank order); the rest arrive already sorted voetbal-first, so we just
// insert a per-sport header whenever the sport changes.
private fun List<Match>.toEntries(): List<MatchesEntry> {
    val entries = mutableListOf<MatchesEntry>()
    val (featured, rest) = partition { it.featured }
    if (featured.isNotEmpty()) {
        entries += MatchesEntry.FeaturedHeader
        featured.forEach { entries += MatchesEntry.MatchCard(it) }
    }
    var lastSport: String? = null
    for (m in rest) {
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
        ui.isInitialLoading -> LoadingState()
        ui.showError -> ErrorState(stringResource(R.string.matches_load_error), onRetry = viewModel::refresh)
        ui.matches.isEmpty() && !ui.isRefreshing ->
            EmptyState(
                iconRes = R.drawable.ic_empty_face,
                title = stringResource(R.string.matches_empty),
                subtitle = stringResource(R.string.matches_empty_sub),
            )
        else -> {
            val entries = remember(ui.matches) { ui.matches.toEntries() }
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .rotaryScrollable(RotaryScrollableDefaults.behavior(listState), focusRequester),
                autoCentering = null,
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 48.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    SectionHeader(
                        title = stringResource(R.string.source_matches),
                        isRefreshing = ui.isRefreshing,
                        onRefresh = viewModel::refresh,
                    )
                }
                if (ui.showOfflineBanner) {
                    item { OfflineBanner() }
                }
                items(
                    items = entries,
                    key = {
                        when (it) {
                            is MatchesEntry.FeaturedHeader -> "h-featured"
                            is MatchesEntry.SportHeader -> "h-${it.slug}"
                            is MatchesEntry.MatchCard -> "m-${it.match.detailUrl}"
                        }
                    },
                ) { entry ->
                    when (entry) {
                        is MatchesEntry.FeaturedHeader ->
                            GroupHeader(stringResource(R.string.matches_featured))
                        is MatchesEntry.SportHeader -> GroupHeader(MatchSports.label(entry.slug))
                        is MatchesEntry.MatchCard ->
                            MatchCard(entry.match, onClick = { onMatchClick(entry.match) })
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.caption1.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colors.secondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, start = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun MatchCard(match: Match, onClick: () -> Unit) {
    val live = match.status == MatchStatus.LIVE
    ListCard(onClick = onClick, accent = live) {
        Column(Modifier.fillMaxWidth()) {
            if (!match.competition.isNullOrBlank()) {
                Text(
                    text = match.competition.uppercase(),
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val teamStyle = MaterialTheme.typography.title2.copy(fontSize = 18.sp)
                    if (match.home != null && match.away != null) {
                        Text(displayName(match, match.home), style = teamStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(displayName(match, match.away), style = teamStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        Text(match.title, style = teamStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(12.dp))
                ScoreOrStatus(match, live)
            }
        }
    }
}

/** Tennis player names shorten to "F. Tiafoe" so long (doubles) names don't crowd the score. */
private fun displayName(match: Match, name: String): String =
    if (match.sportSlug == "tennis") abbreviatePlayerName(name) else name

@Composable
private fun ScoreOrStatus(match: Match, live: Boolean) {
    val hasScore = match.score != null
    Column(horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val scoreColor = if (live) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface
            if (hasScore && match.subScore != null) {
                // Tennis: sets big, current-set games as a teal subscript on each side.
                Text(
                    text = tennisScore(match.score!!, match.subScore!!, MaterialTheme.colors.secondary),
                    style = MaterialTheme.typography.title1.copy(fontWeight = FontWeight.ExtraBold),
                    color = scoreColor,
                )
            } else {
                Text(
                    text = match.score ?: localizeKickoffTime(match.statusText).ifBlank { "—" },
                    style = MaterialTheme.typography.title1.copy(
                        fontWeight = if (hasScore) FontWeight.ExtraBold else FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = scoreColor,
                )
            }
            if (live) {
                Spacer(Modifier.width(7.dp))
                LiveDot(size = 9.dp)
            }
        }
        // Under the score: the sets summary (tennis) or the live minute/status.
        val caption = when {
            hasScore && match.subScore != null -> "sets ${match.score}"
            hasScore && match.statusText.isNotBlank() -> match.statusText
            else -> null
        }
        if (caption != null) {
            Spacer(Modifier.width(2.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * Builds "2₄ - 1₃": the set counts from [sets] ("2 - 1") at full size, each followed by its
 * current-set games from [games] ("4-3") as a smaller, teal subscript. Falls back to plain
 * [sets] text if either doesn't split into two sides.
 */
private fun tennisScore(sets: String, games: String, subColor: androidx.compose.ui.graphics.Color) =
    buildAnnotatedString {
        val s = sets.split(" - ")
        val g = games.split("-")
        if (s.size == 2 && g.size == 2) {
            val sub = SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 0.55.em, color = subColor)
            append(s[0]); withStyle(sub) { append(g[0].trim()) }
            append(" - ")
            append(s[1]); withStyle(sub) { append(g[1].trim()) }
        } else {
            append(sets)
        }
    }

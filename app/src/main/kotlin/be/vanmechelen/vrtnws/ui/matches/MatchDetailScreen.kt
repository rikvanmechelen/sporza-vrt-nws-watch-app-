package be.vanmechelen.vrtnws.ui.matches

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import be.vanmechelen.vrtnws.R
import be.vanmechelen.vrtnws.model.BlockType
import be.vanmechelen.vrtnws.model.ContentBlock
import be.vanmechelen.vrtnws.model.Match
import be.vanmechelen.vrtnws.model.MatchDetail
import be.vanmechelen.vrtnws.model.MatchEvent
import be.vanmechelen.vrtnws.model.MatchStatus
import be.vanmechelen.vrtnws.model.StreamItem

/**
 * Single vertical scroll: score header → quick events → "Fase per fase" stream → recap.
 * The [match] provides the always-available header (teams + score); the fetched [MatchDetail]
 * fills in the sections below.
 */
@OptIn(androidx.wear.compose.foundation.ExperimentalWearFoundationApi::class)
@Composable
fun MatchDetailScreen(
    viewModel: MatchDetailViewModel,
    match: Match,
    onOpenOnPhone: (String) -> Unit,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .rotaryScrollable(RotaryScrollableDefaults.behavior(listState), focusRequester),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 32.dp),
    ) {
        item { ScoreHeader(match) }

        when (val state = ui) {
            is MatchDetailUiState.Loading -> item { CenteredProgress() }
            is MatchDetailUiState.Failed -> item { FallbackNotice(R.string.match_load_error) }
            is MatchDetailUiState.Ready -> matchDetailSections(state.detail)
        }

        item {
            Spacer(Modifier.height(6.dp))
            Chip(
                label = { Text(stringResource(R.string.open_on_phone)) },
                onClick = { onOpenOnPhone(viewModel.matchUrl) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun androidx.wear.compose.foundation.lazy.ScalingLazyListScope.matchDetailSections(
    detail: MatchDetail,
) {
    if (detail.isEmpty) {
        item { FallbackNotice(R.string.match_load_error) }
        return
    }
    if (detail.events.isNotEmpty()) {
        item { SectionTitle(R.string.match_events) }
        items(detail.events.size) { i -> EventRow(detail.events[i]) }
    }
    if (detail.stream.isNotEmpty()) {
        item { SectionTitle(R.string.match_stream) }
        items(detail.stream.size) { i -> StreamRow(detail.stream[i]) }
    }
    if (detail.recap.isNotEmpty()) {
        item { SectionTitle(R.string.match_recap) }
        items(detail.recap.size) { i -> BlockText(detail.recap[i]) }
    }
}

@Composable
private fun ScoreHeader(match: Match) {
    val live = match.status == MatchStatus.LIVE
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!match.competition.isNullOrBlank()) {
            Text(
                text = match.competition,
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = if (match.home != null && match.away != null) "${match.home}\n${match.away}" else match.title,
            style = MaterialTheme.typography.title3,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = match.score ?: match.statusText.ifBlank { "—" },
            style = MaterialTheme.typography.display3,
            color = if (live) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
        )
        if (match.score != null && match.statusText.isNotBlank()) {
            Text(
                text = match.statusText,
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(res: Int) {
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.title3,
        color = MaterialTheme.colors.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun EventRow(event: MatchEvent) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        if (event.minute.isNotBlank()) {
            Text(
                text = event.minute,
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.width(36.dp),
            )
        }
        Text(text = event.text, style = MaterialTheme.typography.body2)
    }
}

@Composable
private fun StreamRow(item: StreamItem) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        val heading = listOfNotNull(item.time?.takeIf { it.isNotBlank() }, item.title?.takeIf { it.isNotBlank() })
            .joinToString(" · ")
        if (heading.isNotBlank()) {
            Text(
                text = heading,
                style = MaterialTheme.typography.button,
                color = MaterialTheme.colors.onSurface,
            )
        }
        if (item.text.isNotBlank()) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BlockText(block: ContentBlock) {
    when (block.type) {
        BlockType.HEADING -> Text(
            text = block.text,
            style = MaterialTheme.typography.title3,
            color = MaterialTheme.colors.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
        BlockType.QUOTE -> Text(
            text = block.text,
            style = MaterialTheme.typography.body2.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colors.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        BlockType.PARAGRAPH -> Text(
            text = block.text,
            style = MaterialTheme.typography.body2,
            modifier = Modifier.padding(vertical = 2.dp),
        )
    }
}

@Composable
private fun FallbackNotice(messageRes: Int) {
    Text(
        text = stringResource(messageRes),
        style = MaterialTheme.typography.body2,
        color = MaterialTheme.colors.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun CenteredProgress() {
    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

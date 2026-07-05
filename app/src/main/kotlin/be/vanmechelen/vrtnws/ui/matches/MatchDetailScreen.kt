package be.vanmechelen.vrtnws.ui.matches

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import be.vanmechelen.vrtnws.R
import be.vanmechelen.vrtnws.model.BlockType
import be.vanmechelen.vrtnws.model.ContentBlock
import be.vanmechelen.vrtnws.model.Match
import be.vanmechelen.vrtnws.model.MatchDetail
import be.vanmechelen.vrtnws.model.MatchEvent
import be.vanmechelen.vrtnws.model.MatchEventType
import be.vanmechelen.vrtnws.model.MatchStatus
import be.vanmechelen.vrtnws.model.StreamItem
import be.vanmechelen.vrtnws.ui.components.CardColor
import be.vanmechelen.vrtnws.ui.components.HandoffButton
import be.vanmechelen.vrtnws.ui.components.LivePill

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
        autoCentering = null,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 44.dp),
    ) {
        item { ScoreHeader(match) }

        when (val state = ui) {
            is MatchDetailUiState.Loading -> item { CenteredProgress() }
            is MatchDetailUiState.Failed -> item { FallbackNotice() }
            is MatchDetailUiState.Ready -> matchDetailSections(state.detail)
        }

        item {
            Spacer(Modifier.height(10.dp))
            HandoffButton(
                onClick = { onOpenOnPhone(viewModel.matchUrl) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun androidx.wear.compose.foundation.lazy.ScalingLazyListScope.matchDetailSections(
    detail: MatchDetail,
) {
    if (detail.isEmpty) {
        item { FallbackNotice() }
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
                text = match.competition.uppercase(),
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
        }
        val two = match.home != null && match.away != null
        if (two) {
            Text(match.home!!, style = MaterialTheme.typography.title2, textAlign = TextAlign.Center)
        }
        Text(
            text = match.score ?: match.statusText.ifBlank { "—" },
            style = MaterialTheme.typography.display1,
            color = if (live) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
            modifier = Modifier.padding(vertical = 2.dp),
        )
        if (two) {
            Text(match.away!!, style = MaterialTheme.typography.title2, textAlign = TextAlign.Center)
        } else if (match.score != null) {
            Text(match.title, style = MaterialTheme.typography.title3, textAlign = TextAlign.Center)
        }
        if (live) {
            Spacer(Modifier.height(10.dp))
            LivePill(text = liveLabel(match.statusText))
        } else if (match.score != null && match.statusText.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = match.statusText,
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
        }
    }
}

private fun liveLabel(statusText: String): String =
    if (statusText.isBlank()) "LIVE" else "LIVE · $statusText"

@Composable
private fun SectionTitle(res: Int) {
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.title3,
        color = MaterialTheme.colors.secondary,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun EventRow(event: MatchEvent) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardColor)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (event.minute.isNotBlank()) {
            Text(
                text = event.minute,
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.onSurfaceVariant,
                modifier = Modifier.width(32.dp),
            )
        }
        Text(text = eventIcon(event.type), style = MaterialTheme.typography.body1)
        Spacer(Modifier.width(10.dp))
        Text(
            text = event.text,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface,
        )
    }
}

/** A glyph for the event kind, so the timeline reads at a glance. */
private fun eventIcon(type: MatchEventType): String = when (type) {
    MatchEventType.GOAL -> "⚽"
    MatchEventType.OWN_GOAL -> "🥅"
    MatchEventType.SUBSTITUTION -> "🔁"
    MatchEventType.YELLOW_CARD -> "🟨"
    MatchEventType.RED_CARD -> "🟥"
    MatchEventType.OTHER -> "•"
}

@Composable
private fun StreamRow(item: StreamItem) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
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
            style = MaterialTheme.typography.title2,
            color = MaterialTheme.colors.secondary,
            modifier = Modifier.padding(top = 10.dp),
        )
        BlockType.QUOTE -> Text(
            text = block.text,
            style = MaterialTheme.typography.body1.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colors.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        BlockType.PARAGRAPH -> Text(
            text = block.text,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.88f),
            modifier = Modifier.padding(vertical = 3.dp),
        )
    }
}

@Composable
private fun FallbackNotice() {
    Text(
        text = stringResource(R.string.match_load_error),
        style = MaterialTheme.typography.body1,
        color = MaterialTheme.colors.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun CenteredProgress() {
    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(indicatorColor = MaterialTheme.colors.primary)
    }
}

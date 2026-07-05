package be.vanmechelen.vrtnws.ui.matches

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import be.vanmechelen.vrtnws.tile.sportEmoji
import be.vanmechelen.vrtnws.ui.components.CardColor
import be.vanmechelen.vrtnws.ui.components.HandoffButton
import be.vanmechelen.vrtnws.ui.components.LivePill

/** Horizontal inset so section text clears the round screen's curve (matches the article reader). */
private val BodyPadding = 20.dp

/**
 * Lead-hero match detail, mirroring the article reader: a full-bleed hero fills the top arc, then a
 * single vertical scroll of sections below (quick events → "Fase per fase" stream → recap).
 *
 * Matches never carry a photo, so the hero is always the "replacement graphic" — a section-tinted
 * diagonal-stripe field (green, since this screen is themed [Section.SPORT]) with the sport emoji as
 * a large watermark, under a scrim. The scoreboard (competition kicker + teams + score + live pill)
 * rides the lower part of the hero, flowing downward like an article title — so a long name or a
 * tennis set-score simply continues onto the black below rather than clipping into the top arc.
 *
 * Like the article reader this is a plain [Column] + [verticalScroll] + [rotaryScrollable] (NOT a
 * ScalingLazyColumn, which scales the first item and would break the full-bleed hero), with the
 * same `graphicsLayer(Offscreen)` + `BlendMode.DstIn` gradient fading both scroll edges. The [match]
 * provides the always-available hero; the fetched [MatchDetail] fills the sections below.
 */
@OptIn(androidx.wear.compose.foundation.ExperimentalWearFoundationApi::class)
@Composable
fun MatchDetailScreen(
    viewModel: MatchDetailViewModel,
    match: Match,
    onOpenOnPhone: (String) -> Unit,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    // The scrollable Column lives inside BoxWithConstraints (a SubcomposeLayout), so the rotary
    // focus node isn't attached on the first frame and a lone entry request is lost (dead crown).
    // Key on `ui` so it re-fires when the detail settles (Loading→Ready/Failed), a recomposition
    // that lands after layout when the node exists — same fix the article reader uses.
    LaunchedEffect(ui) { runCatching { focusRequester.requestFocus() } }

    val topFade by remember {
        derivedStateOf { (scrollState.value / 80f).coerceIn(0f, 1f) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        val leadHeight = maxHeight * 0.52f
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 1f - topFade),
                            0.10f to Color.Black,
                            0.87f to Color.Black,
                            1.0f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .verticalScroll(scrollState)
                .rotaryScrollable(RotaryScrollableDefaults.behavior(scrollState), focusRequester),
        ) {
            MatchLeadHeader(match = match, height = leadHeight)
            Spacer(Modifier.height(6.dp))

            when (val state = ui) {
                is MatchDetailUiState.Loading -> CenteredProgress()
                is MatchDetailUiState.Failed -> FallbackNotice()
                is MatchDetailUiState.Ready -> MatchSections(state.detail)
            }

            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().padding(horizontal = BodyPadding), Alignment.Center) {
                HandoffButton(
                    onClick = { onOpenOnPhone(viewModel.matchUrl) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(44.dp))
        }
    }
}

@Composable
private fun MatchSections(detail: MatchDetail) {
    if (detail.isEmpty) {
        FallbackNotice()
        return
    }
    if (detail.events.isNotEmpty()) {
        SectionTitle(R.string.match_events)
        detail.events.forEach { EventRow(it) }
    }
    if (detail.stream.isNotEmpty()) {
        SectionTitle(R.string.match_stream)
        detail.stream.forEach { StreamRow(it) }
    }
    if (detail.recap.isNotEmpty()) {
        SectionTitle(R.string.match_recap)
        detail.recap.forEach { BlockText(it) }
    }
}

/**
 * The lead slot: a sport-tinted stripe field with the sport emoji watermark bleeding to the top arc
 * under a scrim, with the competition kicker + scoreboard riding the lower part and flowing down.
 */
@Composable
private fun MatchLeadHeader(match: Match, height: Dp) {
    val live = match.status == MatchStatus.LIVE
    val accent = MaterialTheme.colors.secondary
    Box(Modifier.fillMaxWidth()) {
        // Replacement graphic + scrim: fixed height, pinned to the top arc.
        Box(Modifier.fillMaxWidth().height(height)) {
            Box(Modifier.fillMaxSize().background(stripeBrush()))
            // Sport emoji watermark, sitting in the upper third above the scoreboard.
            Text(
                text = sportEmoji(match.sportSlug),
                fontSize = 66.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = height * 0.10f)
                    .graphicsLayer { alpha = 0.22f },
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.20f to Color.Transparent,
                            0.55f to Color.Black.copy(alpha = 0.55f),
                            1.0f to Color.Black,
                        ),
                    ),
            )
        }
        // Kicker + scoreboard, anchored into the hero's lower part and flowing down.
        Column(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = height * 0.34f)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            matchKicker(match)?.let { kicker ->
                Text(
                    text = kicker.uppercase(),
                    style = MaterialTheme.typography.caption3,
                    color = accent,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            val two = match.home != null && match.away != null
            val titleShadow = Shadow(
                color = Color.Black.copy(alpha = 0.7f),
                offset = Offset(0f, 2f),
                blurRadius = 14f,
            )
            if (two) {
                Text(
                    text = match.home!!,
                    style = MaterialTheme.typography.title2.copy(shadow = titleShadow),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = match.score ?: match.statusText.ifBlank { "—" },
                style = MaterialTheme.typography.display1.copy(shadow = titleShadow),
                color = if (live) MaterialTheme.colors.primary else Color.White,
                modifier = Modifier.padding(vertical = 2.dp),
            )
            if (two) {
                Text(
                    text = match.away!!,
                    style = MaterialTheme.typography.title2.copy(shadow = titleShadow),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            } else if (match.score != null) {
                Text(
                    text = match.title,
                    style = MaterialTheme.typography.title3.copy(shadow = titleShadow),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
            if (live) {
                Spacer(Modifier.height(10.dp))
                LivePill(text = liveLabel(match.statusText))
            } else if (match.score != null && match.statusText.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = match.statusText,
                    style = MaterialTheme.typography.caption1,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

/** Diagonal-stripe replacement graphic, tinted to the section accent (green on this SPORT screen). */
@Composable
private fun stripeBrush(): Brush {
    val base = MaterialTheme.colors.primaryVariant
    val a = base.copy(alpha = 0.55f).compositeOver(MaterialTheme.colors.background)
    val b = base.copy(alpha = 0.32f).compositeOver(MaterialTheme.colors.background)
    return Brush.linearGradient(
        0.00f to a, 0.25f to a, 0.25f to b, 0.50f to b,
        0.50f to a, 0.75f to a, 0.75f to b, 1.00f to b,
        start = Offset(0f, 0f),
        end = Offset(44f, 44f),
        tileMode = androidx.compose.ui.graphics.TileMode.Repeated,
    )
}

private fun liveLabel(statusText: String): String =
    if (statusText.isBlank()) "LIVE" else "LIVE · $statusText"

@Composable
private fun SectionTitle(res: Int) {
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.title3,
        color = MaterialTheme.colors.secondary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = BodyPadding).padding(top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun EventRow(event: MatchEvent) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = BodyPadding)
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
    Column(Modifier.fillMaxWidth().padding(horizontal = BodyPadding).padding(vertical = 5.dp)) {
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = BodyPadding).padding(top = 10.dp),
        )
        BlockType.QUOTE -> Text(
            text = block.text,
            style = MaterialTheme.typography.body1.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colors.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = BodyPadding).padding(vertical = 4.dp),
        )
        BlockType.PARAGRAPH -> Text(
            text = block.text,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.88f),
            modifier = Modifier.fillMaxWidth().padding(horizontal = BodyPadding).padding(vertical = 3.dp),
        )
    }
}

@Composable
private fun FallbackNotice() {
    Text(
        text = stringResource(R.string.match_load_error),
        style = MaterialTheme.typography.body1,
        color = MaterialTheme.colors.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = BodyPadding).padding(vertical = 8.dp),
    )
}

@Composable
private fun CenteredProgress() {
    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(indicatorColor = MaterialTheme.colors.primary)
    }
}

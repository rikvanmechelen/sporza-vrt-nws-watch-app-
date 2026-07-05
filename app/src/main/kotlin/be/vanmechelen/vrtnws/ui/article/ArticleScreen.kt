package be.vanmechelen.vrtnws.ui.article

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import be.vanmechelen.vrtnws.R
import be.vanmechelen.vrtnws.model.Article
import be.vanmechelen.vrtnws.model.BlockType
import be.vanmechelen.vrtnws.model.ContentBlock
import be.vanmechelen.vrtnws.ui.components.HandoffButton
import be.vanmechelen.vrtnws.ui.theme.Section
import coil.compose.AsyncImage

/** Horizontal inset so body text clears the round screen's curve. */
private val BodyPadding = 20.dp

/**
 * Lead-image-first reader. When the article has a photo it bleeds to the top arc under a scrim,
 * with the kicker (category) + title riding the bottom of the image — so the title is always the
 * first legible thing under the curve. No photo → a section-tinted diagonal-stripe placeholder
 * fills the same slot, keeping the layout stable between image and no-image articles. Below the
 * lead, one scroll: meta (source mark + relative time) → body → pullquote → "open on phone".
 * Both scroll edges fade; the top fades in only once the lead has scrolled up.
 */
@OptIn(androidx.wear.compose.foundation.ExperimentalWearFoundationApi::class)
@Composable
fun ArticleScreen(
    viewModel: ArticleViewModel,
    article: Article,
    section: Section,
    onOpenOnPhone: (String) -> Unit,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    // Top fade ramps in over the first ~40dp of scroll, so the lead image sits flush to the arc
    // at rest but content fades under the curve once you start reading.
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
            LeadHeader(article = article, section = section, height = leadHeight)
            Spacer(Modifier.height(16.dp))
            MetaRow(section = section, publishedEpochMs = article.publishedEpochMs)
            Spacer(Modifier.height(12.dp))

            when (val state = ui) {
                is ArticleUiState.Loading -> CenteredProgress()
                is ArticleUiState.Failed -> FallbackNotice()
                is ArticleUiState.Ready ->
                    if (state.content.isEmpty) FallbackNotice()
                    else state.content.blocks.forEach { BlockText(it) }
            }

            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().padding(horizontal = BodyPadding), Alignment.Center) {
                HandoffButton(
                    onClick = { onOpenOnPhone(viewModel.articleUrl) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(44.dp))
        }
    }
}

/**
 * The lead slot: photo (or section-tinted diagonal stripes when there's none) bleeding to the top
 * arc under a scrim, with the kicker + title starting in the image's lower third.
 *
 * The image is a fixed-height layer pinned to the top; the kicker + title flow *downward* from
 * ~40% down the image. A short title rides the bottom of the image; a long one is shown in full
 * and simply continues past the image onto black below — so the title never grows up into the
 * narrow top arc, and is never clipped.
 */
@Composable
private fun LeadHeader(article: Article, section: Section, height: androidx.compose.ui.unit.Dp) {
    val accent = MaterialTheme.colors.secondary
    // Wraps its height to the taller of the image or the (possibly overflowing) title block.
    Box(Modifier.fillMaxWidth()) {
        // Image + scrim: fixed height, pinned to the top arc.
        Box(Modifier.fillMaxWidth().height(height)) {
            if (!article.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(stripeBrush(section)))
            }
            // Scrim so the kicker + title stay legible over any photo; fades to solid black at the
            // image bottom, which blends seamlessly into the black behind an overflowing title.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.28f to Color.Transparent,
                            0.60f to Color.Black.copy(alpha = 0.62f),
                            1.0f to Color.Black,
                        ),
                    ),
            )
        }
        // Kicker + title, anchored into the image's lower third and flowing down.
        Column(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = height * 0.42f)
                .padding(start = 30.dp, end = 30.dp),
        ) {
            val kicker = kickerLabel(article.category)
            if (kicker != null) {
                Text(
                    text = kicker.uppercase(),
                    style = MaterialTheme.typography.caption3,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Text(
                text = article.title,
                style = MaterialTheme.typography.title1.copy(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.7f),
                        offset = Offset(0f, 2f),
                        blurRadius = 14f,
                    ),
                ),
                color = Color.White,
            )
        }
    }
}

/** Diagonal-stripe placeholder for image-less articles, tinted to the section accent. */
@Composable
private fun stripeBrush(section: Section): Brush {
    // Two close shades of the section's deep accent, so the stripes read as texture, not noise.
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

/** Source mark (rounded badge with the brand initial) + brand name · relative publish time. */
@Composable
private fun MetaRow(section: Section, publishedEpochMs: Long) {
    val source = remember(section) { readerSourceFor(section) }
    val time = remember(publishedEpochMs) {
        DateUtils.getRelativeTimeSpanString(publishedEpochMs).toString()
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = BodyPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(18.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colors.primaryVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = source.mark,
                style = MaterialTheme.typography.caption2.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                ),
                color = MaterialTheme.colors.secondary,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${source.label} · $time",
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun BlockText(block: ContentBlock) {
    when (block.type) {
        BlockType.HEADING -> Text(
            text = block.text,
            style = MaterialTheme.typography.title2,
            color = MaterialTheme.colors.primary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = BodyPadding).padding(top = 12.dp, bottom = 2.dp),
        )
        BlockType.QUOTE -> Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = BodyPadding)
                .height(IntrinsicSize.Min)
                .padding(vertical = 8.dp),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colors.primary),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = block.text,
                style = MaterialTheme.typography.body1.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colors.onSurface,
            )
        }
        BlockType.PARAGRAPH -> Text(
            text = block.text,
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.88f),
            modifier = Modifier.fillMaxWidth().padding(horizontal = BodyPadding).padding(vertical = 5.dp),
        )
    }
}

@Composable
private fun FallbackNotice() {
    Text(
        text = stringResource(R.string.article_load_error),
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

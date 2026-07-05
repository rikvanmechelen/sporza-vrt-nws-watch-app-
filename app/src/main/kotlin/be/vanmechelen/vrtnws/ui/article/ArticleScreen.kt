package be.vanmechelen.vrtnws.ui.article

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
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
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.compose.ui.res.stringResource
import be.vanmechelen.vrtnws.R
import be.vanmechelen.vrtnws.model.BlockType
import be.vanmechelen.vrtnws.model.ContentBlock
import be.vanmechelen.vrtnws.ui.components.HandoffButton

@OptIn(androidx.wear.compose.foundation.ExperimentalWearFoundationApi::class)
@Composable
fun ArticleScreen(
    viewModel: ArticleViewModel,
    title: String,
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
        // Inset content so body text doesn't run into the round screen's curved edges.
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 40.dp, bottom = 44.dp),
    ) {
        item {
            Text(
                text = title,
                style = MaterialTheme.typography.title1,
                color = MaterialTheme.colors.onSurface,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            )
        }
        when (val state = ui) {
            is ArticleUiState.Loading -> item { CenteredProgress() }

            is ArticleUiState.Failed -> item { FallbackNotice() }

            is ArticleUiState.Ready -> {
                if (state.content.isEmpty) {
                    item { FallbackNotice() }
                } else {
                    items(state.content.blocks) { block -> BlockText(block) }
                }
            }
        }
        item {
            Spacer(Modifier.height(10.dp))
            HandoffButton(
                onClick = { onOpenOnPhone(viewModel.articleUrl) },
                modifier = Modifier.fillMaxWidth(),
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
            color = MaterialTheme.colors.primary,
            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
        )
        BlockType.QUOTE -> Row(
            Modifier.height(IntrinsicSize.Min).padding(vertical = 8.dp),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colors.primary),
            )
            Spacer(Modifier.width(12.dp))
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
            modifier = Modifier.padding(vertical = 5.dp),
        )
    }
}

@Composable
private fun FallbackNotice() {
    Text(
        text = stringResource(R.string.article_load_error),
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

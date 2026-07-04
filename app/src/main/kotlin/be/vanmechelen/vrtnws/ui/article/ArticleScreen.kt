package be.vanmechelen.vrtnws.ui.article

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.compose.ui.res.stringResource
import be.vanmechelen.vrtnws.R
import be.vanmechelen.vrtnws.model.BlockType
import be.vanmechelen.vrtnws.model.ContentBlock

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
            .fillMaxSize()
            .rotaryScrollable(RotaryScrollableDefaults.behavior(listState), focusRequester),
        // Inset content so body text doesn't run into the round screen's curved edges.
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 32.dp),
    ) {
        item {
            Text(
                text = title,
                style = MaterialTheme.typography.title3,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        when (val state = ui) {
            is ArticleUiState.Loading -> item { CenteredProgress() }

            is ArticleUiState.Failed -> item {
                FallbackNotice(R.string.article_load_error)
            }

            is ArticleUiState.Ready -> {
                if (state.content.isEmpty) {
                    item { FallbackNotice(R.string.article_load_error) }
                } else {
                    items(state.content.blocks.size) { i ->
                        BlockText(state.content.blocks[i])
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            Chip(
                label = { Text(stringResource(R.string.open_on_phone)) },
                onClick = { onOpenOnPhone(viewModel.articleUrl) },
                colors = ChipDefaults.secondaryChipColors(),
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

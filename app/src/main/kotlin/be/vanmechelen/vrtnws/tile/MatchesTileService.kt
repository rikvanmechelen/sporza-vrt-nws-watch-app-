package be.vanmechelen.vrtnws.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import be.vanmechelen.vrtnws.R
import be.vanmechelen.vrtnws.VrtNwsApp
import be.vanmechelen.vrtnws.model.Match
import be.vanmechelen.vrtnws.ui.MainActivity
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import java.util.concurrent.TimeUnit

private const val RES_VERSION = "1"
private const val LIVE_RED = 0xFFE53935.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
private const val DIM = 0xFFB0B0B0.toInt()

/**
 * A Tile showing live Sporza match scores (up to 3, voetbal-first), or the next upcoming match
 * when nothing is live. Tapping opens the app directly on the Matches tab.
 *
 * Unlike the headline tile, matches are in-memory only (see [DefaultMatchesRepository]), so this
 * refreshes over the network on each tile request before reading the calendar.
 */
class MatchesTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> = scope.future {
        val matches = runCatching {
            val repo = VrtNwsApp.instance.graph.matchesRepository
            repo.refresh()
            repo.matches().first()
        }.getOrDefault(emptyList())
        buildTile(matchesTileModel(matches))
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> = scope.future {
        ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build()
    }

    private fun buildTile(model: MatchesTileModel): TileBuilders.Tile {
        val root = if (model.rows.isEmpty()) {
            emptyLayout()
        } else {
            contentLayout(model)
        }

        val entry = TimelineBuilders.TimelineEntry.Builder()
            .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(root).build())
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.Builder().addTimelineEntry(entry).build())
            .setFreshnessIntervalMillis(TimeUnit.MINUTES.toMillis(1))
            .build()
    }

    /** Whole-tile tap → open the app on the Matches tab. */
    private fun openMatchesModifiers(): ModifiersBuilders.Modifiers {
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId("open_matches")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setClassName(MainActivity::class.java.name)
                            .setPackageName(packageName)
                            .addKeyToExtraMapping(
                                MainActivity.EXTRA_TAB,
                                ActionBuilders.stringExtra(MainActivity.TAB_MATCHES),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()
        return ModifiersBuilders.Modifiers.Builder()
            .setClickable(clickable)
            .setPadding(ModifiersBuilders.Padding.Builder().setAll(dp(10f)).build())
            .build()
    }

    private fun emptyLayout(): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(openMatchesModifiers())
            .addContent(
                Text.Builder(this, getString(R.string.tile_matches_empty))
                    .setTypography(Typography.TYPOGRAPHY_BODY2)
                    .setColor(argb(WHITE))
                    .setMaxLines(2)
                    .build(),
            )
            .build()

    private fun contentLayout(model: MatchesTileModel): LayoutElementBuilders.LayoutElement {
        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

        model.rows.forEach { column.addContent(matchRow(it, model.isLive)) }

        val footer = when {
            model.moreLiveCount > 0 -> getString(R.string.tile_matches_more, model.moreLiveCount)
            else -> null
        }
        if (footer != null) {
            column.addContent(
                Text.Builder(this, footer)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(argb(DIM))
                    .setMaxLines(1)
                    .build(),
            )
        }

        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(openMatchesModifiers())
            .addContent(column.build())
            .build()
    }

    /** One compact score/status line, with a red dot when live. */
    private fun matchRow(match: Match, isLive: Boolean): LayoutElementBuilders.LayoutElement {
        val row = LayoutElementBuilders.Row.Builder()
            .setWidth(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setTop(dp(2f)).setBottom(dp(2f)).build(),
                    )
                    .build(),
            )

        if (isLive) {
            row.addContent(liveDot())
            row.addContent(spacer(4f))
        }
        row.addContent(
            Text.Builder(this, matchRowLabel(match, isLive))
                .setTypography(Typography.TYPOGRAPHY_BODY2)
                .setColor(argb(WHITE))
                .setMaxLines(1)
                .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
                .build(),
        )
        return row.build()
    }

    private fun liveDot(): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(dp(6f))
            .setHeight(dp(6f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(LIVE_RED))
                            .setCorner(
                                ModifiersBuilders.Corner.Builder().setRadius(dp(3f)).build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()

    private fun spacer(width: Float): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Spacer.Builder().setWidth(dp(width)).build()
}

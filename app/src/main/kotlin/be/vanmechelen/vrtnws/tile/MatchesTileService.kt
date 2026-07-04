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
private const val WHITE = 0xFFFFFFFF.toInt()
private const val DIM = 0xFFB0B0B0.toInt()
private const val YELLOW = 0xFFFFD200.toInt() // VRT yellow — the score accent

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

    /**
     * A structured scoreboard row: sport emoji · home (right-aligned) · the accented score/status ·
     * away (left-aligned). Names ellipsize; the score is the visual hero (bold, VRT yellow when
     * live). Sports without two sides (e.g. cycling) collapse to emoji · title · status.
     */
    private fun matchRow(match: Match, isLive: Boolean): LayoutElementBuilders.LayoutElement {
        val row = LayoutElementBuilders.Row.Builder()
            .setWidth(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setTop(dp(3f)).setBottom(dp(3f)).build(),
                    )
                    .build(),
            )

        row.addContent(
            Text.Builder(this, sportEmoji(match.sportSlug))
                .setTypography(Typography.TYPOGRAPHY_BODY2)
                .setColor(argb(WHITE))
                .setMaxLines(1)
                .build(),
        )
        row.addContent(spacer(5f))

        val score = scoreCell(matchMidText(match, isLive), match.subScore, if (isLive) YELLOW else WHITE)
        if (match.home != null || match.away != null) {
            row.addContent(nameCell(match.home.orEmpty(), LayoutElementBuilders.HORIZONTAL_ALIGN_END))
            row.addContent(spacer(6f))
            row.addContent(score)
            row.addContent(spacer(6f))
            row.addContent(nameCell(match.away.orEmpty(), LayoutElementBuilders.HORIZONTAL_ALIGN_START))
        } else {
            row.addContent(nameCell(match.title, LayoutElementBuilders.HORIZONTAL_ALIGN_START))
            row.addContent(spacer(6f))
            row.addContent(score)
        }
        return row.build()
    }

    /** An expanding, single-line, ellipsized team/player name aligned to one edge. */
    private fun nameCell(name: String, align: Int): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(align)
            .addContent(
                Text.Builder(this, name)
                    .setTypography(Typography.TYPOGRAPHY_BODY2)
                    .setColor(argb(DIM))
                    .setMaxLines(1)
                    .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
                    .build(),
            )
            .build()

    /**
     * The hero score. [sub] (the current-set games in tennis) rides low next to the main score
     * like a subscript — the enclosing Row is bottom-aligned so the smaller text sits on the
     * main score's baseline.
     */
    private fun scoreCell(main: String, sub: String?, color: Int): LayoutElementBuilders.LayoutElement {
        val mainText = Text.Builder(this, main)
            .setTypography(Typography.TYPOGRAPHY_TITLE3)
            .setColor(argb(color))
            .setMaxLines(1)
            .build()
        if (sub.isNullOrBlank()) return mainText
        return LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM)
            .addContent(mainText)
            .addContent(spacer(2f))
            .addContent(
                Text.Builder(this, sub)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(argb(DIM))
                    .setMaxLines(1)
                    .build(),
            )
            .build()
    }

    private fun spacer(width: Float): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Spacer.Builder().setWidth(dp(width)).build()
}

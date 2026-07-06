package be.vanmechelen.vrtnws.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.FONT_WEIGHT_BOLD
import androidx.wear.protolayout.LayoutElementBuilders.FONT_WEIGHT_MEDIUM
import androidx.wear.protolayout.LayoutElementBuilders.FONT_WEIGHT_NORMAL
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
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

private const val RES_VERSION = "2"
private const val RES_REFRESH = "ic_refresh"

// Redesign palette (mirrors the app's section colours; ProtoLayout uses the system font).
private const val NAME = 0xFFE6E7EA.toInt() // team / player names
private const val DIM = 0xFFA6AAB2.toInt() // meta, upcoming score
private const val GREEN = 0xFF2FE07A.toInt() // Sporza — live score hero
private const val TEAL = 0xFF8FE9BC.toInt() // section accent — header, games subscript, "+N meer"
private const val CORAL = 0xFFFF5147.toInt() // live indicator dot
private const val CARD = 0xFF111A16.toInt() // green-tinted scoreboard row background

// Tile scale 0.75 — 0.75× the design-px sizes, to sit a touch smaller than the app's 1.0 screens
// while staying consistent. (Design px × 0.75: score 24→18, name 16→12, labels 13→10, emoji 17→13.)
private const val SZ_HEADER = 10f
private const val SZ_NAME = 12f
private const val SZ_SCORE = 18f
private const val SZ_SUB = 10f
private const val SZ_FOOTER = 10f
private const val SZ_EMOJI = 13f

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
        ResourceBuilders.Resources.Builder()
            .setVersion(RES_VERSION)
            .addIdToImageMapping(
                RES_REFRESH,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(R.drawable.ic_refresh)
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

    private fun buildTile(model: MatchesTileModel): TileBuilders.Tile {
        val root = if (model.rows.isEmpty()) emptyLayout() else contentLayout(model)

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
            .setPadding(
                ModifiersBuilders.Padding.Builder()
                    .setStart(dp(18f)).setEnd(dp(18f)).setTop(dp(8f)).setBottom(dp(8f)).build(),
            )
            .build()
    }

    /**
     * A small refresh affordance. Tapping it fires a [LoadAction][ActionBuilders.LoadAction],
     * which re-triggers [onTileRequest] — and that already calls `repo.refresh()` before reading
     * the calendar, so the latest scores are refetched in place without leaving the tile. It's a
     * nested clickable inside the whole-tile "open app" box, so a tap here wins in its own bounds.
     */
    private fun refreshButton(box: Float = 32f, icon: Float = 16f): LayoutElementBuilders.LayoutElement {
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId("refresh_matches")
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()
        return LayoutElementBuilders.Box.Builder()
            .setWidth(dp(box))
            .setHeight(dp(box))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(CARD))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(box / 2f)).build())
                            .build(),
                    )
                    .build(),
            )
            .addContent(
                LayoutElementBuilders.Image.Builder()
                    .setResourceId(RES_REFRESH)
                    .setWidth(dp(icon))
                    .setHeight(dp(icon))
                    .setColorFilter(
                        LayoutElementBuilders.ColorFilter.Builder().setTint(argb(TEAL)).build(),
                    )
                    .build(),
            )
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
                LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(refreshButton())
                    .addContent(vSpacer(12f))
                    .addContent(text(getString(R.string.tile_matches_empty), SZ_NAME, NAME, maxLines = 2))
                    .build(),
            )
            .build()

    private fun contentLayout(model: MatchesTileModel): LayoutElementBuilders.LayoutElement {
        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

        // Refresh sits at the top in both states: when live it rides the "Live nu" header row;
        // the upcoming fallback has no header, so it gets the standalone button up top too (rather
        // than trailing at the bottom) — keeping the affordance in a consistent place.
        if (model.isLive) {
            column.addContent(liveHeader())
        } else {
            column.addContent(refreshButton())
        }
        column.addContent(vSpacer(12f))

        model.rows.forEachIndexed { i, match ->
            if (i > 0) column.addContent(vSpacer(8f))
            column.addContent(matchRow(match, model.isLive))
        }

        if (model.moreLiveCount > 0) {
            column.addContent(vSpacer(8f))
            column.addContent(
                text(getString(R.string.tile_matches_more, model.moreLiveCount), SZ_FOOTER, TEAL, FONT_WEIGHT_BOLD),
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

    /** "● Live nu ⟳" — a coral dot next to a teal, glanceable header, with refresh alongside. */
    private fun liveHeader(): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(dot(CORAL, 6f))
            .addContent(spacer(6f))
            .addContent(text(getString(R.string.tile_live_now), SZ_HEADER, TEAL, FONT_WEIGHT_BOLD))
            .addContent(spacer(8f))
            .addContent(refreshButton(box = 26f, icon = 14f))
            .build()

    /**
     * A structured scoreboard row inside a green-tinted rounded card: sport emoji · home
     * (right-aligned) · the score (centre hero, green when live) · away (left-aligned). Names
     * ellipsize; tennis names shorten to surnames so doubles never push the score off.
     */
    private fun matchRow(match: Match, isLive: Boolean): LayoutElementBuilders.LayoutElement {
        val row = LayoutElementBuilders.Row.Builder()
            .setWidth(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(CARD))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(14f)).build())
                            .build(),
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(dp(11f)).setEnd(dp(11f)).setTop(dp(8f)).setBottom(dp(8f))
                            .build(),
                    )
                    .build(),
            )

        row.addContent(text(sportEmoji(match.sportSlug), SZ_EMOJI, NAME))
        row.addContent(spacer(7f))

        val score = scoreCell(localizeKickoffTime(matchMidText(match, isLive)), match.subScore, if (isLive) GREEN else DIM)
        if (match.home != null || match.away != null) {
            row.addContent(nameCell(displayName(match, match.home.orEmpty()), LayoutElementBuilders.HORIZONTAL_ALIGN_END))
            row.addContent(spacer(7f))
            row.addContent(score)
            row.addContent(spacer(7f))
            row.addContent(nameCell(displayName(match, match.away.orEmpty()), LayoutElementBuilders.HORIZONTAL_ALIGN_START))
        } else {
            row.addContent(nameCell(match.title, LayoutElementBuilders.HORIZONTAL_ALIGN_START))
            row.addContent(spacer(7f))
            row.addContent(score)
        }
        return row.build()
    }

    /** Tennis names shorten to a bare surname on the tile to survive doubles in a narrow row. */
    private fun displayName(match: Match, name: String): String =
        if (match.sportSlug == "tennis") abbreviatePlayerName(name, surnameOnly = true) else name

    /** An expanding, single-line, ellipsized team/player name aligned to one edge. */
    private fun nameCell(name: String, align: Int): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(align)
            .addContent(text(name, SZ_NAME, NAME, FONT_WEIGHT_MEDIUM, maxLines = 1, ellipsize = true))
            .build()

    /**
     * The hero score. For tennis the current-set games ride as a subscript on each side's set
     * count — "2₄-1₄" (sets 2-1, games 4-4) — by splitting [main] ("2 - 1") and [sub] ("4-4") into
     * their two sides. Football (no [sub]) is just the plain score. The enclosing Row is
     * bottom-aligned so the smaller games sit on the set count's baseline.
     */
    private fun scoreCell(main: String, sub: String?, color: Int): LayoutElementBuilders.LayoutElement {
        val sets = main.split(" - ")
        val games = sub?.split("-")
        if (sub != null && sets.size == 2 && games != null && games.size == 2) {
            return LayoutElementBuilders.Row.Builder()
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM)
                .addContent(setCount(sets[0], color))
                .addContent(gameSub(games[0].trim()))
                .addContent(setCount("-", color))
                .addContent(setCount(sets[1], color))
                .addContent(gameSub(games[1].trim()))
                .build()
        }
        return setCount(main, color)
    }

    private fun setCount(s: String, color: Int): LayoutElementBuilders.LayoutElement =
        text(s, SZ_SCORE, color, FONT_WEIGHT_BOLD)

    private fun gameSub(s: String): LayoutElementBuilders.LayoutElement =
        text(s, SZ_SUB, TEAL, FONT_WEIGHT_BOLD)

    /** Low-level text with an explicit size (the material Text presets don't go small enough). */
    private fun text(
        s: String,
        sizeSp: Float,
        color: Int,
        weight: Int = FONT_WEIGHT_NORMAL,
        maxLines: Int = 1,
        ellipsize: Boolean = false,
    ): LayoutElementBuilders.LayoutElement {
        val fontStyle = LayoutElementBuilders.FontStyle.Builder()
            .setSize(sp(sizeSp))
            .setWeight(weight)
            .setColor(argb(color))
            .build()
        val b = LayoutElementBuilders.Text.Builder()
            .setText(s)
            .setFontStyle(fontStyle)
            .setMaxLines(maxLines)
        if (ellipsize) {
            b.setOverflow(
                LayoutElementBuilders.TextOverflowProp.Builder()
                    .setValue(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
                    .build(),
            )
        }
        return b.build()
    }

    /** A solid dot (used for the live indicator) — a fully-rounded coloured box. */
    private fun dot(color: Int, size: Float): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(dp(size))
            .setHeight(dp(size))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(color))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(size / 2f)).build())
                            .build(),
                    )
                    .build(),
            )
            .build()

    private fun spacer(width: Float): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Spacer.Builder().setWidth(dp(width)).build()

    private fun vSpacer(height: Float): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Spacer.Builder().setHeight(dp(height)).build()
}

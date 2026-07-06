package me.vanmechelen.vrtsporza.tile

import android.text.format.DateUtils
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.FONT_WEIGHT_NORMAL
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import me.vanmechelen.vrtsporza.R
import me.vanmechelen.vrtsporza.VrtNwsApp
import me.vanmechelen.vrtsporza.freshness.syncedClock
import me.vanmechelen.vrtsporza.model.NewsSource
import me.vanmechelen.vrtsporza.ui.MainActivity
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import java.util.concurrent.TimeUnit

private const val RES_VERSION = "1"

// News (VRT NWS) purple identity — mirrors the app.
private const val LAVENDER = 0xFFA99CFF.toInt() // kicker
private const val VIOLET = 0xFF302070.toInt() // badge fill
private const val BADGE_TEXT = 0xFFC7C0FF.toInt()
private const val HEADLINE = 0xFFFFFFFF.toInt()
private const val META = 0xFFA6AAB2.toInt()

// Freshness marker: news-lavender dot at 55% + a dim neutral label (mirrors the app's #6E7078).
private const val SYNC_DOT = 0x8CA99CFF.toInt() // lavender @ 55% alpha
private const val SYNC_LABEL = 0xFF9A9DA4.toInt() // dim neutral (matches VrtAccents.FreshnessLabel)

/** A Tile showing the latest cached headline; tapping opens the app. */
class LatestHeadlineTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> = scope.future {
        val repo = VrtNwsApp.instance.graph.repository
        val headline = runCatching { repo.latestHeadline() }.getOrNull()
        val title = headline?.title ?: getString(R.string.empty_headlines)
        // Meta line mirrors the design: "1 uur geleden · tik om te openen" (age from the article).
        val meta = headline?.let {
            val age = DateUtils.getRelativeTimeSpanString(it.publishedEpochMs).toString()
            "$age · ${getString(R.string.tile_tap_to_open)}"
        }
        // Freshness = when the NEWS feed was last synced (persisted in Room). Shown as an absolute
        // clock time, not a relative "X geleden": a tile is a frozen snapshot (re-renders only every
        // ~30 min), so a relative string would go stale and lie — "om 14:23" stays correct.
        val syncedAt = runCatching { repo.lastSyncedAt(NewsSource.NEWS_LATEST).first() }.getOrNull()
        buildTile(title, meta, syncedAt)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> = scope.future {
        ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build()
    }

    private fun buildTile(title: String, meta: String?, syncedAtEpochMs: Long?): TileBuilders.Tile {
        val launch = ModifiersBuilders.Clickable.Builder()
            .setId("open")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setClassName(MainActivity::class.java.name)
                            .setPackageName(packageName)
                            .build(),
                    )
                    .build(),
            )
            .build()

        val content = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(kicker())
            .addContent(spacer(16f))
            .addContent(
                Text.Builder(this, title)
                    .setTypography(Typography.TYPOGRAPHY_TITLE1)
                    .setColor(argb(HEADLINE))
                    .setMaxLines(4)
                    .build(),
            )

        if (meta != null) {
            content.addContent(spacer(14f))
            content.addContent(
                Text.Builder(this, meta)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(argb(META))
                    .setMaxLines(1)
                    .build(),
            )
        }

        // Freshness marker sits below the tap-hint (only once we have a real last-synced time).
        if (syncedAtEpochMs != null) {
            content.addContent(spacer(10f))
            content.addContent(freshnessRow(syncedAtEpochMs))
        }

        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(launch)
                    .setPadding(ModifiersBuilders.Padding.Builder().setAll(dp(20f)).build())
                    .build(),
            )
            .addContent(content.build())
            .build()

        val entry = TimelineBuilders.TimelineEntry.Builder()
            .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(root).build())
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.Builder().addTimelineEntry(entry).build())
            .setFreshnessIntervalMillis(TimeUnit.MINUTES.toMillis(30))
            .build()
    }

    /** "▮N  LAATSTE NIEUWS" — a violet badge mark next to a lavender uppercase kicker. */
    private fun kicker(): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(badge())
            .addContent(spacer(8f))
            .addContent(
                Text.Builder(this, getString(R.string.tile_latest))
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(LAVENDER))
                    .setMaxLines(1)
                    .build(),
            )
            .build()

    private fun badge(): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(dp(22f))
            .setHeight(dp(22f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(VIOLET))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(7f)).build())
                            .build(),
                    )
                    .build(),
            )
            .addContent(
                Text.Builder(this, "N")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(argb(BADGE_TEXT))
                    .setMaxLines(1)
                    .build(),
            )
            .build()

    private fun spacer(height: Float): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Spacer.Builder().setHeight(dp(height)).build()

    /** "● bijgewerkt om 14:23" — a lavender dot + a dim absolute-time label. */
    private fun freshnessRow(syncedAtEpochMs: Long): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(dot(SYNC_DOT, 5f))
            .addContent(hSpacer(6f))
            .addContent(
                syncedText(getString(R.string.freshness_updated_at, syncedClock(syncedAtEpochMs))),
            )
            .build()

    private fun syncedText(s: String): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Text.Builder()
            .setText(s)
            .setMaxLines(1)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(10f))
                    .setWeight(FONT_WEIGHT_NORMAL)
                    .setColor(argb(SYNC_LABEL))
                    .build(),
            )
            .build()

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

    private fun hSpacer(width: Float): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Spacer.Builder().setWidth(dp(width)).build()
}

package be.vanmechelen.vrtnws.tile

import android.text.format.DateUtils
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
import be.vanmechelen.vrtnws.ui.MainActivity
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import java.util.concurrent.TimeUnit

private const val RES_VERSION = "1"

// News (VRT NWS) purple identity — mirrors the app.
private const val LAVENDER = 0xFFA99CFF.toInt() // kicker
private const val VIOLET = 0xFF302070.toInt() // badge fill
private const val BADGE_TEXT = 0xFFC7C0FF.toInt()
private const val HEADLINE = 0xFFFFFFFF.toInt()
private const val META = 0xFFA6AAB2.toInt()

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
        val headline = runCatching { VrtNwsApp.instance.graph.repository.latestHeadline() }.getOrNull()
        val title = headline?.title ?: getString(R.string.empty_headlines)
        // Meta line mirrors the design: "1 uur geleden · tik om te openen" (age from the article).
        val meta = headline?.let {
            val age = DateUtils.getRelativeTimeSpanString(it.publishedEpochMs).toString()
            "$age · ${getString(R.string.tile_tap_to_open)}"
        }
        buildTile(title, meta)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> = scope.future {
        ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build()
    }

    private fun buildTile(title: String, meta: String?): TileBuilders.Tile {
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
}

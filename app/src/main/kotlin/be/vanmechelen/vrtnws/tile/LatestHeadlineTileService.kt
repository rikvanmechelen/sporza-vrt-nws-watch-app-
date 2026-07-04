package be.vanmechelen.vrtnws.tile

import androidx.wear.protolayout.ActionBuilders
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
        val title = headline?.title ?: getString(be.vanmechelen.vrtnws.R.string.empty_headlines)
        buildTile(requestParams, title)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> = scope.future {
        ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build()
    }

    private fun buildTile(params: RequestBuilders.TileRequest, title: String): TileBuilders.Tile {
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

        val content = Text.Builder(this, title)
            .setTypography(Typography.TYPOGRAPHY_BODY2)
            .setColor(androidx.wear.protolayout.ColorBuilders.argb(0xFFFFFFFF.toInt()))
            .setMaxLines(4)
            .build()

        val padding = ModifiersBuilders.Padding.Builder().setAll(dp(12f)).build()
        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(launch)
                    .setPadding(padding)
                    .build(),
            )
            .addContent(content)
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
}

package me.vanmechelen.vrtsporza.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import me.vanmechelen.vrtsporza.R
import me.vanmechelen.vrtsporza.VrtNwsApp
import me.vanmechelen.vrtsporza.model.Article
import me.vanmechelen.vrtsporza.ui.MainActivity

/**
 * Shows the latest cached headline as a watch-face complication; tapping opens the app.
 *
 * The redesign gives both slots a "VRT NWS" identity via the complication title: SHORT_TEXT reads
 * "NWS" + a compact age ("1u"), LONG_TEXT reads "VRT NWS" + the headline. Accent colour is the
 * watch face's to control, so we lean on titles for identity rather than colour.
 */
class LatestHeadlineComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val headline = runCatching { VrtNwsApp.instance.graph.repository.latestHeadline() }.getOrNull()
        return complicationData(request.complicationType, headline)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData =
        complicationData(type, null)

    private fun complicationData(type: ComplicationType, headline: Article?): ComplicationData {
        val text = headline?.title ?: getString(R.string.tile_latest)
        val tap = launchAppIntent()
        return when (type) {
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(plain(text), descr())
                    .setTitle(plain(getString(R.string.headlines_title)))
                    .setTapAction(tap)
                    .build()
            else -> {
                val short = headline?.let { compactAge(it.publishedEpochMs) } ?: "•"
                ShortTextComplicationData.Builder(plain(short), descr())
                    .setTitle(plain(getString(R.string.complication_short_label)))
                    .setTapAction(tap)
                    .build()
            }
        }
    }

    private fun plain(text: String): ComplicationText = PlainComplicationText.Builder(text).build()

    private fun descr(): ComplicationText = plain(getString(R.string.tile_latest))

    /** Compact Dutch relative age for the tiny SHORT_TEXT slot: "nu", "5m", "1u", "3d". */
    private fun compactAge(epochMs: Long): String {
        val mins = (System.currentTimeMillis() - epochMs) / 60_000
        return when {
            mins < 1 -> "nu"
            mins < 60 -> "${mins}m"
            mins < 1_440 -> "${mins / 60}u"
            else -> "${mins / 1_440}d"
        }
    }

    private fun launchAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

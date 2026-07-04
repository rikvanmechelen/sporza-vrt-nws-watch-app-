package be.vanmechelen.vrtnws.complication

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
import be.vanmechelen.vrtnws.R
import be.vanmechelen.vrtnws.VrtNwsApp
import be.vanmechelen.vrtnws.ui.MainActivity

/** Shows the latest cached headline as a watch-face complication; tapping opens the app. */
class LatestHeadlineComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val headline = runCatching { VrtNwsApp.instance.graph.repository.latestHeadline() }.getOrNull()
        val text = headline?.title ?: getString(R.string.tile_latest)
        return complicationData(request.complicationType, text)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData =
        complicationData(type, getString(R.string.tile_latest))

    private fun complicationData(type: ComplicationType, text: String): ComplicationData {
        val description: ComplicationText = PlainComplicationText.Builder(getString(R.string.tile_latest)).build()
        val tap = launchAppIntent()
        return when (type) {
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(PlainComplicationText.Builder(text).build(), description)
                    .setTapAction(tap)
                    .build()
            else ->
                ShortTextComplicationData.Builder(
                    PlainComplicationText.Builder(text.take(20)).build(),
                    description,
                )
                    .setTapAction(tap)
                    .build()
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

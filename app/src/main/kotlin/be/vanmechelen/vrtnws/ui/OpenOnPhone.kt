package be.vanmechelen.vrtnws.ui

import android.content.Intent
import android.content.Context
import android.net.Uri
import androidx.wear.remote.interactions.RemoteActivityHelper

/** Opens [url] in the browser on the paired phone. Fire-and-forget. */
fun openOnPhone(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW)
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .setData(Uri.parse(url))
    runCatching { RemoteActivityHelper(context).startRemoteActivity(intent) }
}

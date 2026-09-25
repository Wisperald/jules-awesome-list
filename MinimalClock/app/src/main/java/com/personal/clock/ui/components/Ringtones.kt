package com.personal.clock.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import com.personal.clock.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opens the system ringtone picker (no audio files are bundled with the app).
 * The callback receives the chosen URI, or null for "default alarm sound".
 */
@Composable
fun rememberRingtonePicker(current: String?, onPicked: (String?) -> Unit): () -> Unit {
    val title = stringResource(R.string.ringtone_picker_title)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.let {
            IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        }
        val default = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        onPicked(if (uri == null || uri == default) null else uri.toString())
    }
    return {
        val default = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, default)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current?.toUri() ?: default)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title)
        try {
            launcher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            // No picker on this device: keep the default sound.
        }
    }
}

/** Human-readable ringtone title, resolved off the main thread. */
@Composable
fun ringtoneTitle(uri: String?): String {
    val context = LocalContext.current
    val defaultLabel = stringResource(R.string.ringtone_default)
    val title by produceState(initialValue = if (uri == null) defaultLabel else "", uri, defaultLabel) {
        value = if (uri == null) defaultLabel else withContext(Dispatchers.IO) { loadTitle(context, uri) } ?: defaultLabel
    }
    return title
}

private fun loadTitle(context: Context, uri: String): String? = try {
    RingtoneManager.getRingtone(context, uri.toUri())?.getTitle(context)
} catch (_: Exception) {
    null
}

package org.futo.voiceinput.models

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.delay
import org.futo.voiceinput.settings.getSetting
import org.futo.voiceinput.settings.setSetting
import org.futo.voiceinput.shared.types.ModelLoader

/**
 * Remembers which file the user picked for which model.
 *
 * Models are identified by [ModelLoader.key] (stable across upstream list reordering, and shared when
 * the same model appears in both the English and multilingual lists). Only the URI string is stored;
 * the file itself stays wherever the user keeps it and is never copied.
 */
internal fun ModelLoader.storageId(context: Context): String =
    key(context).toString().replace(Regex("[^A-Za-z0-9_.-]"), "_")

private fun uriKey(id: String) = stringPreferencesKey("model_uri_$id")

fun Context.getModelUri(model: ModelLoader): Uri? =
    getSetting(uriKey(model.storageId(this)), "").takeIf { it.isNotBlank() }?.let { Uri.parse(it) }

suspend fun Context.setModelUri(model: ModelLoader, uri: Uri) {
    setSetting(uriKey(model.storageId(this)), uri.toString())
    awaitModelUri(model, uri)
}

suspend fun Context.clearModelUri(model: ModelLoader) {
    setSetting(uriKey(model.storageId(this)), "")
    awaitModelUri(model, null)
}

/** getSetting() reads a snapshot that is refreshed asynchronously after a write; wait (max ~2 s) for it. */
private suspend fun Context.awaitModelUri(model: ModelLoader, expected: Uri?) {
    repeat(100) {
        if (getModelUri(model) == expected) return
        delay(20)
    }
}

/** True if the system still lets this app read [uri] (permission was persisted and not revoked). */
fun Context.hasPersistedReadAccess(uri: Uri): Boolean =
    contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

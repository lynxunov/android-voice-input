package org.futo.voiceinput.models

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import org.futo.voiceinput.settings.MODEL_URI_ENGLISH_0
import org.futo.voiceinput.settings.MODEL_URI_ENGLISH_1
import org.futo.voiceinput.settings.MODEL_URI_ENGLISH_2
import org.futo.voiceinput.settings.MODEL_URI_MULTILINGUAL_0
import org.futo.voiceinput.settings.MODEL_URI_MULTILINGUAL_1
import org.futo.voiceinput.settings.MODEL_URI_MULTILINGUAL_2
import org.futo.voiceinput.settings.SettingsKey
import org.futo.voiceinput.settings.getSetting
import org.futo.voiceinput.settings.setSetting

/**
 * Maps each model slot to its DataStore key.
 * English slots 0-2 correspond to ENGLISH_MODELS indices.
 * Multilingual slots 0-2 correspond to MULTILINGUAL_MODELS indices.
 */
enum class ModelSlot(val key: SettingsKey<String>, val displayName: String) {
    ENGLISH_0(MODEL_URI_ENGLISH_0,      "English – tiny (39M)"),
    ENGLISH_1(MODEL_URI_ENGLISH_1,      "English – base (74M)"),
    ENGLISH_2(MODEL_URI_ENGLISH_2,      "English – small (244M)"),
    MULTILINGUAL_0(MODEL_URI_MULTILINGUAL_0, "Multilingual – tiny (39M)"),
    MULTILINGUAL_1(MODEL_URI_MULTILINGUAL_1, "Multilingual – base (74M)"),
    MULTILINGUAL_2(MODEL_URI_MULTILINGUAL_2, "Multilingual – small (244M)");

    val isEnglish: Boolean get() = this.ordinal <= 2
}

/**
 * Persists a SAF URI for a model slot.
 *
 * Callers MUST have already called:
 *   context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
 * before calling this, so the permission survives reboots.
 */
suspend fun Context.setModelUri(slot: ModelSlot, uri: Uri) {
    setSetting(slot.key, uri.toString())
}

/**
 * Clears the URI for a model slot (marks it as not configured).
 */
suspend fun Context.clearModelUri(slot: ModelSlot) {
    setSetting(slot.key, "")
}

/**
 * Returns the Uri for a model slot, or null if not configured.
 */
suspend fun Context.getModelUri(slot: ModelSlot): Uri? {
    val raw = getSetting(slot.key)
    return if (raw.isBlank()) null else Uri.parse(raw)
}

/**
 * Returns true if the URI for this slot is configured AND the system still
 * holds a persisted read permission for it (i.e. the file is still accessible).
 */
suspend fun Context.isModelAvailable(slot: ModelSlot): Boolean {
    val uri = getModelUri(slot) ?: return false
    return contentResolver.persistedUriPermissions.any {
        it.uri == uri && it.isReadPermission
    }
}

/**
 * Opens a ParcelFileDescriptor for reading the model file at the given URI.
 * Caller is responsible for closing the descriptor.
 * Returns null if the URI is not set or not accessible.
 */
suspend fun Context.openModelFd(slot: ModelSlot): android.os.ParcelFileDescriptor? {
    val uri = getModelUri(slot) ?: return null
    return try {
        contentResolver.openFileDescriptor(uri, "r")
    } catch (e: Exception) {
        android.util.Log.e("ModelUriStore", "Failed to open model fd for $slot: ${e.message}")
        null
    }
}

/**
 * Returns a map of slot → isAvailable for all slots.
 * Used by the UI to show which models are configured.
 */
suspend fun Context.allModelSlotStatus(): Map<ModelSlot, Boolean> {
    return ModelSlot.values().associateWith { isModelAvailable(it) }
}

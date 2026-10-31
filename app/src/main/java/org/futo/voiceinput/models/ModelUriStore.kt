package org.futo.voiceinput.models

import android.content.Context
import android.net.Uri
import org.futo.voiceinput.settings.MODEL_URI_ENGLISH_0
import org.futo.voiceinput.settings.MODEL_URI_ENGLISH_1
import org.futo.voiceinput.settings.MODEL_URI_ENGLISH_2
import org.futo.voiceinput.settings.MODEL_URI_ENGLISH_3
import org.futo.voiceinput.settings.MODEL_URI_ENGLISH_4
import org.futo.voiceinput.settings.MODEL_URI_ENGLISH_5
import org.futo.voiceinput.settings.MODEL_URI_ENGLISH_6
import org.futo.voiceinput.settings.MODEL_URI_ENGLISH_7
import org.futo.voiceinput.settings.MODEL_URI_ENGLISH_8
import org.futo.voiceinput.settings.MODEL_URI_ENGLISH_9
import org.futo.voiceinput.settings.MODEL_URI_MULTILINGUAL_0
import org.futo.voiceinput.settings.MODEL_URI_MULTILINGUAL_1
import org.futo.voiceinput.settings.MODEL_URI_MULTILINGUAL_2
import org.futo.voiceinput.settings.MODEL_URI_MULTILINGUAL_3
import org.futo.voiceinput.settings.SettingsKey
import org.futo.voiceinput.settings.getSetting
import org.futo.voiceinput.settings.setSetting
import org.futo.voiceinput.shared.ENGLISH_MODELS
import org.futo.voiceinput.shared.MULTILINGUAL_MODELS

/**
 * One slot per model in ENGLISH_MODELS and MULTILINGUAL_MODELS.
 * Index matches the list position exactly.
 */
enum class ModelSlot(val key: SettingsKey<String>, val isEnglish: Boolean) {
    // English — matches ENGLISH_MODELS indices 0..9
    ENGLISH_0(MODEL_URI_ENGLISH_0, true),
    ENGLISH_1(MODEL_URI_ENGLISH_1, true),
    ENGLISH_2(MODEL_URI_ENGLISH_2, true),
    ENGLISH_3(MODEL_URI_ENGLISH_3, true),
    ENGLISH_4(MODEL_URI_ENGLISH_4, true),
    ENGLISH_5(MODEL_URI_ENGLISH_5, true),
    ENGLISH_6(MODEL_URI_ENGLISH_6, true),
    ENGLISH_7(MODEL_URI_ENGLISH_7, true),
    ENGLISH_8(MODEL_URI_ENGLISH_8, true),
    ENGLISH_9(MODEL_URI_ENGLISH_9, true),
    // Multilingual — matches MULTILINGUAL_MODELS indices 0..3
    MULTILINGUAL_0(MODEL_URI_MULTILINGUAL_0, false),
    MULTILINGUAL_1(MODEL_URI_MULTILINGUAL_1, false),
    MULTILINGUAL_2(MODEL_URI_MULTILINGUAL_2, false),
    MULTILINGUAL_3(MODEL_URI_MULTILINGUAL_3, false);

    val displayName: String get() = when {
        isEnglish -> ENGLISH_MODELS.getOrNull(ordinal)?.let { ctx ->
            // Use the string res ID converted to a slot-local name
            "English slot $ordinal"
        } ?: "English slot $ordinal"
        else -> "Multilingual slot ${ordinal - 10}"
    }

    val modelIndex: Int get() = if (isEnglish) ordinal else ordinal - 10
}

suspend fun Context.setModelUri(slot: ModelSlot, uri: Uri) = setSetting(slot.key, uri.toString())
suspend fun Context.clearModelUri(slot: ModelSlot) = setSetting(slot.key, "")

suspend fun Context.getModelUri(slot: ModelSlot): Uri? {
    val raw = getSetting(slot.key)
    return if (raw.isBlank()) null else Uri.parse(raw)
}

suspend fun Context.isModelAvailable(slot: ModelSlot): Boolean {
    val uri = getModelUri(slot) ?: return false
    return contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
}

suspend fun Context.allModelSlotStatus(): Map<ModelSlot, Boolean> =
    ModelSlot.values().associateWith { isModelAvailable(it) }

/** Returns a ModelSafUri for the given slot, or null if not configured. */
suspend fun Context.toModelSafUri(slot: ModelSlot): org.futo.voiceinput.shared.types.ModelSafUri? {
    val uri = getModelUri(slot) ?: return null
    if (!contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }) return null
    val nameRes = if (slot.isEnglish)
        ENGLISH_MODELS.getOrNull(slot.modelIndex)?.name ?: org.futo.voiceinput.R.string.model_manager
    else
        MULTILINGUAL_MODELS.getOrNull(slot.modelIndex)?.name ?: org.futo.voiceinput.R.string.model_manager
    return org.futo.voiceinput.shared.types.ModelSafUri(
        name = nameRes,
        uri = uri,
        caps = if (slot.isEnglish) ENGLISH_MODELS.getOrNull(slot.modelIndex)?.caps
               else MULTILINGUAL_MODELS.getOrNull(slot.modelIndex)?.caps
               ?: org.futo.voiceinput.shared.WhisperEnCapabilities
    )
}

package org.futo.voiceinput.migration

import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.ENGLISH_MODELS
import org.futo.voiceinput.MULTILINGUAL_MODELS
import org.futo.voiceinput.settings.MODELS_MIGRATED
import org.futo.voiceinput.settings.setSetting
import java.io.File

// OkHttp, downloadModels, ModelInfo, getModelsToDownload removed:
// This fork has no network access. Model files are user-selected via SAF.

/**
 * Returns true if any legacy TFLite model files still exist in filesDir.
 * Used to decide whether to run the migration job.
 */
fun isUsingTfliteLegacy(context: Context): Boolean {
    val filesDir = context.filesDir
    return (ENGLISH_MODELS + MULTILINGUAL_MODELS).any { model ->
        !model.legacy.is_builtin_asset && (
            File(filesDir, model.legacy.encoder_xatn_file).exists() ||
            File(filesDir, model.legacy.decoder_file).exists() ||
            File(filesDir, model.legacy.vocab_file).exists()
        )
    }
}

/**
 * Deletes any legacy TFLite model files from filesDir and marks migration as done.
 * Safe to call even when no legacy files exist.
 */
fun deleteLegacyModels(context: Context) {
    val filesDir = context.filesDir

    val oldFiles = (ENGLISH_MODELS + MULTILINGUAL_MODELS).flatMap { model ->
        if (!model.legacy.is_builtin_asset) {
            listOf(
                File(filesDir, model.legacy.encoder_xatn_file),
                File(filesDir, model.legacy.decoder_file),
                File(filesDir, model.legacy.vocab_file)
            )
        } else emptyList()
    }.filter { it.exists() }.distinct()

    oldFiles.forEach {
        it.delete()
        Log.d("MigrationUtils", "Deleted legacy model file: ${it.name}")
    }

    if (oldFiles.isNotEmpty()) {
        runBlocking { context.setSetting(MODELS_MIGRATED, true) }
    }
}

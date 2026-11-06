package org.futo.voiceinput.shared.whisper

import android.content.Context
import org.futo.voiceinput.shared.ggml.WhisperGGML
import org.futo.voiceinput.shared.types.ModelLoader


class ModelManager(
    val context: Context
) {
    private val loadedModels: HashMap<Any, WhisperGGML> = hashMapOf()

    // Fork: loads can run in parallel (preload) and cleanUp() can run from onTrimMemory; guard the map.
    private val lock = Any()

    fun obtainModel(model: ModelLoader): WhisperGGML = synchronized(lock) {
        val key = model.key(context)
        loadedModels.getOrPut(key) { model.loadGGML(context) }
    }

    fun cancelAll() {
        val models = synchronized(lock) { loadedModels.values.toList() }
        models.forEach { it.cancel() }
    }

    suspend fun cleanUp() {
        val models = synchronized(lock) { loadedModels.values.toList().also { loadedModels.clear() } }
        for (model in models) {
            model.cancel()
            model.close()
        }
    }
}

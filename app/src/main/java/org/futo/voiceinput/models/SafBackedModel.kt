package org.futo.voiceinput.models

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import org.futo.voiceinput.shared.ggml.InvalidModelException
import org.futo.voiceinput.shared.ggml.WhisperGGML
import org.futo.voiceinput.shared.types.ModelCapabilities
import org.futo.voiceinput.shared.types.ModelDownloadable
import org.futo.voiceinput.shared.types.ModelLoader
import org.futo.voiceinput.shared.types.ModelPresence
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Wraps one of upstream's model entries so that it is loaded from the file the user picked with the
 * system file picker (Storage Access Framework) instead of from the app's private download folder.
 *
 * Everything upstream does with a model (settings screens, the recognizer, streaming, language
 * switching) keeps working unchanged because this is still a [ModelLoader].
 */
class SafBackedModel internal constructor(
    private val inner: ModelLoader,
    private val uri: Uri?,
) : ModelLoader {
    override val name: Int get() = inner.name
    override val caps: ModelCapabilities get() = inner.caps

    private val expectedFileName: String =
        (inner as? ModelDownloadable)?.ggmlFile ?: "model file"

    override fun exists(context: Context): Boolean =
        uri != null && context.hasPersistedReadAccess(uri)

    // Reuses upstream's "missing files" plumbing: non-empty means "the user still has to pick a file".
    override fun getRequiredDownloadList(context: Context, forUpgrade: Boolean): List<String> =
        if (exists(context)) emptyList() else listOf(expectedFileName)

    override fun getPresence(context: Context): ModelPresence =
        if (exists(context)) ModelPresence.Exists else ModelPresence.Missing

    override fun key(context: Context): Any = "saf:${inner.key(context)}:$uri"

    // Same underlying model => same model. Upstream compares loaders with == to decide whether a
    // language needs a different model, so the wrapper must not make identical models look different.
    override fun equals(other: Any?) = other is SafBackedModel && other.inner === inner
    override fun hashCode() = System.identityHashCode(inner)

    override fun loadGGML(context: Context): WhisperGGML {
        val source = uri ?: throw InvalidModelException(false)
        val id = inner.storageId(context)

        val pfd = try {
            context.contentResolver.openFileDescriptor(source, "r")
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open $expectedFileName: ${e.message}")
            null
        } ?: throw InvalidModelException(false)

        // 1) Preferred: load straight from the file the user picked (no copy, no extra storage).
        try {
            val model = WhisperGGML(File("/proc/self/fd/${pfd.fd}"))
            // Keep the descriptor open as long as the model may be in use (one per model; replaced on reload).
            openDescriptors.put(id, pfd)?.close()
            Log.i(TAG, "Loaded $expectedFileName directly from the selected file")
            return model
        } catch (e: InvalidModelException) {
            if (e.fileError) {
                // The file really is not a valid model for this engine. Never delete a user's file.
                pfd.close()
                throw e
            }
            Log.w(TAG, "Direct load failed for $expectedFileName, falling back to a temporary copy")
        }

        // 2) Fallback: some storage providers do not allow re-opening the descriptor by path.
        //    Copy to the code cache (the same thing upstream does for its built-in model) and load that.
        try {
            val tmp = File(context.codeCacheDir, "_SELECTED_$id")
            context.contentResolver.openInputStream(source)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            } ?: throw InvalidModelException(false)
            return WhisperGGML(tmp)
        } finally {
            pfd.close()
        }
    }

    companion object {
        private const val TAG = "SafBackedModel"
        private val openDescriptors = ConcurrentHashMap<String, ParcelFileDescriptor>()
    }
}

/** Resolves an upstream model entry to the file the user picked for it (if any). */
fun ModelLoader.withSafStorage(context: Context): ModelLoader =
    SafBackedModel(this, context.getModelUri(this))

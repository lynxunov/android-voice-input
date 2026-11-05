package org.futo.voiceinput.models

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import org.futo.voiceinput.shared.ggml.InvalidModelException
import org.futo.voiceinput.shared.ggml.WhisperGGML
import org.futo.voiceinput.shared.types.ModelCapabilities
import org.futo.voiceinput.shared.types.ModelDownloadable
import org.futo.voiceinput.shared.types.ModelLoader
import org.futo.voiceinput.shared.types.ModelPresence
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Outcome of the most recent attempt to load a model (kept in memory, shown in the Model Manager). */
data class LoadReport(val ok: Boolean, val summary: String)

/**
 * Wraps one of upstream's model entries so that it is loaded from the file the user picked with the
 * system file picker (Storage Access Framework) instead of from the app's private download folder.
 *
 * Everything upstream does with a model (settings screens, the recognizer, streaming, language
 * switching) keeps working unchanged because this is still a [ModelLoader].
 *
 * Loading strategy:
 *  1. Direct: the inference engine opens models by *path*, so the picked file is handed over as
 *     `/proc/self/fd/N` – no copy, no extra storage. This only works if Android lets this app re-open
 *     the descriptor by path, which is checked first.
 *  2. Fallback: if that is not possible (scoped storage / some providers), a private copy is made once
 *     in app storage and reused. It is shown in the Model Manager and can be deleted there.
 */
class SafBackedModel internal constructor(
    private val inner: ModelLoader,
    private val uri: Uri?,
) : ModelLoader {
    override val id: String get() = inner.id
    override val legacyId get() = inner.legacyId
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
        val source = uri ?: fail("none", "no file has been selected for this model")
        val id = inner.storageId(context)
        val started = SystemClock.elapsedRealtime()

        val (model, how) = load(context, source, id)

        val summary = "$how, ${SystemClock.elapsedRealtime() - started} ms"
        reports[id] = LoadReport(true, summary)
        Log.i(TAG, "$expectedFileName loaded: $summary")
        return model
    }

    private fun load(context: Context, source: Uri, id: String): Pair<WhisperGGML, String> {
        val pfd = try {
            context.contentResolver.openFileDescriptor(source, "r")
        } catch (e: Exception) {
            null
        } ?: fail(id, "the selected file cannot be opened any more (deleted, moved, or access revoked)")

        // ---- 1) direct, zero-copy ----
        val directProblem: String
        if (pfd.statSize <= 0) {
            directProblem = "the storage provider did not give a regular file"
        } else {
            val path = "/proc/self/fd/${pfd.fd}"
            // The engine opens by path, so first check that this process may re-open the descriptor that way.
            val reopenError = try {
                FileInputStream(path).use { it.read() }
                null
            } catch (e: IOException) {
                e.message ?: e.javaClass.simpleName
            }

            if (reopenError == null) {
                try {
                    val model = WhisperGGML(File(path))
                    // Keep the descriptor open while the model may be in use (one per model; replaced on reload).
                    openDescriptors.put(id, pfd)?.close()
                    return model to "direct from the selected file"
                } catch (e: InvalidModelException) {
                    if (e.fileError) {
                        // We could open it, and the engine still rejects it: not a model this engine supports.
                        pfd.close()
                        fail(id, "the engine rejected the file (not a valid or supported model file)", fileError = true)
                    }
                    directProblem = "the engine could not open the file by path"
                }
            } else {
                directProblem = "Android does not let the app re-open the file by path ($reopenError)"
            }
        }

        // ---- 2) fallback: private copy (made once, then reused) ----
        try {
            val copy = ensurePrivateCopy(context, source, id, pfd, directProblem)
            val model = try {
                WhisperGGML(copy)
            } catch (e: InvalidModelException) {
                fail(id, "$directProblem; the engine also rejected the private copy (fileError=${e.fileError})", e.fileError)
            }
            return model to "private copy of ${copy.length() / (1024 * 1024)} MB because $directProblem"
        } finally {
            pfd.close()
        }
    }

    private fun ensurePrivateCopy(context: Context, source: Uri, id: String, pfd: ParcelFileDescriptor, why: String): File {
        val dir = File(context.filesDir, COPY_DIR).apply { mkdirs() }
        val target = File(dir, id)
        val meta = File(dir, "$id.meta")
        val size = pfd.statSize
        val expectedMeta = "$source|$size"

        if (target.isFile && (size <= 0 || target.length() == size) && meta.isFile && meta.readText() == expectedMeta) {
            return target
        }

        meta.delete()
        val part = File(dir, "$id.part")
        if (size > 0 && dir.usableSpace < size + (64L shl 20)) {
            fail(id, "$why; and there is not enough free storage for a private copy (${size / (1024 * 1024)} MB needed)")
        }
        try {
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input -> part.outputStream().use { input.copyTo(it) } }
        } catch (e: IOException) {
            part.delete()
            fail(id, "$why; and copying the file failed (${e.message})")
        }
        target.delete()
        if (!part.renameTo(target)) fail(id, "$why; and the private copy could not be stored")
        meta.writeText(expectedMeta)
        Log.w(TAG, "Made a private copy of $expectedFileName (${target.length()} bytes): $why")
        return target
    }

    private fun fail(id: String, detail: String, fileError: Boolean = false): Nothing {
        Log.e(TAG, "$expectedFileName: $detail")
        reports[id] = LoadReport(false, detail)
        throw InvalidModelException(fileError)
    }

    companion object {
        private const val TAG = "SafBackedModel"
        private const val COPY_DIR = "selected-models"
        private val openDescriptors = ConcurrentHashMap<String, ParcelFileDescriptor>()
        private val reports = ConcurrentHashMap<String, LoadReport>()

        fun lastReport(id: String): LoadReport? = reports[id]

        fun privateCopySize(context: Context, id: String): Long =
            File(File(context.filesDir, COPY_DIR), id).takeIf { it.isFile }?.length() ?: 0L

        fun deletePrivateCopy(context: Context, id: String) {
            val dir = File(context.filesDir, COPY_DIR)
            File(dir, id).delete(); File(dir, "$id.meta").delete(); File(dir, "$id.part").delete()
        }
    }
}

/** Resolves an upstream model entry to the file the user picked for it (if any). */
fun ModelLoader.withSafStorage(context: Context): ModelLoader =
    SafBackedModel(this, context.getModelUri(this))

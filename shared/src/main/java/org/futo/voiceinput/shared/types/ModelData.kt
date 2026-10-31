package org.futo.voiceinput.shared.types

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import org.futo.voiceinput.shared.ggml.InvalidModelException
import org.futo.voiceinput.shared.ggml.WhisperGGML
import java.io.File
import java.io.FileNotFoundException

data class ModelCapabilities(
    val supportedLanguages: Set<Language>,
    val supportsLanguageDetection: Boolean,
    val supportsPersonalDict: Boolean,
    val supportsStreaming: Boolean,
    val isWhisper: Boolean = false,
    val paramCount: Int = 0,
    val banishedToOther: Boolean = false,
    val requiresPersonalDictSpecialCharacterProcessing: Boolean = false,
    val unrecommendedLanguages: Set<Language> = emptySet(),
)

enum class ModelPresence {
    Exists,
    UpgradeAvailable,
    Missing
}

interface ModelLoader {
    @get:StringRes
    val name: Int
    val caps: ModelCapabilities

    fun exists(context: Context): Boolean
    fun getRequiredDownloadList(context: Context, forUpgrade: Boolean = false): List<String>
    fun getCleanupListAfterUpgrade(context: Context): List<String> = emptyList()
    fun loadGGML(context: Context): WhisperGGML
    fun key(context: Context): Any

    fun getPresence(context: Context): ModelPresence =
        when {
            getRequiredDownloadList(context, false).isNotEmpty() -> ModelPresence.Missing
            getRequiredDownloadList(context, true).isNotEmpty() -> ModelPresence.UpgradeAvailable
            else -> ModelPresence.Exists
        }
}

// ModelBuiltInAsset removed: no built-in models in this fork.
// All models are user-selected via SAF (ModelSafUri) or legacy filesDir (ModelDownloadable).

private fun Context.ggmlFile(fname: String) = File(filesDir, fname)

class ModelDownloadable(
    override val name: Int,
    val ggmlFile: String,
    val checksum: String,
    override val caps: ModelCapabilities,
    val backwardsCompatFiles: List<String> = emptyList(),
    val otherCleanupFiles: List<String> = emptyList(),
) : ModelLoader {
    private fun matchingFile(context: Context): File? =
        if (context.ggmlFile(ggmlFile).isFile) context.ggmlFile(ggmlFile)
        else backwardsCompatFiles.firstNotNullOfOrNull { f ->
            context.ggmlFile(f).let { if (it.exists()) it else null }
        }

    override fun exists(context: Context): Boolean = getRequiredDownloadList(context).isEmpty()

    override fun getRequiredDownloadList(context: Context, forUpgrade: Boolean): List<String> = when {
        forUpgrade && !context.ggmlFile(ggmlFile).isFile -> listOf(ggmlFile)
        matchingFile(context) == null -> listOf(ggmlFile)
        else -> emptyList()
    }

    override fun getCleanupListAfterUpgrade(context: Context): List<String> =
        backwardsCompatFiles + otherCleanupFiles

    override fun loadGGML(context: Context): WhisperGGML {
        val file = matchingFile(context) ?: throw FileNotFoundException(ggmlFile)
        return try {
            WhisperGGML(file)
        } catch (e: InvalidModelException) {
            if (e.fileError) file.delete()
            throw e
        }
    }

    override fun key(context: Context): Any = "Downloadable$ggmlFile$checksum"
}

/**
 * Loads a model from a SAF URI persisted via takePersistableUriPermission.
 *
 * Uses /proc/self/fd/N to obtain a stable file path from the open ParcelFileDescriptor —
 * zero-copy, works across reboots as long as the URI permission is held.
 * The PFD is kept open for the lifetime of the WhisperGGML instance.
 */
class ModelSafUri(
    override val name: Int,
    val uri: Uri,
    override val caps: ModelCapabilities,
) : ModelLoader {

    override fun exists(context: Context): Boolean {
        if (uri == Uri.EMPTY) return false
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }

    override fun getRequiredDownloadList(context: Context, forUpgrade: Boolean): List<String> =
        emptyList() // SAF models are never "downloaded" — user picks them

    override fun loadGGML(context: Context): WhisperGGML {
        if (!exists(context)) throw FileNotFoundException("SAF URI not accessible: $uri")

        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw FileNotFoundException("Could not open PFD for URI: $uri")

        // /proc/self/fd/N gives a stable path to the open fd without copying the file.
        // WhisperGGML opens the file by path via JNI; keeping pfd alive ensures the fd stays valid.
        val procPath = "/proc/self/fd/${pfd.fd}"
        return try {
            WhisperGGML(File(procPath), pfd)
        } catch (e: Exception) {
            pfd.close()
            throw e
        }
    }

    override fun key(context: Context): Any = "SAF$uri"

    override fun getPresence(context: Context): ModelPresence =
        if (exists(context)) ModelPresence.Exists else ModelPresence.Missing
}

/** Plain file reference — used for legacy migration and testing. */
class ModelFileFile(
    override val name: Int,
    val file: File,
    override val caps: ModelCapabilities,
) : ModelLoader {
    override fun exists(context: Context): Boolean = file.exists()
    override fun getRequiredDownloadList(context: Context, forUpgrade: Boolean): List<String> = emptyList()
    override fun loadGGML(context: Context): WhisperGGML {
        if (!file.isFile) throw FileNotFoundException(file.absolutePath)
        return WhisperGGML(file)
    }
    override fun key(context: Context): Any = "File${file.absolutePath}"
    override fun getPresence(context: Context): ModelPresence = ModelPresence.Exists
}

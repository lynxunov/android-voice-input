package org.futo.voiceinput.ml

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.futo.voiceinput.ModelData
import org.futo.voiceinput.ggml.BailLanguageException
import org.futo.voiceinput.ggml.DecodingMode
import org.futo.voiceinput.ggml.WhisperGGML
import org.futo.voiceinput.migration.MigrationActivity
import org.futo.voiceinput.models.ModelSlot
import org.futo.voiceinput.models.getModelUri
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Synchronises inference so two threads don't use the same tensor or free a model in use.
 */
@OptIn(DelicateCoroutinesApi::class)
private val inferenceContext = newSingleThreadContext("InferenceContext")

// ── Model loading ─────────────────────────────────────────────────────────────

/**
 * Loads a model from an asset bundled inside the APK.
 */
@Throws(IOException::class)
private fun loadMappedAsset(context: Context, filePath: String): MappedByteBuffer =
    context.assets.openFd(filePath).use { fd ->
        FileInputStream(fd.fileDescriptor).use { fis ->
            fis.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }

/**
 * Loads a model from a SAF URI previously persisted via [ModelUriStore].
 * The file is NOT copied — it is memory-mapped in-place from the user's chosen location.
 *
 * @throws IOException if the URI has no persisted permission or the file can't be opened.
 */
@Throws(IOException::class)
private fun loadMappedSafUri(context: Context, slot: ModelSlot): MappedByteBuffer {
    val uri = runBlocking { context.getModelUri(slot) }
        ?: throw IOException("No URI configured for model slot $slot. " +
                             "Open Settings → Model → Model Manager to pick a file.")

    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
        ?: throw IOException("Could not open file descriptor for URI: $uri")

    return pfd.use { descriptor ->
        FileInputStream(descriptor.fileDescriptor).use { fis ->
            fis.channel.use { ch ->
                ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size()).also { it.load() }
            }
        }
    }
}

/**
 * Resolves the correct [ModelSlot] for a given [ModelData] based on the ggml filename.
 * Falls back to null if no matching slot is found (e.g. built-in asset — no slot needed).
 */
private fun slotForModel(model: ModelData): ModelSlot? {
    if (model.ggml.is_builtin_asset) return null
    return when (model.ggml.ggml_file) {
        "base_en_acft_q8_0.bin"   -> ModelSlot.ENGLISH_1
        "small_en_acft_q8_0.bin"  -> ModelSlot.ENGLISH_2
        "tiny_acft_q8_0.bin"      -> ModelSlot.MULTILINGUAL_0
        "base_acft_q8_0.bin"      -> ModelSlot.MULTILINGUAL_1
        "small_acft_q8_0.bin"     -> ModelSlot.MULTILINGUAL_2
        else -> null
    }
}

@Throws(IOException::class)
fun loadGGMLModel(context: Context, model: ModelData, onPartialDecode: (String) -> Unit): WhisperGGML {
    val modelBuffer = if (model.ggml.is_builtin_asset) {
        loadMappedAsset(context, model.ggml.ggml_file)
    } else {
        val slot = slotForModel(model)
            ?: throw IOException("Unknown model file '${model.ggml.ggml_file}' — cannot locate SAF slot.")
        loadMappedSafUri(context, slot)
    }
    return WhisperGGML(modelBuffer, onPartialDecode)
}

private fun openMigrationIfModelIsLegacy(context: Context, model: ModelData) {
    if (listOf(
            model.legacy.encoder_xatn_file,
            model.legacy.decoder_file,
            model.legacy.vocab_file
        ).all { File(context.filesDir, it).exists() }
    ) {
        val intent = Intent(context, MigrationActivity::class.java)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

// ── Run states ────────────────────────────────────────────────────────────────

enum class RunState {
    ExtractingFeatures,
    ProcessingEncoder,
    StartedDecoding,
    SwitchingModel,
    OOMError
}

// ── Model wrapper ─────────────────────────────────────────────────────────────

class WhisperModelWrapper(
    val context: Context,
    primaryModel: ModelData,
    fallbackEnglishModel: ModelData?,
    private val suppressNonSpeech: Boolean,
    private val languages: Set<String>,
    private val onStatusUpdate: (RunState) -> Unit,
    private val onPartialDecode: (String) -> Unit,
) {
    private var primaryModelGGML: WhisperGGML? = null
    private var fallbackModelGGML: WhisperGGML? = null

    init {
        require(primaryModel != fallbackEnglishModel) {
            "Fallback model must be unique from the primary model"
        }

        try {
            primaryModelGGML = loadGGMLModel(context, primaryModel, onPartialDecode)
        } catch (e: Exception) {
            runBlocking { primaryModelGGML?.close() }
            Log.e("WhisperModel", "Failed to load primary model: ${e.stackTraceToString()}")
            openMigrationIfModelIsLegacy(context, primaryModel)
            throw e
        }

        fallbackEnglishModel?.let { fallback ->
            try {
                fallbackModelGGML = loadGGMLModel(context, fallback, onPartialDecode)
            } catch (e: Exception) {
                runBlocking { fallbackModelGGML?.close() }
                Log.e("WhisperModel", "Failed to load fallback model: ${e.stackTraceToString()}")
                openMigrationIfModelIsLegacy(context, fallback)
                throw e
            }
        }
    }

    private var modelJob: Job? = null

    suspend fun run(
        samples: FloatArray,
        glossary: String,
        forceLanguage: String?,
        decodingMode: DecodingMode
    ): String {
        yield()

        val glossaryCleaned = glossary.trim().replace("\n", ", ").replace("  ", " ")
        val prompt = if (glossary.isBlank()) "" else "(Glossary: $glossaryCleaned)"

        val languagesOrLanguage = forceLanguage?.let { arrayOf(it) } ?: languages.toTypedArray()
        val bailLanguages = if (fallbackModelGGML != null) arrayOf("en") else arrayOf()

        return try {
            yield()
            onStatusUpdate(RunState.ProcessingEncoder)
            primaryModelGGML!!.infer(
                samples, prompt, languagesOrLanguage, bailLanguages, decodingMode, suppressNonSpeech
            )
        } catch (e: BailLanguageException) {
            yield()
            onStatusUpdate(RunState.SwitchingModel)
            assert(e.language == "en")
            fallbackModelGGML?.infer(
                samples, prompt, languagesOrLanguage, arrayOf(), decodingMode, suppressNonSpeech
            ) ?: throw IllegalStateException("Fallback model is null")
        }
    }

    suspend fun close() = withContext(inferenceContext) {
        primaryModelGGML?.close()
        fallbackModelGGML?.close()
    }
}

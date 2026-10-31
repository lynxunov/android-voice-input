package org.futo.voiceinput.shared.ggml

import android.os.ParcelFileDescriptor
import androidx.annotation.Keep
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(DelicateCoroutinesApi::class)
val inferenceContext = newSingleThreadContext("whisper-ggml-inference")

enum class DecodingMode(val value: Int) {
    Greedy(0),
    BeamSearch5(5)
}

class BailLanguageException(val language: String, val samples: FloatArray) : Exception()
class InferenceCancelledException : Exception()
class InvalidModelException(val fileError: Boolean) :
    Exception("The Whisper model could not be loaded from the given buffer")

@Keep
class WhisperGGML(
    file: File,
    // Optional: keeps the ParcelFileDescriptor alive for SAF-backed /proc/self/fd paths.
    // Closed when this WhisperGGML is closed.
    private val pfd: ParcelFileDescriptor? = null
) {
    private var handle: Long = 0L

    init {
        handle = openNative(file.absolutePath)
        if (handle == 0L || handle == 1L) {
            pfd?.close()
            throw InvalidModelException(handle == 1L)
        }
    }

    private var partialResultCallback: (String) -> Unit = { }

    @Keep
    private fun invokePartialResult(text: String) {
        partialResultCallback(text.trim())
    }

    @Throws(BailLanguageException::class, InferenceCancelledException::class)
    suspend fun infer(
        sampleFlow: Flow<FloatArray>,
        prompt: String,
        languages: Array<String>,
        bailLanguages: Array<String>,
        partialResultCallback: (String) -> Unit,
        tier: Int,
    ): String = withContext(inferenceContext) {
        if (handle == 0L) throw IllegalStateException("WhisperGGML has already been closed")
        this@WhisperGGML.partialResultCallback = partialResultCallback

        if (tier >= 3) return@withContext offlineInfer(sampleFlow, prompt, languages, bailLanguages)

        val streamStatus = startStreamNative(handle, prompt, languages, bailLanguages, tier)
        if (!streamStatus) return@withContext offlineInfer(sampleFlow, prompt, languages, bailLanguages)

        try {
            val frames = mutableListOf<FloatArray>()
            var awaitingOfflineFallback = false
            sampleFlow.collect {
                frames.add(it)
                if (awaitingOfflineFallback) return@collect
                if (!feedStreamNative(handle, it)) {
                    stopStreamNative(handle, false)
                    awaitingOfflineFallback = true
                }
            }
            if (awaitingOfflineFallback) return@withContext offlineInfer(frames, prompt, languages, bailLanguages)
            return@withContext stopStreamNative(handle, true) ?: "?"
        } catch (e: Throwable) {
            stopStreamNative(handle, false)
            throw e
        }
    }

    private suspend fun offlineInfer(
        sampleFlow: Flow<FloatArray>,
        prompt: String,
        languages: Array<String>,
        bailLanguages: Array<String>
    ) = offlineInfer(sampleFlow.toList(), prompt, languages, bailLanguages)

    private suspend fun offlineInfer(
        sampleList: List<FloatArray>,
        prompt: String,
        languages: Array<String>,
        bailLanguages: Array<String>
    ): String = withContext(inferenceContext) {
        val samples = FloatArray(sampleList.sumOf { it.size })
        var i = 0
        for (sample in sampleList) { sample.copyInto(samples, i); i += sample.size }
        if (samples.isEmpty()) throw IllegalArgumentException("samples is empty")

        val result = offlineInferNative(handle, samples, prompt, languages, bailLanguages)?.trim()
            ?: throw IllegalStateException("Inference failed")

        when {
            result.contains("<>CANCELLED<>") && result.contains("flag") ->
                throw InferenceCancelledException()
            result.contains("<>CANCELLED<>") && result.contains("lang=") ->
                throw BailLanguageException(result.split("lang=")[1], samples)
            result.contains("<>CANCELLED<>") ->
                throw IllegalStateException("Cancelled for unknown reason")
            else -> result
        }
    }

    fun cancel() { if (handle != 0L) cancelNative(handle) }

    suspend fun close() = withContext(inferenceContext) {
        if (handle != 0L) { closeNative(handle); handle = 0L }
        pfd?.close()
    }

    private external fun openNative(path: String): Long
    private external fun offlineInferNative(handle: Long, samples: FloatArray, prompt: String, languages: Array<String>, bailLanguages: Array<String>): String?
    private external fun startStreamNative(handle: Long, prompt: String, languages: Array<String>, bailLanguages: Array<String>, tier: Int): Boolean
    private external fun feedStreamNative(handle: Long, samples: FloatArray): Boolean
    private external fun stopStreamNative(handle: Long, finalize: Boolean): String?
    private external fun cancelNative(handle: Long)
    private external fun closeNative(handle: Long)
}

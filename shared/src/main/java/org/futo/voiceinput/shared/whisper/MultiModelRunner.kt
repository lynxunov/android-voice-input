package org.futo.voiceinput.shared.whisper

import android.util.Log
import androidx.collection.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.futo.voiceinput.shared.ggml.BailLanguageException
import org.futo.voiceinput.shared.ggml.InferenceCancelledException
import org.futo.voiceinput.shared.types.InferenceState
import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.types.ModelInferenceCallback
import org.futo.voiceinput.shared.types.ModelLoader
import org.futo.voiceinput.shared.types.getLanguageFromWhisperString
import org.futo.voiceinput.shared.types.toWhisperString


data class MultiModelRunConfiguration(
    val primaryModel: ModelLoader,
    val languageSpecificModels: Map<Language, ModelLoader>
)

data class DecodingConfiguration(
    val glossary: List<String>,
    val languages: Set<Language>,
    val suppressSymbols: Boolean,
    val tier: Int,
    /** Fork: split the recording at pauses and detect the language per chunk (mixed-language dictation). */
    val segmentByPauses: Boolean = false,
    /** Fork: in mixed recordings, still switch to per-language models (slower). Default: one multilingual model. */
    val perLanguageModelsInMixed: Boolean = false,
    /** Fork: silence length (ms) that splits chunks. */
    val pauseGapMs: Int = 350,
    /** Fork: single-language recordings: drop long silences before processing. */
    val cutLongPauses: Boolean = false,
    /** Fork: drop chunks that contain no speech (noise) before transcription. */
    val ignoreNonSpeech: Boolean = true,
    /** Fork: give each chunk the text so far as context. */
    val useChunkContext: Boolean = true,
    /** Fork: if two languages are given, mixed-language chunks are detected among these only. */
    val primaryLanguages: Set<Language> = emptySet(),
    /** Fork: dictionary / blocklist entries. */
    val dictionary: List<DictionaryEntry> = emptyList(),
)

private fun<T> MutableSet<T>.flatMapInPlace(transformator: (T) -> Iterable<T>) {
    val result = flatMap(transformator)
    clear()
    addAll(result)
}

object GlossaryProcessor {
    private val cache = LruCache<String, Glossary>(6)
    class Glossary(val compiledWords: String, private val transformationRules: List<Pair<Regex, String>>) {
        fun processResult(result: String) =
            transformationRules.fold(result) { v, rule -> rule.first.replace(v, rule.second) }
    }


    private fun wordToRegex(word: String) = "\\b${word.replace(" ", "[\\.,\\?!\\s]+")}\\b"
        .toRegex(RegexOption.IGNORE_CASE)

    fun compileGlossary(model: ModelLoader, languages: Set<Language>, words: List<String>): Glossary {
        val cacheKey = words.joinToString("\n") + model.caps.requiresPersonalDictSpecialCharacterProcessing + languages
        return cache[cacheKey] ?: run {
            val transformationRules = mutableListOf<Pair<Regex, String>>()
            val compiledWords = buildString {
                for (word in words) {
                    val transformedWords = mutableSetOf(word)
                    if (Language.English in languages && model.caps.requiresPersonalDictSpecialCharacterProcessing) {
                        val rules = listOf(
                            "@" to setOf("at", "at the"),
                            ".com" to setOf("dot com", "dotcom"), // asr4all likes dotcom sometimes :)
                            "." to setOf("dot"), // period?
                            "/" to setOf("slash", "or"),
                            "+" to setOf("plus"),
                            "-" to setOf("minus", "dash", ""),
                            ":" to setOf("colon"),

                            "10" to setOf("ten", "one zero"),
                            "11" to setOf("eleven", "one one"),
                            "12" to setOf("twelve", "one two"),
                            "13" to setOf("thirteen", "one three"),
                            "14" to setOf("fourteen", "one four"),
                            "15" to setOf("fifteen", "one five"),
                            "16" to setOf("sixteen", "one six"),
                            "17" to setOf("seventeen", "one seven"),
                            "18" to setOf("eighteen", "one eight"),
                            "19" to setOf("nineteen", "one nine"),

                            "0" to setOf("zero"),
                            "1" to setOf("one"),
                            "2" to setOf("two"),
                            "3" to setOf("three"),
                            "4" to setOf("four"),
                            "5" to setOf("five"),
                            "6" to setOf("six"),
                            "7" to setOf("seven"),
                            "8" to setOf("eight"),
                            "9" to setOf("nine")
                        )

                        rules.forEach { (character, replacements) ->
                            if (transformedWords.any { character in it }) {
                                transformedWords.flatMapInPlace {
                                    replacements.map { substitution ->
                                        it.replace(
                                            character,
                                            " $substitution "
                                        ).replace("  ", " ").trim()
                                    }
                                }
                            }
                        }
                    }

                    transformedWords.forEach {
                        append(it)
                        append('\n')

                        // PCEC may insert punctuation in long stuff like "Adamson one, two, three at Gmail dot com"
                        // so this evil regex is needed to fix it
                        if (it != word || it.any(Char::isUpperCase) || it.contains(' '))
                            transformationRules.add(wordToRegex(it) to word)
                    }
                }

                if ((Language.English in languages || Language.Spanish in languages)  && model.caps.requiresPersonalDictSpecialCharacterProcessing) {
                    for(word in listOf("FUTO Voice Input", "FUTO Keyboard", "FUTO")) {
                        append(word)
                        append('\n')
                        transformationRules.add(wordToRegex(word) to word)
                    }
                }
            }

            val glossary = Glossary(compiledWords, transformationRules)
            cache.put(cacheKey, glossary)

            glossary
        }
    }
}

class MultiModelRunner(
    private val modelManager: ModelManager
) {
    suspend fun preload(runConfiguration: MultiModelRunConfiguration) = coroutineScope {
        val jobs = mutableListOf<Job>()

        jobs.add(launch(Dispatchers.Default) {
            modelManager.obtainModel(runConfiguration.primaryModel)
        })

        if (runConfiguration.languageSpecificModels.count() < 2) {
            runConfiguration.languageSpecificModels.forEach {
                jobs.add(launch(Dispatchers.Default) {
                    modelManager.obtainModel(it.value)
                })
            }
        }

        jobs.joinAll()
    }

    @Throws(InferenceCancelledException::class)
    suspend fun run(
        sampleFlow: Flow<FloatArray>,
        runConfiguration: MultiModelRunConfiguration,
        decodingConfiguration: DecodingConfiguration,
        callback: ModelInferenceCallback
    ): String = coroutineScope {
        callback.updateStatus(InferenceState.LoadingModel)
        val primaryModel = modelManager.obtainModel(runConfiguration.primaryModel)

        val allowedLanguages = decodingConfiguration.languages.map { it.toWhisperString() }.toTypedArray()
        val bailLanguages = runConfiguration.languageSpecificModels.filter { it.value != runConfiguration.primaryModel }.keys.map { it.toWhisperString() }.toTypedArray()

        val languageCodes = allowedLanguages.toList()
        val glossary = GlossaryProcessor.compileGlossary(
            runConfiguration.primaryModel,
            decodingConfiguration.languages,
            (decodingConfiguration.glossary + decodingConfiguration.dictionary.termsFor(DictKind.WORD, languageCodes)).distinct()
        )

        // Result clean-up: spelling from the dictionary, no stray writing systems, then blocklist.
        fun tidy(text: String) = ScriptFilter.apply(glossary.processResult(text), languageCodes)
        val blockAll = decodingConfiguration.dictionary.termsFor(DictKind.BLOCK, languageCodes)
        fun clean(text: String) = Blocklist.apply(tidy(text), blockAll)

        // A flow that can be collected only once, like the live audio stream of the classic path.
        fun oneShot(chunk: FloatArray): Flow<FloatArray> {
            val channel = kotlinx.coroutines.channels.Channel<FloatArray>(1)
            channel.trySend(chunk)
            channel.close()
            return channel.receiveAsFlow()
        }

        // Upstream behaviour: one language for the whole recording; bail to a per-language model if needed.
        suspend fun classic(flow: Flow<FloatArray>): String = try {
            callback.updateStatus(InferenceState.Encoding)
            primaryModel.infer(
                sampleFlow = flow,
                prompt = glossary.compiledWords,
                languages = allowedLanguages,
                bailLanguages = bailLanguages,
                partialResultCallback = {
                    callback.partialResult(clean(it))
                },
                tier = decodingConfiguration.tier,
            )
        } catch(e: BailLanguageException) {
            callback.updateStatus(InferenceState.SwitchingModel)
            val language = getLanguageFromWhisperString(e.language)

            val specificModelLoader = runConfiguration.languageSpecificModels[language]!!
            val specificModel = modelManager.obtainModel(specificModelLoader)

            val remainder = flow.toList()
            specificModel.infer(
                sampleFlow = (listOf(e.samples) + remainder).asFlow(),
                prompt = glossary.compiledWords,
                languages = arrayOf(e.language),
                bailLanguages = arrayOf(),
                partialResultCallback = {
                    callback.partialResult(clean(it))
                },
                tier = 3,//decodingConfiguration.tier,
            )
        }

        val caps = runConfiguration.primaryModel.caps
        val speechFilter: ((FloatArray) -> Boolean)? = if (decodingConfiguration.ignoreNonSpeech) ::chunkContainsSpeech else null
        val segmented = decodingConfiguration.segmentByPauses &&
                allowedLanguages.size > 1 &&
                caps.supportsLanguageDetection &&
                !caps.supportsStreaming

        val result = if (!segmented) {
            if (decodingConfiguration.cutLongPauses && !caps.supportsStreaming) {
                // Silence costs processing time but carries no words: keep only speech with short pauses.
                val parts = sampleFlow.segmentByPauses(decodingConfiguration.pauseGapMs, speechFilter).toList()
                val trimmed = FloatArray(parts.sumOf { it.size })
                var at = 0
                for (part in parts) { part.copyInto(trimmed, at); at += part.size }
                classic(if (trimmed.isEmpty()) emptyFlow() else oneShot(trimmed))
            } else classic(sampleFlow.filter { it.isNotEmpty() })
        } else {
            // Fork: pause-segmented, per-chunk language detection.
            // The first chunk is held back until a second one exists: a recording with a single chunk
            // takes the classic path (per-language model if configured), a longer one stays on the
            // multilingual model so no model has to be swapped mid-recording.
            // Optional primary pair: chunks are detected between these two languages only.
            val chunkLanguages = decodingConfiguration.primaryLanguages
                .map { it.toWhisperString() }.filter { it in allowedLanguages }.toTypedArray().let { if (it.size >= 2) it else allowedLanguages }
            val mixedBail = if (decodingConfiguration.perLanguageModelsInMixed) bailLanguages else arrayOf()
            val texts = mutableListOf<String>()
            var pending: FloatArray? = null
            var count = 0

            suspend fun runChunk(chunk: FloatArray, part: Int, total: Int) {
                val shown = { partial: String ->
                    callback.partialResult(tidy((texts + partial).joinToString(" ")))
                }
                // Earlier text gives Whisper context (names, topic, spelling) for short chunks.
                val chunkPrompt = if (decodingConfiguration.useChunkContext && texts.isNotEmpty())
                    glossary.compiledWords + "\u0001" + texts.joinToString(" ").takeLast(200)
                else glossary.compiledWords
                val text = try {
                    callback.updateStatus(InferenceState.Encoding)
                    callback.progress(part, total)
                    primaryModel.infer(
                        sampleFlow = flowOf(chunk),
                        prompt = chunkPrompt,
                        languages = chunkLanguages,
                        bailLanguages = mixedBail,
                        partialResultCallback = shown,
                        tier = 3,
                    )
                } catch (e: BailLanguageException) {
                    callback.updateStatus(InferenceState.SwitchingModel)
                    val language = getLanguageFromWhisperString(e.language)
                    val loader = runConfiguration.languageSpecificModels[language]
                    if (loader == null) throw e
                    modelManager.obtainModel(loader).infer(
                        sampleFlow = flowOf(e.samples),
                        prompt = chunkPrompt,
                        languages = arrayOf(e.language),
                        bailLanguages = arrayOf(),
                        partialResultCallback = shown,
                        tier = 3,
                    )
                }
                val detected = primaryModel.detectedLanguage()
                detected?.let { getLanguageFromWhisperString(it) }?.let { callback.languageDetected(it) }
                // Blocklist entries of the detected language (or of all enabled ones, if unknown)
                val blockHere = decodingConfiguration.dictionary.termsFor(
                    DictKind.BLOCK, if (detected != null && detected in languageCodes) listOf(detected) else languageCodes
                )
                val cleaned = Blocklist.apply(tidy(collapseRepeat(text.trim())), blockHere)
                if (cleaned.isNotBlank()) texts.add(cleaned)
            }

            // Producer cuts chunks as audio arrives; consumer transcribes them in order. After the user
            // stops, the producer finishes almost instantly, so the total is known for the progress text.
            val chunks = kotlinx.coroutines.channels.Channel<FloatArray>(kotlinx.coroutines.channels.Channel.UNLIMITED)
            var produced = 0
            val producer = launch {
                try {
                    sampleFlow.segmentByPauses(decodingConfiguration.pauseGapMs, speechFilter).collect { produced++; chunks.send(it) }
                } finally { chunks.close() }
            }
            for (chunk in chunks) {
                count++
                when (count) {
                    1 -> pending = chunk
                    2 -> { runChunk(pending!!, 1, produced); pending = null; runChunk(chunk, 2, produced) }
                    else -> runChunk(chunk, count, produced)
                }
            }
            producer.join()

            when (count) {
                0 -> classic(emptyFlow())
                1 -> classic(oneShot(pending!!))
                else -> texts.joinToString(" ")
            }
        }

        return@coroutineScope clean(result)
    }

    fun cancelAll() {
        modelManager.cancelAll()
    }
}

/**
 * Whisper sometimes outputs a short utterance twice ("This is a test. This is a test.") when audio starts
 * abruptly. If a text is exactly one passage repeated twice (>= 3 words each), keep one copy.
 */
internal fun collapseRepeat(text: String): String {
    val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.size < 6 || words.size % 2 != 0) return text
    val half = words.size / 2
    fun norm(w: String) = w.lowercase().trim { !it.isLetterOrDigit() }
    for (i in 0 until half) if (norm(words[i]) != norm(words[i + half])) return text
    return words.take(half).joinToString(" ")
}

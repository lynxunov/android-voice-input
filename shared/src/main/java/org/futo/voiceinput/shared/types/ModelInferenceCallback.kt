package org.futo.voiceinput.shared.types

enum class InferenceState {
    ExtractingMel, LoadingModel, Encoding, DecodingLanguage, SwitchingModel, DecodingStarted
}

interface ModelInferenceCallback {
    fun updateStatus(state: InferenceState)
    fun languageDetected(language: Language)
    fun partialResult(string: String)

    /** Fork: which chunk of a pause-segmented recording is being processed (1-based). */
    fun progress(part: Int, total: Int) {}
}
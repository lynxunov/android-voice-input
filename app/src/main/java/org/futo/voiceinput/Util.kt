package org.futo.voiceinput

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import org.futo.voiceinput.models.ModelPickerActivity
import org.futo.voiceinput.settings.ENGLISH_MODEL_INDEX
import org.futo.voiceinput.settings.LANGUAGE_TOGGLES
import org.futo.voiceinput.settings.MANUALLY_SELECT_LANGUAGE
import org.futo.voiceinput.settings.MULTILINGUAL_MODEL_INDEX
import org.futo.voiceinput.settings.USE_LANGUAGE_SPECIFIC_MODELS
import org.futo.voiceinput.settings.getSetting

enum class Status {
    Unknown,
    False,
    True;

    companion object {
        fun from(found: Boolean): Status = if (found) True else False
    }
}

data class ModelDigests(
    val encoder_digest: String,
    val decoder_digest: String,
    val vocab_digest: String
)

enum class PromptingStyle {
    // <|startoftranscript|><|notimestamps|> Text goes here.<|endoftext|>
    SingleLanguageOnly,
    // <|startoftranscript|><|en|><|transcribe|><|notimestamps|> Text goes here.<|endoftext|>
    LanguageTokenAndAction,
}

data class ModelDataLegacy(
    val is_builtin_asset: Boolean,
    val encoder_xatn_file: String,
    val decoder_file: String,
    val vocab_file: String,
    val vocab_raw_asset: Int? = null,
    val digests: ModelDigests,
    val promptingStyle: PromptingStyle
)

data class ModelDataGGML(
    val is_builtin_asset: Boolean,
    val ggml_file: String,
    val digest: String
)

data class ModelData(
    val name: String,
    val ggml: ModelDataGGML,
    val legacy: ModelDataLegacy
)

// ── Array helpers ─────────────────────────────────────────────────────────────

fun Array<DoubleArray>.transpose(): Array<DoubleArray> =
    Array(this[0].size) { i -> DoubleArray(this.size) { j -> this[j][i] } }

fun Array<DoubleArray>.shape(): IntArray = arrayOf(size, this[0].size).toIntArray()

fun DoubleArray.toFloatArray(): FloatArray = this.map { it.toFloat() }.toFloatArray()
fun FloatArray.toDoubleArray(): DoubleArray = this.map { it.toDouble() }.toDoubleArray()

// ── Activity helpers ──────────────────────────────────────────────────────────

/**
 * Opens the Model Manager screen (SAF-based picker).
 * Replaces the old startModelDownloadActivity — no network involved.
 */
fun Context.startModelPickerActivity() {
    startAppActivity(ModelPickerActivity::class.java)
}

fun <T> Context.startAppActivity(activity: Class<T>, clearTop: Boolean = false) {
    val intent = Intent(this, activity)
    if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (clearTop) {
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    startActivity(intent)
}

fun Context.openURI(uri: String, newTask: Boolean = false) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        if (newTask) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, e.localizedMessage, Toast.LENGTH_SHORT).show()
    }
}

// ── Language list ─────────────────────────────────────────────────────────────

// Numbers from Appendix E. Figure 11. https://cdn.openai.com/papers/whisper.pdf
// Trained hours are for transcribing; translation hours not included.
data class LanguageEntry(val id: String, val name: String, val trainedHourCount: Int)

val LANGUAGE_LIST = listOf(
    LanguageEntry("en", "English",       438218),
    LanguageEntry("zh", "Chinese",        23446),
    LanguageEntry("de", "German",         13344),
    LanguageEntry("es", "Spanish",        11100),
    LanguageEntry("ru", "Russian",         9761),
    LanguageEntry("fr", "French",          9752),
    LanguageEntry("pt", "Portuguese",      8573),
    LanguageEntry("ko", "Korean",          7993),
    LanguageEntry("ja", "Japanese",        7054),
    LanguageEntry("tr", "Turkish",         4333),
    LanguageEntry("pl", "Polish",          4278),
    LanguageEntry("it", "Italian",         2585),
    LanguageEntry("sv", "Swedish",         2119),
    LanguageEntry("nl", "Dutch",           2077),
    LanguageEntry("ca", "Catalan",         1883),
    LanguageEntry("fi", "Finnish",         1066),
    LanguageEntry("id", "Indonesian",      1014),
    LanguageEntry("ar", "Arabic",           739),
    LanguageEntry("uk", "Ukrainian",        697),
    LanguageEntry("vi", "Vietnamese",       691),
    LanguageEntry("he", "Hebrew",           688),
    LanguageEntry("el", "Greek",            529),
    LanguageEntry("da", "Danish",           473),
    LanguageEntry("ms", "Malay",            382),
    LanguageEntry("hu", "Hungarian",        379),
    LanguageEntry("ro", "Romanian",         356),
    LanguageEntry("no", "Norwegian",        266),
    LanguageEntry("th", "Thai",             226),
    LanguageEntry("cs", "Czech",            192),
    LanguageEntry("ta", "Tamil",            134),
    LanguageEntry("ur", "Urdu",             104),
    LanguageEntry("hr", "Croatian",          91),
    LanguageEntry("sk", "Slovak",            90),
    LanguageEntry("bg", "Bulgarian",         86),
    LanguageEntry("tl", "Tagalog",           75),
    LanguageEntry("cy", "Welsh",             73),
    LanguageEntry("lt", "Lithuanian",        67),
    LanguageEntry("lv", "Latvian",           65),
    LanguageEntry("az", "Azerbaijani",       47),
    LanguageEntry("et", "Estonian",          41),
    LanguageEntry("sl", "Slovenian",         41),
    LanguageEntry("sr", "Serbian",           28),
    LanguageEntry("fa", "Persian",           24),
    LanguageEntry("eu", "Basque",            21),
    LanguageEntry("is", "Icelandic",         16),
    LanguageEntry("mk", "Macedonian",        16),
    LanguageEntry("hy", "Armenian",          13),
    LanguageEntry("kk", "Kazakh",            12),
    LanguageEntry("hi", "Hindi",             12),
    LanguageEntry("bs", "Bosnian",           11),
    LanguageEntry("gl", "Galician",           9),
    LanguageEntry("sq", "Albanian",           6),
    LanguageEntry("si", "Sinhala",            5),
    LanguageEntry("sw", "Swahili",            5),
    LanguageEntry("te", "Telugu",             4),
    LanguageEntry("af", "Afrikaans",          4),
    LanguageEntry("kn", "Kannada",            4),
    LanguageEntry("be", "Belarusian",         2),
    LanguageEntry("km", "Khmer",              1),
    LanguageEntry("bn", "Bengali",            1),
    LanguageEntry("mt", "Maltese",            1),
    LanguageEntry("ht", "Haitian Creole",     1),
    LanguageEntry("pa", "Punjabi",            1),
    LanguageEntry("mr", "Marathi",            1),
    LanguageEntry("ne", "Nepali",             1),
    LanguageEntry("ka", "Georgian",           1),
    LanguageEntry("ml", "Malayalam",          1),
    // < 0.5 h of training data:
    LanguageEntry("yi", "Yiddish",            0),
    LanguageEntry("uz", "Uzbek",              0),
    LanguageEntry("gu", "Gujarati",           0),
    LanguageEntry("tg", "Tajik",              0),
    LanguageEntry("mg", "Malagasy",           0),
    LanguageEntry("my", "Burmese",            0),
    LanguageEntry("su", "Sundanese",          0),
    LanguageEntry("lo", "Lao",                0)
)

// ── Model definitions ─────────────────────────────────────────────────────────

val ENGLISH_MODELS = listOf(
    ModelData(
        name = "English-39 (default)",
        ggml = ModelDataGGML(
            is_builtin_asset = true,
            ggml_file = "tiny_en_acft_q8_0.bin.not.tflite",
            digest = "4b5480aa1b14a7efc5b578ef176510970a898049671c3cd237285b3e3f6bfbfc"
        ),
        legacy = ModelDataLegacy(
            is_builtin_asset = false,
            encoder_xatn_file = "tiny-en-encoder-xatn.tflite",
            decoder_file      = "tiny-en-decoder.tflite",
            vocab_file        = "tinyenvocab.json",
            digests           = ModelDigests("", "", ""),
            promptingStyle    = PromptingStyle.SingleLanguageOnly
        )
    ),
    ModelData(
        name = "English-74 (slower, more accurate)",
        ggml = ModelDataGGML(
            is_builtin_asset = false,
            ggml_file = "base_en_acft_q8_0.bin",
            digest = "e9b4b7b81b8a28769e8aa9962aa39bb9f21b622cf6a63982e93f065ed5caf1c8"
        ),
        legacy = ModelDataLegacy(
            is_builtin_asset = false,
            encoder_xatn_file = "base.en-encoder-xatn.tflite",
            decoder_file      = "base.en-decoder.tflite",
            vocab_file        = "base.en-vocab.json",
            digests           = ModelDigests(
                "c94bcafb3cd95c193ca5ada5b94e517f1645dbf72e72986f55c6c8729d04da23",
                "d6979b4a06416ff1d3e38a238997e4051684c60aa2fcab6ae7d7dbafab75494f",
                "48d9307faad7c1c1a708fbfa7f4b57cb6d5936ceee4cdf354e2b7d8cdf0cf24b"
            ),
            promptingStyle = PromptingStyle.SingleLanguageOnly
        )
    ),
    ModelData(
        name = "English-244 (slow)",
        ggml = ModelDataGGML(
            is_builtin_asset = false,
            ggml_file = "small_en_acft_q8_0.bin",
            digest = "58fbe949992dafed917590d58bc12ca577b08b9957f0b3e0d7ee71b64bed3aa8"
        ),
        legacy = ModelDataLegacy(
            is_builtin_asset = false,
            encoder_xatn_file = "base.en-encoder-xatn.tflite",
            decoder_file      = "base.en-decoder.tflite",
            vocab_file        = "base.en-vocab.json",
            digests           = ModelDigests(
                "c94bcafb3cd95c193ca5ada5b94e517f1645dbf72e72986f55c6c8729d04da23",
                "d6979b4a06416ff1d3e38a238997e4051684c60aa2fcab6ae7d7dbafab75494f",
                "48d9307faad7c1c1a708fbfa7f4b57cb6d5936ceee4cdf354e2b7d8cdf0cf24b"
            ),
            promptingStyle = PromptingStyle.SingleLanguageOnly
        )
    )
)

val MULTILINGUAL_MODELS = listOf(
    ModelData(
        name = "Multilingual-39 (less accurate)",
        ggml = ModelDataGGML(
            is_builtin_asset = false,
            ggml_file = "tiny_acft_q8_0.bin",
            digest = "07aa4d514144deacf5ffec5cacb36c93dee272fda9e64ac33a801f8cd5cbd953"
        ),
        legacy = ModelDataLegacy(
            is_builtin_asset = false,
            encoder_xatn_file = "tiny-multi-encoder-xatn.tflite",
            decoder_file      = "tiny-multi-decoder.tflite",
            vocab_file        = "tiny-multi-vocab.json",
            digests           = ModelDigests(
                "1240660ec64f549052cd469aef7ee6ff30ecd9bf45dafcf330f2a77d20f081ee",
                "1c19e4d891a9bb976023bdacefa475a9cefb522c3c1d722f2b98a1e3e08d4a2c",
                "bd5b181b5ea2b5ea58b1f4ef8c48c7636e66443c212a5d9fb4dfe5bae15d6055"
            ),
            promptingStyle = PromptingStyle.LanguageTokenAndAction
        )
    ),
    ModelData(
        name = "Multilingual-74 (default)",
        ggml = ModelDataGGML(
            is_builtin_asset = false,
            ggml_file = "base_acft_q8_0.bin",
            digest = "e44f352c9aa2c3609dece20c733c4ad4a75c28cd9ab07d005383df55fa96efc4"
        ),
        legacy = ModelDataLegacy(
            is_builtin_asset = false,
            encoder_xatn_file = "base-encoder-xatn.tflite",
            decoder_file      = "base-decoder.tflite",
            vocab_file        = "base-vocab.json",
            digests           = ModelDigests(
                "8832248eefbc1b7a297ac12358357001c613da4183099966fbb6950079d252f8",
                "3369ab7e0ec7ebf828cef7a5740a6d32e1e90502737b89812e383c76041f878b",
                "bd5b181b5ea2b5ea58b1f4ef8c48c7636e66443c212a5d9fb4dfe5bae15d6055"
            ),
            promptingStyle = PromptingStyle.LanguageTokenAndAction
        )
    ),
    ModelData(
        name = "Multilingual-244 (slow)",
        ggml = ModelDataGGML(
            is_builtin_asset = false,
            ggml_file = "small_acft_q8_0.bin",
            digest = "15ef255465a6dc582ecf1ec651a4618c7ee2c18c05570bbe46493d248d465ac4"
        ),
        legacy = ModelDataLegacy(
            is_builtin_asset = false,
            encoder_xatn_file = "small-encoder-xatn.tflite",
            decoder_file      = "small-decoder.tflite",
            vocab_file        = "small-vocab.json",
            digests           = ModelDigests(
                "03e141a363cbb983799dbf589e53298324bc1dc906eb8fabc8a412d40338f0d9",
                "1dbdeac1c0fabede5aa57424b7e1e8061f34c6f646fa1031e8aead20a25f4e41",
                "bd5b181b5ea2b5ea58b1f4ef8c48c7636e66443c212a5d9fb4dfe5bae15d6055"
            ),
            promptingStyle = PromptingStyle.LanguageTokenAndAction
        )
    )
)

// ── Language → model mapping ───────────────────────────────────────────────────

suspend fun Context.getLanguageModelMap(): Map<String, ModelData> {
    val modelIdx         = getSetting(MULTILINGUAL_MODEL_INDEX)
    val englishModelIdx  = getSetting(ENGLISH_MODEL_INDEX)
    val manuallySelect   = getSetting(MANUALLY_SELECT_LANGUAGE)
    val languages        = getSetting(LANGUAGE_TOGGLES)

    val map = hashMapOf<String, ModelData>()
    languages.forEach { lang ->
        map[lang] = if (lang == "en") ENGLISH_MODELS[englishModelIdx]
                    else              MULTILINGUAL_MODELS[modelIdx]
    }

    // "unk" slot used for initial language detection when multiple languages are enabled
    if (languages.size > 1 && !manuallySelect) {
        map["unk"] = MULTILINGUAL_MODELS[modelIdx]
    }

    return map
}

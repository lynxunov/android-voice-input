package org.futo.voiceinput.shared.whisper

import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Fork addition: dictionaries + blocklist.
 *
 * One entry groups all spellings of the same thing across languages ("Venlafaxin" for de, "venlafaxine"
 * for en). The special language code [ALL] is a spelling used for every language, except those listed in
 * [excluded]. WORD entries help recognition (prompt + spelling fix); BLOCK entries are removed from the result.
 */
enum class DictKind { WORD, BLOCK }

data class DictionaryEntry(
    val kind: DictKind,
    val variants: Map<String, String>,
    val excluded: Set<String> = emptySet(),
) {
    fun termFor(lang: String): String? {
        variants[lang]?.let { return it }
        val universal = variants[ALL]
        return if (universal != null && lang !in excluded) universal else null
    }

    companion object {
        const val ALL = "*"
    }
}

fun List<DictionaryEntry>.termsFor(kind: DictKind, langs: Collection<String>): List<String> =
    filter { it.kind == kind }
        .flatMap { e -> langs.mapNotNull { e.termFor(it) } }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

object DictionaryJson {
    fun parse(json: String): List<DictionaryEntry> = try {
        if (json.isBlank()) emptyList() else {
            val array = JSONObject(json).getJSONArray("entries")
            (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                val kind = if (o.optString("kind") == "block") DictKind.BLOCK else DictKind.WORD
                val v = o.getJSONObject("variants")
                val variants = v.keys().asSequence().associateWith { v.getString(it) }.filterValues { it.isNotBlank() }
                val ex = o.optJSONArray("excluded")
                val excluded = if (ex == null) emptySet() else (0 until ex.length()).map { ex.getString(it) }.toSet()
                if (variants.isEmpty()) null else DictionaryEntry(kind, variants, excluded)
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun toJson(entries: List<DictionaryEntry>): String {
        val array = JSONArray()
        entries.forEach { e ->
            array.put(JSONObject().apply {
                put("kind", if (e.kind == DictKind.BLOCK) "block" else "word")
                put("variants", JSONObject().apply { e.variants.forEach { (k, v) -> put(k, v) } })
                put("excluded", JSONArray(e.excluded.toList()))
            })
        }
        return JSONObject().put("entries", array).toString()
    }
}

object Blocklist {
    /** Removes whole-word, case-insensitive occurrences of [terms] and tidies spaces/punctuation left behind. */
    fun apply(text: String, terms: List<String>): String {
        if (terms.isEmpty() || text.isEmpty()) return text
        var result = text
        for (term in terms.sortedByDescending { it.length }) {
            val body = term.trim().split(Regex("\\s+")).joinToString("\\s+") { Pattern.quote(it) }
            val regex = Regex("(?<![\\p{L}\\p{N}])$body(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
            result = regex.replace(result, "")
        }
        return result
            .replace(Regex("\\s{2,}"), " ")
            .replace(Regex("\\s+([,.;:!?])"), "$1")
            .replace(Regex("([,;])\\s*([,;.!?])"), "$2")
            .trim()
            .trimStart(',', ';', ':')
            .trim()
    }
}

/**
 * Whisper occasionally emits characters of a writing system nobody asked for (CJK on noise, for example).
 * A script is only removed if none of the enabled languages uses it.
 */
object ScriptFilter {
    private val families: List<Pair<Set<String>, Set<Character.UnicodeScript>>> = listOf(
        setOf("zh", "yue", "ja") to setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA),
        setOf("ko") to setOf(Character.UnicodeScript.HANGUL),
        setOf("ru", "uk", "bg", "sr", "mk", "be", "kk", "mn", "tg", "ky", "tt", "ba") to setOf(Character.UnicodeScript.CYRILLIC),
        setOf("ar", "fa", "ur", "ps", "sd") to setOf(Character.UnicodeScript.ARABIC),
        setOf("he", "yi") to setOf(Character.UnicodeScript.HEBREW),
        setOf("th") to setOf(Character.UnicodeScript.THAI),
        setOf("hi", "mr", "ne", "sa") to setOf(Character.UnicodeScript.DEVANAGARI),
        setOf("bn", "as") to setOf(Character.UnicodeScript.BENGALI),
        setOf("ta") to setOf(Character.UnicodeScript.TAMIL),
        setOf("te") to setOf(Character.UnicodeScript.TELUGU),
        setOf("kn") to setOf(Character.UnicodeScript.KANNADA),
        setOf("ml") to setOf(Character.UnicodeScript.MALAYALAM),
        setOf("gu") to setOf(Character.UnicodeScript.GUJARATI),
        setOf("pa") to setOf(Character.UnicodeScript.GURMUKHI),
        setOf("si") to setOf(Character.UnicodeScript.SINHALA),
        setOf("el") to setOf(Character.UnicodeScript.GREEK),
    )

    fun apply(text: String, enabledCodes: Collection<String>): String {
        val forbidden = HashSet<Character.UnicodeScript>()
        for ((codes, scripts) in families) if (enabledCodes.none { it in codes }) forbidden.addAll(scripts)
        if (forbidden.isEmpty()) return text
        val sb = StringBuilder()
        var removed = false
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val n = Character.charCount(cp)
            val script = try { Character.UnicodeScript.of(cp) } catch (_: Exception) { null }
            if (script != null && script in forbidden) removed = true else sb.appendCodePoint(cp)
            i += n
        }
        return if (removed) sb.toString().replace(Regex("\\s{2,}"), " ").trim() else text
    }
}

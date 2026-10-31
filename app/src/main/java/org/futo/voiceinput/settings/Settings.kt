package org.futo.voiceinput.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.futo.voiceinput.theme.presets.VoiceInputTheme

suspend fun <T> Context.getSetting(key: Preferences.Key<T>, default: T): T {
    val valueFlow: Flow<T> = dataStore.data.map { it[key] ?: default }.take(1)
    return valueFlow.first()
}

fun <T> Context.getSettingFlow(key: Preferences.Key<T>, default: T): Flow<T> =
    dataStore.data.map { it[key] ?: default }.take(1)

suspend fun <T> Context.setSetting(key: Preferences.Key<T>, value: T) {
    dataStore.edit { it[key] = value }
}

fun <T> Context.getSettingBlocking(key: Preferences.Key<T>, default: T): T =
    runBlocking { getSetting(key, default) }

fun <T> Context.setSettingBlocking(key: Preferences.Key<T>, value: T) =
    runBlocking { setSetting(key, value) }

fun <T> LifecycleOwner.deferGetSetting(key: Preferences.Key<T>, default: T, onObtained: (T) -> Unit): Job {
    val context = (this as Context)
    return lifecycleScope.launch {
        withContext(Dispatchers.Default) {
            val value = context.getSetting(key, default)
            withContext(Dispatchers.Main) { onObtained(value) }
        }
    }
}

fun <T> LifecycleOwner.deferSetSetting(key: Preferences.Key<T>, value: T): Job {
    val context = (this as Context)
    return lifecycleScope.launch { withContext(Dispatchers.Default) { context.setSetting(key, value) } }
}

data class SettingsKey<T>(val key: Preferences.Key<T>, val default: T)

suspend fun <T> Context.getSetting(key: SettingsKey<T>): T = getSetting(key.key, key.default)
fun <T> Context.getSettingFlow(key: SettingsKey<T>): Flow<T> = getSettingFlow(key.key, key.default)
suspend fun <T> Context.setSetting(key: SettingsKey<T>, value: T) = setSetting(key.key, value)
fun <T> LifecycleOwner.deferGetSetting(key: SettingsKey<T>, onObtained: (T) -> Unit): Job =
    deferGetSetting(key.key, key.default, onObtained)
fun <T> LifecycleOwner.deferSetSetting(key: SettingsKey<T>, value: T): Job =
    deferSetSetting(key.key, value)

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

// ── Audio / UI ────────────────────────────────────────────────────────────────
val ENABLE_SOUND          = SettingsKey(booleanPreferencesKey("enable_sounds"),       true)
val ENABLE_ANIMATIONS     = SettingsKey(booleanPreferencesKey("enable_animations"),   true)
val VERBOSE_PROGRESS      = SettingsKey(booleanPreferencesKey("verbose_progress"),    false)
val DISALLOW_SYMBOLS      = SettingsKey(booleanPreferencesKey("disallow_symbols"),    true)
val ENABLE_30S_LIMIT      = SettingsKey(booleanPreferencesKey("enable_30s_limit"),    false)
val IS_VAD_ENABLED        = SettingsKey(booleanPreferencesKey("enable_vad"),          true)
val BEAM_SEARCH           = SettingsKey(booleanPreferencesKey("use_beam_search"),     true)
val PREFER_BLUETOOTH_MIC  = SettingsKey(booleanPreferencesKey("prefer_bluetooth_mic"), false)

// ── Streaming ─────────────────────────────────────────────────────────────────
/**
 * Streaming tier: 0 = auto (default, enabled if model supports it), 1 = word-level,
 * 2 = sentence-level, 3 = offline only (no streaming).
 * On by default (tier 0) per user preference; models that don't support streaming fall back to offline.
 */
val STREAMING_TIER        = SettingsKey(intPreferencesKey("streaming_tier"),          0)

// ── Language display ──────────────────────────────────────────────────────────
/** Show the detected language(s) during processing. */
val SHOW_DETECTED_LANGUAGES = SettingsKey(booleanPreferencesKey("show_detected_langs"), false)

// ── Language / model selection ────────────────────────────────────────────────
val ENABLE_MULTILINGUAL          = SettingsKey(booleanPreferencesKey("enable_multilingual"),          false)
val USE_LANGUAGE_SPECIFIC_MODELS = SettingsKey(booleanPreferencesKey("USE_LANGUAGE_SPECIFIC_MODELS"), true)
val ALLOW_UNDERTRAINED_LANGUAGES = SettingsKey(booleanPreferencesKey("allow_undertrained_languages"), false)
val MANUALLY_SELECT_LANGUAGE     = SettingsKey(booleanPreferencesKey("manually_select_language"),     false)
val LANGUAGE_TOGGLES             = SettingsKey(stringSetPreferencesKey("enabled_languages"),          setOf("en"))
val ENGLISH_MODEL_INDEX          = SettingsKey(intPreferencesKey("english_model_index"),              4) // asr4all-m
val MULTILINGUAL_MODEL_INDEX     = SettingsKey(intPreferencesKey("multilingual_model_index"),         1) // base whisper

// ── Bilingual mid-sentence switching ─────────────────────────────────────────
val ENABLE_MIDSENTENCE_SWITCHING = SettingsKey(booleanPreferencesKey("enable_midsentence_switching"), false)
val PRIMARY_LANGUAGE_PAIR        = SettingsKey(stringSetPreferencesKey("primary_language_pair"),      setOf("en", "de"))

// ── Model URIs (SAF — persisted across reboots) ───────────────────────────────
val MODEL_URI_ENGLISH_0      = SettingsKey(stringPreferencesKey("model_uri_en_0"),  "")
val MODEL_URI_ENGLISH_1      = SettingsKey(stringPreferencesKey("model_uri_en_1"),  "")
val MODEL_URI_ENGLISH_2      = SettingsKey(stringPreferencesKey("model_uri_en_2"),  "")
val MODEL_URI_ENGLISH_3      = SettingsKey(stringPreferencesKey("model_uri_en_3"),  "")
val MODEL_URI_ENGLISH_4      = SettingsKey(stringPreferencesKey("model_uri_en_4"),  "")
val MODEL_URI_ENGLISH_5      = SettingsKey(stringPreferencesKey("model_uri_en_5"),  "")
val MODEL_URI_ENGLISH_6      = SettingsKey(stringPreferencesKey("model_uri_en_6"),  "")
val MODEL_URI_ENGLISH_7      = SettingsKey(stringPreferencesKey("model_uri_en_7"),  "")
val MODEL_URI_ENGLISH_8      = SettingsKey(stringPreferencesKey("model_uri_en_8"),  "")
val MODEL_URI_ENGLISH_9      = SettingsKey(stringPreferencesKey("model_uri_en_9"),  "")
val MODEL_URI_MULTILINGUAL_0 = SettingsKey(stringPreferencesKey("model_uri_ml_0"),  "")
val MODEL_URI_MULTILINGUAL_1 = SettingsKey(stringPreferencesKey("model_uri_ml_1"),  "")
val MODEL_URI_MULTILINGUAL_2 = SettingsKey(stringPreferencesKey("model_uri_ml_2"),  "")
val MODEL_URI_MULTILINGUAL_3 = SettingsKey(stringPreferencesKey("model_uri_ml_3"),  "")

// ── Dictionaries ──────────────────────────────────────────────────────────────
val PERSONAL_DICTIONARY          = SettingsKey(stringPreferencesKey("personal_dict"),             "")
val LANGUAGE_DICTIONARIES        = SettingsKey(stringPreferencesKey("language_dicts"),            "{}")
val UNIVERSAL_DICT_EXCLUDED_LANGS = SettingsKey(stringSetPreferencesKey("universal_dict_excluded_langs"), emptySet())
val DICT_CROSS_LANG_VARIANTS     = SettingsKey(stringPreferencesKey("dict_cross_lang_variants"),  "[]")

// ── Migration ─────────────────────────────────────────────────────────────────
val MODELS_MIGRATED       = SettingsKey(booleanPreferencesKey("models_migrated_1"),  false)
val DISMISS_MIGRATION_TIP = SettingsKey(booleanPreferencesKey("dismiss_migration_tip"), false)

// ── Theme ─────────────────────────────────────────────────────────────────────
val THEME_KEY = SettingsKey(stringPreferencesKey("activeThemeOption"), VoiceInputTheme.key)

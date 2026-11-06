package org.futo.voiceinput.settings.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.voiceinput.R
import org.futo.voiceinput.settings.DICTIONARY_V2
import org.futo.voiceinput.settings.LANGUAGE_TOGGLES
import org.futo.voiceinput.settings.PERSONAL_DICTIONARY
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.SettingItem
import org.futo.voiceinput.settings.SettingsRootList
import org.futo.voiceinput.settings.useDataStore
import org.futo.voiceinput.shared.whisper.DictKind
import org.futo.voiceinput.shared.whisper.DictionaryEntry
import org.futo.voiceinput.shared.whisper.DictionaryJson
import java.util.Locale

private fun langName(code: String) =
    if (code == DictionaryEntry.ALL) "" else Locale(code).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.uppercase() }

/**
 * One list for everything: words that help recognition (per language, for all languages, or linked spellings
 * across languages such as "Venlafaxin" / "venlafaxine") and blocked words/phrases.
 */
@Composable
fun DictionaryScreen(navController: NavHostController = rememberNavController()) {
    val store = useDataStore(DICTIONARY_V2.key, DICTIONARY_V2.default)
    val legacy = useDataStore(PERSONAL_DICTIONARY.key, PERSONAL_DICTIONARY.default)
    val enabled = useDataStore(LANGUAGE_TOGGLES.key, LANGUAGE_TOGGLES.default).value.sorted()

    val entries = remember(store.value) { DictionaryJson.parse(store.value).toMutableStateList() }
    fun save() = store.setValue(DictionaryJson.toJson(entries.toList()))

    // One-time import of the old plain-text dictionary: each word becomes an entry for all languages.
    LaunchedEffect(legacy.value) {
        if (legacy.value.isNotBlank()) {
            val words = legacy.value.split("\n", ",").map { it.trim() }.filter { it.isNotEmpty() }
            val known = entries.flatMap { it.variants.values }.toSet()
            words.filter { it !in known }.forEach {
                entries.add(DictionaryEntry(DictKind.WORD, mapOf(DictionaryEntry.ALL to it)))
            }
            save()
            legacy.setValue("")
        }
    }

    var editing by remember { mutableStateOf<Int?>(null) } // index, or -1 for new
    var newKind by remember { mutableStateOf(DictKind.WORD) }

    SettingsRootList {
        ScreenTitle(stringResource(R.string.lynx_dictionary), showBack = true, navController = navController)

        SettingItem(
            title = stringResource(R.string.lynx_dict_add_word),
            subtitle = stringResource(R.string.lynx_dict_add_word_subtitle),
            onClick = { newKind = DictKind.WORD; editing = -1 }
        ) { }
        SettingItem(
            title = stringResource(R.string.lynx_dict_add_block),
            subtitle = stringResource(R.string.lynx_dict_add_block_subtitle),
            onClick = { newKind = DictKind.BLOCK; editing = -1 }
        ) { }

        for (kind in DictKind.values()) {
            val items = entries.withIndex().filter { it.value.kind == kind }
            if (items.isEmpty()) continue
            Text(
                stringResource(if (kind == DictKind.WORD) R.string.lynx_dict_words else R.string.lynx_dict_blocked),
                modifier = Modifier.padding(start = 76.dp, top = 20.dp, bottom = 4.dp),
                style = org.futo.voiceinput.theme.Typography.Heading.RegularMl
            )
            items.forEach { (i, e) ->
                val title = e.variants.entries.joinToString("  ·  ") { (k, v) ->
                    if (k == DictionaryEntry.ALL) v else "${k.uppercase()}: $v"
                }
                val sub = when {
                    DictionaryEntry.ALL in e.variants && e.excluded.isNotEmpty() ->
                        stringResource(R.string.lynx_dict_all_except, e.excluded.sorted().joinToString { it.uppercase() })
                    DictionaryEntry.ALL in e.variants -> stringResource(R.string.lynx_dict_all_languages)
                    else -> null
                }
                SettingItem(title = title, subtitle = sub, compact = true, onClick = { editing = i }) { }
            }
        }
    }

    val idx = editing
    if (idx != null) {
        val base = if (idx >= 0) entries[idx] else DictionaryEntry(newKind, emptyMap())
        var kind by remember(idx) { mutableStateOf(base.kind) }
        val fields = remember(idx) {
            mutableStateOf(base.variants)
        }
        var excluded by remember(idx) { mutableStateOf(base.excluded) }
        // languages of this entry that are not enabled stay visible so nothing is lost silently
        val codes = (enabled + base.variants.keys.filter { it != DictionaryEntry.ALL && it !in enabled }).distinct()

        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(if (idx >= 0) R.string.lynx_dict_edit else R.string.lynx_dict_new)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = kind == DictKind.WORD, onClick = { kind = DictKind.WORD })
                        Text(stringResource(R.string.lynx_dict_kind_word))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = kind == DictKind.BLOCK, onClick = { kind = DictKind.BLOCK })
                        Text(stringResource(R.string.lynx_dict_kind_block))
                    }
                    Text(
                        stringResource(if (kind == DictKind.WORD) R.string.lynx_dict_hint_word else R.string.lynx_dict_hint_block),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    val all = listOf(DictionaryEntry.ALL) + codes
                    all.forEach { code ->
                        OutlinedTextField(
                            value = fields.value[code] ?: "",
                            onValueChange = { v -> fields.value = fields.value + (code to v) },
                            label = { Text(if (code == DictionaryEntry.ALL) stringResource(R.string.lynx_dict_all_languages) else langName(code)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                    }
                    if (!fields.value[DictionaryEntry.ALL].isNullOrBlank() && codes.isNotEmpty()) {
                        Text(stringResource(R.string.lynx_dict_except), modifier = Modifier.padding(top = 8.dp))
                        codes.forEach { code ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = code in excluded,
                                    onCheckedChange = { on -> excluded = if (on) excluded + code else excluded - code }
                                )
                                Text(langName(code))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cleaned = fields.value.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }
                    val entry = DictionaryEntry(kind, cleaned, if (DictionaryEntry.ALL in cleaned) excluded else emptySet())
                    if (cleaned.isEmpty()) {
                        if (idx >= 0) entries.removeAt(idx)
                    } else if (idx >= 0) entries[idx] = entry else entries.add(entry)
                    save()
                    editing = null
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                Row {
                    if (idx >= 0) TextButton(onClick = { entries.removeAt(idx); save(); editing = null }) {
                        Text(stringResource(R.string.lynx_dict_delete))
                    }
                    TextButton(onClick = { editing = null }) { Text(stringResource(android.R.string.cancel)) }
                }
            }
        )
    }
}

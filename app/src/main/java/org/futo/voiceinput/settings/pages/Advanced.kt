package org.futo.voiceinput.settings.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.futo.voiceinput.settings.IGNORE_NON_SPEECH
import org.futo.voiceinput.settings.LANGUAGE_TOGGLES
import org.futo.voiceinput.settings.PRIMARY_LANGUAGES
import org.futo.voiceinput.settings.SHOW_DETECTED_LANGUAGES
import org.futo.voiceinput.settings.SettingItem
import org.futo.voiceinput.settings.StepSliderSetting
import org.futo.voiceinput.settings.PAUSE_BUTTON_RIGHT
import org.futo.voiceinput.settings.DOUBLE_BACK_EXIT
import org.futo.voiceinput.settings.STOP_GRACE_MS
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import java.util.Locale
import org.futo.voiceinput.settings.IS_VAD_ENABLED
import org.futo.voiceinput.settings.USE_CHUNK_CONTEXT
import org.futo.voiceinput.settings.PAUSE_GAP_MS
import org.futo.voiceinput.settings.useDataStore
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.voiceinput.R
import org.futo.voiceinput.settings.ALLOW_UNDERTRAINED_LANGUAGES
import org.futo.voiceinput.settings.BEAM_SEARCH
import org.futo.voiceinput.settings.DISALLOW_SYMBOLS
import org.futo.voiceinput.settings.ENABLE_30S_LIMIT
import org.futo.voiceinput.settings.MULTI_LANGUAGE_IN_RECORDING
import org.futo.voiceinput.settings.NavigationItem
import org.futo.voiceinput.settings.NavigationItemStyle
import org.futo.voiceinput.settings.PER_LANGUAGE_MODELS_IN_MIXED
import org.futo.voiceinput.settings.STREAMING_TIER
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.SettingRadio
import org.futo.voiceinput.settings.SettingToggleDataStore
import org.futo.voiceinput.settings.SettingsRootList
import org.futo.voiceinput.settings.SettingsViewModel
import org.futo.voiceinput.settings.VERBOSE_PROGRESS
import org.futo.voiceinput.settings.openImeOptions

@Composable
@Preview
fun AdvancedScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    SettingsRootList {
        ScreenTitle(title = stringResource(id = R.string.advanced_settings), showBack = true, navController = navController)

        SettingToggleDataStore(
            stringResource(R.string.suppress_non_speech_annotations),
            DISALLOW_SYMBOLS,
            subtitle = stringResource(R.string.suppress_non_speech_annotations_subtitle)
        )

        SettingToggleDataStore(
            stringResource(R.string.verbose_mode),
            VERBOSE_PROGRESS
        )

        SettingToggleDataStore(stringResource(R.string.use_beam_search), BEAM_SEARCH, subtitle = stringResource(R.string.recommended))

        SettingToggleDataStore(
            stringResource(R.string.allow_undertrained_languages),
            ALLOW_UNDERTRAINED_LANGUAGES,
            subtitle = stringResource(R.string.allow_undertrained_languages_subtitle),
            onChanged = {
            }
        )

        SettingToggleDataStore(
            stringResource(R.string.lynx_multi_language_in_recording),
            MULTI_LANGUAGE_IN_RECORDING,
            subtitle = stringResource(R.string.lynx_multi_language_in_recording_subtitle)
        )

        SettingToggleDataStore(
            stringResource(R.string.lynx_per_language_models_in_mixed),
            PER_LANGUAGE_MODELS_IN_MIXED,
            subtitle = stringResource(R.string.lynx_per_language_models_in_mixed_subtitle)
        )

        SettingToggleDataStore(
            stringResource(R.string.lynx_show_detected_languages),
            SHOW_DETECTED_LANGUAGES,
            subtitle = stringResource(R.string.lynx_show_detected_languages_subtitle)
        )

        // Primary language pair for mixed-language recordings (optional)
        val enabledCodes = useDataStore(LANGUAGE_TOGGLES.key, LANGUAGE_TOGGLES.default).value
        val primary = useDataStore(PRIMARY_LANGUAGES.key, PRIMARY_LANGUAGES.default)
        var showPrimaryDialog by remember { mutableStateOf(false) }
        fun langName(code: String) = Locale(code).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.uppercase() }
        val chosen = primary.value.filter { it in enabledCodes }
        SettingItem(
            title = stringResource(R.string.lynx_primary_languages),
            subtitle = if (chosen.size >= 2) chosen.joinToString(" + ") { langName(it) }
                       else stringResource(R.string.lynx_primary_languages_subtitle),
            onClick = { showPrimaryDialog = true },
            disabled = enabledCodes.size < 3,
            icon = { }
        ) { }
        if (showPrimaryDialog) {
            var selection by remember { mutableStateOf(chosen.toSet()) }
            val maxPick = enabledCodes.size - 1
            AlertDialog(
                onDismissRequest = { showPrimaryDialog = false },
                title = { Text(stringResource(R.string.lynx_primary_languages)) },
                text = {
                    Column {
                        Text(stringResource(R.string.lynx_primary_languages_hint, maxPick))
                        TextButton(onClick = { selection = emptySet() }, enabled = selection.isNotEmpty()) {
                            Text(stringResource(R.string.lynx_select_none))
                        }
                        enabledCodes.sorted().forEach { code ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = code in selection,
                                    onCheckedChange = { on ->
                                        selection = if (on) { if (selection.size < maxPick) selection + code else selection } else selection - code
                                    }
                                )
                                Text(langName(code))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        // two or more languages (at most enabled − 1), or none (= all enabled languages are considered)
                        primary.setValue(if (selection.size >= 2) selection else emptySet())
                        showPrimaryDialog = false
                    }) { Text(stringResource(android.R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { showPrimaryDialog = false }) { Text(stringResource(android.R.string.cancel)) }
                }
            )
        }

        SettingToggleDataStore(
            stringResource(R.string.lynx_ignore_non_speech),
            IGNORE_NON_SPEECH,
            subtitle = stringResource(R.string.lynx_ignore_non_speech_subtitle)
        )

        SettingToggleDataStore(
            stringResource(R.string.lynx_use_chunk_context),
            USE_CHUNK_CONTEXT,
            subtitle = stringResource(R.string.lynx_use_chunk_context_subtitle)
        )

        // Pause length that splits speech (and is cut from the recording). Inactive while auto-stop is on.
        val autoStop = useDataStore(IS_VAD_ENABLED.key, IS_VAD_ENABLED.default).value
        val gap = useDataStore(PAUSE_GAP_MS.key, PAUSE_GAP_MS.default)
        StepSliderSetting(
            title = stringResource(R.string.lynx_pause_length_title),
            subtitle = stringResource(R.string.lynx_pause_length_subtitle),
            value = gap.value,
            onValueChange = { gap.setValue(it) },
            unit = "ms",
            sliderMin = 50, sliderMax = 1000, step = 25,
            inputMin = 10, inputMax = 1500,
            enabled = !autoStop,
        )

        val grace = useDataStore(STOP_GRACE_MS.key, STOP_GRACE_MS.default)
        StepSliderSetting(
            title = stringResource(R.string.lynx_stop_grace_title),
            subtitle = stringResource(R.string.lynx_stop_grace_subtitle),
            value = grace.value,
            onValueChange = { grace.setValue(it) },
            unit = "ms",
            sliderMin = 0, sliderMax = 1500, step = 100,
            inputMin = 0, inputMax = 3000,
        )

        SettingToggleDataStore(
            stringResource(R.string.lynx_pause_button_right),
            PAUSE_BUTTON_RIGHT,
            subtitle = stringResource(R.string.lynx_pause_button_right_subtitle)
        )

        SettingToggleDataStore(
            stringResource(R.string.lynx_double_back_exit),
            DOUBLE_BACK_EXIT,
            subtitle = stringResource(R.string.lynx_double_back_exit_subtitle)
        )

        NavigationItem(
            title = stringResource(R.string.open_input_method_settings),
            style = NavigationItemStyle.Misc,
            navigate = { openImeOptions(context) }
        )

        SettingToggleDataStore(
            stringResource(R.string.re_enable_30s_limit),
            ENABLE_30S_LIMIT,
        )

        SettingRadio(
            stringResource(R.string.model_settings_streaming_tier),
            listOf(0, 1, 2, 3),
            listOf(
                stringResource(R.string.model_settings_streaming_tier_0),
                stringResource(R.string.model_settings_streaming_tier_1),
                stringResource(R.string.model_settings_streaming_tier_2),
                stringResource(R.string.model_settings_streaming_tier_3),
            ),
            STREAMING_TIER,
        )

    }
}

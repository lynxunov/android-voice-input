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
import org.futo.voiceinput.settings.IS_VAD_ENABLED
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

        // Pause length that splits speech (and is cut from the recording). Inactive while auto-stop is on.
        val autoStop = useDataStore(IS_VAD_ENABLED.key, IS_VAD_ENABLED.default).value
        val gap = useDataStore(PAUSE_GAP_MS.key, PAUSE_GAP_MS.default)
        var sliderValue by remember(gap.value) { mutableFloatStateOf(gap.value.toFloat()) }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.lynx_pause_length, sliderValue.toInt()))
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = (it / 50f).toInt() * 50f },
                onValueChangeFinished = { gap.setValue(sliderValue.toInt()) },
                valueRange = 200f..1500f,
                enabled = !autoStop,
            )
        }

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

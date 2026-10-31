package org.futo.voiceinput

import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.launch
import org.futo.voiceinput.models.ModelSlot
import org.futo.voiceinput.models.toModelSafUri
import org.futo.voiceinput.settings.ENABLE_ANIMATIONS
import org.futo.voiceinput.settings.ENABLE_MULTILINGUAL
import org.futo.voiceinput.settings.ENABLE_SOUND
import org.futo.voiceinput.settings.ENGLISH_MODEL_INDEX
import org.futo.voiceinput.settings.LANGUAGE_TOGGLES
import org.futo.voiceinput.settings.MANUALLY_SELECT_LANGUAGE
import org.futo.voiceinput.settings.MULTILINGUAL_MODEL_INDEX
import org.futo.voiceinput.settings.PERSONAL_DICTIONARY
import org.futo.voiceinput.settings.PREFER_BLUETOOTH_MIC
import org.futo.voiceinput.settings.STREAMING_TIER
import org.futo.voiceinput.settings.USE_LANGUAGE_SPECIFIC_MODELS
import org.futo.voiceinput.settings.VERBOSE_PROGRESS
import org.futo.voiceinput.settings.getSetting
import org.futo.voiceinput.shared.RecognizerView as SharedRecognizerView
import org.futo.voiceinput.shared.RecognizerViewListener
import org.futo.voiceinput.shared.RecognizerViewSettings
import org.futo.voiceinput.shared.RecordingSettings
import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.types.getLanguageFromWhisperString
import org.futo.voiceinput.shared.ui.MicrophoneDeviceState
import org.futo.voiceinput.shared.whisper.DecodingConfiguration
import org.futo.voiceinput.shared.whisper.ModelManager
import org.futo.voiceinput.shared.whisper.MultiModelRunConfiguration

fun Modifier.recognizerSurfaceClickable(
    disabled: Boolean,
    onPauseVAD: (Boolean) -> Unit,
    onFinish: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val ripple = rememberRipple(bounded = false)

    this.pointerInput(disabled) {
        detectTapGestures(onPress = { offset ->
            if (!disabled) {
                val press = PressInteraction.Press(offset)
                interactionSource.emit(press)
                onPauseVAD(true)
                val didRelease = this.tryAwaitRelease()
                onPauseVAD(false)
                if (didRelease) {
                    interactionSource.emit(PressInteraction.Release(press))
                    onFinish()
                } else {
                    interactionSource.emit(PressInteraction.Cancel(press))
                }
            }
        })
    }.indication(interactionSource, ripple).semantics(mergeDescendants = true) { }
}

/**
 * Thin app-level wrapper around shared.RecognizerView.
 * Handles: SAF model resolution, app-specific settings loading, Android accessibility
 * announcements, and partial-result routing into the IME's composing text.
 */
abstract class RecognizerView {
    protected abstract val context: Context
    protected abstract val lifecycleScope: LifecycleCoroutineScope

    abstract fun setContent(content: @Composable () -> Unit)
    abstract fun onCancel()
    abstract fun sendResult(result: String)
    abstract fun sendPartialResult(result: String): Boolean
    abstract fun requestPermission(onGranted: () -> Unit, onRejected: () -> Unit)
    abstract fun decodingStarted()
    abstract fun modelNotFoundOpenPicker()

    private var sharedView: SharedRecognizerView? = null

    private fun announceAccessibility(result: String) {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        if (manager.isEnabled) {
            @Suppress("DEPRECATION")
            val event = AccessibilityEvent.obtain()
            event.packageName = RecognizerView::class.java.`package`!!.name
            event.className = RecognizerView::class.java.name
            event.eventTime = SystemClock.uptimeMillis()
            event.isEnabled = true
            event.text.add(result)
            event.eventType = AccessibilityEvent.TYPE_ANNOUNCEMENT
            manager.sendAccessibilityEvent(event)
        }
    }

    private val listener = object : RecognizerViewListener {
        override fun cancelled() = onCancel()
        override fun recordingStarted(device: MicrophoneDeviceState) {}
        override fun finished(result: String) {
            announceAccessibility(result)
            sendResult(result)
        }
        override fun partialResult(result: String) { sendPartialResult(result) }
        override fun requestPermission(onGranted: () -> Unit, onRejected: () -> Unit): Boolean {
            requestPermission(onGranted, onRejected)
            return true
        }
        override fun openSettings() { modelNotFoundOpenPicker() }
        override fun modelNotFound() { modelNotFoundOpenPicker() }
    }

    @Composable
    fun Content() { sharedView?.Content() }

    fun finish() { sharedView?.finish() }
    fun cancel() { sharedView?.cancel() }
    fun reset() { sharedView?.reset() }
    fun inhibitAutoStop(inhibit: Boolean) { sharedView?.inhibitAutoStop(inhibit) }

    /** True if currently showing the active recording bubble (not loading/error/language-select). */
    val isFinishable get() = sharedView?.isFinishable

    fun init() {
        lifecycleScope.launch {
            val languages = context.getSetting(LANGUAGE_TOGGLES)
                .mapNotNull { getLanguageFromWhisperString(it) }.toSet()
                .ifEmpty { setOf(Language.English) }

            val useMultilingual = context.getSetting(ENABLE_MULTILINGUAL)
            val useLangSpecific = context.getSetting(USE_LANGUAGE_SPECIFIC_MODELS)
            val englishIdx = context.getSetting(ENGLISH_MODEL_INDEX)
            val multilingualIdx = context.getSetting(MULTILINGUAL_MODEL_INDEX)

            val englishSlots = ModelSlot.values().filter { it.isEnglish }
            val multilingualSlots = ModelSlot.values().filter { !it.isEnglish }

            val englishModel = englishSlots.getOrNull(englishIdx)?.let { context.toModelSafUri(it) }
            val multilingualModel = multilingualSlots.getOrNull(multilingualIdx)?.let { context.toModelSafUri(it) }

            val primaryModel = if (useMultilingual) multilingualModel else englishModel
            if (primaryModel == null) { modelNotFoundOpenPicker(); return@launch }

            val languageSpecificModels = if (useMultilingual && useLangSpecific && englishModel != null && languages.contains(Language.English)) {
                mapOf(Language.English to englishModel)
            } else emptyMap()

            val glossary = context.getSetting(PERSONAL_DICTIONARY)
                .split(",", "\n").map { it.trim() }.filter { it.isNotBlank() }

            val streamingTier = context.getSetting(STREAMING_TIER)
            val preferBluetooth = context.getSetting(PREFER_BLUETOOTH_MIC)
            val allowManualLanguage = context.getSetting(MANUALLY_SELECT_LANGUAGE)

            sharedView = SharedRecognizerView(
                context = context,
                listener = listener,
                settings = RecognizerViewSettings(
                    shouldShowVerboseFeedback = context.getSetting(VERBOSE_PROGRESS),
                    shouldShowInlinePartialResult = true,
                    shouldAnimateBubble = context.getSetting(ENABLE_ANIMATIONS),
                    shouldShowLanguageSelector = allowManualLanguage && languages.size > 1,
                    shouldPlaySounds = context.getSetting(ENABLE_SOUND),
                    modelRunConfiguration = MultiModelRunConfiguration(
                        primaryModel = primaryModel,
                        languageSpecificModels = languageSpecificModels
                    ),
                    decodingConfiguration = DecodingConfiguration(
                        glossary = glossary,
                        languages = languages,
                        suppressSymbols = true,
                        tier = streamingTier
                    ),
                    recordingConfiguration = RecordingSettings(
                        preferBluetoothMic = preferBluetooth,
                        requestAudioFocus = true,
                        canExpandSpace = true,
                        useVADAutoStop = true
                    )
                ),
                lifecycleScope = lifecycleScope,
                modelManager = ModelManager(context)
            )
            sharedView?.start()
        }
    }

    fun permissionResultGranted() { /* handled via listener callback in requestPermission */ }
    fun permissionResultRejected() { /* handled via listener callback in requestPermission */ }
}

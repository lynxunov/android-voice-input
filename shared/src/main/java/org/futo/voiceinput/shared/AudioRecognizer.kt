package org.futo.voiceinput.shared

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.SensorPrivacyManager
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.MicrophoneDirection
import android.os.Build
import androidx.lifecycle.LifecycleCoroutineScope
import com.konovalov.vad.Vad
import com.konovalov.vad.config.FrameSize
import com.konovalov.vad.config.Mode
import com.konovalov.vad.config.Model
import com.konovalov.vad.config.SampleRate
import com.konovalov.vad.models.VadModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.futo.voiceinput.shared.ggml.InvalidModelException
import org.futo.voiceinput.shared.types.AudioRecognizerListener
import org.futo.voiceinput.shared.types.InferenceState
import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.types.MagnitudeState
import org.futo.voiceinput.shared.types.ModelInferenceCallback
import org.futo.voiceinput.shared.types.ModelLoader
import org.futo.voiceinput.shared.ui.MicrophoneDeviceState
import org.futo.voiceinput.shared.whisper.DecodingConfiguration
import org.futo.voiceinput.shared.whisper.ModelManager
import org.futo.voiceinput.shared.whisper.MultiModelRunConfiguration
import org.futo.voiceinput.shared.whisper.MultiModelRunner
import org.futo.voiceinput.shared.whisper.isBlankResult
import java.nio.ShortBuffer
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

data class RecordingSettings(
    val preferBluetoothMic: Boolean,
    val requestAudioFocus: Boolean,
    val canExpandSpace: Boolean,
    val useVADAutoStop: Boolean
)

data class AudioRecognizerSettings(
    val modelRunConfiguration: MultiModelRunConfiguration,
    val decodingConfiguration: DecodingConfiguration,
    val recordingConfiguration: RecordingSettings
)

class ModelDoesNotExistException(val models: List<ModelLoader>) : Throwable()

class AudioRecognizer(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    modelManager: ModelManager,
    private val listener: AudioRecognizerListener,
    private val settings: AudioRecognizerSettings
) {
    private var isRecording = false
    private var recorder: AudioRecord? = null

    private val modelRunner = MultiModelRunner(modelManager)
    private val canExpandSpace = settings.recordingConfiguration.canExpandSpace
    private val useVAD = settings.recordingConfiguration.useVADAutoStop
    private var inhibitVAD = false

    private val stopSignal = CompletableDeferred<Unit>()
    private val audioChannel = Channel<FloatArray>(Channel.UNLIMITED)
    private var recorderJob: Job? = null
    private var modelJob: Job? = null
    private var focusRequest: AudioFocusRequest? = null

    private fun focusAudio() {
        unfocusAudio()
        if (!settings.recordingConfiguration.requestAudioFocus) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                focusRequest = AudioFocusRequest
                    .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAcceptsDelayedFocusGain(false) // Android 17: fail fast
                    .build()
                audioManager.requestAudioFocus(focusRequest!!)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun unfocusAudio() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                focusRequest = null
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun isBluetoothAvailable(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.availableCommunicationDevices.any {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                }
            } else false
        } catch (_: Exception) { false }
    }

    private fun setCommunicationDevice(preferBluetoothMic: Boolean): Pair<Boolean, String> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val devices = audioManager.availableCommunicationDevices
                val tgtDevice =
                    devices.firstOrNull { preferBluetoothMic && it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                        ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
                        ?: devices.firstOrNull { it.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                        ?: devices.first()
                if (!audioManager.setCommunicationDevice(tgtDevice)) {
                    audioManager.clearCommunicationDevice(); Pair(false, "")
                } else {
                    Pair(tgtDevice.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO, tgtDevice.productName.toString())
                }
            } else Pair(false, "")
        } catch (_: Exception) { Pair(false, "") }
    }

    private fun clearCommunicationDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).clearCommunicationDevice()
        }
    }

    @Throws(ModelDoesNotExistException::class)
    private fun verifyModelsExist() {
        val missing = mutableListOf<ModelLoader>()
        if (!settings.modelRunConfiguration.primaryModel.exists(context))
            missing.add(settings.modelRunConfiguration.primaryModel)
        for (model in settings.modelRunConfiguration.languageSpecificModels.values)
            if (!model.exists(context)) missing.add(model)
        if (missing.isNotEmpty()) throw ModelDoesNotExistException(missing)
    }

    init { verifyModelsExist() }

    fun reset() {
        recorder?.stop(); recorderJob?.cancel()
        recorder?.release(); recorder = null
        modelJob?.cancel(); isRecording = false
        modelRunner.cancelAll(); unfocusAudio(); clearCommunicationDevice()
    }

    fun finish() { if (isRecording) onFinishRecording() }
    fun cancel() { reset(); listener.cancelled() }

    fun openPermissionSettings() {
        val packageName = context.packageName
        val intent = Intent().apply {
            setClassName(context, "org.futo.inputmethod.latin.MicPermissionActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
        cancel()
    }

    fun start() {
        listener.loading()
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermission()
        else startRecording()
    }

    fun inhibitAutoStop(inhibit: Boolean) { inhibitVAD = inhibit }

    private fun requestPermission() {
        listener.needPermission { wasGranted -> if (wasGranted) startRecording() }
    }

    @Throws(SecurityException::class)
    private fun createAudioRecorder(): AudioRecord {
        val minBuffer = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val r = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuffer)
        this.recorder = r
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            r.setPreferredMicrophoneDirection(MicrophoneDirection.MIC_DIRECTION_TOWARDS_USER)
        return r
    }

    private suspend fun preloadModels() { modelRunner.preload(settings.modelRunConfiguration) }

    private suspend fun recordingJob(recorder: AudioRecord, vad: VadModel?) {
        var hasTalked = false; var anyNoiseAtAll = false
        val canMicBeBlocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (context.getSystemService(SensorPrivacyManager::class.java) as SensorPrivacyManager)
                .supportsSensorToggle(SensorPrivacyManager.Sensors.MICROPHONE)
        else false
        var isMicBlocked = false
        val vadSampleBuffer = ShortBuffer.allocate(480)
        var numConsecutiveNonSpeech = 0; var numConsecutiveSpeech = 0
        val samples = ShortArray(1600)
        val recordingStartTime = System.currentTimeMillis()

        while (isRecording) {
            yield()
            val nRead = recorder.read(samples, 0, 1600, AudioRecord.READ_BLOCKING)
            if (nRead <= 0) break
            yield()
            if (hasTalked && (numConsecutiveNonSpeech > 66) && useVAD && !inhibitVAD) {
                withContext(Dispatchers.Main) { finish() }; return
            }
            if (useVAD && vad != null) {
                var remaining = nRead; var offset = 0
                while (remaining > 0) {
                    if (!vadSampleBuffer.hasRemaining()) {
                        val isSpeech = vad.isSpeech(vadSampleBuffer.array())
                        vadSampleBuffer.clear(); vadSampleBuffer.rewind()
                        if (!isSpeech) { numConsecutiveNonSpeech++; numConsecutiveSpeech = 0 }
                        else { numConsecutiveNonSpeech = 0; numConsecutiveSpeech++ }
                    }
                    val toRead = min(min(remaining, 480), vadSampleBuffer.remaining())
                    for (i in 0 until toRead) { vadSampleBuffer.put(samples[offset]); offset++; remaining-- }
                }
            }
            audioChannel.send(samples.sliceArray(0 until nRead).map { it.toFloat() / Short.MAX_VALUE.toFloat() }.toFloatArray())
            val startSoundPassed = (System.currentTimeMillis() - recordingStartTime) > 600
            if (!startSoundPassed) { numConsecutiveSpeech = 0; numConsecutiveNonSpeech = 0 }
            val rms = sqrt(samples.sumOf { (it.toFloat() / Short.MAX_VALUE.toFloat()).pow(2).toDouble() } / samples.size).toFloat()
            if (startSoundPassed && (rms > 0.01 || numConsecutiveSpeech > 8)) hasTalked = true
            if (rms > 0.0001) { anyNoiseAtAll = true; isMicBlocked = false }
            if (!anyNoiseAtAll && canMicBeBlocked && (System.currentTimeMillis() - recordingStartTime) > 2000) isMicBlocked = true
            val magnitude = (1.0f - 0.1f.pow(24.0f * rms))
            val state = if (hasTalked) MagnitudeState.TALKING else if (isMicBlocked) MagnitudeState.MIC_MAY_BE_BLOCKED else MagnitudeState.NOT_TALKED_YET
            yield()
            withContext(Dispatchers.Main) { listener.updateMagnitude(magnitude, state) }
            while (true) {
                yield()
                val nRead2 = recorder.read(samples, 0, 1600, AudioRecord.READ_NON_BLOCKING)
                if (nRead2 > 0) audioChannel.trySend(samples.sliceArray(0 until nRead2).map { it.toFloat() / Short.MAX_VALUE.toFloat() }.toFloatArray())
                else break
            }
        }
    }

    private fun createVad(): VadModel = Vad.builder().setModel(Model.WEB_RTC_GMM).setMode(Mode.VERY_AGGRESSIVE)
        .setFrameSize(FrameSize.FRAME_SIZE_480).setSampleRate(SampleRate.SAMPLE_RATE_16K)
        .setSpeechDurationMs(150).setSilenceDurationMs(300).build()

    @Throws(SecurityException::class)
    private fun createRecorderAndJob(preferBluetoothMic: Boolean): MicrophoneDeviceState {
        isRecording = false; recorder?.stop()
        val bluetoothInfo = setCommunicationDevice(preferBluetoothMic)
        val task = {
            recorder?.release()
            val recorder = createAudioRecorder()
            recorder.startRecording(); this.recorder = recorder; isRecording = true
            recorderJob = lifecycleScope.launch {
                withContext(Dispatchers.Default) {
                    if (useVAD) createVad().use { recordingJob(recorder, it) }
                    else recordingJob(recorder, null)
                }
            }
        }
        if (recorderJob != null) lifecycleScope.launch { recorderJob?.cancelAndJoin(); task() }
        else task()
        return MicrophoneDeviceState(
            bluetoothAvailable = bluetoothInfo.first || isBluetoothAvailable(),
            bluetoothActive = bluetoothInfo.first,
            deviceName = bluetoothInfo.second,
            bluetoothPreferredByUser = settings.recordingConfiguration.preferBluetoothMic,
            setBluetooth = { listener.recordingStarted(createRecorderAndJob(it)) }
        )
    }

    private fun startRecording() {
        val device = try {
            createRecorderAndJob(settings.recordingConfiguration.preferBluetoothMic)
        } catch (e: SecurityException) {
            clearCommunicationDevice(); requestPermission(); return
        }
        focusAudio(); listener.recordingStarted(device)
        modelJob = lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                try { preloadModels() } catch (_: InvalidModelException) {
                    withContext(Dispatchers.Main) { reset(); listener.modelLoadingFailed() }
                }
                yield()
                val text = modelRunner.run(
                    audioChannel.receiveAsFlow().takeUntil(stopSignal),
                    settings.modelRunConfiguration,
                    settings.decodingConfiguration,
                    runnerCallback
                )
                withContext(Dispatchers.Main) { yield(); listener.finished(text) }
            }
        }
    }

    private fun <T> Flow<T>.takeUntil(signal: Deferred<Unit>): Flow<T> = channelFlow {
        val collector = launch { collect { send(it) } }
        select { signal.onAwait {}; collector.onJoin {} }
        collector.cancel()
    }

    private val runnerCallback = object : ModelInferenceCallback {
        override fun updateStatus(state: InferenceState) { listener.decodingStatus(state) }
        override fun languageDetected(language: Language) { listener.languageDetected(language) }
        override fun partialResult(string: String) { if (!isBlankResult(string)) listener.partialResult(string) }
    }

    private fun onFinishRecording() {
        if (!isRecording) throw IllegalStateException("Should not call onFinishRecording when not recording")
        isRecording = false; recorder?.stop(); recorderJob?.cancel()
        listener.processing(); stopSignal.complete(Unit)
    }
}

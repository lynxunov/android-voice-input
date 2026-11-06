package org.futo.voiceinput.shared.whisper

import com.konovalov.vad.Vad
import com.konovalov.vad.config.FrameSize
import com.konovalov.vad.config.Mode
import com.konovalov.vad.config.Model
import com.konovalov.vad.config.SampleRate

/**
 * Fork addition: decides whether a chunk of 16 kHz audio really contains speech (WebRTC VAD, the same
 * engine as "Auto-stop on silence"). Rustling paper, taps or traffic make Whisper invent sentences; such
 * chunks are dropped before transcription. Judged by the *share* of speech frames, not by loudness, so
 * loud surroundings do not suppress real speech.
 */
fun chunkContainsSpeech(chunk: FloatArray): Boolean {
    val vad = Vad.builder().setModel(Model.WEB_RTC_GMM).setMode(Mode.VERY_AGGRESSIVE)
        .setFrameSize(FrameSize.FRAME_SIZE_480).setSampleRate(SampleRate.SAMPLE_RATE_16K)
        .setSpeechDurationMs(150).setSilenceDurationMs(300).build()
    return vad.use {
        val frame = ShortArray(480)
        var frames = 0
        var speech = 0
        var at = 0
        while (at + 480 <= chunk.size) {
            for (i in 0 until 480) frame[i] = (chunk[at + i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            if (it.isSpeech(frame)) speech++
            frames++
            at += 480
        }
        frames > 0 && speech >= 5 && speech * 5 >= frames
    }
}

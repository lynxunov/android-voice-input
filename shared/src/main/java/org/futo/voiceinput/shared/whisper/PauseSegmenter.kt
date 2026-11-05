package org.futo.voiceinput.shared.whisper

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Fork addition: splits a live 16 kHz mono sample stream into chunks at pauses between words/phrases.
 * Each chunk can then be language-detected and transcribed on its own, which is what makes switching
 * language between phrases of one recording possible (and keeps every inference short).
 *
 * Energy based (cheap, model independent). The speech level adapts, so quiet and loud rooms both work.
 */
private const val SAMPLE_RATE = 16000
private const val FRAME = SAMPLE_RATE / 20            // 50 ms
private const val DEFAULT_GAP_MS = 350                 // silence that closes a chunk
private const val MIN_SPEECH_FRAMES = 16              // a chunk needs >= 0.8 s of speech, else it merges forward
private const val TAIL_SAMPLES = SAMPLE_RATE / 10     // 100 ms of silence kept after speech
private const val HEAD_SAMPLES = SAMPLE_RATE * 3 / 20 // 150 ms of silence kept before next chunk
private const val MAX_CHUNK_SAMPLES = SAMPLE_RATE * 25
private const val MIN_THRESHOLD = 0.004f

private class FloatBuf {
    var data = FloatArray(SAMPLE_RATE * 8)
    var size = 0

    fun append(chunk: FloatArray) {
        if (size + chunk.size > data.size) data = data.copyOf(max(data.size * 2, size + chunk.size))
        chunk.copyInto(data, size)
        size += chunk.size
    }

    fun copy(from: Int, to: Int) = data.copyOfRange(from, to)

    fun dropFirst(n: Int) {
        data.copyInto(data, 0, n, size)
        size -= n
    }

    fun rms(from: Int, len: Int): Float {
        var sum = 0.0
        for (i in from until from + len) sum += data[i] * data[i]
        return sqrt(sum / len).toFloat()
    }
}

fun Flow<FloatArray>.segmentByPauses(gapMs: Int = DEFAULT_GAP_MS): Flow<FloatArray> {
    val upstream = this
    val gapFrames = max(2, gapMs / 50)
    return flow {
        val buf = FloatBuf()
        var scanned = 0
        var speechFrames = 0
        var silentRun = 0
        var lastSpeechEnd = 0
        var peak = 0f
        var emitted = 0

        upstream.collect { chunk ->
            buf.append(chunk)
            while (buf.size - scanned >= FRAME) {
                val rms = buf.rms(scanned, FRAME)
                scanned += FRAME
                peak = max(rms, peak * 0.995f)
                val threshold = max(MIN_THRESHOLD, peak * 0.12f)
                if (rms >= threshold) {
                    speechFrames++
                    silentRun = 0
                    lastSpeechEnd = scanned
                } else {
                    silentRun++
                }

                if (speechFrames >= MIN_SPEECH_FRAMES && silentRun >= gapFrames) {
                    val cut = minOf(buf.size, lastSpeechEnd + TAIL_SAMPLES)
                    emit(buf.copy(0, cut))
                    emitted++
                    val keepFrom = maxOf(cut, scanned - HEAD_SAMPLES).coerceAtMost(scanned)
                    buf.dropFirst(keepFrom)
                    scanned -= keepFrom
                    speechFrames = 0; silentRun = 0; lastSpeechEnd = 0
                } else if (scanned >= MAX_CHUNK_SAMPLES) {
                    emit(buf.copy(0, scanned))
                    emitted++
                    buf.dropFirst(scanned)
                    scanned = 0
                    speechFrames = 0; silentRun = 0; lastSpeechEnd = 0
                }
            }
        }

        // Remainder: keep it if it holds speech (or if nothing was emitted at all, like the classic path).
        if (buf.size > 0 && (speechFrames > 0 || emitted == 0)) emit(buf.copy(0, buf.size))
    }
}

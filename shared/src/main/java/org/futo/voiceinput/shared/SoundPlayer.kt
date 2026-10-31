package org.futo.voiceinput.shared

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION
import android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
import android.media.SoundPool
import java.io.Closeable

internal const val SoundPoolPlayFailure = 0
internal const val LoadStatusSuccess = 0

class SoundPlayer(private val context: Context) : Closeable {
    private val soundPool: SoundPool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
        AudioAttributes.Builder().setUsage(USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(CONTENT_TYPE_SONIFICATION).build()
    ).build()

    private var startSound: Int = -1
    private var cancelSound: Int = -1

    init {
        startSound = soundPool.load(context, R.raw.start, 0)
        cancelSound = soundPool.load(context, R.raw.cancel, 0)
    }

    override fun close() { soundPool.release() }

    private fun playSound(id: Int): Boolean =
        soundPool.play(id, 1.0f, 1.0f, 0, 0, 1.0f) != SoundPoolPlayFailure

    private fun playSoundOrLoad(id: Int) {
        if (!playSound(id)) {
            soundPool.setOnLoadCompleteListener { _, sampleId, status ->
                if (sampleId == id && status == LoadStatusSuccess) playSound(id)
            }
        }
    }

    fun playStartSound() { playSoundOrLoad(startSound) }
    fun playCancelSound() { playSoundOrLoad(cancelSound) }
}

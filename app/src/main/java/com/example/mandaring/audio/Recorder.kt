package com.example.mandaring.audio

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.mandaring.pitch.Audio

/** Records mono speech at [SAMPLE_RATE] with as little device-side processing as possible. */
class Recorder(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    /**
     * Blocks while recording, until [shouldStop] returns true or [maxMillis] have been captured.
     * Returns null if the microphone could not be opened.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun record(maxMillis: Int, shouldStop: () -> Boolean): Audio? {
        val source = audioSource()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val record = try {
            AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minBuffer, SAMPLE_RATE / 5 * 2))
                .build()
        } catch (e: UnsupportedOperationException) {
            Log.w(TAG, "Could not open microphone", e)
            return null
        }

        val samples = ShortArray(SAMPLE_RATE / 1000 * maxMillis)
        var count = 0
        try {
            record.startRecording()
            Log.i(TAG, "Recording: source=$source rate=${record.sampleRate} buffer=${record.bufferSizeInFrames}")
            val chunk = SAMPLE_RATE / 50
            while (count < samples.size && !shouldStop()) {
                val read = record.read(samples, count, minOf(chunk, samples.size - count))
                if (read <= 0) break
                count += read
            }
            record.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Recording failed", e)
            return null
        } finally {
            record.release()
        }
        return Audio(SAMPLE_RATE, FloatArray(count) { samples[it] / 32768f })
    }

    /** UNPROCESSED skips gain control and noise suppression, which would distort the signal. */
    private fun audioSource(): Int {
        val unprocessed = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
        return if (unprocessed == "true") {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        }
    }

    companion object {
        const val SAMPLE_RATE = 16000
        private const val TAG = "Recorder"
    }
}

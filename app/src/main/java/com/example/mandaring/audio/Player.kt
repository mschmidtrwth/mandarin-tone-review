package com.example.mandaring.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.mandaring.pitch.Audio
import com.example.mandaring.pitch.louder
import kotlinx.coroutines.delay

class Player {
    /**
     * Plays [audio] and suspends until it has finished, reporting the playback position.
     * Cancelling the caller stops playback.
     */
    suspend fun play(recording: Audio, onProgress: (positionMs: Float) -> Unit) {
        if (recording.samples.isEmpty()) return
        // Microphone recordings are quiet; only what is played is amplified, never what is stored or analysed.
        val audio = recording.louder()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(audio.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(audio.samples.size * Float.SIZE_BYTES)
            .build()
        try {
            track.write(audio.samples, 0, audio.samples.size, AudioTrack.WRITE_BLOCKING)
            track.play()
            while (true) {
                val position = track.playbackHeadPosition
                if (position >= audio.samples.size) break
                onProgress(position * 1000f / audio.sampleRate)
                delay(PROGRESS_INTERVAL_MS)
            }
        } finally {
            track.release()
        }
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 16L
    }
}

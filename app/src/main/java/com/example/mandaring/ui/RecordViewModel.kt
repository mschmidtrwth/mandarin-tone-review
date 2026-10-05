package com.example.mandaring.ui

import android.Manifest
import android.app.Application
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mandaring.R
import com.example.mandaring.audio.Player
import com.example.mandaring.audio.Recorder
import com.example.mandaring.data.TakeStore
import com.example.mandaring.pitch.Audio
import com.example.mandaring.pitch.PitchAnalyzer
import com.example.mandaring.pitch.PitchTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** A recording together with its analysis. */
class Take(val file: File, val audio: Audio, val track: PitchTrack)

data class RecordUiState(
    val recording: Boolean = false,
    val current: Take? = null,
    val takes: List<File> = emptyList(),
    /** Playback position within the current take, null when not playing. */
    val playbackMs: Float? = null,
    @param:StringRes val message: Int? = null,
)

class RecordViewModel(application: Application) : AndroidViewModel(application) {
    private val store = TakeStore(File(application.filesDir, "takes"))
    private val recorder = Recorder(application)
    private val player = Player()

    var state by mutableStateOf(RecordUiState(takes = store.list()))
        private set

    @Volatile
    private var stopRequested = false
    private var playback: Job? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording() {
        if (state.recording) return
        stopPlayback()
        stopRequested = false
        state = state.copy(recording = true, message = null)
        viewModelScope.launch {
            val audio = withContext(Dispatchers.IO) {
                recorder.record(MAX_TAKE_MS) { stopRequested }
            }
            state = state.copy(recording = false)
            when {
                audio == null -> state = state.copy(message = R.string.error_microphone)
                audio.durationMs < MIN_TAKE_MS -> state = state.copy(message = R.string.hint_hold)
                else -> {
                    val take = withContext(Dispatchers.Default) { analyze(store.save(audio), audio) }
                    state = state.copy(current = take, takes = store.list())
                }
            }
        }
    }

    fun stopRecording() {
        stopRequested = true
    }

    fun select(file: File) {
        stopPlayback()
        viewModelScope.launch {
            val take = withContext(Dispatchers.Default) {
                try {
                    analyze(file, store.load(file))
                } catch (e: IOException) {
                    Log.w(TAG, "Could not read $file", e)
                    null
                }
            }
            state = if (take != null) {
                state.copy(current = take, message = null)
            } else {
                state.copy(message = R.string.error_unreadable)
            }
        }
    }

    fun delete(file: File) {
        if (state.current?.file == file) stopPlayback()
        store.delete(file)
        state = state.copy(
            current = state.current?.takeIf { it.file != file },
            takes = store.list(),
        )
    }

    fun togglePlayback() {
        if (playback?.isActive == true) {
            stopPlayback()
            return
        }
        val take = state.current ?: return
        playback = viewModelScope.launch {
            try {
                player.play(take.audio) { state = state.copy(playbackMs = it) }
            } finally {
                state = state.copy(playbackMs = null)
            }
        }
    }

    private fun stopPlayback() {
        playback?.cancel()
        playback = null
    }

    private fun analyze(file: File, audio: Audio) =
        Take(file, audio, PitchAnalyzer(audio.sampleRate).analyze(audio.samples))

    private companion object {
        const val TAG = "RecordViewModel"
        const val MAX_TAKE_MS = 10_000
        const val MIN_TAKE_MS = 200f
    }
}

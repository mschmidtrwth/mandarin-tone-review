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
import com.example.mandaring.data.ProfileStore
import com.example.mandaring.data.Profiles
import com.example.mandaring.data.Reference
import com.example.mandaring.data.ReferenceLibrary
import com.example.mandaring.data.TakeStore
import com.example.mandaring.pitch.Audio
import com.example.mandaring.pitch.PitchAnalyzer
import com.example.mandaring.pitch.PitchHistogram
import com.example.mandaring.pitch.PitchRange
import com.example.mandaring.pitch.PitchTrack
import com.example.mandaring.pitch.WavIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** A recording together with its analysis. */
class Take(val file: File, val audio: Audio, val track: PitchTrack)

/** What the speaker is asked to record, in order, to establish their range. */
enum class CalibrationStep(@param:StringRes val prompt: Int) {
    LOW(R.string.calibration_low),
    HIGH(R.string.calibration_high),
    SPEECH(R.string.calibration_speech),
}

/** Progress through calibration: [step] recordings are done and pooled in [histogram]. */
data class CalibrationState(val step: Int = 0, val histogram: PitchHistogram = PitchHistogram()) {
    val current: CalibrationStep? get() = CalibrationStep.entries.getOrNull(step)
    val range: PitchRange? get() = histogram.range()
}

data class RecordUiState(
    val profiles: Profiles,
    val recording: Boolean = false,
    val current: Take? = null,
    val takes: List<File> = emptyList(),
    /** Playback position within the current take, null when not playing. */
    val playbackMs: Float? = null,
    /** The bundled recordings, null until they have been analysed. */
    val library: ReferenceLibrary? = null,
    /** The recording being practised against, null when recording freely. */
    val reference: Reference? = null,
    /** Playback position within [reference], null when not playing. */
    val referencePlaybackMs: Float? = null,
    /** Non-null while the calibration flow is open. */
    val calibration: CalibrationState? = null,
    @param:StringRes val message: Int? = null,
)

class RecordViewModel(application: Application) : AndroidViewModel(application) {
    private val profileStore = ProfileStore(File(application.filesDir, "profiles.json"))
    private val recorder = Recorder(application)
    private val player = Player()

    var state by mutableStateOf(
        profileStore.load(application.getString(R.string.default_profile_name)).let {
            RecordUiState(profiles = it, takes = takeStore(it).list())
        },
    )
        private set

    @Volatile
    private var stopRequested = false
    private var playback: Job? = null

    init {
        viewModelScope.launch {
            val started = System.nanoTime()
            val library = withContext(Dispatchers.Default) { ReferenceLibrary.load(application.assets) }
            Log.i(TAG, "Analysed ${library.items.size} references in ${(System.nanoTime() - started) / 1_000_000} ms")
            state = state.copy(library = library)
        }
    }

    private fun takeStore(profiles: Profiles = state.profiles) =
        TakeStore(File(getApplication<Application>().filesDir, "takes/${profiles.currentId}"))

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
                state.calibration != null -> addCalibrationRecording(audio)
                else -> addTake(audio)
            }
        }
    }

    fun stopRecording() {
        stopRequested = true
    }

    private suspend fun addTake(audio: Audio) {
        val store = takeStore()
        val take = withContext(Dispatchers.Default) { analyze(store.save(audio), audio) }
        // Every take refines the speaker's range a little.
        val profile = state.profiles.current
        updateProfiles(state.profiles.withCurrent(profile.copy(histogram = profile.histogram + take.track)))
        state = state.copy(current = take, takes = store.list())
    }

    private suspend fun addCalibrationRecording(audio: Audio) {
        val track = withContext(Dispatchers.Default) { PitchAnalyzer(audio.sampleRate).analyze(audio.samples) }
        val calibration = state.calibration ?: return
        state = if (track.voicedFrames < MIN_CALIBRATION_FRAMES) {
            state.copy(message = R.string.calibration_retry)
        } else {
            state.copy(calibration = CalibrationState(calibration.step + 1, calibration.histogram + track))
        }
    }

    fun startCalibration() {
        stopPlayback()
        state = state.copy(calibration = CalibrationState(), message = null)
    }

    fun cancelCalibration() {
        stopRecording()
        state = state.copy(calibration = null, message = null)
    }

    /** Replaces what was known about the current speaker's range with the calibration recordings. */
    fun saveCalibration() {
        val calibration = state.calibration ?: return
        if (calibration.range == null) return
        updateProfiles(state.profiles.withCurrent(state.profiles.current.copy(histogram = calibration.histogram)))
        state = state.copy(calibration = null, message = null)
    }

    fun selectProfile(id: String) {
        if (id == state.profiles.currentId) return
        switchTo(state.profiles.copy(currentId = id))
    }

    fun addProfile(name: String) {
        if (name.isBlank()) return
        switchTo(state.profiles.adding(name.trim()))
    }

    private fun switchTo(profiles: Profiles) {
        stopPlayback()
        updateProfiles(profiles)
        state = state.copy(current = null, takes = takeStore().list(), message = null)
    }

    private fun updateProfiles(profiles: Profiles) {
        state = state.copy(profiles = profiles)
        profileStore.save(profiles)
    }

    /** Picks the recording to practise against, or none. The take on show belonged to the previous one. */
    fun selectReference(reference: Reference?) {
        if (reference == state.reference) return
        stopPlayback()
        state = state.copy(reference = reference, current = null, message = null)
    }

    fun select(file: File) {
        stopPlayback()
        viewModelScope.launch {
            val take = withContext(Dispatchers.Default) {
                try {
                    analyze(file, WavIo.read(file))
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
        val store = takeStore()
        store.delete(file)
        state = state.copy(
            current = state.current?.takeIf { it.file != file },
            takes = store.list(),
        )
    }

    fun togglePlayback() {
        val wasPlaying = state.playbackMs != null
        stopPlayback()
        val take = state.current
        if (wasPlaying || take == null) return
        playback = viewModelScope.launch {
            player.play(take.audio) { state = state.copy(playbackMs = it) }
            state = state.copy(playbackMs = null)
        }
    }

    fun toggleReferencePlayback() {
        val wasPlaying = state.referencePlaybackMs != null
        stopPlayback()
        val reference = state.reference
        if (wasPlaying || reference == null || state.recording) return
        playback = viewModelScope.launch {
            player.play(reference.audio) { state = state.copy(referencePlaybackMs = it) }
            state = state.copy(referencePlaybackMs = null)
        }
    }

    private fun stopPlayback() {
        playback?.cancel()
        playback = null
        state = state.copy(playbackMs = null, referencePlaybackMs = null)
    }

    private fun analyze(file: File, audio: Audio) =
        Take(file, audio, PitchAnalyzer(audio.sampleRate).analyze(audio.samples))

    private companion object {
        const val TAG = "RecordViewModel"
        const val MAX_TAKE_MS = 10_000
        const val MIN_TAKE_MS = 200f

        /** Half a second of voiced sound, the least a calibration recording must contain. */
        const val MIN_CALIBRATION_FRAMES = 50
    }
}

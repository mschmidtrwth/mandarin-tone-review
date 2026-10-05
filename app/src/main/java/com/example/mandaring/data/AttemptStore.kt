package com.example.mandaring.data

import android.util.Log
import com.example.mandaring.pitch.Rating
import com.example.mandaring.pitch.Result
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/**
 * One attempt at saying a reference word.
 *
 * @property word the reference's name, e.g. `an1jing4`
 * @property distance how far the contour was from the reference, see `Similarity.distance`
 * @property recording the saved take's file name, null if the recording was not kept
 */
data class Attempt(
    val profileId: String,
    val word: String,
    val timeMs: Long,
    val rating: Rating,
    val distance: Float,
    val recording: String? = null,
) {
    val passed: Boolean get() = rating != Rating.OFF

    val result: Result get() = Result(timeMs, passed)
}

/** Every attempt ever made, as a log with one JSON object per line. Small enough to keep in memory. */
class AttemptStore(private val file: File) {
    /** Oldest first. Lines that cannot be read are skipped. */
    fun load(): List<Attempt> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            try {
                val json = JSONObject(line)
                Attempt(
                    profileId = json.getString("profile"),
                    word = json.getString("word"),
                    timeMs = json.getLong("time"),
                    rating = Rating.valueOf(json.getString("rating")),
                    distance = json.getDouble("distance").toFloat(),
                    recording = json.optString("recording").takeIf { it.isNotEmpty() },
                )
            } catch (e: JSONException) {
                Log.w(TAG, "Skipping unreadable attempt", e)
                null
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Skipping attempt with unknown rating", e)
                null
            }
        }
    }

    fun add(attempt: Attempt) {
        val json = JSONObject()
            .put("profile", attempt.profileId)
            .put("word", attempt.word)
            .put("time", attempt.timeMs)
            .put("rating", attempt.rating.name)
            .put("distance", attempt.distance.toDouble())
        attempt.recording?.let { json.put("recording", it) }
        file.appendText(json.toString() + "\n")
    }

    private companion object {
        const val TAG = "AttemptStore"
    }
}

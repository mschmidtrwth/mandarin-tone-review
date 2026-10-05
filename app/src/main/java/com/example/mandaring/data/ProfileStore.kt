package com.example.mandaring.data

import android.util.Log
import com.example.mandaring.pitch.PitchHistogram
import com.example.mandaring.pitch.PitchRange
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** One speaker and what is known about their voice. */
data class Profile(
    val id: String,
    val name: String,
    val histogram: PitchHistogram = PitchHistogram(),
) {
    /** Null until the speaker has calibrated or recorded enough speech. */
    val range: PitchRange? get() = histogram.range()
}

data class Profiles(val all: List<Profile>, val currentId: String) {
    val current: Profile get() = all.first { it.id == currentId }

    fun withCurrent(profile: Profile) = copy(all = all.map { if (it.id == profile.id) profile else it })

    fun adding(name: String): Profiles {
        val profile = Profile(UUID.randomUUID().toString(), name)
        return Profiles(all + profile, profile.id)
    }
}

/** Persists the speaker profiles as a small JSON file. */
class ProfileStore(private val file: File) {
    /** Starts with a single profile called [defaultName] if nothing usable is stored. */
    fun load(defaultName: String): Profiles {
        try {
            if (file.exists()) {
                val json = JSONObject(file.readText())
                val array = json.getJSONArray("profiles")
                val all = (0 until array.length()).map { index ->
                    val item = array.getJSONObject(index)
                    Profile(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        histogram = PitchHistogram.decode(item.optString("histogram")),
                    )
                }
                val currentId = json.optString("current")
                if (all.isNotEmpty()) {
                    return Profiles(all, currentId.takeIf { id -> all.any { it.id == id } } ?: all.first().id)
                }
            }
        } catch (e: JSONException) {
            Log.w(TAG, "Ignoring unreadable profiles file", e)
        }
        return Profiles(emptyList(), "").adding(defaultName)
    }

    fun save(profiles: Profiles) {
        val array = JSONArray()
        for (profile in profiles.all) {
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("histogram", profile.histogram.encode()),
            )
        }
        file.writeText(JSONObject().put("current", profiles.currentId).put("profiles", array).toString())
    }

    private companion object {
        const val TAG = "ProfileStore"
    }
}

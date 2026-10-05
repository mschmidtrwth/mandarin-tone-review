package com.example.mandaring.data

import com.example.mandaring.pitch.Audio
import com.example.mandaring.pitch.WavIo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Recorded takes, one WAV file each, named by recording time. */
class TakeStore(private val directory: File) {
    /** Newest first. */
    fun list(): List<File> =
        directory.listFiles { file -> file.extension == "wav" }.orEmpty().sortedByDescending { it.name }

    fun save(audio: Audio): File {
        directory.mkdirs()
        val name = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        return File(directory, "$name.wav").also { WavIo.write(it, audio) }
    }

    fun delete(file: File) {
        file.delete()
    }
}

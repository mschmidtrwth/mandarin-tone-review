package com.example.mandaring.pitch

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Mono audio with samples in -1..1. */
class Audio(val sampleRate: Int, val samples: FloatArray) {
    val durationMs: Float get() = samples.size * 1000f / sampleRate
}

/** Reads and writes 16-bit PCM WAV. Multi-channel input is mixed down to mono. */
object WavIo {
    private const val PCM_FORMAT = 1
    private const val HEADER_SIZE = 44

    fun read(file: File): Audio = file.inputStream().use { read(it) }

    fun read(input: InputStream): Audio {
        val buffer = ByteBuffer.wrap(input.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.remaining() < 12 || buffer.tag() != "RIFF") throw IOException("Not a RIFF file")
        buffer.int
        if (buffer.tag() != "WAVE") throw IOException("Not a WAVE file")

        var sampleRate = 0
        var channels = 0
        while (buffer.remaining() >= 8) {
            val tag = buffer.tag()
            val size = buffer.int
            val next = buffer.position() + size + (size and 1)
            when (tag) {
                "fmt " -> {
                    val format = buffer.short.toInt()
                    channels = buffer.short.toInt()
                    sampleRate = buffer.int
                    buffer.position(buffer.position() + 6)
                    val bitsPerSample = buffer.short.toInt()
                    if (format != PCM_FORMAT || bitsPerSample != 16) {
                        throw IOException("Only 16-bit PCM is supported (format $format, $bitsPerSample bit)")
                    }
                }
                "data" -> {
                    if (channels == 0) throw IOException("data chunk before fmt chunk")
                    val frames = minOf(size, buffer.remaining()) / (2 * channels)
                    val samples = FloatArray(frames) {
                        var sum = 0
                        repeat(channels) { sum += buffer.short }
                        sum / (channels * 32768f)
                    }
                    return Audio(sampleRate, samples)
                }
            }
            if (next > buffer.limit()) break
            buffer.position(next)
        }
        throw IOException("No data chunk found")
    }

    fun write(file: File, audio: Audio) = file.writeBytes(encode(audio))

    fun encode(audio: Audio): ByteArray {
        val dataSize = audio.samples.size * 2
        val buffer = ByteBuffer.allocate(HEADER_SIZE + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(HEADER_SIZE - 8 + dataSize).put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray()).putInt(16)
            .putShort(PCM_FORMAT.toShort())
            .putShort(1)
            .putInt(audio.sampleRate)
            .putInt(audio.sampleRate * 2)
            .putShort(2)
            .putShort(16)
        buffer.put("data".toByteArray()).putInt(dataSize)
        for (sample in audio.samples) {
            buffer.putShort((sample * 32767f).coerceIn(-32768f, 32767f).toInt().toShort())
        }
        return buffer.array()
    }

    private fun ByteBuffer.tag(): String = ByteArray(4).also { get(it) }.toString(Charsets.US_ASCII)
}

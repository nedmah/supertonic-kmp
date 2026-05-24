package com.nedmah.supertonic_kmp.internal

import kotlin.math.roundToInt


/**
 * Converts raw PCM float samples to a WAV [ByteArray].
 *
 * Output format: 44100 Hz, 16-bit PCM, mono — matches Supertonic v3 vocoder output.
 */
internal object WavBuilder {

    private const val SAMPLE_RATE = 44100
    private const val BITS_PER_SAMPLE = 16
    private const val CHANNELS = 1
    private const val HEADER_SIZE = 44

    /**
     * Converts float samples in [-1.0, 1.0] to a WAV [ByteArray].
     *
     * @param samples Raw PCM float samples from the vocoder.
     * @return Complete WAV file as [ByteArray], including the 44-byte header.
     */
    fun build(samples: FloatArray): ByteArray {
        val dataSize = samples.size * (BITS_PER_SAMPLE / 8)
        val totalSize = HEADER_SIZE + dataSize
        val result = ByteArray(totalSize)

        writeHeader(result, dataSize)
        writePcm(result, samples)

        return result
    }

    // private

    private fun writeHeader(out: ByteArray, dataSize: Int) {
        val byteRate = SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE / 8)
        val blockAlign = CHANNELS * (BITS_PER_SAMPLE / 8)

        // RIFF chunk
        out.writeAscii(0, "RIFF")
        out.writeInt32Le(4, out.size - 8)
        out.writeAscii(8, "WAVE")

        // fmt sub-chunk
        out.writeAscii(12, "fmt ")
        out.writeInt32Le(16, 16)            // sub-chunk size
        out.writeInt16Le(20, 1)             // PCM = 1
        out.writeInt16Le(22, CHANNELS)
        out.writeInt32Le(24, SAMPLE_RATE)
        out.writeInt32Le(28, byteRate)
        out.writeInt16Le(32, blockAlign)
        out.writeInt16Le(34, BITS_PER_SAMPLE)

        // data sub-chunk
        out.writeAscii(36, "data")
        out.writeInt32Le(40, dataSize)
    }

    private fun writePcm(out: ByteArray, samples: FloatArray) {
        var offset = HEADER_SIZE
        for (sample in samples) {
            val clamped = sample.coerceIn(-1f, 1f)
            val pcm = (clamped * Short.MAX_VALUE).roundToInt().toShort()
            out[offset++] = (pcm.toInt() and 0xFF).toByte()
            out[offset++] = ((pcm.toInt() shr 8) and 0xFF).toByte()
        }
    }

    // --- ByteArray write helpers ---

    private fun ByteArray.writeAscii(offset: Int, value: String) {
        value.forEachIndexed { i, c -> this[offset + i] = c.code.toByte() }
    }

    private fun ByteArray.writeInt32Le(offset: Int, value: Int) {
        this[offset + 0] = (value and 0xFF).toByte()
        this[offset + 1] = ((value shr 8) and 0xFF).toByte()
        this[offset + 2] = ((value shr 16) and 0xFF).toByte()
        this[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun ByteArray.writeInt16Le(offset: Int, value: Int) {
        this[offset + 0] = (value and 0xFF).toByte()
        this[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }
}
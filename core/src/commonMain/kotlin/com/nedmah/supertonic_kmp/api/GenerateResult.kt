package com.nedmah.supertonic_kmp.api

/**
 * Audio generation result.
 */
sealed class GenerateResult {

    /**
     * Successful generation.
     *
     * @param wav : Bytes of the WAV file. Format: 44100 Hz, 16-bit PCM, mono.
     * @param durationMs : Duration of the generated audio in milliseconds.
     */
    data class Success(
        val wav: ByteArray,
        val durationMs: Long,
    ) : GenerateResult() {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            return durationMs == other.durationMs && wav.contentEquals(other.wav)
        }

        override fun hashCode(): Int {
            var result = wav.contentHashCode()
            result = 31 * result + durationMs.hashCode()
            return result
        }
    }

    /** Generation error. */
    data class Error(val cause: Throwable) : GenerateResult()
}

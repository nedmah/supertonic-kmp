package com.nedmah.supertonic_kmp.api

/**
 * Platform player for speak().
 * Used only if you call speak() — if you use generate(),
 * the player is not used at all.
 */
internal expect object PlatformPlayer {
    /** Play WAV sync (blocks till the end). */
    fun play(wav: ByteArray)
    fun stop()
}

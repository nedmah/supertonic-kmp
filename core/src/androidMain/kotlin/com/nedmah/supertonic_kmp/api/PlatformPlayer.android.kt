package com.nedmah.supertonic_kmp.api

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

private const val WAV_HEADER_SIZE = 44
private const val SAMPLE_RATE = 44100

/**
 * Android [PlatformPlayer] backed by [AudioTrack].
 *
 * Used only by [SupertonicTts.speak]. If you use [SupertonicTts.generate],
 * this object is never touched.
 *
 * Playback is synchronous - [play] blocks until the audio finishes.
 * [stop] is safe to call from any thread.
 */
actual object PlatformPlayer {

    @Volatile private var activeTrack: AudioTrack? = null

    /**
     * Plays a WAV [ByteArray] synchronously, blocking until playback completes.
     *
     * Skips the 44-byte WAV header and writes raw 16-bit PCM mono at 44100 Hz
     * directly to [AudioTrack].
     */
    actual fun play(wav: ByteArray) {
        if (wav.size <= WAV_HEADER_SIZE) return

        val pcmData = wav.copyOfRange(WAV_HEADER_SIZE, wav.size)
        val sampleCount = pcmData.size / 2

        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = maxOf(minBuffer, pcmData.size)

        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )

        val latch = java.util.concurrent.CountDownLatch(1)
        track.notificationMarkerPosition = sampleCount
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(track: AudioTrack) { latch.countDown() }
            override fun onPeriodicNotification(track: AudioTrack) {}
        })

        activeTrack = track
        try {
            track.play()
            track.write(pcmData, 0, pcmData.size)
            // Drain - wait until all data is played out
            latch.await()
        } finally {
            activeTrack = null
            track.stop()
            track.release()
        }
    }

    /** Stops the current playback immediately. Safe to call from any thread. */
    actual fun stop() {
        activeTrack?.stop()
    }
}